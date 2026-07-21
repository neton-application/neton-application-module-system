package controller.admin.notice.dto

import kotlinx.serialization.Serializable
import neton.validation.annotations.Max
import neton.validation.annotations.Min
import neton.validation.annotations.NotBlank
import neton.validation.annotations.Size

@Serializable
data class NoticeVO(
    val id: Long,
    val title: String,
    val content: String,
    val type: Int,
    val status: Int,
    val createdAt: String? = null,
    val updatedAt: String? = null
)

@Serializable
data class CreateNoticeRequest(
    @property:NotBlank
    @property:Size(min = 1, max = 100)
    val title: String,

    @property:NotBlank
    @property:Size(min = 1, max = 4000)
    val content: String,

    @property:Min(0)
    @property:Max(9)
    val type: Int,

    @property:Min(0)
    @property:Max(1)
    val status: Int = 1
)

@Serializable
data class UpdateNoticeRequest(
    @property:Min(1)
    val id: Long,

    @property:NotBlank
    @property:Size(min = 1, max = 100)
    val title: String,

    @property:NotBlank
    @property:Size(min = 1, max = 4000)
    val content: String,

    @property:Min(0)
    @property:Max(9)
    val type: Int,

    @property:Min(0)
    @property:Max(1)
    val status: Int = 1
)
