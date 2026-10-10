package com.example.bankingmockup.transfer.presentation

import com.example.bankingmockup.account.domain.AccountCanceledException
import com.example.bankingmockup.account.domain.AccountNotFoundException
import com.example.bankingmockup.account.domain.InsufficientBalanceException
import com.example.bankingmockup.account.domain.InvalidAccountAmountException
import com.example.bankingmockup.account.presentation.AccountExceptionHandler
import com.example.bankingmockup.transfer.application.TransferService
import com.example.bankingmockup.transfer.domain.SameAccountTransferException
import com.example.bankingmockup.transfer.domain.TransferCommand
import com.example.bankingmockup.transfer.domain.TransferNotFoundByClientTransferIdException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.mockito.Mockito
import org.springframework.http.MediaType
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter
import com.fasterxml.jackson.databind.SerializationFeature
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.time.Instant
import java.util.stream.Stream

class TransferErrorResponseContractTest {
    private val service = Mockito.mock(TransferService::class.java)
    private val mvc = MockMvcBuilders.standaloneSetup(TransferController(service))
        .setControllerAdvice(AccountExceptionHandler(), TransferExceptionHandler())
        // standalone MockMvc에는 Boot의 JSON 설정이 자동 적용되지 않는다.
        // 실제 기본 설정처럼 Instant를 ISO 문자열로 보내도록 맞춘다.
        .setMessageConverters(MappingJackson2HttpMessageConverter(
            Jackson2ObjectMapperBuilder.json()
                .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .build(),
        ))
        .build()

    @ParameterizedTest(name = "{0}")
    @MethodSource("rejections")
    @DisplayName("송금 거절 사유마다 HTTP 상태와 구체적인 오류 코드를 함께 반환한다")
    fun returnsExplicitRejectionContract(case: RejectionCase) {
        // given: 서비스가 명확한 업무 거절을 반환하는 상황을 재현한다.
        val command = TransferCommand("test-transfer", "test-provider", "test-recipient", 100, null)
        Mockito.doThrow(case.exception).`when`(service).transfer(command)

        // when: DTO 검증을 통과한 요청이 송금 서비스에 도달한다.
        val result = mvc.perform(post("/api/transfers")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""{"clientTransferId":"test-transfer","fromProviderAccountId":"test-provider","toAccountNumber":"test-recipient","amount":100}"""))
            // then: Banking은 문구가 아닌 HTTP 상태와 code 조합으로 판단할 수 있다.
            .andExpect(status().`is`(case.httpStatus))
            .andExpect(jsonPath("$.code").value(case.code))
            .andExpect(jsonPath("$.message").value(case.message))
            .andExpect(jsonPath("$.timestamp").isString)
            .andReturn()

        val json = com.fasterxml.jackson.module.kotlin.jacksonObjectMapper()
            .readTree(result.response.contentAsByteArray)
        assertThat(Instant.parse(json["timestamp"].asText())).isNotNull()
        assertThat(json["message"].asText()).doesNotContain("test-sensitive-reference")
    }

    @Test
    @DisplayName("거래 결과 없음은 계좌 없음과 다른 코드의 404 응답을 반환한다")
    fun distinguishesMissingTransferFromMissingAccount() {
        // given: 대사 조회에서만 발생하는 거래 기록 없음 예외다.
        Mockito.doThrow(TransferNotFoundByClientTransferIdException("test-transfer"))
            .`when`(service).findByClientTransferId("test-transfer")

        // when / then: 같은 404라도 송금 계좌 없음과 혼동하지 않는다.
        mvc.perform(get("/api/transfers/test-transfer"))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.code").value("TRANSFER_NOT_FOUND"))
            .andExpect(jsonPath("$.message").value("해당 송금의 처리 기록이 없습니다."))
            .andExpect(jsonPath("$.timestamp").isString)
    }

    data class RejectionCase(
        val description: String,
        val exception: RuntimeException,
        val httpStatus: Int,
        val code: String,
        val message: String,
    ) {
        override fun toString(): String = description
    }

    companion object {
        @JvmStatic
        fun rejections(): Stream<RejectionCase> = Stream.of(
            RejectionCase("계좌 없음", AccountNotFoundException("test-sensitive-reference"), 404,
                "ACCOUNT_NOT_FOUND", "계좌를 찾을 수 없습니다."),
            RejectionCase("계좌 해지", AccountCanceledException("test-sensitive-reference"), 400,
                "ACCOUNT_CANCELED", "해지된 계좌는 거래할 수 없습니다."),
            RejectionCase("잔액 부족", InsufficientBalanceException("test-sensitive-reference"), 400,
                "INSUFFICIENT_BALANCE", "거래에 필요한 잔액이 부족합니다."),
            RejectionCase("잘못된 금액", InvalidAccountAmountException(), 400,
                "INVALID_ACCOUNT_AMOUNT", "거래 금액은 0보다 커야 합니다."),
            RejectionCase("동일 계좌 송금", SameAccountTransferException(), 400,
                "SAME_ACCOUNT_TRANSFER", "출금 계좌와 수취 계좌는 달라야 합니다."),
        )
    }
}
