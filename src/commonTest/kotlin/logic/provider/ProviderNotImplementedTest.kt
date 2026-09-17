package logic.provider

import kotlinx.coroutines.runBlocking
import neton.logging.Fields
import neton.logging.Logger
import kotlin.test.Test
import kotlin.test.assertFailsWith

/**
 * STD-0 contract: 未实现或未配置的 provider 必须 NOT fake success —— 抛
 * [ProviderNotImplementedException] instead of returning `true` / an empty-openId user.
 * This is the guardrail that keeps a fake SMS/email delivery or an empty social identity
 * off the live auth path.
 */
class ProviderNotImplementedTest {

    private val log: Logger = NoopLogger

    @Test
    fun `sms send does not fake success`() {
        runBlocking {
            assertFailsWith<ProviderNotImplementedException> {
                SmsProvider(log, neton.http.testkit.ScriptedHttpClient()).send("13800001111", "code 123456", "{}")
            }
        }
    }

    @Test
    fun `email send does not fake success`() {
        runBlocking {
            assertFailsWith<ProviderNotImplementedException> {
                // 空配置 = 没选发信方式（vendor 既不是 smtp 也不是任何 HTTP 厂商）：
                // 必须抛，不能装作发出去了。真实发信路径见 EmailProvider 的 vendor 分支。
                EmailProvider(log, neton.http.testkit.ScriptedHttpClient()).send("a@b.com", "code 123456", "{}")
            }
        }
    }

    @Test
    fun `google getUserInfo does not return empty identity`() {
        runBlocking {
            assertFailsWith<ProviderNotImplementedException> {
                GoogleSocialProvider(log).getUserInfo("{}", "auth-code", "https://cb")
            }
        }
    }

    @Test
    fun `google getAuthRedirectUrl does not return placeholder url`() {
        runBlocking {
            assertFailsWith<ProviderNotImplementedException> {
                GoogleSocialProvider(log).getAuthRedirectUrl("{}", "https://cb")
            }
        }
    }

    @Test
    fun `telegram getUserInfo does not return empty identity`() {
        runBlocking {
            assertFailsWith<ProviderNotImplementedException> {
                TelegramSocialProvider(log).getUserInfo("{}", "auth-code", "https://cb")
            }
        }
    }

    @Test
    fun `telegram getAuthRedirectUrl does not return empty url`() {
        runBlocking {
            assertFailsWith<ProviderNotImplementedException> {
                TelegramSocialProvider(log).getAuthRedirectUrl("{}", "https://cb")
            }
        }
    }
}

private object NoopLogger : Logger {
    override fun trace(msg: String, fields: Fields) {}
    override fun debug(msg: String, fields: Fields) {}
    override fun info(msg: String, fields: Fields) {}
    override fun warn(msg: String, fields: Fields, cause: Throwable?) {}
    override fun error(msg: String, fields: Fields, cause: Throwable?) {}
}
