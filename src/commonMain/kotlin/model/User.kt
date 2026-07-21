package model

import kotlinx.serialization.Serializable
import neton.database.annotations.Table
import neton.database.annotations.Id
import neton.database.annotations.SoftDelete
import neton.database.annotations.CreatedAt
import neton.database.annotations.UpdatedAt

@Serializable
@Table("system_users")
data class User(
    @Id
    val id: Long = 0,
    val username: String,
    val passwordHash: String,
    val nickname: String,
    val email: String? = null,
    val mobile: String? = null,
    val avatar: String? = null,
    val gender: Int = 0,
    val deptId: Long = 0,
    val remark: String? = null,
    val status: Int = 1,
    @SoftDelete
    val deleted: Int = 0,
    @CreatedAt
    val createdAt: Long? = null,
    @UpdatedAt
    val updatedAt: Long? = null
)
