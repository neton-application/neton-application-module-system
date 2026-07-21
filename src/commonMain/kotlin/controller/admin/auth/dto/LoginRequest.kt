package controller.admin.auth.dto

import kotlinx.serialization.Serializable
import neton.validation.annotations.NotBlank
import neton.validation.annotations.Size

@Serializable
data class LoginRequest(
    @property:NotBlank
    @property:Size(min = 3, max = 64)
    val username: String,

    @property:NotBlank
    @property:Size(min = 8, max = 128)
    val password: String
)

@Serializable
data class RefreshTokenRequest(
    @property:NotBlank
    @property:Size(min = 32, max = 4096)
    val refreshToken: String
)
