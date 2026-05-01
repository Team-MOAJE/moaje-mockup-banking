package com.example.bankingmockup.account.application

import com.example.bankingmockup.account.domain.Account
import com.example.bankingmockup.account.domain.AccountNotFoundException
import com.example.bankingmockup.account.domain.AccountRepository
import com.example.bankingmockup.account.domain.AccountStatus
import com.example.bankingmockup.account.domain.InvalidAccountAmountException
import com.example.bankingmockup.account.domain.TransactionHistory
import com.example.bankingmockup.account.domain.TransactionType
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.Instant
import java.util.UUID

@Service
class AccountService(
    private val accountRepository: AccountRepository,
) {
    private val clock: Clock = Clock.systemUTC()

    fun register(command: RegisterAccountCommand): Account {
        if (command.initialBalance < 0) {
            throw IllegalArgumentException("initialBalance must not be negative")
        }

        val account = Account(
            accountNumber = generateAccountNumber(),
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

    fun withdraw(accountNumber: String, command: WithdrawCommand): Account {
        if (command.amount <= 0) {
            throw InvalidAccountAmountException()
        }

        val history = TransactionHistory(
            transactionId = UUID.randomUUID().toString(),
            accountNumber = accountNumber,
            type = TransactionType.WITHDRAWAL,
            amount = command.amount,
            memo = command.memo,
            createdAt = Instant.now(clock),
        )

        return accountRepository.withdraw(accountNumber, command.amount, history)
    }

    private fun generateAccountNumber(): String =
        UUID.randomUUID().toString().replace("-", "")
}

data class RegisterAccountCommand(
    val bankCode: String,
    val productName: String,
    val initialBalance: Long,
)

data class WithdrawCommand(
    val amount: Long,
    val memo: String?,
)
