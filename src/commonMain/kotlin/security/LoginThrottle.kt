package security

import logic.SystemErrorCodes
import neton.core.http.HttpException
import neton.core.http.NetonErrorCode
import neton.redis.RedisClient
import neton.security.crypto.HmacSha256

/**
 * 登录失败冷却（spec application/auth-abuse-protection §4.1，评审 R08）。
 *
 * - **冷却按「身份域 + 账号 + IP」计数**：同一来源对同一账号在 [WINDOW_SECONDS] 内失败 [MAX_FAILURES] 次，
 *   该来源对该账号冷却到窗口结束，返回 429 + Retry-After。其他来源不受影响 —— 攻击者不能靠输错密码
 *   把真正的管理员锁在门外。
 * - **跨 IP 的账号失败只是风险信号**：按「身份域 + 账号」另计，超过 [ACCOUNT_RISK_THRESHOLD] 记结构化安全
 *   事件（由调用方写日志），不锁号。
 * - 冷却期间被拒绝的请求不做凭据验证，也不增加失败次数、不续期冷却。成功登录只清本账号本来源的计数。
 * - 键里不放原始账号：用带密钥的摘要，缓存与日志都看不到手机号 / 用户名；账号原样参与摘要，不擅自转小写。
 *
 * 没有 Redis 时不做账号级冷却，只剩入口限流。
 */
class LoginThrottle(private val redis: RedisClient?, private val digestKey: ByteArray = ByteArray(0)) {

    class Locked(val retryAfterSeconds: Long) : HttpException(
        NetonErrorCode.RATE_LIMIT_EXCEEDED,
        SystemErrorCodes.LOGIN_LOCKED,
        headers = mapOf("Retry-After" to retryAfterSeconds.toString()),
    )

    /** 冷却中则抛 [Locked]（429）。 */
    suspend fun ensureNotLocked(realm: String, identifier: String, clientIp: String?) {
        val r = redis ?: return
        val key = pairKey(realm, identifier, clientIp)
        val failures = r.getValue(key)?.toLongOrNull() ?: 0
        if (failures >= MAX_FAILURES) {
            val ttl = r.evalToLong("return redis.call('TTL', KEYS[1])", listOf(key), emptyList()) ?: WINDOW_SECONDS
            throw Locked(ttl.coerceIn(1, WINDOW_SECONDS))
        }
    }

    /** 记一次凭据失败；返回该账号跨来源的失败次数（供调用方判断是否记风险事件）。 */
    suspend fun recordFailure(realm: String, identifier: String, clientIp: String?): Long {
        val r = redis ?: return 0
        val incr = "local n = redis.call('INCR', KEYS[1]) if n == 1 then redis.call('EXPIRE', KEYS[1], ARGV[1]) end return n"
        r.evalToLong(incr, listOf(pairKey(realm, identifier, clientIp)), listOf(WINDOW_SECONDS.toString()))
        return r.evalToLong(incr, listOf(accountKey(realm, identifier)), listOf(WINDOW_SECONDS.toString())) ?: 0
    }

    suspend fun reset(realm: String, identifier: String, clientIp: String?) {
        redis?.delete(pairKey(realm, identifier, clientIp))
    }

    private fun digest(identifier: String): String =
        HmacSha256.signHex(digestKey, identifier.trim().encodeToByteArray()).take(32)

    private fun pairKey(realm: String, identifier: String, clientIp: String?) =
        "auth:fail:$realm:${digest(identifier)}:${clientIp ?: "unknown"}"

    private fun accountKey(realm: String, identifier: String) = "auth:risk:$realm:${digest(identifier)}"

    companion object {
        const val MAX_FAILURES = 5L
        const val WINDOW_SECONDS = 900L
        /** 跨来源失败达到这个数记安全事件（不锁号）。 */
        const val ACCOUNT_RISK_THRESHOLD = 20L
    }
}
