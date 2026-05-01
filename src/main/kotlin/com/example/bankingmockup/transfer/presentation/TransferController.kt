package com.example.bankingmockup.transfer.presentation

import com.example.bankingmockup.transfer.application.TransferService
import com.example.bankingmockup.transfer.domain.TransferCommand
import com.example.bankingmockup.transfer.domain.TransferResult
import jakarta.validation.Valid
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Instant

@RestController
@RequestMapping("/api/transfers")
class TransferController(
    private val transferService: TransferService,
) {
    @PostMapping
    fun transfer(@Valid @RequestBody request: TransferRequest): TransferResponse =
        transferService.transfer(request.toCommand()).toResponse()
}

data class TransferRequest(
    @field:NotBlank
    val fromAccountNumber: String,
    @field:NotBlank
    val toAccountNumber: String,
    @field:Min(1)
    val amount: Long,
    val memo: String? = null,
) {
    fun toCommand(): TransferCommand =
        TransferCommand(
            fromAccountNumber = fromAccountNumber,
            toAccountNumber = toAccountNumber,
            amount = amount,
            memo = memo,
        )
}

data class TransferResponse(
    val fromAccountNumber: String,
    val toAccountNumber: String,
    val amount: Long,
    val fromBalance: Long,
    val toBalance: Long,
    val debitHistory: TransferHistoryResponse,
    val creditHistory: TransferHistoryResponse,
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
