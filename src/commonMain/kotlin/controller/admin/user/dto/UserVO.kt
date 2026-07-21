package controller.admin.user.dto

import kotlinx.serialization.Serializable

@Serializable
data class UserVO(
    val id: Long,
    val username: String,
    val nickname: String,
    val email: String? = null,
    val mobile: String? = null,
    val avatar: String? = null,
    val gender: Int = 0,
    val deptId: Long = 0,
    val remark: String? = null,
    val status: Int,
    val createdAt: Long? = null,
    val updatedAt: Long? = null
)
