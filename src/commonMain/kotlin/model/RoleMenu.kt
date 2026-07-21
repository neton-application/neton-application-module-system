package model

import kotlinx.serialization.Serializable
import neton.database.annotations.Table
import neton.database.annotations.Id
import neton.database.annotations.CreatedAt

@Serializable
@Table("system_role_menus")
data class RoleMenu(
    @Id
    val id: Long = 0,
    val roleId: Long,
    val menuId: Long,
    @CreatedAt
    val createdAt: String? = null
)
