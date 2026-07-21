package model

import kotlinx.serialization.Serializable
import neton.database.annotations.Table
import neton.database.annotations.Id
import neton.database.annotations.CreatedAt
import neton.database.annotations.UpdatedAt

@Serializable
@Table("system_depts")
data class Dept(
    @Id
    val id: Long = 0,
    val name: String,
    val parentId: Long = 0,
    val sort: Int = 0,
    val leaderUserId: Long? = null,
    val status: Int = 1,
    @CreatedAt
    val createdAt: String? = null,
    @UpdatedAt
    val updatedAt: String? = null
)
