package controller.admin.dict.dto

import kotlinx.serialization.Serializable
import neton.validation.annotations.Max
import neton.validation.annotations.Min
import neton.validation.annotations.NotBlank
import neton.validation.annotations.Size

@Serializable
data class DictTypeVO(
    val id: Long,
    val name: String,
    val type: String,
    val status: Int,
    val remark: String? = null
)

@Serializable
data class CreateDictTypeRequest(
    @property:NotBlank
    @property:Size(min = 1, max = 64)
    val name: String,

    @property:NotBlank
    @property:Size(min = 1, max = 64)
    val type: String,

    @property:Min(0)
    @property:Max(1)
    val status: Int = 1,

    @property:Size(min = 0, max = 255)
    val remark: String? = null
)

@Serializable
data class UpdateDictTypeRequest(
    @property:Min(1)
    val id: Long,

    @property:NotBlank
    @property:Size(min = 1, max = 64)
    val name: String,

    @property:NotBlank
    @property:Size(min = 1, max = 64)
    val type: String,

    @property:Min(0)
    @property:Max(1)
    val status: Int = 1,

    @property:Size(min = 0, max = 255)
    val remark: String? = null
)
