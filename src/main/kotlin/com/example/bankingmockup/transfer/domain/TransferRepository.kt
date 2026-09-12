package com.example.bankingmockup.transfer.domain

import com.example.bankingmockup.account.domain.TransactionHistory
import java.time.Instant

interface TransferRepository {
    fun transfer(
        command: LedgerTransferCommand,
        debitHistory: TransactionHistory,
        creditHistory: TransactionHistory,
    ): TransferResult

    fun findByClientTransferId(clientTransferId: String): TransferLookupResult?

    fun reverse(clientTransferId: String, reversedAt: Instant): TransferReversalResult
}
