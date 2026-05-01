package com.example.bankingmockup.auth.infrastructure

import com.example.bankingmockup.auth.domain.AuthTokenRepository
import com.example.bankingmockup.auth.domain.TokenSession
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Repository
import java.time.Duration

@Repository
class RedisAuthTokenRepository(
    private val redisTemplate: StringRedisTemplate,
    private val objectMapper: ObjectMapper,
) : AuthTokenRepository {
    override fun save(accessToken: String, session: TokenSession, ttl: Duration) {
        redisTemplate.opsForValue()
            .set(tokenKey(accessToken), objectMapper.writeValueAsString(session), ttl)
    }

    override fun findByAccessToken(accessToken: String): TokenSession? {
        val value = redisTemplate.opsForValue().get(tokenKey(accessToken)) ?: return null
        return objectMapper.readValue(value, TokenSession::class.java)
    }

    private fun tokenKey(accessToken: String): String = "auth:token:$accessToken"
}
