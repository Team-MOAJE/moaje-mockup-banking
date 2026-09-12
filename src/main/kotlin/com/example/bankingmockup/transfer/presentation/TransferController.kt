package com.example.bankingmockup.transfer.presentation

import com.example.bankingmockup.transfer.application.TransferService
import com.example.bankingmockup.transfer.domain.TransferCommand
import com.example.bankingmockup.transfer.domain.TransferLookupResult
import com.example.bankingmockup.transfer.domain.TransferNotFoundByClientTransferIdException
import com.example.bankingmockup.transfer.domain.TransferResult
import jakarta.validation.Valid
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Instant

/**
 * 이체 Controller
 */
@RestController
@RequestMapping("/api/transfers")
class TransferController(
    private val transferService: TransferService,
) {
    @PostMapping
    fun transfer(@Valid @RequestBody request: TransferRequest): TransferResponse =
        transferService.transfer(request.toCommand()).toResponse()

    @PostMapping("/{clientTransferId}/reverse")
    fun reverse(@PathVariable clientTransferId: String): TransferReversalResponse =
        transferService.reverse(clientTransferId).toResponse()

    @GetMapping("/{clientTransferId}")
    fun findByClientTransferId(@PathVariable clientTransferId: String): TransferLookupResponse =
        transferService.findByClientTransferId(clientTransferId)
            ?.toResponse()
            ?: throw TransferNotFoundByClientTransferIdException(clientTransferId)
}

data class TransferRequest(
    @field:NotBlank
    val clientTransferId: String,
    @field:NotBlank
    val fromProviderAccountId: String,
    @field:NotBlank
    val toAccountNumber: String,
    @field:Min(1)
    val amount: Long,
    val memo: String? = null,
) {
    fun toCommand(): TransferCommand =
        TransferCommand(
            clientTransferId = clientTransferId,
            fromProviderAccountId = fromProviderAccountId,
            toAccountNumber = toAccountNumber,
            amount = amount,
            memo = memo,
        )
}

data class TransferResponse(
    val clientTransferId: String,
    val fromAccountNumber: String,
    val toAccountNumber: String,
    val amount: Long,
    val fromBalance: Long,
    val toBalance: Long,
    val debitHistory: TransferHistoryResponse,
    val creditHistory: TransferHistoryResponse,
)

data class TransferReversalResponse(
    val clientTransferId: String,
    val fromAccountNumber: String,
    val toAccountNumber: String,
    val amount: Long,
    val fromBalance: Long,
    val toBalance: Long,
    val alreadyReversed: Boolean,
    val debitReversalHistory: TransferHistoryResponse?,
    val creditReversalHistory: TransferHistoryResponse?,
)

data class TransferLookupResponse(
    val clientTransferId: String,
    val fromAccountNumber: String,
    val toAccountNumber: String,
    val amount: Long,
    val fromBalance: Long,
    val toBalance: Long,
    val debitTransactionId: String,
    val creditTransactionId: String,
    val status: String,
    val debitReversalTransactionId: String?,
    val creditReversalTransactionId: String?,
    val reversedAt: Instant?,
)

data class TransferHistoryResponse(
    val transactionId: String,
    val accountNumber: String,
    val type: String,
    val amount: Long,
    val counterpartyAccountNumber: String?,
    val counterpartyBankCode: String?,
    val memo: String?,
    val createdAt: Instant,
)

private fun TransferResult.toResponse(): TransferResponse =
    TransferResponse(
        clientTransferId = clientTransferId,
        fromAccountNumber = fromAccountNumber,
        toAccountNumber = toAccountNumber,
        amount = amount,
        fromBalance = fromBalance,
        toBalance = toBalance,
        debitHistory = TransferHistoryResponse(
            transactionId = debitHistory.transactionId,
            accountNumber = debitHistory.accountNumber,
            type = debitHistory.type.name,
            amount = debitHistory.amount,
            counterpartyAccountNumber = debitHistory.counterpartyAccountNumber,
            counterpartyBankCode = debitHistory.counterpartyBankCode,
            memo = debitHistory.memo,
            createdAt = debitHistory.createdAt,
        ),
        creditHistory = TransferHistoryResponse(
            transactionId = creditHistory.transactionId,
            accountNumber = creditHistory.accountNumber,
            type = creditHistory.type.name,
            amount = creditHistory.amount,
            counterpartyAccountNumber = creditHistory.counterpartyAccountNumber,
            counterpartyBankCode = creditHistory.counterpartyBankCode,
            memo = creditHistory.memo,
            createdAt = creditHistory.createdAt,
        ),
    )

private fun TransferLookupResult.toResponse(): TransferLookupResponse =
    TransferLookupResponse(
        clientTransferId = clientTransferId,
        fromAccountNumber = fromAccountNumber,
        toAccountNumber = toAccountNumber,
        amount = amount,
        fromBalance = fromBalance,
        toBalance = toBalance,
        debitTransactionId = debitTransactionId,
        creditTransactionId = creditTransactionId,
        status = status.name,
        debitReversalTransactionId = debitReversalTransactionId,
        creditReversalTransactionId = creditReversalTransactionId,
        reversedAt = reversedAt,
    )

private fun com.example.bankingmockup.transfer.domain.TransferReversalResult.toResponse(): TransferReversalResponse =
    TransferReversalResponse(
        clientTransferId = clientTransferId,
        fromAccountNumber = fromAccountNumber,
        toAccountNumber = toAccountNumber,
        amount = amount,
        fromBalance = fromBalance,
        toBalance = toBalance,
        alreadyReversed = alreadyReversed,
        debitReversalHistory = debitReversalHistory?.toResponse(),
        creditReversalHistory = creditReversalHistory?.toResponse(),
    )

private fun com.example.bankingmockup.account.domain.TransactionHistory.toResponse(): TransferHistoryResponse =
    TransferHistoryResponse(
        transactionId = transactionId,
        accountNumber = accountNumber,
        type = type.name,
        amount = amount,
        counterpartyAccountNumber = counterpartyAccountNumber,
        counterpartyBankCode = counterpartyBankCode,
        memo = memo,
        createdAt = createdAt,
    )
