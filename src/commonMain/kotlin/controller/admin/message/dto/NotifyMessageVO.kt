package controller.admin.message.dto

import kotlinx.serialization.Serializable

@Serializable
data class NotifyMessageVO(
    val id: Long = 0,
    val userId: Long = 0,
    val userType: Int = 0,
    val templateId: Long = 0,
    val templateCode: String? = null,
    val templateType: Int = 0,
    val templateNickname: String? = null,
    val templateContent: String? = null,
    val templateParams: String? = null,
    val readStatus: Int = 0,
    val readTime: String? = null,
    val createdAt: String? = null
)
