package controller.admin.user.dto

import kotlinx.serialization.Serializable
import controller.admin.role.dto.RoleVO

@Serializable
data class UserWithRolesVO(
    val id: Long,
    val username: String,
    val nickname: String,
    val status: Int,
    val roles: List<RoleVO>,
    val createdAt: String? = null,
    val updatedAt: String? = null
)
