package security

import neton.core.interfaces.Identity
import neton.security.jwt.VerifiedJwtToken

/**
 * 令牌身份域（realm）。
 *
 * 同一个应用里有两类完全不同的主体：后台管理员（`system_users`）和终端用户（`account_users`）。
 * 两边的 id 各自从 1 / 10000 起编号，**数值会撞**。令牌里如果只有 `sub`，拿终端用户的令牌去
 * 后台接口就会被当成同 id 的管理员 —— 这正是评审 R01 复现的越权。
 *
 * 所以本应用签发的每一张 HS256 令牌都必须带 [CLAIM]（值为 [name]），并且只能在绑定到该域的
 * 路由组里使用。签名正确只说明「是我们签的」，不说明「是给这里用的」。
 *
 * 域由各自的模块实现并登记到 [TokenRealmRegistry]：管理员域在 system，账号域在 account。
 * 框架 JWT 原语（Neton-JWT-Authenticator-Spec-v1）不认识业务表，域校验不能下沉过去。
 */
interface TokenRealm {
    /** 写进令牌 [CLAIM] 的值。 */
    val name: String

    /** 这个域的令牌可以访问的路由组（routing.conf 的 group 名）。 */
    val routeGroups: Set<String>

    /**
     * 是否接受本应用 HS256 之外、由外部令牌服务签发的令牌（例如 PrivChat 的 RS256 统一令牌）。
     * 外部令牌由框架的 [neton.security.jwt.UnifiedTokenAuthenticator] 校验，它有自己的会话与吊销机制。
     */
    val acceptsExternalTokens: Boolean

    /**
     * 签名、过期、域都已通过之后的业务校验，返回这次请求的身份：令牌类型、主体是否仍可用、
     * 会话是否已被吊销都在这里判断。域也可以用服务端的最新数据替换令牌里的角色与权限
     * （令牌里那份在签发后就不会变，撤权要等它过期）。
     *
     * 返回 null 一律按未认证处理（401），不区分原因 —— 原因写日志，不回给调用方。
     */
    suspend fun resolve(token: VerifiedJwtToken): Identity?

    companion object {
        const val CLAIM = "realm"
        const val TYPE_CLAIM = "type"
        const val TYPE_ACCESS = "access"
        const val TYPE_REFRESH = "refresh"
    }
}

/**
 * 路由组 → 身份域的登记表。
 *
 * 由 system 的 [config.SystemSecurityConfig] 在安全组件启动时创建并 bind；各模块在自己的
 * bootstrap 里登记域。读取发生在请求期，登记晚于安全组件启动没有关系。
 *
 * 一个路由组只能属于一个域，重复登记直接启动失败 —— 两个域抢同一个组，意味着同一个接口
 * 同时信任两类主体，正是要杜绝的情况。
 */
class TokenRealmRegistry {
    private val byGroup = mutableMapOf<String, TokenRealm>()

    fun register(realm: TokenRealm) {
        for (group in realm.routeGroups) {
            val existing = byGroup[group]
            check(existing == null || existing === realm) {
                "Route group '$group' is already bound to token realm '${existing!!.name}', cannot bind '${realm.name}'"
            }
            byGroup[group] = realm
        }
    }

    fun forGroup(group: String?): TokenRealm? = group?.let { byGroup[it] }
}
