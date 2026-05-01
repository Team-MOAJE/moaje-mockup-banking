package com.example.bankingmockup.account.domain

sealed class AccountBusinessException(message: String) : RuntimeException(message)

class AccountNotFoundException(accountNumber: String) :
    AccountBusinessException("Account not found: $accountNumber")

class AccountCanceledException(accountNumber: String) :
    AccountBusinessException("Account is canceled: $accountNumber")

class InsufficientBalanceException(accountNumber: String) :
    AccountBusinessException("Insufficient balance: $accountNumber")

class InvalidAccountAmountException :
    AccountBusinessException("Amount must be positive")
