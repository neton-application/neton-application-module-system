package security

import neton.redis.RedisClient
import kotlin.random.Random
import kotlin.time.Duration.Companion.seconds

/**
 * 会话级吊销与刷新令牌单次使用（spec application/identity-realm §4、评审 R04）。
 *
 * - 单会话退出：把 `ssn` 记为已吊销，保留到该会话最长的令牌过期为止。用户级的「全端退出」由各域的
 *   会话版本负责（管理员 `system_user_sessions`、账号 `session_version`），那份在数据库里，不依赖 Redis。
 * - 刷新令牌只能换一次：每张 refresh 的 `jti` 首次使用时登记。[CONCURRENT_WINDOW_SECONDS] 内再次使用
 *   视为客户端并发刷新（多个页签同时过期），照常放行；超过窗口仍在使用，说明令牌被复制了，吊销整个会话。
 *
 * 没有 Redis 时这两项都无法实现：[available] 为 false，调用方退回到用户级会话版本。
 */
class SessionRevocation(private val redis: RedisClient?) {

    enum class RefreshUse { FIRST, CONCURRENT, REUSED }

    val available: Boolean get() = redis != null

    suspend fun revokeSession(realm: String, sessionId: String, ttlSeconds: Long) {
        redis?.set(revokedKey(realm, sessionId), "1", ttl = ttlSeconds.coerceAtLeast(1).seconds)
    }

    suspend fun isRevoked(realm: String, sessionId: String): Boolean =
        redis?.getValue(revokedKey(realm, sessionId)) != null

    suspend fun claimRefresh(realm: String, jti: String, ttlSeconds: Long, nowSeconds: Long): RefreshUse {
        val r = redis ?: return RefreshUse.FIRST
        val script = """
            local ok = redis.call('SET', KEYS[1], ARGV[1], 'NX', 'EX', ARGV[2])
            if ok then return 0 end
            local first = tonumber(redis.call('GET', KEYS[1]) or ARGV[1])
            if tonumber(ARGV[1]) - first <= tonumber(ARGV[3]) then return 1 end
            return 2
        """.trimIndent()
        return when (r.evalToLong(
            script,
            listOf("auth:refresh:used:$realm:$jti"),
            listOf(nowSeconds.toString(), ttlSeconds.coerceAtLeast(1).toString(), CONCURRENT_WINDOW_SECONDS.toString()),
        )) {
            0L -> RefreshUse.FIRST
            1L -> RefreshUse.CONCURRENT
            else -> RefreshUse.REUSED
        }
    }

    private fun revokedKey(realm: String, sessionId: String) = "auth:session:revoked:$realm:$sessionId"

    companion object {
        const val SESSION_CLAIM = "ssn"
        const val TOKEN_ID_CLAIM = "jti"
        const val CONCURRENT_WINDOW_SECONDS = 30L

        /** 128 位随机 id，十六进制。 */
        fun newId(): String = Random.Default.nextBytes(16).joinToString("") {
            (it.toInt() and 0xff).toString(16).padStart(2, '0')
        }
    }
}
