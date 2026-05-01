package com.example.bankingmockup.transfer.domain

import com.example.bankingmockup.account.domain.TransactionHistory

interface TransferRepository {
    fun transfer(
        command: TransferCommand,
        debitHistory: TransactionHistory,
        creditHistory: TransactionHistory,
    ): TransferResult
}
