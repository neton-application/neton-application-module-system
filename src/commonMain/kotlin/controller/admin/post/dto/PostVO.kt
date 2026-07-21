package controller.admin.post.dto

import kotlinx.serialization.Serializable
import neton.validation.annotations.Max
import neton.validation.annotations.Min
import neton.validation.annotations.NotBlank
import neton.validation.annotations.Size

@Serializable
data class PostVO(
    val id: Long,
    val code: String,
    val name: String,
    val sort: Int,
    val status: Int
)

@Serializable
data class CreatePostRequest(
    @property:NotBlank
    @property:Size(min = 1, max = 64)
    val code: String,

    @property:NotBlank
    @property:Size(min = 1, max = 64)
    val name: String,

    @property:Min(0)
    @property:Max(9999)
    val sort: Int = 0,

    @property:Min(0)
    @property:Max(1)
    val status: Int = 1
)

@Serializable
data class UpdatePostRequest(
    @property:Min(1)
    val id: Long,

    @property:NotBlank
    @property:Size(min = 1, max = 64)
    val code: String,

    @property:NotBlank
    @property:Size(min = 1, max = 64)
    val name: String,

    @property:Min(0)
    @property:Max(9999)
    val sort: Int = 0,

    @property:Min(0)
    @property:Max(1)
    val status: Int = 1
)
