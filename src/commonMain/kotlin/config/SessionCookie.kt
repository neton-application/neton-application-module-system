package config

import neton.core.component.NetonContext
import neton.core.config.ConfigLoader
import neton.core.http.HttpContext

/**
 * 后台会话 Cookie（HttpOnly）。
 *
 * 管理后台不在 localStorage 里保存令牌：HttpOnly Cookie 脚本读不到，浏览器自动附带。
 * 注意它只解决「令牌被持久化后被读走」，不等于 XSS 无害 —— 登录 / 刷新响应体里仍有令牌
 * （供移动端与 vben 等走 `Authorization` 头的客户端使用），页面脚本照样能发起请求。
 *
 * 传输是应用层关切：框架 JwtAuthenticator 按 spec 只读请求头，由 [security.RealmAuthenticator]
 * 把 Cookie 补成那个头。凭 Cookie 认证的写请求还必须带 [security.RealmAuthenticator.CSRF_HEADER]
 * （评审 R09）。
 */
object SessionCookie {

    const val DEFAULT_NAME: String = "neton_session"

    private const val APP_CONFIG_PATH = "config"
    private const val NAME_PATH = "security.session_cookie.name"
    private const val SECURE_PATH = "security.session_cookie.secure"
    private const val SAME_SITE_PATH = "security.session_cookie.same_site"

    private fun config(ctx: NetonContext): Map<String, Any?>? = ConfigLoader.loadApplicationConfig(
        configPath = APP_CONFIG_PATH,
        environment = ConfigLoader.resolveEnvironment(ctx.args),
        args = ctx.args,
    )

    fun name(ctx: NetonContext): String =
        ConfigLoader.getString(config(ctx), NAME_PATH)?.trim()?.takeIf { it.isNotEmpty() } ?: DEFAULT_NAME

    /**
     * `Secure`：`auto`（默认）/ `true` / `false`。
     *
     * `auto` 按请求实际到达的方式决定：经 HTTPS（直连 TLS，或反代声明 `X-Forwarded-Proto: https`）
     * 才加 Secure。后台在 `http://ip:port` 访问的部署照常工作，上了 HTTPS 自动生效，不必记得改配置。
     * 转发头只能影响发请求的人自己拿到的 Cookie 属性，伪造它得不到任何东西。
     */
    private fun secure(ctx: NetonContext, http: HttpContext): Boolean =
        when (val mode = ConfigLoader.getString(config(ctx), SECURE_PATH)?.trim()?.lowercase() ?: "auto") {
            "true" -> true
            "false" -> false
            "auto" -> http.request.isSecure ||
                http.request.header("X-Forwarded-Proto")?.substringBefore(',')?.trim()?.lowercase() == "https"
            else -> error("$SECURE_PATH must be auto, true or false, got '$mode'")
        }

    /** `Lax` 足以挡掉跨站表单提交，又不影响后台自身的同源请求。取值必须是 Strict / Lax / None。 */
    private fun sameSite(ctx: NetonContext): String {
        val raw = ConfigLoader.getString(config(ctx), SAME_SITE_PATH)?.trim()?.takeIf { it.isNotEmpty() } ?: "Lax"
        return SAME_SITE_VALUES.firstOrNull { it.equals(raw, ignoreCase = true) }
            ?: error("$SAME_SITE_PATH must be one of $SAME_SITE_VALUES, got '$raw'")
    }

    private val SAME_SITE_VALUES = listOf("Strict", "Lax", "None")

    /** 从 `Cookie` 请求头里取出会话 token；没有返回 null。 */
    fun read(cookieHeader: String?, name: String): String? {
        if (cookieHeader.isNullOrBlank()) return null
        for (part in cookieHeader.split(';')) {
            val pair = part.trim()
            val eq = pair.indexOf('=')
            if (eq <= 0) continue
            if (pair.substring(0, eq).trim() != name) continue
            return pair.substring(eq + 1).trim().takeIf { it.isNotEmpty() }
        }
        return null
    }

    fun issue(ctx: NetonContext, http: HttpContext, token: String, maxAgeSeconds: Long) {
        http.response.header("Set-Cookie", build(ctx, http, token, maxAgeSeconds))
    }

    fun clear(ctx: NetonContext, http: HttpContext) {
        http.response.header("Set-Cookie", build(ctx, http, "", 0))
    }

    private fun build(ctx: NetonContext, http: HttpContext, value: String, maxAgeSeconds: Long): String = buildString {
        val sameSite = sameSite(ctx)
        val secure = secure(ctx, http)
        // 浏览器会丢弃 SameSite=None 但没有 Secure 的 Cookie：与其让登录「成功了却立刻掉线」，不如直接报配置错。
        check(sameSite != "None" || secure) { "$SAME_SITE_PATH = None requires a Secure cookie (serve the admin over HTTPS)" }
        append(name(ctx)).append('=').append(value)
        append("; Path=/")
        append("; HttpOnly")
        append("; SameSite=").append(sameSite)
        append("; Max-Age=").append(maxAgeSeconds)
        if (secure) append("; Secure")
    }
}
