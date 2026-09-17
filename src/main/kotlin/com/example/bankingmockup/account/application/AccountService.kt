package com.example.bankingmockup.account.application

import com.example.bankingmockup.account.domain.Account
import com.example.bankingmockup.account.domain.AccountNotFoundException
import com.example.bankingmockup.account.domain.AccountRepository
import com.example.bankingmockup.account.domain.AccountStatus
import com.example.bankingmockup.account.domain.AccountSnapshot
import com.example.bankingmockup.account.domain.InvalidAccountAmountException
import com.example.bankingmockup.account.domain.TransactionHistory
import com.example.bankingmockup.account.domain.TransactionType
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ThreadLocalRandom

@Service
class AccountService(
    private val accountRepository: AccountRepository,
) {
    private val clock: Clock = Clock.systemUTC()

    fun register(command: RegisterAccountCommand): Account {
        if (command.initialBalance < 0) { // 최초 init 계좌금액이 0원 이하면 예외발생
            throw IllegalArgumentException("initialBalance must not be negative")
        }

        accountRepository.findActiveByOwnerCiAndBankCodeAndProductName(
            ownerCi = command.ownerCi,
            bankCode = command.bankCode,
            productName = command.productName,
        )?.let { return it }

        val account = Account(
            providerAccountId = UUID.randomUUID().toString(),
            accountNumber = generateUniqueAccountNumber(),
            ownerCi = command.ownerCi,
            bankCode = command.bankCode,
            productName = command.productName,
            balance = command.initialBalance,
            status = AccountStatus.ACTIVE,
            createdAt = Instant.now(clock),
        )

        return accountRepository.save(account)
    }

    fun cancel(accountNumber: String): Account {
        val account = findAccount(accountNumber)
        if (account.status == AccountStatus.CANCELED) {
            return account
        }

        return accountRepository.cancel(accountNumber, Instant.now(clock))
    }

    fun findAccount(accountNumber: String): Account =
        accountRepository.findByAccountNumber(accountNumber)
            ?: throw AccountNotFoundException(accountNumber)

    fun findHistories(accountNumber: String): List<TransactionHistory> {
        findAccount(accountNumber)
        return accountRepository.findHistories(accountNumber)
    }

    /**
     * providerAccountId는 실제 계좌번호를 외부 채널계에 노출하지 않고 계정계 계좌를 참조하기 위한 불투명 식별자다.
     * Snapshot은 Redis의 단일 원자 연산으로 잔액과 증분 거래내역을 함께 읽어 서로 다른 시점의 데이터를 섞지 않는다.
     */
    fun findSnapshot(providerAccountId: String, cursor: Instant): AccountSnapshot =
        accountRepository.findSnapshotByProviderAccountId(providerAccountId, cursor.toEpochMilli())
            ?: throw AccountNotFoundException(providerAccountId)

    fun withdraw(accountNumber: String, command: WithdrawCommand): Account {
        if (command.amount <= 0) {
            throw InvalidAccountAmountException()
        }

        val history = TransactionHistory(
            transactionId = UUID.randomUUID().toString(),
            accountNumber = accountNumber,
            type = TransactionType.WITHDRAWAL,
            amount = command.amount,
            merchantName = command.merchantName,
            memo = command.memo,
            createdAt = Instant.now(clock),
        )

        return accountRepository.withdraw(accountNumber, command.amount, history)
    }

    private fun generateUniqueAccountNumber(): String {
        repeat(MAX_ACCOUNT_NUMBER_GENERATION_ATTEMPTS) {
            val candidate = generateAccountNumber()
            if (accountRepository.findByAccountNumber(candidate) == null) {
                return candidate
            }
        }

        throw IllegalStateException("Failed to generate unique account number")
    }

    private fun generateAccountNumber(): String =
        ThreadLocalRandom.current()
            .nextLong(MIN_ACCOUNT_NUMBER, MAX_ACCOUNT_NUMBER_EXCLUSIVE)
            .toString()

    companion object {
        private const val MIN_ACCOUNT_NUMBER = 10_000_000_000_000L
        private const val MAX_ACCOUNT_NUMBER_EXCLUSIVE = 100_000_000_000_000L
        private const val MAX_ACCOUNT_NUMBER_GENERATION_ATTEMPTS = 10
    }
}

data class RegisterAccountCommand(
    val ownerCi: String,
    val bankCode: String,
    val productName: String,
    val initialBalance: Long,
)

data class WithdrawCommand(
    val amount: Long,
    val merchantName: String? = null,
    val memo: String?,
)
