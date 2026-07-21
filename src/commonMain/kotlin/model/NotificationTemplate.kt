package model

import kotlinx.serialization.Serializable
import neton.database.annotations.Table
import neton.database.annotations.Id
import neton.database.annotations.SoftDelete
import neton.database.annotations.CreatedAt
import neton.database.annotations.UpdatedAt

@Serializable
@Table("system_notification_templates")
data class NotificationTemplate(
    @Id
    val id: Long = 0,
    val name: String,
    val code: String,
    val type: Int = 0,
    val messageTemplateId: Long = 0,
    val params: String? = null,
    val status: Int = 1,
    val remark: String? = null,
    @SoftDelete
    val deleted: Int = 0,
    @CreatedAt
    val createdAt: String? = null,
    @UpdatedAt
    val updatedAt: String? = null
)
