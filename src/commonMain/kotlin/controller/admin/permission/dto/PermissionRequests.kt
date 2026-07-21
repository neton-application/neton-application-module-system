package controller.admin.permission.dto

import kotlinx.serialization.Serializable
import neton.validation.annotations.Min
import neton.validation.annotations.Size

@Serializable
data class AssignRoleMenuRequest(
    @property:Min(1)
    val roleId: Long,

    @property:Size(min = 1, max = 500)
    val menuIds: List<Long>
)

@Serializable
data class AssignUserRoleRequest(
    @property:Min(1)
    val userId: Long,

    @property:Size(min = 1, max = 100)
    val roleIds: List<Long>
)
