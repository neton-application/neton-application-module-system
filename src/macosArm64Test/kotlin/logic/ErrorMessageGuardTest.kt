package logic

import contract.testing.ErrorMessageGuard
import kotlin.test.Test

/**
 * system 的业务错误 message 必须是 [SystemErrorCodes] 里的机器码（spec application/system-errors）。
 * 除框架异常外，还检查本模块自己的异常子类和 `@RateLimit` 的 message（它会原样进 429 响应）。
 */
class ErrorMessageGuardTest {
    @Test
    fun error_messages_are_machine_codes() {
        ErrorMessageGuard.assertModuleClean(
            allowlist = setOf(
                // 子类声明把构造参数原样交给父类；调用方的实参由下面的 ProviderNotImplementedException 条目检查。
                "kotlin/logic/provider/ProviderExceptions.kt#HttpException#message",
                // 字段错误构造器本身：参数原样放进 ValidationError；调用方的实参由下面的 SystemErrorCodes.field 条目检查。
                "kotlin/logic/SystemErrorCodes.kt#ValidationError#code",
            ),
            constructors = ErrorMessageGuard.DEFAULT_CONSTRUCTORS + mapOf(
                "ProviderNotImplementedException" to listOf(0),
                "UnauthorizedException" to listOf(0),
                "SystemErrorCodes.field" to listOf(1),
            ),
        )
    }

    @Test
    fun rate_limit_messages_are_machine_codes() {
        // @RateLimit 的参数全是具名的；下标 1 对应具名参数 `message`，缺省（框架默认英文句子）同样算违规。
        ErrorMessageGuard.assertModuleClean(constructors = mapOf("RateLimit" to listOf(1)))
    }
}
