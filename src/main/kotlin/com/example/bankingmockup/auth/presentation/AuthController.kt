package com.example.bankingmockup.auth.presentation

import com.example.bankingmockup.auth.application.AuthTokenService
import com.example.bankingmockup.auth.application.IssueTokenCommand
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

@RestController
class AuthController(
    private val authTokenService: AuthTokenService,
) {
    @PostMapping("/oauth/2.0/token")
    fun issue(@Valid @RequestBody request: IssueTokenRequest): IssueTokenResponse {
        val result = authTokenService.issue(request.toCommand())
        return IssueTokenResponse(
            accessToken = result.accessToken,
            tokenType = result.tokenType,
            expiresIn = result.expiresIn,
        )
    }
}

data class IssueTokenRequest(
    @field:NotBlank
    val ci: String,
    val userName: String? = null,
    val phoneNumber: String? = null,
) {
    fun toCommand(): IssueTokenCommand =
        IssueTokenCommand(
            ci = ci,
            userName = userName,
            phoneNumber = phoneNumber,
        )
}

data class IssueTokenResponse(
    val accessToken: String,
    val tokenType: String,
    val expiresIn: Long,
)
