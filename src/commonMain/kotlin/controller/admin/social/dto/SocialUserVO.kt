package controller.admin.social.dto

import kotlinx.serialization.Serializable

@Serializable
data class SocialUserVO(
    val id: Long = 0,
    val userId: Long = 0,
    val userType: Int = 0,
    val socialType: String,
    val openId: String,
    val nickname: String? = null,
    val avatar: String? = null,
    val createdAt: String? = null
)
