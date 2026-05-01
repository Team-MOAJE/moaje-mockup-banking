package com.example.bankingmockup.account.domain

import java.time.Instant

interface AccountRepository {
    fun save(account: Account): Account
    fun findByAccountNumber(accountNumber: String): Account?
    fun cancel(accountNumber: String, canceledAt: Instant): Account
    fun withdraw(accountNumber: String, amount: Long, history: TransactionHistory): Account
    fun findHistories(accountNumber: String): List<TransactionHistory>
}
