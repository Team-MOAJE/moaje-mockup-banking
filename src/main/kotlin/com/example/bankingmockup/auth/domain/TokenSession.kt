package com.example.bankingmockup.auth.domain

import java.time.Instant

data class TokenSession(
    val ci: String,
    val userName: String?,
    val phoneNumber: String?,
    val issuedAt: Instant,
    val expiresAt: Instant,
)
