package model

import kotlinx.serialization.Serializable
import neton.database.annotations.Table
import neton.database.annotations.Id
import neton.database.annotations.CreatedAt

@Serializable
@Table("system_login_logs")
data class LoginLog(
    @Id
    val id: Long = 0,
    val userId: Long? = null,
    val username: String? = null,
    val userIp: String? = null,
    val userAgent: String? = null,
    val loginResult: Int,
    @CreatedAt
    val createdAt: String? = null
)
