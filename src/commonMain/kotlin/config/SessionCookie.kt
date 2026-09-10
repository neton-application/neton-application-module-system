package config

import neton.core.component.NetonContext
import neton.core.config.ConfigLoader
import neton.core.http.HttpContext

/**
 * 后台会话 Cookie（HttpOnly）。
 *
 * 为什么要有它：管理后台是纯前端应用，把 access token 放 localStorage 意味着任何一次
 * XSS 都能把它读走并带走整个会话。HttpOnly Cookie 脚本读不到，浏览器自动附带，
 * 前端根本不需要经手 token。
 *
 * 传输是应用层关切，不是框架关切：框架的 [neton.security.jwt.JwtAuthenticator] 按
 * spec 冻结成「只读某个请求头」，这里不去改它，而是在应用的认证桥里把 Cookie 补成
 * 那个头（见 CoreAuthenticatorBridge）。因此 Header 与 Cookie 两种方式同时成立 ——
 * 移动端/服务间调用继续用 `Authorization`，浏览器用 Cookie。
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
     * `Secure` 默认关闭。
     *
     * 后台目前经 `http://ip:port` 访问（域名备案前的既定形态），默认开 Secure 会让
     * 浏览器直接丢弃这个 Cookie，表现为「登录成功但立刻又跳回登录页」，且没有任何
     * 报错。上 HTTPS 后用 `security.session_cookie.secure = true` 打开。
     */
    private fun secure(ctx: NetonContext): Boolean =
        ConfigLoader.getString(config(ctx), SECURE_PATH)?.trim()?.lowercase() == "true"

    /** `Lax` 足以挡掉跨站表单提交，又不影响后台自身的同源请求。 */
    private fun sameSite(ctx: NetonContext): String =
        ConfigLoader.getString(config(ctx), SAME_SITE_PATH)?.trim()?.takeIf { it.isNotEmpty() } ?: "Lax"

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
        http.response.header("Set-Cookie", build(ctx, token, maxAgeSeconds))
    }

    fun clear(ctx: NetonContext, http: HttpContext) {
        http.response.header("Set-Cookie", build(ctx, "", 0))
    }

    private fun build(ctx: NetonContext, value: String, maxAgeSeconds: Long): String = buildString {
        append(name(ctx)).append('=').append(value)
        append("; Path=/")
        append("; HttpOnly")
        append("; SameSite=").append(sameSite(ctx))
        append("; Max-Age=").append(maxAgeSeconds)
        if (secure(ctx)) append("; Secure")
    }
}
