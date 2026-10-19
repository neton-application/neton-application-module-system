package logic.provider

import kotlinx.coroutines.runBlocking
import neton.core.component.NetonContext
import neton.http.client.EgressSettings
import neton.http.client.GuardedHttpClientProvider
import neton.http.client.HttpClient
import neton.http.client.HttpClientConfig
import neton.http.client.HttpClientError
import neton.http.client.HttpClientException
import neton.http.client.HttpClientMethod
import neton.http.client.HttpClientProvider
import neton.http.client.IpNetwork
import neton.http.client.OutboundAddressPolicy
import neton.http.client.TargetRejection
import neton.http.mail.SmtpAccount
import neton.http.mail.SmtpMessage
import neton.http.mail.SmtpTargetRejectedException
import neton.http.mail.SmtpTransport
import neton.http.testkit.ScriptedHttpClient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * 短信 apiUrl、邮件 endpoint、SMTP 主机都是后台消息渠道里的数据：发送走受控客户端 / 带运维出站策略的 SMTP，
 * 地址被拒时报 MESSAGE_ENDPOINT_FORBIDDEN，而不是当成一次普通的发送失败。
 */
class MessageEgressTest {

    private val log = neton.logging.defaultLoggerFactory().get("test.message-egress")

    private fun refusing(reason: TargetRejection) = ScriptedHttpClient().apply {
        for (method in listOf(HttpClientMethod.Get, HttpClientMethod.Post)) {
            on(method, "http") { throw HttpClientException(HttpClientError.TargetRejected(reason, "refused by test")) }
        }
    }

    @Test
    fun smsToAnInternalApiUrlIsAnEndpointError() = runBlocking {
        val config = """{"username":"u","password":"p","apiUrl":"http://169.254.169.254/sms"}"""
        val e = assertFailsWith<MessageEndpointForbiddenException> {
            SmsProvider(log, refusing(TargetRejection.ADDRESS)).send("+8613800001111", "code", config)
        }
        assertEquals(logic.SystemErrorCodes.MESSAGE_ENDPOINT_FORBIDDEN, e.message)
    }

    @Test
    fun httpEmailVendorToAnInternalEndpointIsAnEndpointError() = runBlocking {
        val config = """{"vendor":"http","apiKey":"k","from":"a@b.test","endpoint":"http://10.0.0.5/mail"}"""
        assertFailsWith<MessageEndpointForbiddenException> {
            EmailProvider(log, refusing(TargetRejection.ADDRESS), smtpPolicy = OutboundAddressPolicy()).send("x@y.test", "subject\nbody", config)
        }
        Unit
    }

    @Test
    fun smtpSendsCarryTheOperatorSmtpPolicy() = runBlocking {
        val policy = EgressSettings(allowedNetworks = listOf(IpNetwork.parse("10.1.0.0/16"))).smtpPolicy
        val seen = mutableListOf<OutboundAddressPolicy?>()
        val transport = object : SmtpTransport {
            override suspend fun send(account: SmtpAccount, message: SmtpMessage, addressPolicy: OutboundAddressPolicy?) {
                seen += addressPolicy
                throw SmtpTargetRejectedException(TargetRejection.ADDRESS, "refused by test")
            }
        }
        val config = """{"vendor":"smtp","host":"127.0.0.1","port":25,"from":"a@b.test"}"""
        assertFailsWith<MessageEndpointForbiddenException> {
            EmailProvider(log, ScriptedHttpClient(), transport, smtpPolicy = policy).send("x@y.test", "subject\nbody", config)
        }
        assertSame(policy, seen.single())
    }

    @Test
    fun theEgressProviderIsBoundOnceAndBuildsGuardedClients() {
        val configs = mutableListOf<HttpClientConfig>()
        val ctx = NetonContext(emptyArray())
        ctx.bind(HttpClientProvider::class, HttpClientProvider { block ->
            configs += HttpClientConfig().apply(block)
            ScriptedHttpClient()
        })
        val egress = init.bindEgress(ctx, neton.logging.defaultLoggerFactory())
        assertSame(egress, ctx.get(GuardedHttpClientProvider::class))
        egress.create { connectMillis = 1_000 }
        assertSame(egress.policy, configs.single().addressPolicy)

        // 宿主先绑定的那一个原样沿用：全进程只有一份策略
        val hostBound = GuardedHttpClientProvider(EgressSettings(allowedHosts = setOf("relay.internal"))) { ScriptedHttpClient() }
        val other = NetonContext(emptyArray()).apply { bind(GuardedHttpClientProvider::class, hostBound) }
        assertSame(hostBound, init.bindEgress(other, neton.logging.defaultLoggerFactory()))
        assertTrue(hostBound.settings.isRelaxed)
    }
}
