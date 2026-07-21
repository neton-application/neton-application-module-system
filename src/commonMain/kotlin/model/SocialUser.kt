package model

import kotlinx.serialization.Serializable
import neton.database.annotations.Table
import neton.database.annotations.Id
import neton.database.annotations.SoftDelete
import neton.database.annotations.CreatedAt
import neton.database.annotations.UpdatedAt

@Serializable
@Table("system_social_users")
data class SocialUser(
    @Id
    val id: Long = 0,
    val userId: Long = 0,
    val userType: Int = 0,
    val socialType: String,
    val openId: String,
    val token: String? = null,
    val rawTokenInfo: String? = null,
    val nickname: String? = null,
    val avatar: String? = null,
    val rawUserInfo: String? = null,
    @SoftDelete
    val deleted: Int = 0,
    @CreatedAt
    val createdAt: String? = null,
    @UpdatedAt
    val updatedAt: String? = null
)
