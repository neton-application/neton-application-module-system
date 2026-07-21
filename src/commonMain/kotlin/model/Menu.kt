package model

import kotlinx.serialization.Serializable
import neton.database.annotations.Table
import neton.database.annotations.Id
import neton.database.annotations.CreatedAt
import neton.database.annotations.UpdatedAt

@Serializable
@Table("system_menus")
data class Menu(
    @Id
    val id: Long = 0,
    val name: String,
    val permission: String? = null,
    val type: Int,
    val parentId: Long = 0,
    val path: String? = null,
    val component: String? = null,
    val icon: String? = null,
    val sort: Int = 0,
    val status: Int = 1,
    @CreatedAt
    val createdAt: String? = null,
    @UpdatedAt
    val updatedAt: String? = null
)
