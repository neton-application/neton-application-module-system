package controller.admin.social.dto

import kotlinx.serialization.Serializable
import neton.validation.annotations.NotBlank
import neton.validation.annotations.Size

@Serializable
data class SocialBindRequest(
    @property:NotBlank
    @property:Size(min = 3, max = 32)
    val socialType: String,

    @property:NotBlank
    @property:Size(min = 4, max = 2048)
    val code: String,

    @property:Size(min = 1, max = 1024)
    val redirectUri: String? = null
)
