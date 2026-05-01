package com.example.bankingmockup.auth.infrastructure

import com.example.bankingmockup.auth.presentation.HmacAndTokenInterceptor
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration
class AuthWebMvcConfig(
    private val hmacAndTokenInterceptor: HmacAndTokenInterceptor,
) : WebMvcConfigurer {
    override fun addInterceptors(registry: InterceptorRegistry) {
        registry.addInterceptor(hmacAndTokenInterceptor)
            .addPathPatterns("/api/**")
    }
}
