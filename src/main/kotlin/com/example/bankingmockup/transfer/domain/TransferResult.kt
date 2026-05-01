package com.example.bankingmockup.transfer.domain

import com.example.bankingmockup.account.domain.TransactionHistory

data class TransferResult(
    val fromAccountNumber: String,
    val toAccountNumber: String,
    val amount: Long,
    val fromBalance: Long,
    val toBalance: Long,
    val debitHistory: TransactionHistory,
    val creditHistory: TransactionHistory,
)
