package com.example.bankingmockup.account.presentation

import com.example.bankingmockup.account.domain.AccountBusinessException
import com.example.bankingmockup.account.domain.AccountCanceledException
import com.example.bankingmockup.account.domain.AccountNotFoundException
import com.example.bankingmockup.account.domain.InsufficientBalanceException
import com.example.bankingmockup.account.domain.InvalidAccountAmountException
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestControllerAdvice
import java.time.Instant

@RestControllerAdvice
class AccountExceptionHandler {
    @ExceptionHandler(AccountNotFoundException::class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    fun notFound(exception: AccountNotFoundException): ErrorResponse =
        ErrorResponse("ACCOUNT_NOT_FOUND", exception.message.orEmpty(), Instant.now())

    @ExceptionHandler(
        AccountCanceledException::class,
        InsufficientBalanceException::class,
        InvalidAccountAmountException::class,
    )
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun business(exception: AccountBusinessException): ErrorResponse =
        ErrorResponse("ACCOUNT_BUSINESS_ERROR", exception.message.orEmpty(), Instant.now())
}

data class ErrorResponse(
    val code: String,
    val message: String,
    val timestamp: Instant,
)
