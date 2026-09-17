package com.example.bankingmockup.fixture.application

import com.example.bankingmockup.account.domain.AccountNotFoundException
import com.example.bankingmockup.account.domain.AccountRepository
import com.example.bankingmockup.account.domain.TransactionHistory
import com.example.bankingmockup.account.domain.TransactionType
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneOffset

enum class FixturePersona {
    BALANCED_STUDENT,
    CAFE_AND_DINING,
    COMMUTER,
    STUDY_FOCUSED,
}

data class GeneratePersonaFixtureCommand(
    val fixtureRunId: String,
    val ownerCi: String,
    val bankCode: String,
    val productName: String,
    val persona: FixturePersona,
    val months: Int,
)

data class PersonaFixtureResult(
    val fixtureRunId: String,
    val providerAccountId: String,
    val persona: FixturePersona,
    val months: List<String>,
    val createdTransactionCount: Int,
    val skippedTransactionCount: Int,
)

@Service
class TestFixtureService(
    private val accountRepository: AccountRepository,
    private val clock: Clock = Clock.systemUTC(),
) {
    /**
     * 테스트 거래도 계정계 원장인 Mock Banking에 먼저 기록한다.
     * Asset DB에 직접 넣지 않아 Snapshot 수집, 분류, 중복 방지까지 실제 흐름으로 검증할 수 있다.
     */
    fun generate(command: GeneratePersonaFixtureCommand): PersonaFixtureResult {
        require(command.months in 1..6) { "months must be between 1 and 6" }

        val account = accountRepository.findActiveByOwnerCiAndBankCodeAndProductName(
            command.ownerCi,
            command.bankCode,
            command.productName,
        ) ?: throw AccountNotFoundException(command.productName)
        val existingIds = accountRepository.findHistories(account.accountNumber)
            .mapTo(mutableSetOf()) { it.transactionId }
        val currentInstant = Instant.now(clock)
        val currentMonth = YearMonth.from(currentInstant.atZone(ZoneOffset.UTC))
        var created = 0
        var skipped = 0

        val targetMonths = (command.months - 1 downTo 0).map { currentMonth.minusMonths(it.toLong()) }
        targetMonths.forEach { month ->
            templates.forEachIndexed { index, template ->
                val transactionId = "fixture:${command.fixtureRunId}:$month:$index"
                if (!existingIds.add(transactionId)) {
                    skipped++
                    return@forEachIndexed
                }

                val occurredAt = occurredAt(month, currentMonth, currentInstant, index)
                val history = TransactionHistory(
                    transactionId = transactionId,
                    accountNumber = account.accountNumber,
                    type = TransactionType.WITHDRAWAL,
                    amount = template.amountFor(command.persona),
                    merchantName = template.merchantName,
                    memo = "fixture:${command.fixtureRunId}",
                    createdAt = occurredAt,
                    completedAtEpochMillis = occurredAt.toEpochMilli(),
                )
                accountRepository.withdraw(account.accountNumber, history.amount, history)
                created++
            }
        }

        return PersonaFixtureResult(
            fixtureRunId = command.fixtureRunId,
            providerAccountId = account.providerAccountId,
            persona = command.persona,
            months = targetMonths.map(YearMonth::toString),
            createdTransactionCount = created,
            skippedTransactionCount = skipped,
        )
    }

    private fun occurredAt(
        month: YearMonth,
        currentMonth: YearMonth,
        currentInstant: Instant,
        index: Int,
    ): Instant {
        val currentDay = currentInstant.atZone(ZoneOffset.UTC).dayOfMonth
        val availableLastDay = if (month == currentMonth) currentDay else month.lengthOfMonth()
        val day = minOf(2 + index * 3, availableLastDay)
        return month.atDay(day).atTime(12, 0, index).toInstant(ZoneOffset.UTC)
    }

    private data class TransactionTemplate(
        val merchantName: String,
        val baseAmount: Long,
        val category: FixtureCategory,
    ) {
        fun amountFor(persona: FixturePersona): Long {
            val multiplier = when (persona) {
                FixturePersona.BALANCED_STUDENT -> 1
                FixturePersona.CAFE_AND_DINING -> if (category in setOf(FixtureCategory.FOOD, FixtureCategory.CAFE)) 3 else 1
                FixturePersona.COMMUTER -> if (category == FixtureCategory.TRANSPORT) 4 else 1
                FixturePersona.STUDY_FOCUSED -> if (category in setOf(FixtureCategory.STUDY, FixtureCategory.CAFE)) 3 else 1
            }
            return baseAmount * multiplier
        }
    }

    private enum class FixtureCategory { FOOD, CAFE, TRANSPORT, CULTURE, SHOPPING, STUDY, HOUSING, UNCATEGORIZED }

    private companion object {
        val templates = listOf(
            TransactionTemplate("배달의민족", 18_000, FixtureCategory.FOOD),
            TransactionTemplate("스타벅스 강남점", 5_500, FixtureCategory.CAFE),
            TransactionTemplate("카카오T 택시", 12_000, FixtureCategory.TRANSPORT),
            TransactionTemplate("CGV 대학로", 15_000, FixtureCategory.CULTURE),
            TransactionTemplate("올리브영 신촌점", 23_000, FixtureCategory.SHOPPING),
            TransactionTemplate("교보문고 광화문점", 28_000, FixtureCategory.STUDY),
            TransactionTemplate("KT 통신요금", 45_000, FixtureCategory.HOUSING),
            TransactionTemplate("MOAJE TEST STORE", 7_000, FixtureCategory.UNCATEGORIZED),
        )
    }
}
