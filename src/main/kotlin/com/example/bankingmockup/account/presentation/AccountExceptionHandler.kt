package com.example.bankingmockup.account.presentation

import com.example.bankingmockup.account.domain.AccountCanceledException
import com.example.bankingmockup.account.domain.AccountNotFoundException
import com.example.bankingmockup.account.domain.InsufficientBalanceException
import com.example.bankingmockup.account.domain.InvalidAccountAmountException
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestControllerAdvice
import java.time.Instant

@RestControllerAdvice
class AccountExceptionHandler {
    @ExceptionHandler(AccountNotFoundException::class)
    fun notFound(exception: AccountNotFoundException): ResponseEntity<ErrorResponse> =
        // 송금에 필요한 계좌가 없으면 잔액 변경 전에 거절한다.
        // 예외 메시지에 포함된 계좌 식별자는 응답에 노출하지 않는다.
        error(HttpStatus.NOT_FOUND, "ACCOUNT_NOT_FOUND", "계좌를 찾을 수 없습니다.")

    @ExceptionHandler(AccountCanceledException::class)
    fun canceled(exception: AccountCanceledException): ResponseEntity<ErrorResponse> =
        // 현재 Mock은 송금 양쪽 계좌가 ACTIVE인지 잔액 변경 전에 검사한다.
        // 해지된 계좌로 인한 거절을 잔액 부족과 구분한다.
        error(HttpStatus.BAD_REQUEST, "ACCOUNT_CANCELED", "해지된 계좌는 거래할 수 없습니다.")

    @ExceptionHandler(InsufficientBalanceException::class)
    fun insufficientBalance(exception: InsufficientBalanceException): ResponseEntity<ErrorResponse> =
        // 송금에 필요한 잔액이 부족하면 출금·입금을 실행하지 않는다.
        // Banking이 설명 문구를 해석하지 않고 코드로 거절 사유를 구분하게 한다.
        error(HttpStatus.BAD_REQUEST, "INSUFFICIENT_BALANCE", "거래에 필요한 잔액이 부족합니다.")

    @ExceptionHandler(InvalidAccountAmountException::class)
    fun invalidAmount(exception: InvalidAccountAmountException): ResponseEntity<ErrorResponse> =
        // 0 이하의 금액은 금융 처리 전에 거절한다.
        // 요청 DTO 검증에서 발생하는 일반 400 응답과는 구분되는 업무 오류다.
        error(HttpStatus.BAD_REQUEST, "INVALID_ACCOUNT_AMOUNT", "거래 금액은 0보다 커야 합니다.")

    private fun error(status: HttpStatus, code: String, message: String): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(status).body(ErrorResponse(code, message, Instant.now()))
}

data class ErrorResponse(
    val code: String,
    val message: String,
    val timestamp: Instant,
)
