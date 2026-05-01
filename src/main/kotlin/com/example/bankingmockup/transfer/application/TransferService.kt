package com.example.bankingmockup.transfer.application

import com.example.bankingmockup.account.domain.InvalidAccountAmountException
import com.example.bankingmockup.account.domain.TransactionHistory
import com.example.bankingmockup.account.domain.TransactionType
import com.example.bankingmockup.transfer.domain.SameAccountTransferException
import com.example.bankingmockup.transfer.domain.TransferCommand
import com.example.bankingmockup.transfer.domain.TransferRepository
import com.example.bankingmockup.transfer.domain.TransferResult
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.Instant
import java.util.UUID

@Service
class TransferService(
    private val transferRepository: TransferRepository,
) {
    private val clock: Clock = Clock.systemUTC()

    fun transfer(command: TransferCommand): TransferResult {
        if (command.amount <= 0) {
            throw InvalidAccountAmountException()
        }
        if (command.fromAccountNumber == command.toAccountNumber) {
            throw SameAccountTransferException()
        }

        val now = Instant.now(clock)
        val debitHistory = TransactionHistory(
            transactionId = UUID.randomUUID().toString(),
            accountNumber = command.fromAccountNumber,
            type = TransactionType.TRANSFER_OUT,
            amount = command.amount,
            counterpartyAccountNumber = command.toAccountNumber,
            memo = command.memo,
            createdAt = now,
        )
        val creditHistory = TransactionHistory(
            transactionId = UUID.randomUUID().toString(),
            accountNumber = command.toAccountNumber,
            type = TransactionType.TRANSFER_IN,
            amount = command.amount,
            counterpartyAccountNumber = command.fromAccountNumber,
            memo = command.memo,
            createdAt = now,
        )

        return transferRepository.transfer(command, debitHistory, creditHistory)
    }
}
