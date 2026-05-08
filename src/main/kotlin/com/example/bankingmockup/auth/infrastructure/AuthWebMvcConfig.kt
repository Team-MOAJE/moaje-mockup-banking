package com.example.bankingmockup.auth.infrastructure

import com.example.bankingmockup.auth.presentation.HmacAndTokenInterceptor
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

/**
 * 인증 및 무결성 검사를 위한 Config
 * HMAC
 */

@Configuration
class AuthWebMvcConfig(
    private val hmacAndTokenInterceptor: HmacAndTokenInterceptor,
) : WebMvcConfigurer {
    override fun addInterceptors(registry: InterceptorRegistry) {
        registry.addInterceptor(hmacAndTokenInterceptor)
            .addPathPatterns("/api/**")
    }
}
