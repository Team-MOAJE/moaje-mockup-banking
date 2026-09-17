package com.example.bankingmockup.account.application

import com.example.bankingmockup.account.domain.Account
import com.example.bankingmockup.account.domain.AccountRepository
import com.example.bankingmockup.account.domain.AccountSnapshot
import com.example.bankingmockup.account.domain.AccountStatus
import com.example.bankingmockup.account.domain.TransactionHistory
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.Instant

@DisplayName("Mock Banking provider 계좌 Snapshot")
class AccountServiceSnapshotTest {
    @Test
    @DisplayName("외부 출금의 가맹점명을 원장 거래내역에 보존한다")
    fun preservesMerchantNameOnWithdrawal() {
        // given: Asset이 카테고리를 분류할 때 사용할 원본 가맹점명이다.
        val repository = SnapshotRepository()
        val service = AccountService(repository)

        // when
        service.withdraw(
            accountNumber = "account-1",
            command = WithdrawCommand(amount = 4_500L, merchantName = "스타벅스 강남점", memo = "커피"),
        )

        // then: Mock Banking은 카테고리를 정하지 않고 원본 가맹점명만 보존한다.
        assertThat(repository.withdrawnHistory?.merchantName).isEqualTo("스타벅스 강남점")
        assertThat(repository.withdrawnHistory?.memo).isEqualTo("커피")
    }

    @Test
    @DisplayName("providerAccountId와 cursor를 Repository에 그대로 전달한다")
    fun delegatesOpaqueProviderIdAndCursor() {
        // given: 계좌번호를 모르는 Banking 채널계가 불투명 provider 식별자로 조회한다.
        val repository = SnapshotRepository()
        val service = AccountService(repository)
        val cursor = Instant.parse("2026-01-01T00:00:00Z")

        // when
        val result = service.findSnapshot("provider-1", cursor)

        // then: cursor는 밀리초 단위로 전달되고 기준 잔액이 그대로 반환된다.
        assertThat(repository.requestedProviderAccountId).isEqualTo("provider-1")
        assertThat(repository.requestedCursor).isEqualTo(cursor.toEpochMilli())
        assertThat(result.balance).isEqualTo(12_000L)
    }

    private class SnapshotRepository : AccountRepository {
        var requestedProviderAccountId: String? = null
        var requestedCursor: Long? = null
        var withdrawnHistory: TransactionHistory? = null

        override fun findSnapshotByProviderAccountId(providerAccountId: String, cursorEpochMillis: Long): AccountSnapshot {
            requestedProviderAccountId = providerAccountId
            requestedCursor = cursorEpochMillis
            return AccountSnapshot(providerAccountId, 12_000L, AccountStatus.ACTIVE, Instant.now(), emptyList())
        }

        override fun save(account: Account) = account
        override fun findByAccountNumber(accountNumber: String): Account? = null
        override fun findByProviderAccountId(providerAccountId: String): Account? = null
        override fun findActiveByOwnerCiAndBankCodeAndProductName(ownerCi: String, bankCode: String, productName: String): Account? = null
        override fun cancel(accountNumber: String, canceledAt: Instant): Account = error("not used")
        override fun withdraw(accountNumber: String, amount: Long, history: TransactionHistory): Account {
            withdrawnHistory = history
            return Account(
                providerAccountId = "provider-1",
                accountNumber = accountNumber,
                ownerCi = "owner-ci",
                bankCode = "088",
                productName = "테스트 통장",
                balance = 10_000L - amount,
                status = AccountStatus.ACTIVE,
                createdAt = Instant.EPOCH,
            )
        }
        override fun findHistories(accountNumber: String): List<TransactionHistory> = emptyList()
    }
}
