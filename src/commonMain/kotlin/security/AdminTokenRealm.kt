package security

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import logic.AdminSessionLogic
import neton.core.interfaces.Identity
import neton.security.jwt.VerifiedJwtToken

/**
 * 管理员身份域：`system_users`，只接受本应用签发的 HS256 令牌。
 *
 * 外部统一令牌一律不收 —— 它们的主体是终端用户，id 空间和管理员重叠。
 *
 * 每次请求：
 * - 账号启用、会话版本一致（改密 / 重置 / 全端退出后旧令牌失效）、会话未被单独吊销（退出）、
 *   MFA 状态与令牌一致；
 * - 角色与权限**不取令牌里那份**，而是按当前授权版本解析（评审 R05）：令牌签发后撤权、停用角色、
 *   停用菜单都要立即生效，不能等令牌过期。
 */
class AdminTokenRealm(
    private val sessions: () -> AdminSessionLogic,
    private val revocation: () -> SessionRevocation,
    private val authorization: suspend (userId: Long) -> AdminAuthorization,
) : TokenRealm {
    override val name: String = NAME
    override val routeGroups: Set<String> = setOf("admin")
    override val acceptsExternalTokens: Boolean = false

    private val cache = AuthorizationCache()

    override suspend fun resolve(token: VerifiedJwtToken): Identity? {
        if (token.claimString(TokenRealm.TYPE_CLAIM) != TokenRealm.TYPE_ACCESS) return null
        val id = token.identity.userId.value.toLong()
        val state = sessions().accessState(id) ?: return null
        if (state.status != 1) return null
        if (token.claimString(SESSION_VERSION_CLAIM)?.toLongOrNull() != state.sessionVersion) return null
        if (!state.acceptsMfa(token.claimString("mfa_revision")?.toLongOrNull(), token.claimString("mfa_verified") == "true")) return null
        val sessionId = token.claimString(SessionRevocation.SESSION_CLAIM)
        if (sessionId != null && revocation().isRevoked(NAME, sessionId)) return null

        val granted = cache.get(id, state.authzVersion) { authorization(id) }
        return RealmIdentity(
            userId = token.identity.userId,
            roles = granted.roles,
            permissions = granted.permissions,
            realm = NAME,
            sessionId = sessionId,
        )
    }

    companion object {
        const val NAME = "admin"
        const val SESSION_VERSION_CLAIM = "sv"
    }
}

/**
 * 进程内的权限缓存，按「用户 + 全局授权版本」取值。版本由数据库给出，任何实例上的 RBAC 写操作
 * 都会让所有实例的缓存同时失效，无需广播。条目数以用户数为上限；版本变化后旧条目在下次访问时覆盖。
 */
internal class AuthorizationCache {
    private data class Entry(val version: Long, val value: AdminAuthorization)
    private val entries = mutableMapOf<Long, Entry>()
    // 请求可能在多个线程上并发；查库放在锁外，避免一个慢查询拖住所有管理员请求。
    private val lock = Mutex()

    suspend fun get(userId: Long, version: Long, load: suspend () -> AdminAuthorization): AdminAuthorization {
        lock.withLock { entries[userId] }?.takeIf { it.version == version }?.let { return it.value }
        val value = load()
        lock.withLock { entries[userId] = Entry(version, value) }
        return value
    }
}
