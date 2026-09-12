package com.example.bankingmockup.transfer.domain

import com.example.bankingmockup.account.domain.TransactionHistory
import java.time.Instant

data class TransferResult(
    val clientTransferId: String,
    val fromAccountNumber: String,
    val toAccountNumber: String,
    val amount: Long,
    val fromBalance: Long,
    val toBalance: Long,
    val debitHistory: TransactionHistory,
    val creditHistory: TransactionHistory,
)

enum class TransferLookupStatus {
    SUCCEEDED,
    REVERSED,
}

data class TransferLookupResult(
    val clientTransferId: String,
    val fromAccountNumber: String,
    val toAccountNumber: String,
    val amount: Long,
    val fromBalance: Long,
    val toBalance: Long,
    val debitTransactionId: String,
    val creditTransactionId: String,
    val status: TransferLookupStatus,
    val debitReversalTransactionId: String? = null,
    val creditReversalTransactionId: String? = null,
    val reversedAt: Instant? = null,
)

data class TransferReversalResult(
    val clientTransferId: String,
    val fromAccountNumber: String,
    val toAccountNumber: String,
    val amount: Long,
    val fromBalance: Long,
    val toBalance: Long,
    val debitReversalHistory: TransactionHistory?,
    val creditReversalHistory: TransactionHistory?,
    val alreadyReversed: Boolean = false,
)
