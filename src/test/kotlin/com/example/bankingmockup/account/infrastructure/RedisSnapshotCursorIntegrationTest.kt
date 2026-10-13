package com.example.bankingmockup.account.infrastructure

import com.example.bankingmockup.account.domain.*
import com.example.bankingmockup.transfer.domain.LedgerTransferCommand
import com.example.bankingmockup.transfer.infrastructure.RedisTransferRepository
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.*
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory
import org.springframework.data.redis.core.StringRedisTemplate
import java.time.Instant
import java.util.UUID

@Tag("redis")
class RedisSnapshotCursorIntegrationTest {
    private val connection = LettuceConnectionFactory(
        System.getenv("MOCK_REDIS_TEST_HOST") ?: "127.0.0.1",
        System.getenv("MOCK_REDIS_TEST_PORT")?.toInt() ?: 6379,
    ).apply { afterPropertiesSet(); start() }
    private val redis = StringRedisTemplate(connection)
    private val mapper = jacksonObjectMapper().findAndRegisterModules()
        .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
    private val accounts = RedisAccountRepository(redis, mapper)
    private val transfers = RedisTransferRepository(redis, mapper)
    private val ownedKeys = mutableSetOf<String>()

    @AfterEach
    fun cleanup() {
        // FLUSHDB는 다른 개발 데이터도 지운다. 이번 테스트가 만든 무작위 키만 삭제한다.
        try { if (ownedKeys.isNotEmpty()) redis.delete(ownedKeys) } finally { connection.destroy() }
    }

    @Test
    @DisplayName("조회 전에 만들어졌지만 조회 후 확정된 송금도 다음 증분 스냅샷에서 빠지지 않는다")
    fun delayedTransferIsVisibleAfterCursor() {
        // given: 최초 요청 시각은 어제이고, 실제 처리는 첫 스냅샷보다 뒤에 일어난다.
        val from = account(10_000)
        val to = account(0)
        val before = accounts.findSnapshotByProviderAccountId(from.providerAccountId, 0)!!
        val occurredAt = Instant.now().minusSeconds(86_400)
        val transferId = UUID.randomUUID().toString()
        ownedKeys += "{banking}:transfer:$transferId"
        val debit = history(from, TransactionType.TRANSFER_OUT, occurredAt)
        val credit = history(to, TransactionType.TRANSFER_IN, occurredAt)
        // when: Lua를 실제 Redis에서 실행한다. 가짜 Repository로 대신하지 않는다.
        transfers.transfer(LedgerTransferCommand(transferId, from.accountNumber, to.accountNumber, 1000, null), debit, credit)
        val after = accounts.findSnapshotByProviderAccountId(from.providerAccountId, before.asOf.toEpochMilli())!!
        // then: 요청 날짜는 보존되고 조회 cursor는 실제 반영 시각을 기준으로 거래를 찾는다.
        assertThat(after.balance).isEqualTo(9000)
        assertThat(after.histories).hasSize(1)
        assertThat(after.histories.single().createdAt).isEqualTo(occurredAt)
        assertThat(after.histories.single().completedAtEpochMillis).isGreaterThanOrEqualTo(before.asOf.toEpochMilli())
    }

    @Test
    @DisplayName("늦게 처리된 외부 출금도 원 발생 시각과 확정 시각을 구분해 조회된다")
    fun delayedWithdrawalIsVisibleAfterCursor() {
        // given
        val account = account(10_000)
        val before = accounts.findSnapshotByProviderAccountId(account.providerAccountId, 0)!!
        val occurredAt = Instant.now().minusSeconds(86_400)
        // when
        accounts.withdraw(account.accountNumber, 1000, history(account, TransactionType.WITHDRAWAL, occurredAt))
        val after = accounts.findSnapshotByProviderAccountId(account.providerAccountId, before.asOf.toEpochMilli())!!
        // then
        assertThat(after.balance).isEqualTo(9000)
        assertThat(after.histories).hasSize(1)
        assertThat(after.histories.single().createdAt).isEqualTo(occurredAt)
        assertThat(after.histories.single().completedAtEpochMillis).isGreaterThanOrEqualTo(before.asOf.toEpochMilli())
    }

    @Test
    @DisplayName("Redis 저장과 원자적 스냅샷 조회에서 은행 정보를 유지한다")
    fun bankInformationSurvivesRedisRoundTrip() {
        // given
        val account = account(10_000)
        // when
        val stored = accounts.findByAccountNumber(account.accountNumber)!!
        val snapshot = accounts.findSnapshotByProviderAccountId(account.providerAccountId, 0)!!
        // then: 실제 Lua 응답도 검사해 HTTP 응답 이전의 누락을 발견한다.
        assertThat(stored.bankCode).isEqualTo("TEST")
        assertThat(stored.bankName).isEqualTo("테스트 은행")
        assertThat(snapshot.bankCode).isEqualTo("TEST")
        assertThat(snapshot.bankName).isEqualTo("테스트 은행")
    }

    @Test
    @DisplayName("은행명이 없는 기존 Redis 계좌도 이름을 추측하지 않고 조회한다")
    fun legacyAccountWithoutBankNameRemainsReadable() {
        // given: 필드 추가 전의 Redis 데이터를 재현한다.
        val account = account(0)
        redis.opsForHash<String, String>().delete(RedisAccountKeys.account(account.accountNumber), "bankName")
        // when
        val stored = accounts.findByAccountNumber(account.accountNumber)!!
        val snapshot = accounts.findSnapshotByProviderAccountId(account.providerAccountId, 0)!!
        // then
        assertThat(stored.bankName).isNull()
        assertThat(snapshot.bankName).isNull()
        assertThat(snapshot.bankCode).isEqualTo("TEST")
    }

    private fun account(balance: Long): Account {
        val id = "test-${UUID.randomUUID()}"
        val account = Account(id, id, id, "TEST", id, balance, AccountStatus.ACTIVE, Instant.now(), bankName = "테스트 은행")
        ownedKeys += RedisAccountKeys.account(id)
        ownedKeys += RedisAccountKeys.history(id)
        ownedKeys += RedisAccountKeys.providerAccountIndex(id)
        ownedKeys += RedisAccountKeys.accountOwnerIndex(id, "TEST", id)
        return accounts.save(account)
    }

    private fun history(account: Account, type: TransactionType, occurredAt: Instant) =
        TransactionHistory(UUID.randomUUID().toString(), account.accountNumber, type, 1000, createdAt = occurredAt)
}
