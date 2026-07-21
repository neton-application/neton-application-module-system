package model

import kotlinx.serialization.Serializable
import neton.database.annotations.Table
import neton.database.annotations.Id
import neton.database.annotations.CreatedAt

@Serializable
@Table("system_message_logs")
data class MessageLog(
    @Id
    val id: Long = 0,
    val channelId: Long,
    val templateId: Long? = null,
    val templateCode: String? = null,
    val receiver: String,
    val content: String? = null,
    val params: String? = null,
    val sendStatus: Int = 0,
    /** epoch millis；列类型 bigint（spec 与 system_message_logs.send_time 对齐） */
    val sendTime: Long? = null,
    val errorMessage: String? = null,
    val userId: Long? = null,
    val userType: Int = 0,
    @CreatedAt
    val createdAt: Long = 0,
)
