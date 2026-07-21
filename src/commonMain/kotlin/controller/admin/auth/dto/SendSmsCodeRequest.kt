package controller.admin.auth.dto

import kotlinx.serialization.Serializable
import neton.validation.annotations.NotBlank
import neton.validation.annotations.Pattern
import neton.validation.annotations.Size

@Serializable
data class SendSmsCodeRequest(
    @property:NotBlank
    @property:Pattern(regex = "^1\\d{10}$", message = "mobile format is invalid")
    val mobile: String,

    @property:NotBlank
    @property:Size(min = 3, max = 32)
    val scene: String = "login"  // login, reset-password, etc.
)
