package model

import kotlinx.serialization.Serializable
import neton.database.annotations.Table
import neton.database.annotations.Id
import neton.database.annotations.SoftDelete
import neton.database.annotations.CreatedAt
import neton.database.annotations.UpdatedAt

@Serializable
@Table("system_message_templates")
data class MessageTemplate(
    @Id
    val id: Long = 0,
    val name: String,
    val code: String,
    val content: String,
    val params: String? = null,
    val channelId: Long = 0,
    val type: Int = 0,
    val status: Int = 1,
    val remark: String? = null,
    @SoftDelete
    val deleted: Int = 0,
    @CreatedAt
    val createdAt: String? = null,
    @UpdatedAt
    val updatedAt: String? = null
)
