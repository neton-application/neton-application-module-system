package logic.provider

import kotlinx.serialization.json.*
import neton.core.http.HttpHeaders
import neton.http.client.HttpClient
import neton.http.client.HttpClientBody
import neton.http.client.HttpClientMethod
import neton.http.client.HttpClientRequest
import neton.logging.Logger

/**
 * 邮件发送：走各厂商的 HTTP 接口，不走 SMTP。
 *
 * 为什么没有 SMTP：本运行时（Kotlin/Native）的 socket 没有 TLS（Ktor KTOR-694），465/587 都要 TLS，
 * 明文 25 端口现在没有哪家收信方接受。所以「邮箱账号」里的 SMTP 字段只是保留的配置位，
 * vendor 是 smtp 或缺省时直接报「不可用」，不装作发出去了。
 *
 * 通道 config（与 MailAccountVO 同形）：
 * - vendor：resend | sendgrid | mailgun | brevo | http
 * - apiKey；mail（发件地址）；fromName；endpoint（mailgun 填 `https://api.mailgun.net/v3/<domain>`，
 *   http 填任意接收 JSON 的地址；其它厂商留空用官方地址）
 *
 * 内容约定：模板正文第一行若是 `Subject: xxx` 则作主题，其余作正文；正文含 HTML 标签按 html 发。
 */
