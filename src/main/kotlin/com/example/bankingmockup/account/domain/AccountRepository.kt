package com.example.bankingmockup.account.domain

import java.time.Instant

interface AccountRepository {
    fun save(account: Account): Account
    fun findByAccountNumber(accountNumber: String): Account?
    fun findByProviderAccountId(providerAccountId: String): Account?
    fun findActiveByOwnerCiAndBankCodeAndProductName(ownerCi: String, bankCode: String, productName: String): Account?
    fun cancel(accountNumber: String, canceledAt: Instant): Account
    fun withdraw(accountNumber: String, amount: Long, history: TransactionHistory): Account
    fun findHistories(accountNumber: String): List<TransactionHistory>
    fun findSnapshotByProviderAccountId(providerAccountId: String, cursorEpochMillis: Long): AccountSnapshot?
}

data class AccountSnapshot(
    val providerAccountId: String,
    val balance: Long,
    val status: AccountStatus,
    val asOf: Instant,
    val histories: List<TransactionHistory>,
)
