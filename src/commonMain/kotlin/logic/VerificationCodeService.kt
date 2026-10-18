package logic

import neton.core.http.HttpException
import neton.core.http.NetonErrorCode
import neton.logging.Logger
import neton.redis.RedisClient
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.random.Random
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds

/**
 * 统一的验证码服务（spec application/auth-abuse-protection、评审 R06/R07/R08）。
 *
 * 此前短信、邮件、管理员、账号各自存取验证码：键都是 `sms:code:<手机号>`，登录码能拿去重置密码，
 * 管理员和终端用户用同一个手机号时还会互相覆盖；只有 account 的短信做了原子消费和猜错上限。
 * 现在所有发码与核验都经过这里：
 *
 * - 挑战按「身份域 + 用途 + 通道 + 接收方」隔离，码只能用于发它的那个用途。
 * - 核验、消费、记错在一个 Lua 脚本里原子完成；同一个码猜错 [MAX_FAILS] 次即作废，
 *   失败计数跟着挑战走，换 IP、换入口都共享同一份预算。
 * - 发送有冷却、按接收方的每日上限和按通道的全局每日预算，防止被拿来轰炸手机号或刷短信费。
 * - 登录后才能发的码（绑定邮箱、换绑手机）可以绑定发起人 [actor]：别的账号拿到同一个码也用不了。
 * - 码不进任何日志（[MessageSendLogic] 写发送记录前会脱敏）。没有配置发送通道时不假装发送成功：
 *   直接报服务不可用。
 * - 模拟通道（[Settings.simulate]，spec auth-abuse-protection §5.2）：只存不发。额度与挑战照常，
 *   不调用任何供应商、不查模板；码写进 `verification.simulated` 日志并镜像到模拟收件箱，
 *   供开发取码接口读取（[latestSimulated]）。能否打开由启动时的环境闸决定（config.requireSimulationAllowed）。
 */
