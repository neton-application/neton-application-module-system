package controller.admin.message.dto

import kotlinx.serialization.Serializable
import neton.validation.annotations.Max
import neton.validation.annotations.Min
import neton.validation.annotations.NotBlank
import neton.validation.annotations.Size

@Serializable
data class NotificationTemplateVO(
    val id: Long = 0,
    val name: String,
    val code: String,
    val type: Int = 0,
    val messageTemplateId: Long = 0,
    val params: String? = null,
    val status: Int = 1,
    val remark: String? = null,
    val createdAt: String? = null
)

@Serializable
data class CreateNotificationTemplateRequest(
    @property:NotBlank
    @property:Size(min = 1, max = 64)
    val name: String,

    @property:NotBlank
    @property:Size(min = 1, max = 64)
    val code: String,

    @property:Min(0)
    @property:Max(9)
    val type: Int = 0,

    @property:Min(1)
    val messageTemplateId: Long = 0,

    @property:Size(min = 0, max = 2000)
    val params: String? = null,

    @property:Min(0)
    @property:Max(1)
    val status: Int = 1,

    @property:Size(min = 0, max = 255)
    val remark: String? = null
)

@Serializable
data class UpdateNotificationTemplateRequest(
    @property:Min(1)
    val id: Long,

    @property:NotBlank
    @property:Size(min = 1, max = 64)
    val name: String,

    @property:NotBlank
    @property:Size(min = 1, max = 64)
    val code: String,

    @property:Min(0)
    @property:Max(9)
    val type: Int = 0,

    @property:Min(1)
    val messageTemplateId: Long = 0,

    @property:Size(min = 0, max = 2000)
    val params: String? = null,

    @property:Min(0)
    @property:Max(1)
    val status: Int = 1,

    @property:Size(min = 0, max = 255)
    val remark: String? = null
)
