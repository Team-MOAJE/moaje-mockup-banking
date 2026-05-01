package com.example.bankingmockup.account.infrastructure

import com.example.bankingmockup.account.domain.Account
import com.example.bankingmockup.account.domain.AccountCanceledException
import com.example.bankingmockup.account.domain.AccountNotFoundException
import com.example.bankingmockup.account.domain.AccountRepository
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
        return account
    }

    override fun findByAccountNumber(accountNumber: String): Account? {
        val values = redisTemplate.opsForHash<String, String>()
            .entries(RedisAccountKeys.account(accountNumber))

        return values.toAccountOrNull()
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

    private fun Account.toHash(): Map<String, String> =
        buildMap {
            put("accountNumber", accountNumber)
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
            accountNumber = getValue("accountNumber"),
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

local newBalance = redis.call('HINCRBY', accountKey, 'balance', -amount)
redis.call('ZADD', historyKey, score, historyJson)

return 'OK:' .. tostring(newBalance)
"""
    }
}
