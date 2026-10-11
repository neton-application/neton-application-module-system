package security

import neton.security.identity.Identity
import neton.security.identity.UserId

/**
 * 认证后的请求身份，额外带上所属身份域和会话 id，退出接口据此只吊销当前会话。
 */
data class RealmIdentity(
    override val userId: UserId,
    override val roles: Set<String>,
    override val permissions: Set<String>,
    val realm: String,
    /** 令牌的 `ssn` 声明；同一次登录签出的 access/refresh 共用，刷新后保持不变。 */
    val sessionId: String?,
) : Identity
