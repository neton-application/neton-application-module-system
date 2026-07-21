package controller.admin.role.dto

import kotlinx.serialization.Serializable
import neton.validation.annotations.Max
import neton.validation.annotations.Min
import neton.validation.annotations.NotBlank
import neton.validation.annotations.Size

@Serializable
data class RoleVO(
    val id: Long,
    val code: String,
    val name: String,
    val description: String? = null,
    val status: Int
)

@Serializable
data class CreateRoleRequest(
    @property:NotBlank
    @property:Size(min = 2, max = 64)
    val code: String,

    @property:NotBlank
    @property:Size(min = 2, max = 64)
    val name: String,

    @property:Size(min = 0, max = 255)
    val description: String? = null,

    @property:Min(0)
    @property:Max(9999)
    val sort: Int = 0,

    @property:Min(0)
    @property:Max(1)
    val status: Int = 1
)

@Serializable
data class UpdateRoleRequest(
    @property:Min(1)
    val id: Long,

    @property:NotBlank
    @property:Size(min = 2, max = 64)
    val code: String,

    @property:NotBlank
    @property:Size(min = 2, max = 64)
    val name: String,

    @property:Size(min = 0, max = 255)
    val description: String? = null,

    @property:Min(0)
    @property:Max(9999)
    val sort: Int = 0,

    @property:Min(0)
    @property:Max(1)
    val status: Int = 1
)
