package controller.admin.message.dto

import kotlinx.serialization.Serializable

@Serializable
data class MessageLogVO(
    val id: Long = 0,
    val channelId: Long,
    val templateId: Long? = null,
    val templateCode: String? = null,
    val receiver: String,
    val content: String? = null,
    val params: String? = null,
    val sendStatus: Int = 0,
    /** epoch millis */
    val sendTime: Long? = null,
    val errorMessage: String? = null,
    val userId: Long? = null,
    val userType: Int = 0,
    /** epoch millis */
    val createdAt: Long = 0,
)
