package logic.provider

import logic.SystemErrorCodes
import kotlinx.serialization.json.*
import neton.core.http.HttpHeaders
import neton.http.client.HttpClient
import neton.http.client.HttpClientBody
import neton.http.client.HttpClientMethod
import neton.http.client.HttpClientRequest
import neton.logging.Logger

/**
 * 邮件发送：SMTP（任意邮箱，含企业邮箱 / QQ / 163）或厂商 HTTP 接口（Resend / SendGrid / Mailgun / Brevo / 自定义）。
 *
 * SMTP 走应用 bind 进来的 [neton.http.mail.SmtpTransport]（默认是 neton-http 自带的 `defaultSmtpTransport()`，
 * 跑在 Neton 自己的 netonstream io + tls 上）；HTTP 厂商直接用 HttpClient。
 *
 * 通道 config（与 MailAccountVO 同形）：
 * - vendor：smtp | resend | sendgrid | mailgun | brevo | http
 * - smtp：host / port / username / password / sslEnable（465 隐式 TLS）/ starttlsEnable（587）
 * - apiKey；mail（发件地址）；fromName；endpoint（mailgun 填 `https://api.mailgun.net/v3/<domain>`，
 *   http 填任意接收 JSON 的地址；其它厂商留空用官方地址）
 *
 * 内容约定：模板正文第一行若是 `Subject: xxx` 则作主题，其余作正文；正文含 HTML 标签按 html 发。
 */
class EmailProvider(
    private val log: Logger,
    private val http: HttpClient,
    /** SMTP 发信实现，由应用装配层 bind（通常是 neton-http 的 `defaultSmtpTransport()`）；没绑就不支持 smtp */
    private val smtp: neton.http.mail.SmtpTransport? = null,
    /**
     * SMTP 主机来自后台的邮箱账号：按运维出站策略校验并钉住地址（[neton.http.client.EgressSettings.smtpPolicy]）。
     * [http] 同理必须是受控客户端（endpoint 也是后台数据）。
     */
    private val smtpPolicy: neton.http.client.OutboundAddressPolicy,
) : MessageProvider {
    override val type: String = "email"
    private val json = Json { ignoreUnknownKeys = true }

    private class Cfg(
        val vendor: String, val apiKey: String, val from: String, val fromName: String?, val endpoint: String?,
        val host: String?, val port: Int, val username: String, val password: String, val ssl: Boolean, val starttls: Boolean,
    )

    override suspend fun send(receiver: String, content: String, config: String): Boolean {
        val cfg = parse(config)
        val to = receiver.split(',', ';').map { it.trim() }.filter { it.isNotEmpty() }
        if (to.isEmpty()) throw IllegalArgumentException("email receiver is empty")
        val (subject, body) = splitSubject(content)
        val html = looksLikeHtml(body)
        if (cfg.vendor == "smtp") {
            val transport = smtp ?: throw ProviderNotImplementedException(SystemErrorCodes.SMTP_TRANSPORT_UNAVAILABLE) // 应用没有 bind SmtpTransport
            val host = cfg.host ?: throw IllegalArgumentException("SMTP 主机为空")
            try {
                transport.send(
                    neton.http.mail.SmtpAccount(host, cfg.port, cfg.username, cfg.password, cfg.ssl, cfg.starttls),
                    neton.http.mail.SmtpMessage(cfg.from, cfg.fromName, to, subject, body, html),
                    smtpPolicy,
                )
            } catch (e: neton.http.mail.SmtpTargetRejectedException) {
                log.warn("email.endpoint.forbidden vendor=smtp host=$host reason=${e.reason} detail=${e.message}")
                throw MessageEndpointForbiddenException()
            }
            log.info("email.sent vendor=smtp host=$host to=${to.joinToString(",") { mask(it) }}")
            return true
        }
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
            else -> {
                // 邮箱账号里只能选 smtp / resend / sendgrid / mailgun / brevo / http 之一。
                log.warn("email.vendor.unsupported vendor=${cfg.vendor}")
                throw ProviderNotImplementedException(SystemErrorCodes.EMAIL_VENDOR_UNSUPPORTED)
            }
        }
        val res = try {
            http.request(request)
        } catch (e: neton.http.client.HttpClientException) {
            val rejected = e.error as? neton.http.client.HttpClientError.TargetRejected ?: throw e
            log.warn("email.endpoint.forbidden vendor=${cfg.vendor} reason=${rejected.reason} detail=${rejected.message}")
            throw MessageEndpointForbiddenException()
        }
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
        fun b(k: String, d: Boolean) = o[k]?.takeIf { it !is JsonNull }?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull() ?: d
        return Cfg(
            vendor, s("apiKey") ?: "", s("mail") ?: "", s("fromName"), s("endpoint"),
            s("host"), s("port")?.toIntOrNull() ?: 465, s("username") ?: "", s("password") ?: "", b("sslEnable", true), b("starttlsEnable", false),
        )
    }

    private fun fromHeader(cfg: Cfg) = if (cfg.fromName != null) "${cfg.fromName} <${cfg.from}>" else cfg.from

    private fun splitSubject(content: String): Pair<String, String> {
        val firstBreak = content.indexOf('\n')
        val first = (if (firstBreak < 0) content else content.substring(0, firstBreak)).trim()
        return if (first.startsWith("Subject:", ignoreCase = true) && firstBreak >= 0) {
            first.substring(8).trim() to content.substring(firstBreak + 1).trimStart('\r', '\n')
        } else "通知" to content
    }

    private val htmlTag = Regex("<(html|body|p|div|table|a|br|h[1-6])[\\s>/]", RegexOption.IGNORE_CASE)
    private fun looksLikeHtml(s: String) = htmlTag.containsMatchIn(s)

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
