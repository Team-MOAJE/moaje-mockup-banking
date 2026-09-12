package com.example.bankingmockup.transfer.infrastructure

import com.example.bankingmockup.account.domain.AccountCanceledException
import com.example.bankingmockup.account.domain.AccountNotFoundException
import com.example.bankingmockup.account.domain.InsufficientBalanceException
import com.example.bankingmockup.account.domain.TransactionHistory
import com.example.bankingmockup.account.domain.TransactionType
import com.example.bankingmockup.account.infrastructure.RedisAccountKeys
import com.example.bankingmockup.transfer.domain.LedgerTransferCommand
import com.example.bankingmockup.transfer.domain.TransferLookupResult
import com.example.bankingmockup.transfer.domain.TransferLookupStatus
import com.example.bankingmockup.transfer.domain.TransferRepository
import com.example.bankingmockup.transfer.domain.TransferReversalResult
import com.example.bankingmockup.transfer.domain.TransferResult
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.script.DefaultRedisScript
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.UUID

@Repository
class RedisTransferRepository(
    private val redisTemplate: StringRedisTemplate,
    private val objectMapper: ObjectMapper,
) : TransferRepository {
    override fun transfer(
        command: LedgerTransferCommand,
        debitHistory: TransactionHistory,
        creditHistory: TransactionHistory,
    ): TransferResult {
        val script = DefaultRedisScript(TRANSFER_SCRIPT, String::class.java)
        val result = redisTemplate.execute(
            script,
            listOf(
                RedisAccountKeys.account(command.fromAccountNumber),
                RedisAccountKeys.account(command.toAccountNumber),
                RedisAccountKeys.history(command.fromAccountNumber),
                RedisAccountKeys.history(command.toAccountNumber),
                RedisTransferKeys.transfer(command.clientTransferId),
            ),
            command.clientTransferId,
            command.amount.toString(),
            debitHistory.createdAt.toEpochMilli().toString(),
            objectMapper.writeValueAsString(debitHistory),
            objectMapper.writeValueAsString(creditHistory),
            debitHistory.transactionId,
            creditHistory.transactionId,
            command.fromAccountNumber,
            command.toAccountNumber,
        ) ?: throw IllegalStateException("Redis transfer script returned null")

        return when {
            result == "FROM_NOT_FOUND" -> throw AccountNotFoundException(command.fromAccountNumber)
            result == "TO_NOT_FOUND" -> throw AccountNotFoundException(command.toAccountNumber)
            result == "FROM_CANCELED" -> throw AccountCanceledException(command.fromAccountNumber)
            result == "TO_CANCELED" -> throw AccountCanceledException(command.toAccountNumber)
            result == "INSUFFICIENT_BALANCE" -> throw InsufficientBalanceException(command.fromAccountNumber)
            result == "DUPLICATE_TRANSFER" -> throw IllegalStateException("Duplicate clientTransferId: ${command.clientTransferId}")
            result.startsWith("OK:") -> {
                val tokens = result.split(":")
                TransferResult(
                    clientTransferId = command.clientTransferId,
                    fromAccountNumber = command.fromAccountNumber,
                    toAccountNumber = command.toAccountNumber,
                    amount = command.amount,
                    fromBalance = tokens[1].toLong(),
                    toBalance = tokens[2].toLong(),
                    debitHistory = debitHistory,
                    creditHistory = creditHistory,
                )
            }
            else -> throw IllegalStateException("Unexpected transfer result: $result")
        }
    }

    override fun findByClientTransferId(clientTransferId: String): TransferLookupResult? {
        val transferKey = RedisTransferKeys.transfer(clientTransferId)
        val transfer = redisTemplate.opsForHash<String, String>().entries(transferKey)
        if (transfer.isEmpty()) {
            return null
        }

        val fromAccountNumber = transfer["fromAccountNumber"] ?: return null
        val toAccountNumber = transfer["toAccountNumber"] ?: return null
        val amount = transfer["amount"]?.toLongOrNull() ?: return null
        val debitTransactionId = transfer["debitTransactionId"] ?: return null
        val creditTransactionId = transfer["creditTransactionId"] ?: return null
        val fromBalance = redisTemplate.opsForHash<String, String>()
            .get(RedisAccountKeys.account(fromAccountNumber), "balance")
            ?.toLongOrNull() ?: 0L
        val toBalance = redisTemplate.opsForHash<String, String>()
            .get(RedisAccountKeys.account(toAccountNumber), "balance")
            ?.toLongOrNull() ?: 0L
        val status = if (transfer["reversed"] == "true") {
            TransferLookupStatus.REVERSED
        } else {
            TransferLookupStatus.SUCCEEDED
        }

        return TransferLookupResult(
            clientTransferId = clientTransferId,
            fromAccountNumber = fromAccountNumber,
            toAccountNumber = toAccountNumber,
            amount = amount,
            fromBalance = fromBalance,
            toBalance = toBalance,
            debitTransactionId = debitTransactionId,
            creditTransactionId = creditTransactionId,
            status = status,
            debitReversalTransactionId = transfer["debitReversalTransactionId"],
            creditReversalTransactionId = transfer["creditReversalTransactionId"],
            reversedAt = transfer["reversedAtEpochMillis"]?.toLongOrNull()?.let(Instant::ofEpochMilli),
        )
    }

    override fun reverse(clientTransferId: String, reversedAt: Instant): TransferReversalResult {
        /**
         * 망취소는 원 송금 레코드가 있어야 정확히 수행할 수 있습니다.
         *
         * 트러블 슈팅 포인트:
         * - timeout 이후 원 송금이 실제 반영됐는지 알 수 없기 때문에 clientTransferId로 Redis에 저장된 원 송금 레코드를 찾습니다.
         * - 레코드가 없으면 목업 환경에서는 "원 송금이 반영되지 않았거나 이미 취소할 것이 없음"으로 보고 멱등 응답을 반환합니다.
         *
         * Kotlin 포인트:
         * - nullable 값(`String?`, `Long?`)을 먼저 확인한 뒤 아래 로직에서는 non-null 값처럼 사용합니다.
         * - early return을 사용하면 중첩 if를 줄이고 정상 흐름을 아래에 두기 좋습니다.
         */
        val transferKey = RedisTransferKeys.transfer(clientTransferId)
        val fromAccountNumber = redisTemplate.opsForHash<String, String>().get(transferKey, "fromAccountNumber")
        val toAccountNumber = redisTemplate.opsForHash<String, String>().get(transferKey, "toAccountNumber")
        val amount = redisTemplate.opsForHash<String, String>().get(transferKey, "amount")?.toLong()

        if (fromAccountNumber == null || toAccountNumber == null || amount == null) {
            return TransferReversalResult(
                clientTransferId = clientTransferId,
                fromAccountNumber = "",
                toAccountNumber = "",
                amount = 0,
                fromBalance = 0,
                toBalance = 0,
                debitReversalHistory = null,
                creditReversalHistory = null,
                alreadyReversed = true,
            )
        }

        val debitReversalHistory = TransactionHistory(
            transactionId = UUID.randomUUID().toString(),
            accountNumber = fromAccountNumber,
            type = TransactionType.REVERSAL_IN,
            amount = amount,
            counterpartyAccountNumber = toAccountNumber,
            memo = "망취소",
            createdAt = reversedAt,
        )
        val creditReversalHistory = TransactionHistory(
            transactionId = UUID.randomUUID().toString(),
            accountNumber = toAccountNumber,
            type = TransactionType.REVERSAL_OUT,
            amount = amount,
            counterpartyAccountNumber = fromAccountNumber,
            memo = "망취소",
            createdAt = reversedAt,
        )

        val script = DefaultRedisScript(REVERSE_SCRIPT, String::class.java)
        val result = redisTemplate.execute(
            script,
            listOf(
                transferKey,
                RedisAccountKeys.account(fromAccountNumber),
                RedisAccountKeys.account(toAccountNumber),
                RedisAccountKeys.history(fromAccountNumber),
                RedisAccountKeys.history(toAccountNumber),
            ),
            debitReversalHistory.createdAt.toEpochMilli().toString(),
            objectMapper.writeValueAsString(debitReversalHistory),
            objectMapper.writeValueAsString(creditReversalHistory),
            debitReversalHistory.transactionId,
            creditReversalHistory.transactionId,
        ) ?: throw IllegalStateException("Redis reverse script returned null")

        return when {
            result == "ALREADY_REVERSED" -> {
                val amount = redisTemplate.opsForHash<String, String>().get(transferKey, "amount")?.toLong() ?: 0L
                val fromBalance = redisTemplate.opsForHash<String, String>()
                    .get(RedisAccountKeys.account(fromAccountNumber), "balance")
                    ?.toLong() ?: 0L
                val toBalance = redisTemplate.opsForHash<String, String>()
                    .get(RedisAccountKeys.account(toAccountNumber), "balance")
                    ?.toLong() ?: 0L
                TransferReversalResult(
                    clientTransferId = clientTransferId,
                    fromAccountNumber = fromAccountNumber,
                    toAccountNumber = toAccountNumber,
                    amount = amount,
                    fromBalance = fromBalance,
                    toBalance = toBalance,
                    debitReversalHistory = null,
                    creditReversalHistory = null,
                    alreadyReversed = true,
                )
            }
            result == "TRANSFER_NOT_FOUND" -> throw IllegalStateException("Transfer not found: $clientTransferId")
            result == "FROM_NOT_FOUND" -> throw AccountNotFoundException(fromAccountNumber)
            result == "TO_NOT_FOUND" -> throw AccountNotFoundException(toAccountNumber)
            result == "FROM_CANCELED" -> throw AccountCanceledException(fromAccountNumber)
            result == "TO_CANCELED" -> throw AccountCanceledException(toAccountNumber)
            result == "TO_INSUFFICIENT_BALANCE" -> throw InsufficientBalanceException(toAccountNumber)
            result.startsWith("OK:") -> {
                val tokens = result.split(":")
                TransferReversalResult(
                    clientTransferId = clientTransferId,
                    fromAccountNumber = fromAccountNumber,
                    toAccountNumber = toAccountNumber,
                    amount = tokens[3].toLong(),
                    fromBalance = tokens[1].toLong(),
                    toBalance = tokens[2].toLong(),
                    debitReversalHistory = debitReversalHistory,
                    creditReversalHistory = creditReversalHistory,
                )
            }
            else -> throw IllegalStateException("Unexpected reverse result: $result")
        }
    }

    /**
     * lua script 통해 Redis 제어
     * lua script, 처리하는 내용
     * 1. 출금 계좌 존재 확인
     * 2. 입금 계좌 존재 확인
     * 3. 양쪽 계좌 상태 ACTIVE 확인
     * 4. 출금 계좌 잔액 충분 여부 확인
     * 5. 출금 계좌 HINCRBY -amount
     * 6. 입금 계좌 HINCRBY +amount
     * 7. 출금 계좌 거래내역 ZADD
     * 8. 입금 계좌 거래내역 ZADD
     */
    companion object {
        private const val TRANSFER_SCRIPT = """
local fromAccountKey = KEYS[1]
local toAccountKey = KEYS[2]
local fromHistoryKey = KEYS[3]
local toHistoryKey = KEYS[4]
local transferKey = KEYS[5]
local clientTransferId = ARGV[1]
local amount = tonumber(ARGV[2])
local score = tonumber(ARGV[3])
local debitHistoryJson = ARGV[4]
local creditHistoryJson = ARGV[5]
local debitTransactionId = ARGV[6]
local creditTransactionId = ARGV[7]
local fromAccountNumber = ARGV[8]
local toAccountNumber = ARGV[9]

if redis.call('EXISTS', transferKey) == 1 then
  return 'DUPLICATE_TRANSFER'
end

if redis.call('EXISTS', fromAccountKey) == 0 then 
  return 'FROM_NOT_FOUND'
end

if redis.call('EXISTS', toAccountKey) == 0 then
  return 'TO_NOT_FOUND'
end

if redis.call('HGET', fromAccountKey, 'status') ~= 'ACTIVE' then
  return 'FROM_CANCELED'
end

if redis.call('HGET', toAccountKey, 'status') ~= 'ACTIVE' then
  return 'TO_CANCELED'
end

local fromBalance = tonumber(redis.call('HGET', fromAccountKey, 'balance'))
if fromBalance < amount then
  return 'INSUFFICIENT_BALANCE'
end

-- 요청 시각과 실제 처리 시각은 다를 수 있다. cursor는 스냅샷과 같은 Redis 시계를 사용해야 누락이 없다.
-- 완료 시각은 잔액과 이력을 확정하는 이 원자 연산의 기준 시각이며, 최초 발생 시각은 JSON에서 유지한다.
local now = redis.call('TIME')
score = tonumber(now[1]) * 1000 + math.floor(tonumber(now[2]) / 1000)
local debit = cjson.decode(debitHistoryJson)
local credit = cjson.decode(creditHistoryJson)
debit.completedAtEpochMillis = score
credit.completedAtEpochMillis = score
debitHistoryJson = cjson.encode(debit)
creditHistoryJson = cjson.encode(credit)
local fromNewBalance = redis.call('HINCRBY', fromAccountKey, 'balance', -amount)
local toNewBalance = redis.call('HINCRBY', toAccountKey, 'balance', amount)
redis.call('ZADD', fromHistoryKey, score, debitHistoryJson)
redis.call('ZADD', toHistoryKey, score, creditHistoryJson)
redis.call('HSET', transferKey,
  'clientTransferId', clientTransferId,
  'fromAccountNumber', fromAccountNumber,
  'toAccountNumber', toAccountNumber,
  'amount', tostring(amount),
  'debitTransactionId', debitTransactionId,
  'creditTransactionId', creditTransactionId,
  'reversed', 'false',
  'createdAtEpochMillis', tostring(score)
)

return 'OK:' .. tostring(fromNewBalance) .. ':' .. tostring(toNewBalance)
"""

        private const val REVERSE_SCRIPT = """
local transferKey = KEYS[1]
local fromAccountKey = KEYS[2]
local toAccountKey = KEYS[3]
local fromHistoryKey = KEYS[4]
local toHistoryKey = KEYS[5]
local score = tonumber(ARGV[1])
local debitReversalHistoryJson = ARGV[2]
local creditReversalHistoryJson = ARGV[3]
local debitReversalTransactionId = ARGV[4]
local creditReversalTransactionId = ARGV[5]

if redis.call('EXISTS', transferKey) == 0 then
  return 'TRANSFER_NOT_FOUND'
end

if redis.call('HGET', transferKey, 'reversed') == 'true' then
  return 'ALREADY_REVERSED'
end

if redis.call('EXISTS', fromAccountKey) == 0 then
  return 'FROM_NOT_FOUND'
end

if redis.call('EXISTS', toAccountKey) == 0 then
  return 'TO_NOT_FOUND'
end

if redis.call('HGET', fromAccountKey, 'status') ~= 'ACTIVE' then
  return 'FROM_CANCELED'
end

if redis.call('HGET', toAccountKey, 'status') ~= 'ACTIVE' then
  return 'TO_CANCELED'
end

local amount = tonumber(redis.call('HGET', transferKey, 'amount'))
local toBalance = tonumber(redis.call('HGET', toAccountKey, 'balance'))
if toBalance < amount then
  return 'TO_INSUFFICIENT_BALANCE'
end

local now = redis.call('TIME')
score = tonumber(now[1]) * 1000 + math.floor(tonumber(now[2]) / 1000)
local fromNewBalance = redis.call('HINCRBY', fromAccountKey, 'balance', amount)
local toNewBalance = redis.call('HINCRBY', toAccountKey, 'balance', -amount)
redis.call('ZADD', fromHistoryKey, score, debitReversalHistoryJson)
redis.call('ZADD', toHistoryKey, score, creditReversalHistoryJson)
redis.call('HSET', transferKey,
  'reversed', 'true',
  'reversedAtEpochMillis', tostring(score),
  'debitReversalTransactionId', debitReversalTransactionId,
  'creditReversalTransactionId', creditReversalTransactionId
)

return 'OK:' .. tostring(fromNewBalance) .. ':' .. tostring(toNewBalance) .. ':' .. tostring(amount)
"""
    }
}
