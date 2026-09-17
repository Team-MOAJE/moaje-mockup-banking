package com.example.bankingmockup.account.presentation

import com.example.bankingmockup.account.application.AccountService
import com.example.bankingmockup.account.application.RegisterAccountCommand
import com.example.bankingmockup.account.application.WithdrawCommand
import com.example.bankingmockup.account.domain.Account
import com.example.bankingmockup.account.domain.TransactionHistory
import com.example.bankingmockup.auth.presentation.HmacAndTokenInterceptor
import jakarta.validation.Valid
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.time.Instant

/**
 * 계좌 등록/조회/출금 Controller
 *
 */
@RestController
@RequestMapping("/api/accounts")
class AccountController(
    private val accountService: AccountService,
) {
    @PostMapping
    fun register(
        servletRequest: HttpServletRequest,
        @Valid @RequestBody request: RegisterAccountRequest,
    ): AccountResponse =
        accountService.register(request.toCommand(servletRequest.authenticatedCi())).toResponse()

    @DeleteMapping("/{accountNumber}")
    fun cancel(@PathVariable accountNumber: String): AccountResponse =
        accountService.cancel(accountNumber).toResponse()

    @GetMapping("/{accountNumber}")
    fun get(@PathVariable accountNumber: String): AccountResponse =
        accountService.findAccount(accountNumber).toResponse()

    @GetMapping("/{accountNumber}/histories")
    fun histories(@PathVariable accountNumber: String): List<TransactionHistoryResponse> =
        accountService.findHistories(accountNumber).map { it.toResponse() }

    @GetMapping("/providers/{providerAccountId}/snapshot")
    fun snapshot(
        @PathVariable providerAccountId: String,
        @RequestParam(required = false, defaultValue = "1970-01-01T00:00:00Z") cursor: Instant,
    ): ProviderAccountSnapshotResponse =
        accountService.findSnapshot(providerAccountId, cursor).let { snapshot ->
            ProviderAccountSnapshotResponse(
                providerAccountId = snapshot.providerAccountId,
                balance = snapshot.balance,
                status = snapshot.status.name,
                asOf = snapshot.asOf,
                histories = snapshot.histories.map { it.toResponse().withoutAccountNumbers() },
            )
        }

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
    fun toCommand(ownerCi: String): RegisterAccountCommand =
        RegisterAccountCommand(
            ownerCi = ownerCi,
            bankCode = bankCode,
            productName = productName,
            initialBalance = initialBalance,
        )
}

data class WithdrawRequest(
    @field:Min(1)
    val amount: Long,
    val merchantName: String? = null,
    val memo: String? = null,
) {
    fun toCommand(): WithdrawCommand =
        WithdrawCommand(amount = amount, merchantName = merchantName, memo = memo)
}


data class AccountResponse(
    val providerAccountId: String,
    val accountNumber: String,
    val bankCode: String,
    val productName: String,
    val balance: Long,
    val status: String,
    val createdAt: Instant,
    val canceledAt: Instant?,
)

data class ProviderAccountSnapshotResponse(
    val providerAccountId: String,
    val balance: Long,
    val status: String,
    val asOf: Instant,
    val histories: List<TransactionHistoryResponse>,
)

data class TransactionHistoryResponse(
    val transactionId: String,
    val accountNumber: String,
    val type: String,
    val amount: Long,
    val counterpartyAccountNumber: String?,
    val counterpartyBankCode: String?,
    val merchantName: String?,
    val memo: String?,
    val createdAt: Instant,
    val completedAtEpochMillis: Long? = null,
)

private fun Account.toResponse(): AccountResponse =
    AccountResponse(
        providerAccountId = providerAccountId,
        accountNumber = accountNumber,
        bankCode = bankCode,
        productName = productName,
        balance = balance,
        status = status.name,
        createdAt = createdAt,
        canceledAt = canceledAt,
    )

private fun TransactionHistoryResponse.withoutAccountNumbers(): TransactionHistoryResponse =
    copy(accountNumber = "", counterpartyAccountNumber = null)

private fun TransactionHistory.toResponse(): TransactionHistoryResponse =
    TransactionHistoryResponse(
        transactionId = transactionId,
        accountNumber = accountNumber,
        type = type.name,
        amount = amount,
        counterpartyAccountNumber = counterpartyAccountNumber,
        counterpartyBankCode = counterpartyBankCode,
        merchantName = merchantName,
        memo = memo,
        createdAt = createdAt,
        completedAtEpochMillis = completedAtEpochMillis,
    )

private fun HttpServletRequest.authenticatedCi(): String =
    getAttribute(HmacAndTokenInterceptor.AUTHENTICATED_CI_ATTRIBUTE) as? String
        ?: throw IllegalStateException("Authenticated CI is required")
