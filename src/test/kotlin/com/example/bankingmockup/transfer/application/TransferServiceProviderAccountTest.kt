package com.example.bankingmockup.transfer.application

import com.example.bankingmockup.account.domain.Account
import com.example.bankingmockup.account.domain.AccountRepository
import com.example.bankingmockup.account.domain.AccountSnapshot
import com.example.bankingmockup.account.domain.AccountStatus
import com.example.bankingmockup.account.domain.TransactionHistory
import com.example.bankingmockup.transfer.domain.LedgerTransferCommand
import com.example.bankingmockup.transfer.domain.TransferCommand
import com.example.bankingmockup.transfer.domain.TransferLookupResult
import com.example.bankingmockup.transfer.domain.TransferRepository
import com.example.bankingmockup.transfer.domain.TransferResult
import com.example.bankingmockup.transfer.domain.TransferReversalResult
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.Instant

class TransferServiceProviderAccountTest {
    @Test
    @DisplayName("providerAccountId는 Mock Banking 내부에서만 실제 출금 계좌번호로 해석한다")
    fun resolvesProviderAccountIdInsideLedgerBoundary() {
        // given: Banking은 실제 계좌번호를 모르고 Mock Banking이 발급한 불투명 식별자만 전달한다.
        val transferRepository = CapturingTransferRepository()
        val service = TransferService(transferRepository, FakeAccountRepository(sourceAccount()))

        // when
        service.transfer(
            TransferCommand(
                clientTransferId = "transfer-1",
                fromProviderAccountId = "provider-opaque-1",
                toAccountNumber = "destination-account",
                amount = 10_000,
                memo = null,
            ),
        )

        // then: 실제 원장 키 변환은 Mock Banking Application 안에서 끝나고 Repository에만 전달된다.
        assertThat(transferRepository.capturedCommand?.clientTransferId).isEqualTo("transfer-1")
        assertThat(transferRepository.capturedCommand?.fromAccountNumber).isEqualTo("ledger-account-number")
        assertThat(transferRepository.capturedCommand?.toAccountNumber).isEqualTo("destination-account")
    }

    private fun sourceAccount() = Account(
        providerAccountId = "provider-opaque-1",
        accountNumber = "ledger-account-number",
        ownerCi = "test-owner",
        bankCode = "088",
        productName = "테스트 계좌",
        balance = 100_000,
        status = AccountStatus.ACTIVE,
        createdAt = Instant.parse("2026-01-01T00:00:00Z"),
    )

    private class FakeAccountRepository(private val account: Account) : AccountRepository {
        override fun findByProviderAccountId(providerAccountId: String): Account? =
            account.takeIf { it.providerAccountId == providerAccountId }

        override fun save(account: Account) = throw UnsupportedOperationException()
        override fun findByAccountNumber(accountNumber: String): Account? = null
        override fun findActiveByOwnerCiAndBankCodeAndProductName(ownerCi: String, bankCode: String, productName: String) = null
        override fun cancel(accountNumber: String, canceledAt: Instant) = throw UnsupportedOperationException()
        override fun withdraw(accountNumber: String, amount: Long, history: TransactionHistory) = throw UnsupportedOperationException()
        override fun findHistories(accountNumber: String): List<TransactionHistory> = emptyList()
        override fun findSnapshotByProviderAccountId(providerAccountId: String, cursorEpochMillis: Long): AccountSnapshot? = null
    }

    private class CapturingTransferRepository : TransferRepository {
        var capturedCommand: LedgerTransferCommand? = null

        override fun transfer(
            command: LedgerTransferCommand,
            debitHistory: TransactionHistory,
            creditHistory: TransactionHistory,
        ): TransferResult {
            capturedCommand = command
            return TransferResult(
                clientTransferId = command.clientTransferId,
                fromAccountNumber = command.fromAccountNumber,
                toAccountNumber = command.toAccountNumber,
                amount = command.amount,
                fromBalance = 90_000,
                toBalance = 10_000,
                debitHistory = debitHistory,
                creditHistory = creditHistory,
            )
        }

        override fun findByClientTransferId(clientTransferId: String): TransferLookupResult? = null
        override fun reverse(clientTransferId: String, reversedAt: Instant): TransferReversalResult =
            throw UnsupportedOperationException()
    }
}
