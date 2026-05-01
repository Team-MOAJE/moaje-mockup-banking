package com.example.bankingmockup.transfer.infrastructure

import com.example.bankingmockup.account.domain.AccountCanceledException
import com.example.bankingmockup.account.domain.AccountNotFoundException
import com.example.bankingmockup.account.domain.InsufficientBalanceException
import com.example.bankingmockup.account.domain.TransactionHistory
import com.example.bankingmockup.account.infrastructure.RedisAccountKeys
import com.example.bankingmockup.transfer.domain.TransferCommand
import com.example.bankingmockup.transfer.domain.TransferRepository
import com.example.bankingmockup.transfer.domain.TransferResult
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.script.DefaultRedisScript
import org.springframework.stereotype.Repository

@Repository
class RedisTransferRepository(
    private val redisTemplate: StringRedisTemplate,
    private val objectMapper: ObjectMapper,
) : TransferRepository {
    override fun transfer(
        command: TransferCommand,
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
            ),
            command.amount.toString(),
            debitHistory.createdAt.toEpochMilli().toString(),
            objectMapper.writeValueAsString(debitHistory),
            objectMapper.writeValueAsString(creditHistory),
        ) ?: throw IllegalStateException("Redis transfer script returned null")

        return when {
            result == "FROM_NOT_FOUND" -> throw AccountNotFoundException(command.fromAccountNumber)
            result == "TO_NOT_FOUND" -> throw AccountNotFoundException(command.toAccountNumber)
            result == "FROM_CANCELED" -> throw AccountCanceledException(command.fromAccountNumber)
            result == "TO_CANCELED" -> throw AccountCanceledException(command.toAccountNumber)
            result == "INSUFFICIENT_BALANCE" -> throw InsufficientBalanceException(command.fromAccountNumber)
            result.startsWith("OK:") -> {
                val tokens = result.split(":")
                TransferResult(
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

    companion object {
        private const val TRANSFER_SCRIPT = """
local fromAccountKey = KEYS[1]
local toAccountKey = KEYS[2]
local fromHistoryKey = KEYS[3]
local toHistoryKey = KEYS[4]
local amount = tonumber(ARGV[1])
local score = tonumber(ARGV[2])
local debitHistoryJson = ARGV[3]
local creditHistoryJson = ARGV[4]

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

local fromNewBalance = redis.call('HINCRBY', fromAccountKey, 'balance', -amount)
local toNewBalance = redis.call('HINCRBY', toAccountKey, 'balance', amount)
redis.call('ZADD', fromHistoryKey, score, debitHistoryJson)
redis.call('ZADD', toHistoryKey, score, creditHistoryJson)

return 'OK:' .. tostring(fromNewBalance) .. ':' .. tostring(toNewBalance)
"""
    }
}
