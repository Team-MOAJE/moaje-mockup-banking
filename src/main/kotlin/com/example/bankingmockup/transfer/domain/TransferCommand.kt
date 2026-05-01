package com.example.bankingmockup.transfer.domain

data class TransferCommand(
    val fromAccountNumber: String,
    val toAccountNumber: String,
    val amount: Long,
    val memo: String?,
)
