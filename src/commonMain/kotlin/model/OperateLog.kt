package model

import kotlinx.serialization.Serializable
import neton.database.annotations.Table
import neton.database.annotations.Id
import neton.database.annotations.CreatedAt

@Serializable
@Table("system_operate_logs")
data class OperateLog(
    @Id
    val id: Long = 0,
    val userId: Long? = null,
    val module: String,
    val name: String,
    val operateType: Int,
    val requestMethod: String? = null,
    val requestUrl: String? = null,
    val requestParams: String? = null,
    val responseResult: String? = null,
    val userIp: String? = null,
    val duration: Long = 0,
    val resultCode: Int = 0,
    @CreatedAt
    val createdAt: String? = null
)
