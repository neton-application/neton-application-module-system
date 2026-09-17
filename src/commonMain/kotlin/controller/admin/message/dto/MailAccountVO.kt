package controller.admin.message.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import model.MessageChannel
import neton.validation.annotations.Max
import neton.validation.annotations.Min
import neton.validation.annotations.NotBlank
import neton.validation.annotations.Pattern
import neton.validation.annotations.Size

@Serializable
data class MailAccountVO(
    val id: Long = 0,
    val mail: String = "",
    val username: String = "",
    val password: String = "",
    val host: String = "",
    val port: Int = 465,
    val sslEnable: Boolean = true,
    val starttlsEnable: Boolean = false,
    /** 发信厂商：resend | sendgrid | mailgun | brevo | http；smtp 在本运行时不可用（无 TLS socket） */
    val vendor: String = "smtp",
    val apiKey: String = "",
    /** mailgun 填 https://api.mailgun.net/v3/<domain>；http 填接收 JSON 的地址；其它留空 */
    val endpoint: String = "",
    val fromName: String = "",
    val status: Int = 1,
    val remark: String? = null,
    val createTime: Long? = null
) {
    fun toChannel(): MessageChannel {
        val config = buildJsonObject {
            put("vendor", vendor); put("apiKey", apiKey); put("endpoint", endpoint); put("fromName", fromName)
            put("mail", mail)
            put("username", username)
            put("password", password)
            put("host", host)
            put("port", port)
            put("sslEnable", sslEnable)
            put("starttlsEnable", starttlsEnable)
        }.toString()
        return MessageChannel(
            id = id,
            name = mail.ifBlank { username },
            code = mail.replace("@", "_at_").replace(".", "_"),
            type = "email",
            config = config,
            status = status,
            remark = remark
        )
    }

    companion object {
        fun fromChannel(channel: MessageChannel): MailAccountVO {
            return try {
                val json = Json { ignoreUnknownKeys = true }
                val obj = json.parseToJsonElement(channel.config ?: "{}").let {
                    it as? kotlinx.serialization.json.JsonObject ?: kotlinx.serialization.json.JsonObject(emptyMap())
                }
                MailAccountVO(
                    id = channel.id,
                    mail = obj["mail"]?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.content }
                        ?: channel.name,
                    username = obj["username"]?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.content }
                        ?: "",
                    password = obj["password"]?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.content }
                        ?: "",
                    host = obj["host"]?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.content } ?: "",
                    port = obj["port"]?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.content?.toIntOrNull() }
                        ?: 465,
                    sslEnable = obj["sslEnable"]?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.content?.toBoolean() }
                        ?: true,
                    starttlsEnable = obj["starttlsEnable"]?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.content?.toBoolean() }
                        ?: false,
                    vendor = obj["vendor"]?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.content } ?: "smtp",
                    apiKey = obj["apiKey"]?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.content } ?: "",
                    endpoint = obj["endpoint"]?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.content } ?: "",
                    fromName = obj["fromName"]?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.content } ?: "",
                    status = channel.status,
                    remark = channel.remark,
                    createTime = channel.createdAt
                )
            } catch (_: Exception) {
                MailAccountVO(
                    id = channel.id,
                    mail = channel.name,
                    status = channel.status,
                    remark = channel.remark,
                    createTime = channel.createdAt
                )
            }
        }
    }
}

@Serializable
data class CreateMailAccountRequest(
    @property:NotBlank
    @property:Pattern("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")
    val mail: String,

    @property:Size(min = 0, max = 128)
    val username: String = "",

    @property:Size(min = 0, max = 255)
    val password: String = "",

    @property:Size(min = 0, max = 255)
    val host: String = "",

    @property:Min(1)
    @property:Max(65535)
    val port: Int = 465,

    val sslEnable: Boolean = true,
    val starttlsEnable: Boolean = false,
    val vendor: String = "smtp",
    @property:Size(min = 0, max = 512)
    val apiKey: String = "",
    @property:Size(min = 0, max = 512)
    val endpoint: String = "",
    @property:Size(min = 0, max = 128)
    val fromName: String = "",
    @property:Min(0)
    @property:Max(1)
    val status: Int = 1,

    @property:Size(min = 0, max = 255)
    val remark: String? = null
)

@Serializable
data class UpdateMailAccountRequest(
    @property:Min(1)
    val id: Long,

    @property:NotBlank
    @property:Pattern("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")
    val mail: String,

    @property:Size(min = 0, max = 128)
    val username: String = "",

    @property:Size(min = 0, max = 255)
    val password: String = "",

    @property:Size(min = 0, max = 255)
    val host: String = "",

    @property:Min(1)
    @property:Max(65535)
    val port: Int = 465,

    val sslEnable: Boolean = true,
    val starttlsEnable: Boolean = false,
    val vendor: String = "smtp",
    @property:Size(min = 0, max = 512)
    val apiKey: String = "",
    @property:Size(min = 0, max = 512)
    val endpoint: String = "",
    @property:Size(min = 0, max = 128)
    val fromName: String = "",
    @property:Min(0)
    @property:Max(1)
    val status: Int = 1,

    @property:Size(min = 0, max = 255)
    val remark: String? = null
)
