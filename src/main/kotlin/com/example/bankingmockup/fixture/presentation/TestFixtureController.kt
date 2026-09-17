package com.example.bankingmockup.fixture.presentation

import com.example.bankingmockup.fixture.application.FixturePersona
import com.example.bankingmockup.fixture.application.GeneratePersonaFixtureCommand
import com.example.bankingmockup.fixture.application.PersonaFixtureResult
import com.example.bankingmockup.fixture.application.TestFixtureService
import jakarta.validation.Valid
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Profile
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

@RestController
@Profile("local", "test")
@ConditionalOnProperty(prefix = "moaje.fixture", name = ["enabled"], havingValue = "true")
@RequestMapping("/internal/test-fixtures/personas")
class TestFixtureController(
    private val testFixtureService: TestFixtureService,
    @Value("\${moaje.fixture.admin-token}") private val adminToken: String,
) {
    /**
     * 이 API는 Compose의 test-tools가 과거 거래를 준비할 때만 사용한다.
     * 프로필, 활성화 속성, 별도 토큰을 모두 요구해 운영 환경에 우연히 노출되는 것을 막는다.
     */
    @PostMapping
    fun generate(
        @RequestHeader("X-Fixture-Token") fixtureToken: String,
        @Valid @RequestBody request: GeneratePersonaFixtureRequest,
    ): PersonaFixtureResult {
        if (!constantTimeEquals(fixtureToken, adminToken)) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Invalid fixture token")
        }
        return testFixtureService.generate(request.toCommand())
    }

    private fun constantTimeEquals(left: String, right: String): Boolean = MessageDigest.isEqual(
        left.toByteArray(StandardCharsets.UTF_8),
        right.toByteArray(StandardCharsets.UTF_8),
    )
}

data class GeneratePersonaFixtureRequest(
    @field:NotBlank val fixtureRunId: String,
    @field:NotBlank val ownerCi: String,
    @field:NotBlank val bankCode: String,
    @field:NotBlank val productName: String,
    val persona: FixturePersona = FixturePersona.BALANCED_STUDENT,
    @field:Min(1) @field:Max(6) val months: Int = 3,
) {
    fun toCommand() = GeneratePersonaFixtureCommand(
        fixtureRunId = fixtureRunId,
        ownerCi = ownerCi,
        bankCode = bankCode,
        productName = productName,
        persona = persona,
        months = months,
    )
}
