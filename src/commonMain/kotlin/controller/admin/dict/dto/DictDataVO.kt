package controller.admin.dict.dto

import kotlinx.serialization.Serializable
import neton.validation.annotations.Max
import neton.validation.annotations.Min
import neton.validation.annotations.NotBlank
import neton.validation.annotations.Size

@Serializable
data class DictDataVO(
    val id: Long,
    val dictType: String,
    val label: String,
    val value: String,
    val sort: Int,
    val status: Int,
    val remark: String? = null
)

@Serializable
data class CreateDictDataRequest(
    @property:NotBlank
    @property:Size(min = 1, max = 64)
    val dictType: String,

    @property:NotBlank
    @property:Size(min = 1, max = 64)
    val label: String,

    @property:NotBlank
    @property:Size(min = 1, max = 128)
    val value: String,

    @property:Min(0)
    @property:Max(9999)
    val sort: Int = 0,

    @property:Min(0)
    @property:Max(1)
    val status: Int = 1,

    @property:Size(min = 0, max = 255)
    val remark: String? = null
)

@Serializable
data class UpdateDictDataRequest(
    @property:Min(1)
    val id: Long,

    @property:NotBlank
    @property:Size(min = 1, max = 64)
    val dictType: String,

    @property:NotBlank
    @property:Size(min = 1, max = 64)
    val label: String,

    @property:NotBlank
    @property:Size(min = 1, max = 128)
    val value: String,

    @property:Min(0)
    @property:Max(9999)
    val sort: Int = 0,

    @property:Min(0)
    @property:Max(1)
    val status: Int = 1,

    @property:Size(min = 0, max = 255)
    val remark: String? = null
)
