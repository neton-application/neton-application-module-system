package logic.provider

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import neton.core.config.getEnv
import neton.http.client.HttpClient
import neton.http.client.HttpClientMethod
import neton.http.client.HttpClientRequest
import neton.logging.Logger

/**
 * SMS message provider — 短信宝 (smsbao.com) 实现。
 *
 * 协议（见旧后端 docs/SMS短信宝.md）：
 * `GET {apiUrl}?u=用户名&p=密码MD5或ApiKey&m=手机号&c=UTF8_URLENCODE(内容)`，
 * 响应体为纯文本，返回 '0' 表示发送成功，其余为错误码。
 *
 * 凭证配置位（二选一，渠道 config 优先）：
 * 1. 消息渠道（message_channel，type=sms）的 config JSON：
 *    `{"username":"...","password":"...","apiUrl":"https://api.smsbao.com/sms","goodsId":""}`
 *    - password：短信宝后台的登录密码 MD5(32位) 或 ApiKey（推荐）
 *    - goodsId：专用通道产品 ID，可选
 * 2. 环境变量兜底：`SMS_SMSBAO_USERNAME` / `SMS_SMSBAO_PASSWORD`
 *
 * 未配置凭证时 [send] 抛 [ProviderNotImplementedException]（HTTP 503）而不是假成功 —
 * 假成功会让调用方以为验证码已送达，悄悄打断登录链路。
 */
class SmsProvider(
    private val log: Logger,
    /** 借用应用绑定的出站客户端；本类不创建、不关闭。 */
    private val http: HttpClient,
) : MessageProvider {

    override val type: String = "sms"

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun send(receiver: String, content: String, config: String): Boolean {
        val cfg = parseConfig(config)
        if (cfg == null) {
            log.warn("sms.provider.not_configured receiver=${mask(receiver)}")
            throw ProviderNotImplementedException(
                "SMS provider is not configured. Set username/password in the sms channel config " +
                    "or via SMS_SMSBAO_USERNAME / SMS_SMSBAO_PASSWORD."
            )
        }

        val mobile = toLocalNumber(receiver)
        val url = buildString {
            append(cfg.apiUrl.trimEnd('/'))
            append(if (contains('?')) '&' else '?')
            append("u=").append(urlEncode(cfg.username))
            append("&p=").append(urlEncode(cfg.password))
            if (!cfg.goodsId.isNullOrBlank()) append("&g=").append(urlEncode(cfg.goodsId))
            append("&m=").append(urlEncode(mobile))
            append("&c=").append(urlEncode(content))
        }

        val body = try {
            val resp = http.request(
                HttpClientRequest(method = HttpClientMethod.Get, url = url)
            )
            if (resp.statusCode !in 200..299) {
                log.error("sms.provider.http_error status=${resp.statusCode} receiver=${mask(receiver)}")
                return false
            }
            resp.body.trim()
        } catch (e: Exception) {
            log.error("sms.provider.request_failed receiver=${mask(receiver)}: ${e.message}")
            return false
        }

        if (body == "0") {
            log.info("sms.provider.sent", mapOf("receiver" to mask(receiver)))
            return true
        }

        // 非 '0' 一律视为发送失败，绝不假成功
        log.error("sms.provider.send_failed receiver=${mask(receiver)} code=$body (${errorText(body)})")
        return false
    }

    private data class SmsbaoConfig(
        val username: String,
        val password: String,
        val apiUrl: String,
        val goodsId: String?
    )

    /** 渠道 config JSON 优先，环境变量兜底；两者都缺 → null。 */
    private fun parseConfig(config: String): SmsbaoConfig? {
        var username: String? = null
        var password: String? = null
        var apiUrl: String? = null
        var goodsId: String? = null
        if (config.isNotBlank() && config != "{}") {
            try {
                val obj = json.parseToJsonElement(config).jsonObject
                username = obj["username"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
                password = obj["password"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
                apiUrl = obj["apiUrl"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
                goodsId = obj["goodsId"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
            } catch (_: Exception) {
                // config 不是合法 JSON，交给环境变量兜底
            }
        }
        username = username ?: getEnv("SMS_SMSBAO_USERNAME")?.takeIf { it.isNotBlank() }
        password = password ?: getEnv("SMS_SMSBAO_PASSWORD")?.takeIf { it.isNotBlank() }
        if (username == null || password == null) return null
        return SmsbaoConfig(
            username = username,
            password = password,
            apiUrl = apiUrl ?: DEFAULT_API_URL,
            goodsId = goodsId
        )
    }

    /** 会员体系里手机号是 E.164（+8613812345678），短信宝收国内号码（13812345678）。 */
    private fun toLocalNumber(receiver: String): String {
        val trimmed = receiver.trim()
        if (trimmed.startsWith("+86")) return trimmed.removePrefix("+86")
        val noPlus = trimmed.removePrefix("+")
        if (noPlus.startsWith("86") && noPlus.length == 13) return noPlus.removePrefix("86")
        return noPlus
    }

    /** UTF-8 percent-encoding（短信宝要求 c 参数做 URL ENCODE）。 */
    private fun urlEncode(value: String): String {
        val sb = StringBuilder()
        for (byte in value.encodeToByteArray()) {
            val b = byte.toInt() and 0xFF
            if (b in 0x30..0x39 || b in 0x41..0x5A || b in 0x61..0x7A ||
                b == 0x2D || b == 0x5F || b == 0x2E || b == 0x7E
            ) {
                sb.append(b.toChar())
            } else {
                sb.append('%').append(HEX[b shr 4]).append(HEX[b and 0x0F])
            }
        }
        return sb.toString()
    }

    private fun mask(receiver: String): String =
        if (receiver.length < 7) "***" else "${receiver.take(3)}****${receiver.takeLast(4)}"

    private fun errorText(code: String): String = when (code) {
        "30" -> "密码错误"
        "40" -> "账号不存在"
        "41" -> "余额不足"
        "43" -> "IP地址限制"
        "50" -> "内容含有敏感词"
        "51" -> "手机号码不正确"
        else -> "未知错误"
    }

    companion object {
        private const val DEFAULT_API_URL = "https://api.smsbao.com/sms"
        private const val HEX = "0123456789ABCDEF"
    }
}
