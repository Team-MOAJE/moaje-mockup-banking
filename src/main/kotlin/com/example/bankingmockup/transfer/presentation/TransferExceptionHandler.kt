package com.example.bankingmockup.transfer.presentation

import com.example.bankingmockup.transfer.domain.SameAccountTransferException
import com.example.bankingmockup.transfer.domain.TransferNotFoundByClientTransferIdException
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestControllerAdvice
import java.time.Instant

@RestControllerAdvice
class TransferExceptionHandler {
    @ExceptionHandler(SameAccountTransferException::class)
    fun sameAccount(exception: SameAccountTransferException): ResponseEntity<TransferErrorResponse> =
        // 출금 계좌와 수취 계좌가 같으면 실제 송금을 실행하기 전에 거절한다.
        // 다른 송금 오류와 혼동하지 않도록 전용 코드와 HTTP 상태를 함께 반환한다.
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
            TransferErrorResponse("SAME_ACCOUNT_TRANSFER", "출금 계좌와 수취 계좌는 달라야 합니다.", Instant.now()),
        )

    @ExceptionHandler(TransferNotFoundByClientTransferIdException::class)
    fun transferNotFound(exception: TransferNotFoundByClientTransferIdException): ResponseEntity<TransferErrorResponse> =
        // 거래 결과 조회에서 해당 clientTransferId의 기록이 없을 때 반환한다.
        // 송금 요청의 계좌 없음(ACCOUNT_NOT_FOUND)과 구분해야 대사가 잘못 판단하지 않는다.
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(
            TransferErrorResponse("TRANSFER_NOT_FOUND", "해당 송금의 처리 기록이 없습니다.", Instant.now()),
        )
}

data class TransferErrorResponse(
    val code: String,
    val message: String,
    val timestamp: Instant,
)
