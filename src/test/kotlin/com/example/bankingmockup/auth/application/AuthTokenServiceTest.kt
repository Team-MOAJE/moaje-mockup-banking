package com.example.bankingmockup.auth.application

import com.example.bankingmockup.auth.domain.AuthTokenRepository
import com.example.bankingmockup.auth.domain.TokenSession
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import java.time.Duration

class AuthTokenServiceTest {
    @Test
    fun `issues access token and stores ci with 30 day ttl`() {
        val repository = InMemoryAuthTokenRepository()
        val service = AuthTokenService(repository)

        val result = service.issue(
            IssueTokenCommand(
                ci = "ci-12345",
                userName = "Kim",
                phoneNumber = "01012345678",
            ),
        )

        assertEquals("Bearer", result.tokenType)
        assertEquals(Duration.ofDays(30).toSeconds(), result.expiresIn)
        assertEquals(Duration.ofDays(30), repository.savedTtl)

        val savedSession = repository.findByAccessToken(result.accessToken)
        assertNotNull(savedSession)
        assertEquals("ci-12345", savedSession.ci)
    }
}

private class InMemoryAuthTokenRepository : AuthTokenRepository {
    private val sessions = mutableMapOf<String, TokenSession>()
    var savedTtl: Duration? = null

    override fun save(accessToken: String, session: TokenSession, ttl: Duration) {
        sessions[accessToken] = session
        savedTtl = ttl
    }

    override fun findByAccessToken(accessToken: String): TokenSession? =
        sessions[accessToken]
}
