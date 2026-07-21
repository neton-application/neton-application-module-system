package controller.admin.auth.dto

import kotlinx.serialization.Serializable
import controller.admin.menu.dto.MenuVO

@Serializable
data class UserInfoVO(
    val userId: String,
    val username: String,
    val nickname: String,
    val avatar: String = "",
    val homePath: String = "/analytics"
)

@Serializable
data class PermissionInfoVO(
    val user: UserInfoVO,
    val roles: List<String>,
    val permissions: List<String>,
    val menus: List<MenuVO>
)
