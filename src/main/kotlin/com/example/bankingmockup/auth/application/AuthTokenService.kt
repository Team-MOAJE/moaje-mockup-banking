package com.example.bankingmockup.auth.application

import com.example.bankingmockup.auth.domain.AuthTokenRepository
import com.example.bankingmockup.auth.domain.TokenSession
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.UUID

@Service
class AuthTokenService(
    private val authTokenRepository: AuthTokenRepository,
) {
    private val clock: Clock = Clock.systemUTC()

    fun issue(command: IssueTokenCommand): IssueTokenResult {
        require(command.ci.isNotBlank()) { "ci must not be blank" }

        val now = Instant.now(clock)
        val accessToken = UUID.randomUUID().toString()
        val session = TokenSession(
            ci = command.ci,
            userName = command.userName,
            phoneNumber = command.phoneNumber,
            issuedAt = now,
            expiresAt = now.plus(TOKEN_TTL),
        )

        authTokenRepository.save(accessToken, session, TOKEN_TTL)

        return IssueTokenResult(
            accessToken = accessToken,
            tokenType = "Bearer",
            expiresIn = TOKEN_TTL.toSeconds(),
        )
    }

    fun findSession(accessToken: String): TokenSession? =
        authTokenRepository.findByAccessToken(accessToken)

    companion object {
        val TOKEN_TTL: Duration = Duration.ofDays(30)
    }
}

data class IssueTokenCommand(
    val ci: String,
    val userName: String?,
    val phoneNumber: String?,
)

data class IssueTokenResult(
    val accessToken: String,
    val tokenType: String,
    val expiresIn: Long,
)
