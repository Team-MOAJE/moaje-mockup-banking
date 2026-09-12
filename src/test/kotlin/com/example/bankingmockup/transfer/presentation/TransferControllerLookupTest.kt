package com.example.bankingmockup.transfer.presentation

import com.example.bankingmockup.account.domain.TransactionHistory
import com.example.bankingmockup.account.domain.TransactionType
import com.example.bankingmockup.account.domain.AccountRepository
import com.example.bankingmockup.transfer.application.TransferService
import com.example.bankingmockup.transfer.domain.LedgerTransferCommand
import com.example.bankingmockup.transfer.domain.TransferLookupResult
import com.example.bankingmockup.transfer.domain.TransferLookupStatus
import com.example.bankingmockup.transfer.domain.TransferNotFoundByClientTransferIdException
import com.example.bankingmockup.transfer.domain.TransferRepository
import com.example.bankingmockup.transfer.domain.TransferResult
import com.example.bankingmockup.transfer.domain.TransferReversalResult
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import java.time.Instant

class TransferControllerLookupTest {
    @Test
    @DisplayName("clientTransferId 조회 결과가 있으면 Mock Banking 송금 상태를 반환한다")
    fun returnsTransferLookupResultByClientTransferId() {
        // given: Mock Banking 원장에 clientTransferId로 찾을 수 있는 송금 결과가 있다.
        val controller = TransferController(TransferService(FakeTransferRepository(existingLookup()), accountRepository()))

        // when
        val response = controller.findByClientTransferId("transfer-1")

        // then: Banking Reconciliation은 이 응답을 기준으로 UNKNOWN 거래를 복구한다.
        assertThat(response.clientTransferId).isEqualTo("transfer-1")
        assertThat(response.debitTransactionId).isEqualTo("debit-1")
        assertThat(response.status).isEqualTo("SUCCEEDED")
    }

    @Test
    @DisplayName("clientTransferId 조회 결과가 없으면 NOT_FOUND로 해석할 예외를 던진다")
    fun throwsNotFoundWhenTransferLookupResultDoesNotExist() {
        // given: 강한 일관성 조회에서 결과가 없으면 송금 요청 자체가 없었던 것으로 본다.
        val controller = TransferController(TransferService(FakeTransferRepository(null), accountRepository()))

        // when & then
        assertThatThrownBy { controller.findByClientTransferId("missing-transfer") }
            .isInstanceOf(TransferNotFoundByClientTransferIdException::class.java)
    }

    @Test
    @DisplayName("이미 반전된 송금 조회는 원거래와 보상거래 식별자를 구분해 반환한다")
    fun returnsReversalDetailsWithoutOverwritingOriginalTransaction() {
        // given: Mock Banking 원장에는 원송금과 이미 완료된 보상거래가 모두 남아 있다.
        val reversedAt = Instant.parse("2026-09-07T00:00:00Z")
        val lookup = existingLookup().copy(
            status = TransferLookupStatus.REVERSED,
            debitReversalTransactionId = "reversal-debit-1",
            creditReversalTransactionId = "reversal-credit-1",
            reversedAt = reversedAt,
        )
        val controller = TransferController(TransferService(FakeTransferRepository(lookup), accountRepository()))

        // when
        val response = controller.findByClientTransferId("transfer-1")

        // then: Banking은 원거래 ID를 보존하면서 별도 보상거래 ID와 시각을 기록할 수 있다.
        assertThat(response.status).isEqualTo("REVERSED")
        assertThat(response.debitTransactionId).isEqualTo("debit-1")
        assertThat(response.debitReversalTransactionId).isEqualTo("reversal-debit-1")
        assertThat(response.reversedAt).isEqualTo(reversedAt)
    }

    private fun existingLookup(): TransferLookupResult {
        return TransferLookupResult(
            clientTransferId = "transfer-1",
            fromAccountNumber = "1000",
            toAccountNumber = "2000",
            amount = 10_000,
            fromBalance = 90_000,
            toBalance = 110_000,
            debitTransactionId = "debit-1",
            creditTransactionId = "credit-1",
            status = TransferLookupStatus.SUCCEEDED,
        )
    }

    private class FakeTransferRepository(
        private val lookupResult: TransferLookupResult?,
    ) : TransferRepository {
        override fun transfer(
            command: LedgerTransferCommand,
            debitHistory: TransactionHistory,
            creditHistory: TransactionHistory,
        ): TransferResult {
            throw UnsupportedOperationException("not used")
        }

        override fun findByClientTransferId(clientTransferId: String): TransferLookupResult? {
            return lookupResult?.takeIf { it.clientTransferId == clientTransferId }
        }

        override fun reverse(clientTransferId: String, reversedAt: Instant): TransferReversalResult {
            throw UnsupportedOperationException("not used")
        }
    }

    private fun accountRepository(): AccountRepository = Mockito.mock(AccountRepository::class.java)
}
