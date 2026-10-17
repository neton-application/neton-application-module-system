package logic

import controller.admin.dev.DevVerificationController
import kotlinx.coroutines.runBlocking
import neton.core.component.NetonContext
import neton.core.config.getEnv
import neton.core.http.HttpException
import neton.core.http.NetonErrorCode
import neton.core.http.NotFoundException
import neton.logging.Fields
import neton.logging.Logger
import neton.redis.DefaultRedisClient
import neton.redis.RedisConfig
import kotlin.random.Random
import kotlin.test.*

/**
 * 模拟通道（spec auth-abuse-protection §5.2）。
 *
 * 不需要 Redis 的部分总是运行；真实 Redis 部分 opt-in：设置 `NETON_VERIFICATION_TEST_REDIS_PORT`
 * 指向一个**一次性的本机 redis-server**（测试会写入、不清库）。
 */
class VerificationSimulationTest {
    private val infos = mutableListOf<Pair<String, Fields>>()
    private val logger = object : Logger {
        override fun trace(msg: String, fields: Fields) {}
        override fun debug(msg: String, fields: Fields) {}
        override fun info(msg: String, fields: Fields) { infos += msg to fields }
        override fun warn(msg: String, fields: Fields, cause: Throwable?) {}
        override fun error(msg: String, fields: Fields, cause: Throwable?) {}
    }

    /** 记录是否有真实供应商被调用。 */
    private var providerCalls = 0
    private val delivery = MessageSendLogic(
        logger,
        MessageChannelLogic(logger, mapOf("sms" to recorder("sms"), "email" to recorder("email"))),
        MessageTemplateLogic(logger),
    )
    private fun recorder(t: String) = object : logic.provider.MessageProvider {
        override val type = t
        override suspend fun send(receiver: String, content: String, config: String): Boolean { providerCalls++; return true }
    }

    private fun controller(svc: VerificationCodeService?): DevVerificationController {
        val ctx = NetonContext(emptyArray())
        if (svc != null) ctx.bind(VerificationCodeService::class, svc)
        return DevVerificationController(ctx)
    }

    @Test
    fun devEndpointIsNotFoundWhenSimulationIsOff() = runBlocking<Unit> {
        val off = VerificationCodeService(logger, null, delivery, VerificationCodeService.Settings(simulate = false))
        assertFalse(off.simulationActive)
        // 连参数都不校验：与不存在的路由一致。
        assertFailsWith<NotFoundException> { controller(off).latest(null, null) }
        assertFailsWith<NotFoundException> { controller(off).latest("sms", "+8613800000000") }
        assertFailsWith<NotFoundException> { controller(null).latest("sms", "+8613800000000") }
        assertNull(off.latestSimulated(VerificationCodeService.Channel.SMS, "+8613800000000"))
    }

    @Test
    fun simulatedDeliveryAgainstRealRedis() = runBlocking<Unit> {
        val port = getEnv("NETON_VERIFICATION_TEST_REDIS_PORT")?.toIntOrNull() ?: return@runBlocking
        val redis = DefaultRedisClient(RedisConfig(host = "127.0.0.1", port = port, keyPrefix = "vcsim${Random.nextInt(1_000_000)}"))
        try {
            val sim = VerificationCodeService(logger, redis, delivery, VerificationCodeService.Settings(simulate = true, resendCooldownSeconds = 60))
            val phone = "+8613800000001"
            val sms = VerificationCodeService.Channel.SMS

            // 没有模板、没有通道，模拟也照样受理；不调用任何供应商。
            sim.issue("account", "login", sms, phone, "sms_verification_login")
            assertEquals(0, providerCalls)
            val record = assertNotNull(sim.latestSimulated(sms, " $phone "))
            assertEquals(6, record.code.length)
            assertEquals("account", record.realm); assertEquals("login", record.purpose); assertEquals(phone, record.receiver)
            assertEquals(VerificationCodeService.CODE_TTL_SECONDS * 1000, record.expiresAt - record.issuedAt)

            // 日志带码与脱敏接收方，不带原始号码。
            val (msg, fields) = infos.last()
            assertEquals("verification.simulated", msg)
            assertEquals(record.code, fields["code"]); assertEquals("+86****0001", fields["to"])

            // 开发接口读出同一条；realm / purpose 不匹配则 404。
            val api = controller(sim)
            assertEquals(record, api.latest("sms", phone))
            assertEquals(record, api.latest("SMS", phone, realm = "account", purpose = "login"))
            assertFailsWith<NotFoundException> { api.latest("sms", phone, realm = "admin") }
            assertFailsWith<NotFoundException> { api.latest("sms", phone, purpose = "reset-password") }
            assertFailsWith<NotFoundException> { api.latest("sms", "+8613800000009") }
            assertFailsWith<neton.core.http.BadRequestException> { api.latest("push", phone) }

            // 冷却照常：模拟不绕过额度。
            val cooldown = assertFailsWith<HttpException> { sim.issue("account", "login", sms, phone, "sms_verification_login") }
            assertEquals(NetonErrorCode.RATE_LIMIT_EXCEEDED, cooldown.code)

            // 码只能用于发它的用途；统一挑战原子消费，一次性。
            assertEquals(VerificationCodeService.Result.EXPIRED, sim.verify("account", "reset-password", sms, phone, record.code))
            assertEquals(VerificationCodeService.Result.OK, sim.verify("account", "login", sms, phone, record.code))
            assertEquals(VerificationCodeService.Result.EXPIRED, sim.verify("account", "login", sms, phone, record.code))

            // 猜错预算照常：第 5 次作废，之后正确码也不行。邮件走同一机制，按小写规范化。
            val mail = VerificationCodeService.Channel.EMAIL
            sim.issue("account", "bind-email", mail, "Dev@Example.com", "mail_verification_bind-email")
            val mailCode = assertNotNull(sim.latestSimulated(mail, "dev@example.COM")).code
            val wrong = if (mailCode == "000000") "111111" else "000000"
            repeat(VerificationCodeService.MAX_FAILS - 1) {
                assertEquals(VerificationCodeService.Result.INVALID, sim.verify("account", "bind-email", mail, "dev@example.com", wrong))
            }
            assertEquals(VerificationCodeService.Result.LOCKED, sim.verify("account", "bind-email", mail, "dev@example.com", wrong))
            assertEquals(VerificationCodeService.Result.EXPIRED, sim.verify("account", "bind-email", mail, "dev@example.com", mailCode))
            assertEquals(0, providerCalls)

            // 模拟关闭：同一 Redis 下没有通道就是 503，不留挑战也不写收件箱。
            val real = VerificationCodeService(logger, redis, delivery, VerificationCodeService.Settings(simulate = false, resendCooldownSeconds = 60))
            val other = "+8613800000002"
            val unavailable = assertFailsWith<HttpException> { real.issue("account", "login", sms, other, "sms_verification_login") }
            assertEquals(NetonErrorCode.SERVICE_UNAVAILABLE, unavailable.code)
            assertFalse(real.hasPending("account", "login", sms, other))
            assertNull(sim.latestSimulated(sms, other))
            assertNull(real.latestSimulated(sms, phone))
        } finally {
            redis.close()
        }
    }
}