class VerificationCodeService(
    private val log: Logger,
    private val redis: RedisClient?,
    private val delivery: MessageSendLogic,
    private val settings: Settings = Settings(),
) {
    enum class Channel(val key: String) { SMS("sms"), EMAIL("email") }

    enum class Result { OK, INVALID, EXPIRED, LOCKED }

    data class Settings(
        /** 模拟通道：只存不发，码可由开发取码接口读出。只有显式声明的 dev / local / test 环境能打开。 */
        val simulate: Boolean = false,
        /** 同一接收方两次发码的最短间隔。 */
        val resendCooldownSeconds: Long = 60,
        /** 同一接收方每天最多发几次。 */
        val dailyLimitPerRecipient: Long = 10,
        /** 每个通道全站每天最多发几条（短信费的硬上限）。0 = 不限。 */
        val dailyLimitGlobal: Long = 0,
    )

    /**
     * 发一条验证码。[templateCode] 是消息模板编码（沿用各部署里已经配好的模板名）。
     * 发送失败或通道缺失时抛出，挑战不会留下 —— 用户看到的是真实的失败，而不是一条永远收不到的码。
     */
    suspend fun issue(realm: String, purpose: String, channel: Channel, recipient: String, templateCode: String, actor: String? = null) {
        val r = redis ?: throw HttpException(NetonErrorCode.SERVICE_UNAVAILABLE, SystemErrorCodes.VERIFICATION_UNAVAILABLE)
        val to = normalize(channel, recipient)
        reserveSendBudget(r, channel, to)

        val code = Random.Default.nextInt(100000, 1000000).toString()
        val key = challengeKey(realm, purpose, channel, to, actor)
        r.set(key, code, ttl = CODE_TTL_SECONDS.seconds)
        r.delete(failsKey(key))

        if (settings.simulate) {
            val now = Clock.System.now().toEpochMilliseconds()
            val record = SimulatedCode(channel.key, to, code, realm, purpose, now, now + CODE_TTL_SECONDS * 1000)
            r.set(simulatedKey(channel, to), json.encodeToString(SimulatedCode.serializer(), record), ttl = CODE_TTL_SECONDS.seconds)
            log.info("verification.simulated", mapOf("realm" to realm, "purpose" to purpose, "channel" to channel.key, "to" to mask(channel, to), "code" to code))
            return
        }

        val outcome = delivery.deliverCode(channel.key, to, templateCode, code)
        when (outcome) {
            MessageSendLogic.Delivery.SENT -> Unit
            else -> {
                r.delete(key)
                r.delete("vc:cooldown:${channel.key}:$to") // 没发出去不该占用户的重试机会
                log.warn("verification.send_failed", mapOf("realm" to realm, "purpose" to purpose, "channel" to channel.key, "to" to mask(channel, to), "outcome" to outcome.name))
                if (outcome == MessageSendLogic.Delivery.UNCONFIGURED) {
                    throw HttpException(NetonErrorCode.SERVICE_UNAVAILABLE, SystemErrorCodes.VERIFICATION_CHANNEL_UNAVAILABLE)
                }
                throw HttpException(NetonErrorCode.SERVICE_UNAVAILABLE, SystemErrorCodes.VERIFICATION_SEND_FAILED)
            }
        }
    }

    /** 核验并消费。成功一次即删除；猜错计数，到上限作废整条挑战。 */
    suspend fun verify(realm: String, purpose: String, channel: Channel, recipient: String, code: String, actor: String? = null): Result {
        val r = redis ?: return Result.EXPIRED
        val key = challengeKey(realm, purpose, channel, normalize(channel, recipient), actor)
        val script = """
            local val = redis.call('GET', KEYS[1])
            if val == false then return 0 end
            if val == ARGV[1] then
                redis.call('DEL', KEYS[1], KEYS[2])
                return 2
            end
            local fails = redis.call('INCR', KEYS[2])
            if fails == 1 then redis.call('EXPIRE', KEYS[2], ARGV[2]) end
            if fails >= tonumber(ARGV[3]) then
                redis.call('DEL', KEYS[1], KEYS[2])
                return 3
            end
            return 1
        """.trimIndent()
        return when (r.evalToLong(script, listOf(key, failsKey(key)), listOf(code.trim(), CODE_TTL_SECONDS.toString(), MAX_FAILS.toString()))) {
            2L -> Result.OK
            1L -> Result.INVALID
            3L -> Result.LOCKED
            else -> Result.EXPIRED
        }
    }

    /** 模拟通道是否打开（开发取码接口据此决定是否存在）。 */
    val simulationActive: Boolean get() = settings.simulate

    /**
     * 模拟收件箱里该接收方最近一条码（开发取码接口用）。模拟未开启时恒为 null。
     * 只是镜像：核验仍只认挑战本身，挑战被消费或作废后这里的码也用不了。
     */
    suspend fun latestSimulated(channel: Channel, recipient: String): SimulatedCode? {
        if (!settings.simulate) return null
        val raw = redis?.getValue(simulatedKey(channel, normalize(channel, recipient))) ?: return null
        return runCatching { json.decodeFromString(SimulatedCode.serializer(), raw) }.getOrNull()
    }

    /** 某个用途下是否有尚未用掉的码（一个入口接受多种用途时，按此选用哪一条）。 */
    suspend fun hasPending(realm: String, purpose: String, channel: Channel, recipient: String, actor: String? = null): Boolean =
        redis?.getValue(challengeKey(realm, purpose, channel, normalize(channel, recipient), actor)) != null

    /** 冷却与每日上限。先占冷却位再计数，两个并发请求只有一个能通过冷却。 */
    private suspend fun reserveSendBudget(r: RedisClient, channel: Channel, to: String) {
        val cooldownKey = "vc:cooldown:${channel.key}:$to"
        if (settings.resendCooldownSeconds > 0 &&
            !r.setIfAbsent(cooldownKey, "1", settings.resendCooldownSeconds * 1000)
        ) {
            // Retry-After = 冷却键真实剩余时间（向上取整秒）；键恰好过期/读不到时按整段冷却给
            val remainingMs = r.evalToLong("return redis.call('PTTL', KEYS[1])", listOf(cooldownKey), emptyList())
            val retryAfter = remainingMs?.takeIf { it > 0 }?.let { (it + 999) / 1000 } ?: settings.resendCooldownSeconds
            throw HttpException(
                NetonErrorCode.RATE_LIMIT_EXCEEDED,
                SystemErrorCodes.VERIFICATION_TOO_FREQUENT,
                headers = mapOf("Retry-After" to retryAfter.coerceIn(1, settings.resendCooldownSeconds).toString()),
            )
        }
        val day = Clock.System.now().toEpochMilliseconds() / 86_400_000
        if (settings.dailyLimitPerRecipient > 0 &&
            countToday(r, "vc:daily:${channel.key}:$to:$day") > settings.dailyLimitPerRecipient
        ) {
            throw HttpException(NetonErrorCode.DAILY_QUOTA_EXCEEDED, SystemErrorCodes.VERIFICATION_DAILY_LIMIT)
        }
        if (settings.dailyLimitGlobal > 0 &&
            countToday(r, "vc:daily:${channel.key}:*:$day") > settings.dailyLimitGlobal
        ) {
            log.warn("verification.global_budget_exhausted", mapOf("channel" to channel.key, "limit" to settings.dailyLimitGlobal))
            throw HttpException(NetonErrorCode.SERVICE_UNAVAILABLE, SystemErrorCodes.VERIFICATION_DAILY_BUDGET_EXHAUSTED)
        }
    }

    private suspend fun countToday(r: RedisClient, key: String): Long = r.evalToLong(
        "local n = redis.call('INCR', KEYS[1]) if n == 1 then redis.call('EXPIRE', KEYS[1], 90000) end return n",
        listOf(key), emptyList(),
    ) ?: 0

    private fun challengeKey(realm: String, purpose: String, channel: Channel, to: String, actor: String?) =
        if (actor == null) "vc:$realm:$purpose:${channel.key}:$to" else "vc:$realm:$purpose:${channel.key}:@$actor:$to"
    private fun failsKey(key: String) = "$key:fails"
    private fun simulatedKey(channel: Channel, to: String) = "vc:sim:${channel.key}:$to"
    private fun normalize(channel: Channel, recipient: String) =
        if (channel == Channel.EMAIL) recipient.trim().lowercase() else recipient.trim()

    private fun mask(channel: Channel, to: String): String = when (channel) {
        Channel.SMS -> if (to.length < 7) "***" else "${to.take(3)}****${to.takeLast(4)}"
        Channel.EMAIL -> to.substringBefore('@').take(2) + "***@" + to.substringAfter('@', "")
    }

    /** 模拟收件箱的一条记录。[receiver] 是规范化后的接收方。时间为 epoch 毫秒。 */
    @Serializable
    data class SimulatedCode(
        val channel: String,
        val receiver: String,
        val code: String,
        val realm: String,
        val purpose: String,
        val issuedAt: Long,
        val expiresAt: Long,
    )

    companion object {
        private val json = Json { ignoreUnknownKeys = true }
        const val CODE_TTL_SECONDS = 300L
        const val MAX_FAILS = 5
    }
}
