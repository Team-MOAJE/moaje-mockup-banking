package com.example.bankingmockup.transfer.domain

data class TransferCommand(
    val clientTransferId: String,
    val fromProviderAccountId: String,
    val toAccountNumber: String,
    val amount: Long,
    val memo: String?,
)

data class LedgerTransferCommand(
    val clientTransferId: String,
    val fromAccountNumber: String,
    val toAccountNumber: String,
    val amount: Long,
    val memo: String?,
)
