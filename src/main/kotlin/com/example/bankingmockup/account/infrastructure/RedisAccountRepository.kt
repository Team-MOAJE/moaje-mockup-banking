package com.example.bankingmockup.account.infrastructure

import com.example.bankingmockup.account.domain.Account
import com.example.bankingmockup.account.domain.AccountCanceledException
import com.example.bankingmockup.account.domain.AccountNotFoundException
import com.example.bankingmockup.account.domain.AccountRepository
import com.example.bankingmockup.account.domain.AccountSnapshot
import com.example.bankingmockup.account.domain.AccountStatus
import com.example.bankingmockup.account.domain.InsufficientBalanceException
import com.example.bankingmockup.account.domain.TransactionHistory
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.script.DefaultRedisScript
import org.springframework.stereotype.Repository
import java.time.Instant

@Repository
class RedisAccountRepository(
    private val redisTemplate: StringRedisTemplate,
    private val objectMapper: ObjectMapper,
) : AccountRepository {
    override fun save(account: Account): Account {
        redisTemplate.opsForHash<String, String>()
            .putAll(RedisAccountKeys.account(account.accountNumber), account.toHash())
        redisTemplate.opsForValue().set(
            RedisAccountKeys.accountOwnerIndex(account.ownerCi, account.bankCode, account.productName),
            account.accountNumber,
        )
        redisTemplate.opsForValue().set(
            RedisAccountKeys.providerAccountIndex(account.providerAccountId),
            account.accountNumber,
        )
        return account
    }

    override fun findByAccountNumber(accountNumber: String): Account? {
        val values = redisTemplate.opsForHash<String, String>()
            .entries(RedisAccountKeys.account(accountNumber))

        return values.toAccountOrNull()
    }

    override fun findByProviderAccountId(providerAccountId: String): Account? {
        val accountNumber = redisTemplate.opsForValue().get(RedisAccountKeys.providerAccountIndex(providerAccountId))
            ?: return null
        return findByAccountNumber(accountNumber)
    }

    override fun findActiveByOwnerCiAndBankCodeAndProductName(
        ownerCi: String,
        bankCode: String,
        productName: String,
    ): Account? {
        val accountNumber = redisTemplate.opsForValue()
            .get(RedisAccountKeys.accountOwnerIndex(ownerCi, bankCode, productName))
            ?: return null

        return findByAccountNumber(accountNumber)
            ?.takeIf { it.status == AccountStatus.ACTIVE }
    }

    override fun cancel(accountNumber: String, canceledAt: Instant): Account {
        val key = RedisAccountKeys.account(accountNumber)
        val script = DefaultRedisScript(CANCEL_SCRIPT, String::class.java)
        val result = redisTemplate.execute(script, listOf(key), canceledAt.toString())
            ?: throw IllegalStateException("Redis cancel script returned null")

        if (result == "NOT_FOUND") {
            throw AccountNotFoundException(accountNumber)
        }

        return findByAccountNumber(accountNumber) ?: throw AccountNotFoundException(accountNumber)
    }

    override fun withdraw(accountNumber: String, amount: Long, history: TransactionHistory): Account {
        val script = DefaultRedisScript(WITHDRAW_SCRIPT, String::class.java)
        val result = redisTemplate.execute(
            script,
            listOf(RedisAccountKeys.account(accountNumber), RedisAccountKeys.history(accountNumber)),
            amount.toString(),
            history.createdAt.toEpochMilli().toString(),
            objectMapper.writeValueAsString(history),
        ) ?: throw IllegalStateException("Redis withdrawal script returned null")

        return when {
            result == "NOT_FOUND" -> throw AccountNotFoundException(accountNumber)
            result == "CANCELED" -> throw AccountCanceledException(accountNumber)
            result == "INSUFFICIENT_BALANCE" -> throw InsufficientBalanceException(accountNumber)
            result.startsWith("OK:") -> findByAccountNumber(accountNumber)
                ?: throw AccountNotFoundException(accountNumber)
            else -> throw IllegalStateException("Unexpected withdrawal result: $result")
        }
    }

    override fun findHistories(accountNumber: String): List<TransactionHistory> {
        val histories = redisTemplate.opsForZSet()
            .range(RedisAccountKeys.history(accountNumber), 0, -1)
            .orEmpty()

        return histories.map {
            objectMapper.readValue(it, TransactionHistory::class.java)
        }
    }

    /**
     * 잔액과 거래내역을 따로 조회하면 두 조회 사이의 송금 때문에 서로 다른 시점의 결과가 섞일 수 있다.
     * Lua에서 HGET과 ZRANGEBYSCORE를 한 번에 실행해 Snapshot의 잔액과 증분 이력을 같은 Redis 실행 시점으로 묶는다.
     */
    override fun findSnapshotByProviderAccountId(
        providerAccountId: String,
        cursorEpochMillis: Long,
    ): AccountSnapshot? {
        val accountNumber = redisTemplate.opsForValue()
            .get(RedisAccountKeys.providerAccountIndex(providerAccountId)) ?: return null
        val raw = redisTemplate.execute(
            DefaultRedisScript(SNAPSHOT_SCRIPT, String::class.java),
            listOf(RedisAccountKeys.account(accountNumber), RedisAccountKeys.history(accountNumber)),
            cursorEpochMillis.coerceAtLeast(0).toString(),
        ) ?: return null
        if (raw.isBlank()) return null

        val json = objectMapper.readTree(raw)
        return AccountSnapshot(
            providerAccountId = providerAccountId,
            balance = json.path("balance").asLong(),
            status = AccountStatus.valueOf(json.path("status").asText()),
            asOf = Instant.ofEpochMilli(json.path("asOfEpochMillis").asLong()),
            histories = json.path("histories").map { historyJson ->
                objectMapper.readValue(historyJson.asText(), TransactionHistory::class.java)
            },
        )
    }

    private fun Account.toHash(): Map<String, String> =
        buildMap {
            put("providerAccountId", providerAccountId)
            put("accountNumber", accountNumber)
            put("ownerCi", ownerCi)
            put("bankCode", bankCode)
            put("productName", productName)
            put("balance", balance.toString())
            put("status", status.name)
            put("createdAt", createdAt.toString())
            canceledAt?.let { put("canceledAt", it.toString()) }
        }

    private fun Map<String, String>.toAccountOrNull(): Account? {
        if (isEmpty()) {
            return null
        }

        return Account(
            providerAccountId = this["providerAccountId"] ?: return null,
            accountNumber = getValue("accountNumber"),
            ownerCi = this["ownerCi"].orEmpty(),
            bankCode = getValue("bankCode"),
            productName = getValue("productName"),
            balance = getValue("balance").toLong(),
            status = AccountStatus.valueOf(getValue("status")),
            createdAt = Instant.parse(getValue("createdAt")),
            canceledAt = this["canceledAt"]?.let(Instant::parse),
        )
    }

    companion object {
        private const val CANCEL_SCRIPT = """
local accountKey = KEYS[1]
local canceledAt = ARGV[1]

if redis.call('EXISTS', accountKey) == 0 then
  return 'NOT_FOUND'
end

redis.call('HSET', accountKey, 'status', 'CANCELED', 'canceledAt', canceledAt)
return 'OK'
"""

        private const val WITHDRAW_SCRIPT = """
local accountKey = KEYS[1]
local historyKey = KEYS[2]
local amount = tonumber(ARGV[1])
local score = tonumber(ARGV[2])
local historyJson = ARGV[3]

if redis.call('EXISTS', accountKey) == 0 then
  return 'NOT_FOUND'
end

if redis.call('HGET', accountKey, 'status') ~= 'ACTIVE' then
  return 'CANCELED'
end

local balance = tonumber(redis.call('HGET', accountKey, 'balance'))
if balance < amount then
  return 'INSUFFICIENT_BALANCE'
end

-- 요청 생성 시각을 cursor로 쓰면 늦게 저장된 거래가 다음 조회에서 빠질 수 있다.
-- 잔액 반영과 같은 Redis 실행 시각을 정렬 기준으로 삼고, 최초 요청 시각은 JSON에 그대로 보존한다.
local now = redis.call('TIME')
score = tonumber(now[1]) * 1000 + math.floor(tonumber(now[2]) / 1000)
local history = cjson.decode(historyJson)
history.completedAtEpochMillis = score
historyJson = cjson.encode(history)
local newBalance = redis.call('HINCRBY', accountKey, 'balance', -amount)
redis.call('ZADD', historyKey, score, historyJson)

return 'OK:' .. tostring(newBalance)
"""

        private const val SNAPSHOT_SCRIPT = """
local accountKey = KEYS[1]
local historyKey = KEYS[2]
local cursor = tonumber(ARGV[1])

if redis.call('EXISTS', accountKey) == 0 then
  return ''
end

local redisTime = redis.call('TIME')
local asOfEpochMillis = tonumber(redisTime[1]) * 1000 + math.floor(tonumber(redisTime[2]) / 1000)
local histories = redis.call('ZRANGEBYSCORE', historyKey, cursor, asOfEpochMillis)

return cjson.encode({
  balance = tonumber(redis.call('HGET', accountKey, 'balance')),
  status = redis.call('HGET', accountKey, 'status'),
  asOfEpochMillis = asOfEpochMillis,
  histories = histories
})
"""
    }
}