class EmailProvider(
    private val log: Logger,
    private val http: HttpClient,
) : MessageProvider {
    override val type: String = "email"
    private val json = Json { ignoreUnknownKeys = true }

    private class Cfg(val vendor: String, val apiKey: String, val from: String, val fromName: String?, val endpoint: String?)

    override suspend fun send(receiver: String, content: String, config: String): Boolean {
        val cfg = parse(config)
        val to = receiver.split(',', ';').map { it.trim() }.filter { it.isNotEmpty() }
        if (to.isEmpty()) throw IllegalArgumentException("email receiver is empty")
        val (subject, body) = splitSubject(content)
        val html = looksLikeHtml(body)
        val request = when (cfg.vendor) {
            "resend" -> HttpClientRequest(
                method = HttpClientMethod.Post, url = cfg.endpoint ?: "https://api.resend.com/emails",
                headers = HttpHeaders.from(mapOf("Authorization" to "Bearer ${cfg.apiKey}", "Content-Type" to "application/json")),
                body = HttpClientBody.Json(buildJsonObject {
                    put("from", fromHeader(cfg)); put("to", JsonArray(to.map { JsonPrimitive(it) })); put("subject", subject)
                    put(if (html) "html" else "text", body)
                }.toString()),
            )
            "sendgrid" -> HttpClientRequest(
                method = HttpClientMethod.Post, url = cfg.endpoint ?: "https://api.sendgrid.com/v3/mail/send",
                headers = HttpHeaders.from(mapOf("Authorization" to "Bearer ${cfg.apiKey}", "Content-Type" to "application/json")),
                body = HttpClientBody.Json(buildJsonObject {
                    put("personalizations", buildJsonArray { add(buildJsonObject { put("to", JsonArray(to.map { e -> buildJsonObject { put("email", e) } })) }) })
                    put("from", buildJsonObject { put("email", cfg.from); cfg.fromName?.let { put("name", it) } })
                    put("subject", subject)
                    put("content", buildJsonArray { add(buildJsonObject { put("type", if (html) "text/html" else "text/plain"); put("value", body) }) })
                }.toString()),
            )
            "brevo" -> HttpClientRequest(
                method = HttpClientMethod.Post, url = cfg.endpoint ?: "https://api.brevo.com/v3/smtp/email",
                headers = HttpHeaders.from(mapOf("api-key" to cfg.apiKey, "Content-Type" to "application/json")),
                body = HttpClientBody.Json(buildJsonObject {
                    put("sender", buildJsonObject { put("email", cfg.from); cfg.fromName?.let { put("name", it) } })
                    put("to", JsonArray(to.map { e -> buildJsonObject { put("email", e) } }))
                    put("subject", subject); put(if (html) "htmlContent" else "textContent", body)
                }.toString()),
            )
            "mailgun" -> {
                val base = cfg.endpoint?.trimEnd('/') ?: throw IllegalArgumentException("mailgun 需要 endpoint：https://api.mailgun.net/v3/<domain>")
                val form = listOf("from" to fromHeader(cfg), "to" to to.joinToString(","), "subject" to subject, (if (html) "html" else "text") to body)
                    .joinToString("&") { (k, v) -> "$k=${urlEncode(v)}" }
                HttpClientRequest(
                    method = HttpClientMethod.Post, url = "$base/messages",
                    headers = HttpHeaders.from(mapOf("Authorization" to "Basic ${basic("api:${cfg.apiKey}")}")),
                    body = HttpClientBody.Text(form, "application/x-www-form-urlencoded"),
                )
            }
            "http" -> HttpClientRequest(
                method = HttpClientMethod.Post, url = cfg.endpoint ?: throw IllegalArgumentException("http 厂商需要 endpoint"),
                headers = HttpHeaders.from(mapOf("Authorization" to "Bearer ${cfg.apiKey}", "Content-Type" to "application/json")),
                body = HttpClientBody.Json(buildJsonObject {
                    put("from", cfg.from); cfg.fromName?.let { put("fromName", it) }
                    put("to", JsonArray(to.map { JsonPrimitive(it) })); put("subject", subject)
                    put("text", if (html) "" else body); put("html", if (html) body else "")
                }.toString()),
            )
            else -> throw ProviderNotImplementedException(
                "邮件通道 vendor=${cfg.vendor} 不可用：本运行时没有 TLS socket，SMTP 发不出去；请在邮箱账号里选 resend / sendgrid / mailgun / brevo / http 之一并填 apiKey。"
            )
        }
        val res = http.request(request)
        if (res.statusCode !in 200..299) {
            log.warn("email.send.failed vendor=${cfg.vendor} status=${res.statusCode} body=${res.body.take(300)}")
            throw IllegalStateException("email send failed: HTTP ${res.statusCode} ${res.body.take(200)}")
        }
        log.info("email.sent vendor=${cfg.vendor} to=${to.joinToString(",") { mask(it) }}")
        return true
    }

    private fun parse(config: String): Cfg {
        val o = runCatching { json.parseToJsonElement(config).jsonObject }.getOrNull() ?: JsonObject(emptyMap())
        fun s(k: String) = o[k]?.takeIf { it !is JsonNull }?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
        val vendor = s("vendor")?.lowercase() ?: if (s("host") != null) "smtp" else "unset"
        return Cfg(vendor, s("apiKey") ?: "", s("mail") ?: "", s("fromName"), s("endpoint"))
    }

    private fun fromHeader(cfg: Cfg) = if (cfg.fromName != null) "${cfg.fromName} <${cfg.from}>" else cfg.from

    private fun splitSubject(content: String): Pair<String, String> {
        val firstBreak = content.indexOf('\n')
        val first = (if (firstBreak < 0) content else content.substring(0, firstBreak)).trim()
        return if (first.startsWith("Subject:", ignoreCase = true) && firstBreak >= 0) {
            first.substring(8).trim() to content.substring(firstBreak + 1).trimStart('\r', '\n')
        } else "通知" to content
    }

    private fun looksLikeHtml(s: String) = Regex("<(html|body|p|div|table|a|br|h[1-6])[\\s>/]", RegexOption.IGNORE_CASE).containsMatchIn(s)

    private fun mask(e: String) = e.substringBefore('@').take(2) + "***@" + e.substringAfter('@', "")

    @OptIn(kotlin.io.encoding.ExperimentalEncodingApi::class)
    private fun basic(s: String) = kotlin.io.encoding.Base64.encode(s.encodeToByteArray())

    private fun urlEncode(s: String): String {
        val sb = StringBuilder()
        for (b in s.encodeToByteArray()) {
            val c = b.toInt() and 0xff; val ch = c.toChar()
            if ((ch in 'A'..'Z') || (ch in 'a'..'z') || (ch in '0'..'9') || ch == '-' || ch == '_' || ch == '.' || ch == '~') sb.append(ch)
            else sb.append('%').append("0123456789ABCDEF"[c shr 4]).append("0123456789ABCDEF"[c and 15])
        }
        return sb.toString()
    }
}
