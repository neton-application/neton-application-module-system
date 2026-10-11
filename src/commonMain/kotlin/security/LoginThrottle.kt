package security

import neton.core.http.HttpException
import neton.core.http.NetonErrorCode
import neton.redis.RedisClient

/**
 * 按账号的登录失败冷却（spec application/auth-abuse-protection、评审 R08）。
 *
 * 入口上的 IP 限流挡不住换 IP 慢速猜同一个账号的密码；这里按「身份域 + 账号标识」计数，
 * [WINDOW_SECONDS] 内失败 [MAX_FAILURES] 次即锁定到窗口结束。成功登录清零。
 *
 * 锁定期间无论密码对错都拒绝，且提示与密码错误不同 —— 这是有意的：被锁的人需要知道等多久，
 * 而锁定本身不泄露密码是否正确。没有 Redis 时不做账号级冷却，只剩入口限流。
 */
class LoginThrottle(private val redis: RedisClient?) {

    suspend fun ensureNotLocked(realm: String, identifier: String) {
        val r = redis ?: return
        val failures = r.getValue(key(realm, identifier))?.toLongOrNull() ?: 0
        if (failures >= MAX_FAILURES) {
            throw HttpException(NetonErrorCode.RATE_LIMIT_EXCEEDED, "Too many failed attempts, please try again later")
        }
    }

    suspend fun recordFailure(realm: String, identifier: String) {
        val r = redis ?: return
        r.evalToLong(
            "local n = redis.call('INCR', KEYS[1]) if n == 1 then redis.call('EXPIRE', KEYS[1], ARGV[1]) end return n",
            listOf(key(realm, identifier)),
            listOf(WINDOW_SECONDS.toString()),
        )
    }

    suspend fun reset(realm: String, identifier: String) {
        redis?.delete(key(realm, identifier))
    }

    private fun key(realm: String, identifier: String) = "auth:fail:$realm:${identifier.trim().lowercase()}"

    companion object {
        const val MAX_FAILURES = 5L
        const val WINDOW_SECONDS = 900L
    }
}
