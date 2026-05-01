package com.example.bankingmockup.transfer.presentation

import com.example.bankingmockup.transfer.domain.SameAccountTransferException
import com.example.bankingmockup.transfer.domain.TransferBusinessException
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestControllerAdvice
import java.time.Instant

@RestControllerAdvice
class TransferExceptionHandler {
    @ExceptionHandler(SameAccountTransferException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun transferBusiness(exception: TransferBusinessException): TransferErrorResponse =
        TransferErrorResponse("TRANSFER_BUSINESS_ERROR", exception.message.orEmpty(), Instant.now())
}

data class TransferErrorResponse(
    val code: String,
    val message: String,
    val timestamp: Instant,
)
