package com.example.bankingmockup.transfer.application

import com.example.bankingmockup.account.domain.InvalidAccountAmountException
import com.example.bankingmockup.account.domain.AccountNotFoundException
import com.example.bankingmockup.account.domain.AccountRepository
import com.example.bankingmockup.account.domain.TransactionHistory
import com.example.bankingmockup.account.domain.TransactionType
import com.example.bankingmockup.transfer.domain.SameAccountTransferException
import com.example.bankingmockup.transfer.domain.TransferCommand
import com.example.bankingmockup.transfer.domain.TransferLookupResult
import com.example.bankingmockup.transfer.domain.LedgerTransferCommand
import com.example.bankingmockup.transfer.domain.TransferRepository
import com.example.bankingmockup.transfer.domain.TransferReversalResult
import com.example.bankingmockup.transfer.domain.TransferResult
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.Instant
import java.util.UUID

@Service
class TransferService(
    private val transferRepository: TransferRepository,
    private val accountRepository: AccountRepository,
) {
    private val clock: Clock = Clock.systemUTC()

    fun transfer(command: TransferCommand): TransferResult {
        if (command.amount <= 0) {
            throw InvalidAccountAmountException()
        }
        /*
         * providerAccountId를 실제 계좌번호로 바꾸는 책임은 계정계 내부에만 둔다.
         * 따라서 Banking은 불투명 식별자로 금융 요청을 보내면서도 원장 키를 알 필요가 없다.
         */
        val fromAccountNumber = accountRepository.findByProviderAccountId(command.fromProviderAccountId)
            ?.accountNumber
            ?: throw AccountNotFoundException(command.fromProviderAccountId)
        if (fromAccountNumber == command.toAccountNumber) {
            throw SameAccountTransferException()
        }

        val ledgerCommand = LedgerTransferCommand(
            clientTransferId = command.clientTransferId,
            fromAccountNumber = fromAccountNumber,
            toAccountNumber = command.toAccountNumber,
            amount = command.amount,
            memo = command.memo,
        )

        val now = Instant.now(clock)
        val debitHistory = TransactionHistory(
            transactionId = UUID.randomUUID().toString(),
            accountNumber = ledgerCommand.fromAccountNumber,
            type = TransactionType.TRANSFER_OUT,
            amount = command.amount,
            counterpartyAccountNumber = ledgerCommand.toAccountNumber,
            memo = command.memo,
            createdAt = now,
        )
        val creditHistory = TransactionHistory(
            transactionId = UUID.randomUUID().toString(),
            accountNumber = ledgerCommand.toAccountNumber,
            type = TransactionType.TRANSFER_IN,
            amount = command.amount,
            counterpartyAccountNumber = ledgerCommand.fromAccountNumber,
            memo = command.memo,
            createdAt = now,
        )

        return transferRepository.transfer(ledgerCommand, debitHistory, creditHistory)
    }

    fun findByClientTransferId(clientTransferId: String): TransferLookupResult? {
        return transferRepository.findByClientTransferId(clientTransferId)
    }

    /**
     * 망취소 요청을 처리합니다.
     *
     * 트러블 슈팅 포인트:
     * - 송금 API timeout은 "실패"가 아니라 "응답을 못 받음"입니다.
     * - 실제 Redis 원장에는 송금이 반영됐을 수 있으므로 clientTransferId로 원 송금 레코드를 찾아 반전 거래를 만듭니다.
     *
     * 기술 포인트:
     * - Kotlin의 `copy` 대신 새 `TransactionHistory`를 명시적으로 생성합니다.
     * - 원 거래와 반전 거래는 서로 다른 거래 ID를 가져야 추적이 쉬워집니다.
     */
    fun reverse(clientTransferId: String): TransferReversalResult {
        return transferRepository.reverse(
            clientTransferId = clientTransferId,
            reversedAt = Instant.now(clock),
        )
    }
}
