package com.example.bankingmockup.auth.presentation

import com.example.bankingmockup.auth.application.AuthTokenService
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.servlet.HandlerInterceptor
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

@Component
class HmacAndTokenInterceptor(
    private val authTokenService: AuthTokenService,
    @Value("\${moaje.banking.secret}") private val hmacSecret: String,
    @Value("\${moaje.banking.max-payload-bytes}") private val maxPayloadBytes: Int,
    @Value("\${moaje.banking.fault-delay-millis}") private val faultDelayMillis: Long,
) : HandlerInterceptor {
    override fun preHandle(
        request: HttpServletRequest,
        response: HttpServletResponse,
        handler: Any,
    ): Boolean {
        val body = request.bodyBytes()

        if (request.contentLengthLong > maxPayloadBytes.toLong() || body.size > maxPayloadBytes) {
            response.sendError(HttpStatus.PAYLOAD_TOO_LARGE.value(), "Payload exceeds 10KB")
            return false
        }

        val accessToken = request.bearerToken()
        if (accessToken == null) {
            response.sendError(HttpStatus.UNAUTHORIZED.value(), "Missing bearer token")
            return false
        }

        val session = authTokenService.findSession(accessToken)
        if (session == null) {
            response.sendError(HttpStatus.UNAUTHORIZED.value(), "Invalid or expired access token")
            return false
        }
        request.setAttribute(AUTHENTICATED_CI_ATTRIBUTE, session.ci)

        val signature = request.getHeader(SIGNATURE_HEADER)
        if (signature.isNullOrBlank() || !constantTimeEquals(signature.lowercase(), hmacSha256Hex(body))) {
            response.sendError(HttpStatus.UNAUTHORIZED.value(), "Invalid request signature")
            return false
        }

        return handleFaultScenario(request, response)
    }

    private fun handleFaultScenario(
        request: HttpServletRequest,
        response: HttpServletResponse,
    ): Boolean {
        return when (request.getHeader(MOCK_SCENARIO_HEADER)) {
            "TIMEOUT_ERROR" -> {
                Thread.sleep(faultDelayMillis)
                true
            }
            "INTERNAL_SERVER_ERROR" -> {
                response.sendError(HttpStatus.INTERNAL_SERVER_ERROR.value(), "Injected internal server error")
                false
            }
            "CONCURRENCY_CONFLICT" -> {
                response.sendError(HttpStatus.CONFLICT.value(), "Injected Redis lock conflict")
                false
            }
            "ACCOUNT_FROZEN" -> {
                response.sendError(HttpStatus.FORBIDDEN.value(), "Injected frozen account")
                false
            }
            else -> true
        }
    }

    private fun HttpServletRequest.bodyBytes(): ByteArray =
        if (this is CachedBodyRequestWrapper) {
            cachedBody
        } else {
            inputStream.readAllBytes()
        }

    private fun HttpServletRequest.bearerToken(): String? {
        val authorization = getHeader(AUTHORIZATION_HEADER) ?: return null
        if (!authorization.startsWith(BEARER_PREFIX)) {
            return null
        }

        return authorization.removePrefix(BEARER_PREFIX).trim().takeIf { it.isNotBlank() }
    }

    private fun hmacSha256Hex(body: ByteArray): String {
        val mac = Mac.getInstance("HmacSHA256")
        val key = SecretKeySpec(hmacSecret.toByteArray(StandardCharsets.UTF_8), "HmacSHA256")
        mac.init(key)
        return mac.doFinal(body).joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }

    private fun constantTimeEquals(left: String, right: String): Boolean =
        MessageDigest.isEqual(
            left.toByteArray(StandardCharsets.UTF_8),
            right.toByteArray(StandardCharsets.UTF_8),
        )

    companion object {
        const val AUTHENTICATED_CI_ATTRIBUTE = "authenticatedCi"
        private const val AUTHORIZATION_HEADER = "Authorization"
        private const val BEARER_PREFIX = "Bearer "
        private const val SIGNATURE_HEADER = "X-Signature"
        private const val MOCK_SCENARIO_HEADER = "X-Mock-Scenario"
    }
}
