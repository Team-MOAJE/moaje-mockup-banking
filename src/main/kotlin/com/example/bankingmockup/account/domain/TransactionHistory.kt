package com.example.bankingmockup.account.domain

import java.time.Instant

data class TransactionHistory(
    val transactionId: String,
    val accountNumber: String,
    val type: TransactionType,
    val amount: Long,
    val counterpartyAccountNumber: String? = null,
    val counterpartyBankCode: String? = null,
    val memo: String? = null,
    val createdAt: Instant,
) {
    init {
        require(amount > 0) { "amount must be positive" }
    }
}
