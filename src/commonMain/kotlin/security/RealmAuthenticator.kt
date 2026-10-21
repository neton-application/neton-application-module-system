package security

import neton.core.interfaces.Identity
import neton.logging.Logger
import neton.security.identity.AuthenticationException
import neton.security.identity.IdentityUser
import neton.security.identity.UserId
import neton.security.jwt.AuthOutcome
import neton.security.jwt.JwtAuthenticator
import neton.security.jwt.UnifiedIdentity
import neton.security.jwt.UnifiedTokenAuthenticator
import neton.core.interfaces.Authenticator as CoreAuthenticator
import neton.core.interfaces.RequestContext as CoreRequestContext

/**
 * 应用的 HTTP 认证器：按路由组把令牌限定在它所属的身份域里（见 [TokenRealm]）。
 *
 * 对每个请求：
 * 1. 取令牌：`Authorization` 头优先，没有时读 HttpOnly 会话 Cookie（浏览器后台用）。
 * 2. 本应用 HS256 能验过 → 令牌的 `realm` 必须等于该路由组登记的域，再交给域做业务校验
 *    （类型、主体状态、会话版本）。路由组没有登记域 → 不接受任何本应用令牌。
 * 3. HS256 验不过 → 只有登记的域声明接受外部令牌时，才交给 [UnifiedTokenAuthenticator]。
 *
 * 任何一步不通过都返回 null（按 neton-core Authenticator 契约，由安全管道决定 401）。
 * 验签只做一次：身份直接取自验签结果，不再交给下游重复解析。
 */
class RealmAuthenticator(
    private val legacy: JwtAuthenticator,
    private val unified: UnifiedTokenAuthenticator?,
    private val realms: TokenRealmRegistry,
    private val headerName: String,
    private val tokenPrefix: String,
    private val sessionCookieName: String,
    private val log: Logger? = null,
) : CoreAuthenticator {

    override val name: String = "realm-jwt"

    companion object {
        /** 凭 Cookie 认证的写请求必须带 `X-Neton-Csrf: 1`。 */
        const val CSRF_HEADER = "X-Neton-Csrf"
        private val SAFE_METHODS = setOf("GET", "HEAD", "OPTIONS")
    }

    override suspend fun authenticate(context: CoreRequestContext): Identity? {
        val fromCookie = context.headers.keys.none { it.equals(headerName, ignoreCase = true) }
        val headers = withCookieSession(context.headers)
        val raw = headers.entries.firstOrNull { it.key.equals(headerName, ignoreCase = true) }?.value ?: return null
        // 配置里的 "Bearer " 读出来可能丢了尾空格：去前缀后再 trim，与框架 JwtAuthenticator 一致。
        if (!raw.startsWith(tokenPrefix.trim())) return null
        val token = raw.removePrefix(tokenPrefix.trim()).trim().takeIf { it.isNotEmpty() } ?: return null
        val realm = realms.forGroup(context.routeGroup)

        // CSRF（评审 R09）：浏览器会自动附带 Cookie，所以凭 Cookie 认证的写请求必须再带一个自定义头。
        // 跨站页面要加这个头就得先过 CORS 预检，而本应用的 CORS 不允许携带凭据，浏览器会直接拦下。
        // 显式 Authorization 头的调用（移动端、服务间）不受影响：别的站点拿不到那个头。
        if (fromCookie && context.method.uppercase() !in SAFE_METHODS &&
            context.headers.entries.none { it.key.equals(CSRF_HEADER, ignoreCase = true) && it.value == "1" }
        ) {
            return reject(context, "csrf_header_missing")
        }

        val verified = try {
            legacy.verifyToken(token)
        } catch (_: AuthenticationException) {
            null
        }
        if (verified != null) {
            if (realm == null) return reject(context, "no_realm_for_group")
            if (verified.claimString(TokenRealm.CLAIM) != realm.name) return reject(context, "realm_mismatch")
            return realm.resolve(verified) ?: reject(context, "realm_rejected")
        }

        // 外部 unified token：新 API 直接吃 token 字符串，返回 AuthOutcome（Failure 一律 fail-closed 返 null）。
        if (realm == null || !realm.acceptsExternalTokens || unified == null) return null
        return when (val outcome = unified.authenticate(token)) {
            is AuthOutcome.Success ->
                if (grantsApplicationAccess(outcome.identity.scope)) outcome.identity.toFrameworkIdentity()
                else reject(context, "restricted_scope")
            is AuthOutcome.Failure -> null
        }
    }

    private fun reject(context: CoreRequestContext, reason: String): Identity? {
        log?.info("security.realm.rejected", mapOf("group" to (context.routeGroup ?: ""), "path" to context.path, "reason" to reason))
        return null
    }

    /** 显式 `Authorization` 头优先；没有时把会话 Cookie 里的令牌补成这个头。 */
    private fun withCookieSession(headers: Map<String, String>): Map<String, String> {
        if (headers.keys.any { it.equals(headerName, ignoreCase = true) }) return headers
        val cookieHeader = headers.entries.firstOrNull { it.key.equals("Cookie", ignoreCase = true) }?.value
        val token = readCookie(cookieHeader, sessionCookieName) ?: return headers
        return headers + (headerName to "${tokenPrefix.trim()} $token")
    }

    private fun readCookie(cookieHeader: String?, name: String): String? {
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
}

/**
 * unified token 的轻量 Identity 视图 → framework `Identity`。映射约定与
 * neton-security `DispatchingJwtAuthenticator`（private）保持一致：roles 留空
 * （unified token 不携带 roles，RBAC 由 application 实时判断），scope 当 permissions
 * —— 只是让现有 Guard 模型有数据可读，不构成授权依据。
 */
private fun UnifiedIdentity.toFrameworkIdentity(): IdentityUser = IdentityUser(
    userId = UserId(userId.toULong()),
    roles = emptySet(),
    permissions = scope.toSet(),
)

/**
 * 外部 unified token 只有带完整能力的 scope（`user` / `im`，或历史上不带 scope）才换得到应用身份。
 *
 * 受限 scope（客服访客的 `messaging`、设备节点的 `agent_node`……）是给 IM 通道签的窄凭据：
 * 若在这里一样变成 `Identity`，它就能以同一个 uid 调用全部 `/app` 接口，受限便形同虚设。
 * 未知 scope 同样不放行——与 privchat-server 的能力矩阵一致（AUTH_SPEC §7.3）。
 */
fun grantsApplicationAccess(scope: List<String>): Boolean =
    scope.isEmpty() || scope.any { it == "user" || it == "im" }
