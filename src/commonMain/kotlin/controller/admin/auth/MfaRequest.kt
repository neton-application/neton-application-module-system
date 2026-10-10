package controller.admin.auth

import kotlinx.serialization.Serializable
import neton.validation.annotations.Size

@Serializable data class MfaRequest(
    @property:Size(min = 64, max = 64) val challengeToken: String,
    @property:Size(min = 0, max = 6) val code: String = "",
)
