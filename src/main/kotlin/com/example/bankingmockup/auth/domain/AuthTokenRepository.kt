package com.example.bankingmockup.auth.domain

import java.time.Duration

interface AuthTokenRepository {
    fun save(accessToken: String, session: TokenSession, ttl: Duration)
    fun findByAccessToken(accessToken: String): TokenSession?
}
