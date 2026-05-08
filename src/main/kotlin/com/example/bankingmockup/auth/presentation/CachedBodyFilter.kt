package com.example.bankingmockup.auth.presentation

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

/**
 * Filter가 HttpServletRequest 로부터 받은 데이터를 여러번 읽을 수 있도록
 * CachedBodyRequestWrapper class 로 wrapping 한다.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class CachedBodyFilter : OncePerRequestFilter() {
    override fun shouldNotFilter(request: HttpServletRequest): Boolean =
        !request.requestURI.startsWith("/api/")

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val wrappedRequest = if (request is CachedBodyRequestWrapper) {
            request
        } else {
            CachedBodyRequestWrapper(request)
        }

        filterChain.doFilter(wrappedRequest, response)
    }
}
