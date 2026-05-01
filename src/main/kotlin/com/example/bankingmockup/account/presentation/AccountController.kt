package com.example.bankingmockup.account.presentation

import com.example.bankingmockup.account.application.AccountService
import com.example.bankingmockup.account.application.RegisterAccountCommand
import com.example.bankingmockup.account.application.WithdrawCommand
import com.example.bankingmockup.account.domain.Account
import com.example.bankingmockup.account.domain.TransactionHistory
import jakarta.validation.Valid
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.time.Instant

@RestController
@RequestMapping("/api/accounts")
class AccountController(
    private val accountService: AccountService,
) {
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun register(@Valid @RequestBody request: RegisterAccountRequest): AccountResponse =
        accountService.register(request.toCommand()).toResponse()

    @DeleteMapping("/{accountNumber}")
    fun cancel(@PathVariable accountNumber: String): AccountResponse =
        accountService.cancel(accountNumber).toResponse()

    @GetMapping("/{accountNumber}")
    fun get(@PathVariable accountNumber: String): AccountResponse =
        accountService.findAccount(accountNumber).toResponse()

    @GetMapping("/{accountNumber}/histories")
    fun histories(@PathVariable accountNumber: String): List<TransactionHistoryResponse> =
        accountService.findHistories(accountNumber).map { it.toResponse() }

    @PostMapping("/{accountNumber}/withdrawals")
    fun withdraw(
        @PathVariable accountNumber: String,
        @Valid @RequestBody request: WithdrawRequest,
    ): AccountResponse =
        accountService.withdraw(accountNumber, request.toCommand()).toResponse()
}

data class RegisterAccountRequest(
    @field:NotBlank
    val bankCode: String,
    @field:NotBlank
    val productName: String,
    @field:Min(0)
    val initialBalance: Long,
) {
    fun toCommand(): RegisterAccountCommand =
        RegisterAccountCommand(
            bankCode = bankCode,
            productName = productName,
            initialBalance = initialBalance,
        )
}

data class WithdrawRequest(
    @field:Min(1)
    val amount: Long,
    val memo: String? = null,
) {
    fun toCommand(): WithdrawCommand =
        WithdrawCommand(amount = amount, memo = memo)
}

data class AccountResponse(
    val accountNumber: String,
    val bankCode: String,
    val productName: String,
    val balance: Long,
    val status: String,
    val createdAt: Instant,
    val canceledAt: Instant?,
)

data class TransactionHistoryResponse(
    val transactionId: String,
    val accountNumber: String,
    val type: String,
    val amount: Long,
    val counterpartyAccountNumber: String?,
    val counterpartyBankCode: String?,
    val memo: String?,
    val createdAt: Instant,
)

private fun Account.toResponse(): AccountResponse =
    AccountResponse(
        accountNumber = accountNumber,
        bankCode = bankCode,
        productName = productName,
        balance = balance,
        status = status.name,
        createdAt = createdAt,
        canceledAt = canceledAt,
    )

private fun TransactionHistory.toResponse(): TransactionHistoryResponse =
    TransactionHistoryResponse(
        transactionId = transactionId,
        accountNumber = accountNumber,
        type = type.name,
        amount = amount,
        counterpartyAccountNumber = counterpartyAccountNumber,
        counterpartyBankCode = counterpartyBankCode,
        memo = memo,
        createdAt = createdAt,
    )
