package com.example.bankingmockup.fixture.application

import com.example.bankingmockup.account.domain.Account
import com.example.bankingmockup.account.domain.AccountRepository
import com.example.bankingmockup.account.domain.AccountSnapshot
import com.example.bankingmockup.account.domain.AccountStatus
import com.example.bankingmockup.account.domain.TransactionHistory
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class TestFixtureServiceTest {
    private val account = Account(
        providerAccountId = "provider-1",
        accountNumber = "10000000000001",
        ownerCi = "fixture-ci",
        bankCode = "001",
        productName = "fixture-account",
        balance = 5_000_000,
        status = AccountStatus.ACTIVE,
        createdAt = Instant.parse("2026-01-01T00:00:00Z"),
    )
    private val repository = InMemoryAccountRepository(account)
    private val service = TestFixtureService(
        repository,
        Clock.fixed(Instant.parse("2026-09-18T09:00:00Z"), ZoneOffset.UTC),
    )

    @Test
    @DisplayName("3개월 페르소나는 월마다 여덟 카테고리 거래를 계정계 원장에 만든다")
    fun createsTransactionsForEveryMonth() {
        val command = command()

        val result = service.generate(command)

        assertThat(result.months).containsExactly("2026-07", "2026-08", "2026-09")
        assertThat(result.createdTransactionCount).isEqualTo(24)
        assertThat(repository.histories.map { it.createdAt.atZone(ZoneOffset.UTC).monthValue }.toSet())
            .containsExactlyInAnyOrder(7, 8, 9)
        assertThat(repository.histories.mapNotNull { it.merchantName })
            .contains("배달의민족", "스타벅스 강남점", "KT 통신요금", "MOAJE TEST STORE")
    }

    @Test
    @DisplayName("같은 fixtureRunId를 재요청하면 이미 만든 거래를 다시 출금하지 않는다")
    fun sameRunIdIsIdempotent() {
        val command = command()
        service.generate(command)

        val retried = service.generate(command)

        assertThat(retried.createdTransactionCount).isZero()
        assertThat(retried.skippedTransactionCount).isEqualTo(24)
        assertThat(repository.histories).hasSize(24)
    }

    @Test
    @DisplayName("월 40건씩 3개월을 생성해 무한 스크롤용 120건을 준비한다")
    fun createsScrollableFixture() {
        // given: 화면에서 선택한 거래량을 하나의 생성 요청으로 전달한다.
        val command = command().copy(transactionsPerMonth = 40)
        // when
        val result = service.generate(command)
        // then: 서로 다른 ID와 현재 시각 이하의 거래만 저장된다.
        assertThat(result.createdTransactionCount).isEqualTo(120)
        assertThat(repository.histories.map { it.transactionId }.distinct()).hasSize(120)
        assertThat(repository.histories.all { !it.createdAt.isAfter(Instant.parse("2026-09-18T09:00:00Z")) }).isTrue()
        assertThat(service.generate(command).createdTransactionCount).isZero()
    }

    @Test
    @DisplayName("월 80건도 유효한 시각으로 생성하며 지원하지 않는 거래량은 거절한다")
    fun validatesVolume() {
        assertThat(service.generate(command().copy(transactionsPerMonth = 80)).createdTransactionCount).isEqualTo(240)
        org.assertj.core.api.Assertions.assertThatThrownBy {
            service.generate(command().copy(transactionsPerMonth = 9))
        }.isInstanceOf(IllegalArgumentException::class.java)
    }

    private fun command() = GeneratePersonaFixtureCommand(
        fixtureRunId = "run-1",
        ownerCi = account.ownerCi,
        bankCode = account.bankCode,
        productName = account.productName,
        persona = FixturePersona.BALANCED_STUDENT,
        months = 3,
    )

    private class InMemoryAccountRepository(private var account: Account) : AccountRepository {
        val histories = mutableListOf<TransactionHistory>()

        override fun findActiveByOwnerCiAndBankCodeAndProductName(
            ownerCi: String,
            bankCode: String,
            productName: String,
        ): Account? = account.takeIf {
            it.ownerCi == ownerCi && it.bankCode == bankCode && it.productName == productName
        }

        override fun withdraw(accountNumber: String, amount: Long, history: TransactionHistory): Account {
            account = account.copy(balance = account.balance - amount)
            histories += history
            return account
        }

        override fun findHistories(accountNumber: String): List<TransactionHistory> = histories.toList()
        override fun save(account: Account): Account = account
        override fun findByAccountNumber(accountNumber: String): Account? = account
        override fun findByProviderAccountId(providerAccountId: String): Account? = account
        override fun cancel(accountNumber: String, canceledAt: Instant): Account = account
        override fun findSnapshotByProviderAccountId(providerAccountId: String, cursorEpochMillis: Long): AccountSnapshot? = null
    }
}
