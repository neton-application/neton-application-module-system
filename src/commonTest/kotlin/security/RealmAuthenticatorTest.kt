package security

import config.requireUsableJwtSecret
import kotlinx.coroutines.runBlocking
import neton.core.interfaces.RequestContext
import neton.security.identity.UserId
import neton.security.jwt.JwtAuthenticator
import neton.security.jwt.VerifiedJwtToken
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * 身份域隔离矩阵（评审 R01/R02）：管理员与账号令牌用同一把密钥签发、id 相同，
 * 只有落在自己域的路由组、且是 access 类型时才被接受。
 */
class RealmAuthenticatorTest {
    private val jwt = JwtAuthenticator(secretKey = "test-only-secret-0123456789abcdef-0123456789")

    /** 只认 access 类型的域；业务层（用户状态、会话版本）由各自模块的测试覆盖。 */
    private class TypeOnlyRealm(
        override val name: String,
        override val routeGroups: Set<String>,
        override val acceptsExternalTokens: Boolean = false,
    ) : TokenRealm {
        override suspend fun resolve(token: VerifiedJwtToken) =
            token.identity.takeIf { token.claimString(TokenRealm.TYPE_CLAIM) == TokenRealm.TYPE_ACCESS }
    }

    private val registry = TokenRealmRegistry().apply {
        register(TypeOnlyRealm("admin", setOf("admin")))
        register(TypeOnlyRealm("account", setOf("app", "manage"), acceptsExternalTokens = true))
    }

    private fun authenticator(prefix: String = "Bearer ") = RealmAuthenticator(
        legacy = jwt, unified = null, realms = registry,
        headerName = "Authorization", tokenPrefix = prefix, sessionCookieName = "neton_session",
    )

    /** 两个域的令牌主体都是 1 —— 正是会撞的那种 id。 */
    private fun token(realm: String?, type: String): String {
        val claims = buildMap<String, Any> {
            realm?.let { put(TokenRealm.CLAIM, it) }
            put(TokenRealm.TYPE_CLAIM, type)
            put("sid", 0L)
        }
        return jwt.createToken(userId = UserId(1u), expiresInSeconds = 600, extraClaims = claims)
    }

    private fun request(group: String, headers: Map<String, String>) = object : RequestContext {
        override val path = "/$group/x"
        override val method = "GET"
        override val headers = headers
        override val routeGroup = group
    }

    private suspend fun authOn(group: String, token: String) =
        authenticator().authenticate(request(group, mapOf("Authorization" to "Bearer $token")))

    @Test
    fun accessTokensWorkOnlyInTheirOwnRealm() = runBlocking<Unit> {
        assertNotNull(authOn("admin", token("admin", "access")))
        assertNotNull(authOn("app", token("account", "access")))
        assertNotNull(authOn("manage", token("account", "access")))

        assertNull(authOn("admin", token("account", "access")), "account token on admin")
        assertNull(authOn("app", token("admin", "access")), "admin token on app")
        assertNull(authOn("manage", token("admin", "access")), "admin token on manage")
    }

    @Test
    fun refreshTokensNeverAuthenticateRequests() = runBlocking<Unit> {
        for (group in listOf("admin", "app", "manage")) {
            assertNull(authOn(group, token("admin", "refresh")), "admin refresh on $group")
            assertNull(authOn(group, token("account", "refresh")), "account refresh on $group")
        }
    }

    /** 加域之前签发的令牌没有 realm：一律失效，必须重新登录，不能默认当成任何一个域。 */
    @Test
    fun tokensWithoutRealmAreRejected() = runBlocking<Unit> {
        for (group in listOf("admin", "app", "manage")) {
            assertNull(authOn(group, token(null, "access")), "realm-less token on $group")
        }
    }

    @Test
    fun groupsWithoutRealmAcceptNoAppTokens() = runBlocking<Unit> {
        assertNull(authOn("open", token("admin", "access")))
        assertNull(authOn("open", token("account", "access")))
    }

    @Test
    fun forgedOrForeignSignaturesAreRejected() = runBlocking<Unit> {
        val other = JwtAuthenticator(secretKey = "another-secret-0123456789abcdef-0123456789")
        val foreign = other.createToken(userId = UserId(1u), expiresInSeconds = 600,
            extraClaims = mapOf(TokenRealm.CLAIM to "admin", TokenRealm.TYPE_CLAIM to "access"))
        assertNull(authOn("admin", foreign))
        // 账号域声明接受外部令牌，但本测试没有装配外部验证器，验不过就是 null，不能回落成本应用令牌。
        assertNull(authOn("app", foreign))
    }

    @Test
    fun sessionCookieIsUsedWhenNoAuthorizationHeader() = runBlocking<Unit> {
        val identity = authenticator().authenticate(
            request("admin", mapOf("Cookie" to "theme=dark; neton_session=${token("admin", "access")}")),
        )
        assertEquals("1", assertNotNull(identity).id)
    }

    /** 配置里写 "Bearer "，运行时可能读到 "Bearer"：两种前缀都必须得到裸令牌。 */
    @Test
    fun prefixWithOrWithoutTrailingSpaceWorks() = runBlocking<Unit> {
        for (prefix in listOf("Bearer", "Bearer ")) {
            val identity = authenticator(prefix).authenticate(
                request("admin", mapOf("Authorization" to "Bearer ${token("admin", "access")}")),
            )
            assertNotNull(identity, "prefix=[$prefix]")
        }
    }

    @Test
    fun aRouteGroupCannotBelongToTwoRealms() {
        assertFailsWith<IllegalStateException> {
            registry.register(TypeOnlyRealm("intruder", setOf("admin")))
        }
    }

    @Test
    fun placeholderAndShortSecretsAreRefused() {
        assertFailsWith<IllegalStateException> { requireUsableJwtSecret(null) }
        assertFailsWith<IllegalStateException> { requireUsableJwtSecret("  ") }
        assertFailsWith<IllegalStateException> { requireUsableJwtSecret("REPLACE_ME_SET_NETON_SECURITY__JWT__SECRETKEY") }
        assertFailsWith<IllegalStateException> { requireUsableJwtSecret("too-short-secret") }
        assertEquals("x".repeat(32), requireUsableJwtSecret(" ${"x".repeat(32)} "))
    }
}
