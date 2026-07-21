package controller.admin.message.dto

import kotlinx.serialization.Serializable
import neton.validation.annotations.NotBlank
import neton.validation.annotations.Size

@Serializable
data class SendMessageRequest(
    @property:NotBlank
    @property:Size(min = 2, max = 128)
    val templateCode: String,

    @property:NotBlank
    @property:Size(min = 2, max = 256)
    val receiver: String,
    val params: Map<String, String> = emptyMap()
)
