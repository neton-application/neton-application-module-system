package model

import kotlinx.serialization.Serializable
import neton.database.annotations.Table
import neton.database.annotations.Id
import neton.database.annotations.CreatedAt
import neton.database.annotations.UpdatedAt

@Serializable
@Table("system_notify_messages")
data class NotifyMessage(
    @Id
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
    @CreatedAt
    val createdAt: String? = null,
    @UpdatedAt
    val updatedAt: String? = null
)
