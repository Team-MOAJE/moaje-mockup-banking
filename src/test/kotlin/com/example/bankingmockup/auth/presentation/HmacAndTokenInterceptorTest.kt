package com.example.bankingmockup.auth.presentation

import com.example.bankingmockup.auth.application.AuthTokenService
import com.example.bankingmockup.auth.application.IssueTokenCommand
import com.example.bankingmockup.auth.domain.AuthTokenRepository
import com.example.bankingmockup.auth.domain.TokenSession
import jakarta.servlet.http.HttpServletResponse
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import java.nio.charset.StandardCharsets
import java.time.Duration
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HmacAndTokenInterceptorTest {
    private val repository = InMemoryAuthTokenRepository()
    private val service = AuthTokenService(repository)
    private val interceptor = HmacAndTokenInterceptor(
        authTokenService = service,
        hmacSecret = SECRET,
        maxPayloadBytes = 10 * 1024,
        faultDelayMillis = 1,
    )

    @Test
    fun `valid token and hmac passes and exposes ci`() {
        val token = issueToken()
        val body = """{"amount":1000}"""
        val request = request(body = body, token = token)
        val response = MockHttpServletResponse()

        val handled = interceptor.preHandle(request, response, Any())

        assertTrue(handled)
        assertEquals("ci-12345", request.getAttribute(HmacAndTokenInterceptor.AUTHENTICATED_CI_ATTRIBUTE))
    }

    @Test
    fun `expired or unknown token returns unauthorized`() {
        val request = request(body = """{"amount":1000}""", token = "unknown-token")
        val response = MockHttpServletResponse()

        val handled = interceptor.preHandle(request, response, Any())

        assertEquals(false, handled)
        assertEquals(HttpServletResponse.SC_UNAUTHORIZED, response.status)
    }

    @Test
    fun `invalid hmac signature returns unauthorized`() {
        val token = issueToken()
        val request = request(
            body = """{"amount":1000}""",
            token = token,
            signature = "wrong-signature",
        )
        val response = MockHttpServletResponse()

        val handled = interceptor.preHandle(request, response, Any())

        assertEquals(false, handled)
        assertEquals(HttpServletResponse.SC_UNAUTHORIZED, response.status)
    }

    @Test
    fun `payload over 10kb returns payload too large`() {
        val token = issueToken()
        val body = "x".repeat(10 * 1024 + 1)
        val request = request(body = body, token = token)
        val response = MockHttpServletResponse()

        val handled = interceptor.preHandle(request, response, Any())

        assertEquals(false, handled)
        assertEquals(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE, response.status)
    }

    @Test
    fun `fault injection timeout delays then passes`() {
        val token = issueToken()
        val request = request(
            body = """{"amount":1000}""",
            token = token,
            scenario = "TIMEOUT_ERROR",
        )
        val response = MockHttpServletResponse()

        val handled = interceptor.preHandle(request, response, Any())

        assertTrue(handled)
    }

    @Test
    fun `fault injection internal server error returns 500`() {
        val token = issueToken()
        val request = request(body = """{}""", token = token, scenario = "INTERNAL_SERVER_ERROR")
        val response = MockHttpServletResponse()

        val handled = interceptor.preHandle(request, response, Any())

        assertEquals(false, handled)
        assertEquals(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, response.status)
    }

    @Test
    fun `fault injection concurrency conflict returns 409`() {
        val token = issueToken()
        val request = request(body = """{}""", token = token, scenario = "CONCURRENCY_CONFLICT")
        val response = MockHttpServletResponse()

        val handled = interceptor.preHandle(request, response, Any())

        assertEquals(false, handled)
        assertEquals(HttpServletResponse.SC_CONFLICT, response.status)
    }

    @Test
    fun `fault injection account frozen returns 403`() {
        val token = issueToken()
        val request = request(body = """{}""", token = token, scenario = "ACCOUNT_FROZEN")
        val response = MockHttpServletResponse()

        val handled = interceptor.preHandle(request, response, Any())

        assertEquals(false, handled)
        assertEquals(HttpServletResponse.SC_FORBIDDEN, response.status)
    }

    private fun issueToken(): String =
        service.issue(IssueTokenCommand("ci-12345", "Kim", "01012345678")).accessToken

    private fun request(
        body: String,
        token: String,
        signature: String = hmac(body),
        scenario: String? = null,
    ): CachedBodyRequestWrapper {
        val request = MockHttpServletRequest("POST", "/api/transfers")
        request.setContent(body.toByteArray(StandardCharsets.UTF_8))
        request.addHeader("Authorization", "Bearer $token")
        request.addHeader("X-Signature", signature)
        scenario?.let { request.addHeader("X-Mock-Scenario", it) }
        return CachedBodyRequestWrapper(request)
    }

    private fun hmac(body: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        val key = SecretKeySpec(SECRET.toByteArray(StandardCharsets.UTF_8), "HmacSHA256")
        mac.init(key)
        return mac.doFinal(body.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }

    companion object {
        private const val SECRET = "Moaje-banking-secret"
    }
}

private class InMemoryAuthTokenRepository : AuthTokenRepository {
    private val sessions = mutableMapOf<String, TokenSession>()

    override fun save(accessToken: String, session: TokenSession, ttl: Duration) {
        sessions[accessToken] = session
    }

    override fun findByAccessToken(accessToken: String): TokenSession? =
        sessions[accessToken]
}
