package com.example.bankingmockup.account.domain

import java.time.Instant

data class Account(
    val accountNumber: String,
    val bankCode: String,
    val productName: String,
    val balance: Long,
    val status: AccountStatus,
    val createdAt: Instant,
    val canceledAt: Instant? = null,
) {
    init {
        require(balance >= 0) { "balance must not be negative" }
    }
}
