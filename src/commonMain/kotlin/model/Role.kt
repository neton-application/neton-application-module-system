package model

import kotlinx.serialization.Serializable
import neton.database.annotations.Table
import neton.database.annotations.Id
import neton.database.annotations.CreatedAt
import neton.database.annotations.UpdatedAt

@Serializable
@Table("system_roles")
data class Role(
    @Id
    val id: Long = 0,
    val code: String,
    val name: String,
    val description: String? = null,
    val sort: Int = 0,
    val status: Int = 1,
    @CreatedAt
    val createdAt: String? = null,
    @UpdatedAt
    val updatedAt: String? = null
)
