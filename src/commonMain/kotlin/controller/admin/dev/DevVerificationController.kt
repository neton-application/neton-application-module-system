package controller.admin.dev

import logic.SystemErrorCodes
import logic.VerificationCodeService
import neton.core.annotations.Controller
import neton.core.annotations.Get
import neton.core.annotations.Permission
import neton.core.annotations.Query
import neton.core.component.NetonContext
import neton.core.http.BadRequestException
import neton.core.http.NotFoundException

/**
 * 开发取码接口（spec auth-abuse-protection §5.2）：读模拟收件箱里某个接收方最近一条验证码，供本地联调与 E2E 使用。
 *
 * 只在模拟通道打开时可用，而模拟通道只能在显式声明的 dev / local / test 环境打开（启动时校验）。
 * 模拟未开启时恒 404（与查不到记录同一个码，不透露开关状态）。读取另需管理员登录与 [PERMISSION]。
 *
 * `receiver` 按发码时的规范形式：短信为 E.164（URL 里的 `+` 要写成 `%2B`），邮件大小写不敏感。
 * 可选 `realm` / `purpose` 用来断言最近一条码确实来自预期的身份域与用途，不一致同样 404。
 */
@Controller("/system/dev/verification")
class DevVerificationController(private val ctx: NetonContext) {

    @Get("/latest")
    @Permission(PERMISSION)
    suspend fun latest(
        @Query channel: String? = null,
        @Query receiver: String? = null,
        @Query realm: String? = null,
        @Query purpose: String? = null,
    ): VerificationCodeService.SimulatedCode {
        // 先判模拟开关：未开启时连参数校验都不做，一律 404。
        val codes = ctx.getOrNull(VerificationCodeService::class)?.takeIf { it.simulationActive } ?: throw NotFoundException(SystemErrorCodes.SIMULATED_CODE_NOT_FOUND)
        val ch = VerificationCodeService.Channel.entries.firstOrNull { it.key == channel?.trim()?.lowercase() }
            ?: throw BadRequestException(SystemErrorCodes.VERIFICATION_CHANNEL_INVALID, SystemErrorCodes.field("channel", SystemErrorCodes.VERIFICATION_CHANNEL_INVALID))
        if (receiver.isNullOrBlank()) throw BadRequestException(SystemErrorCodes.RECEIVER_REQUIRED, SystemErrorCodes.field("receiver", SystemErrorCodes.RECEIVER_REQUIRED))
        val record = codes.latestSimulated(ch, receiver) ?: throw NotFoundException(SystemErrorCodes.SIMULATED_CODE_NOT_FOUND)
        if (!realm.isNullOrBlank() && record.realm != realm.trim()) throw NotFoundException(SystemErrorCodes.SIMULATED_CODE_NOT_FOUND)
        if (!purpose.isNullOrBlank() && record.purpose != purpose.trim()) throw NotFoundException(SystemErrorCodes.SIMULATED_CODE_NOT_FOUND)
        return record
    }

    companion object {
        const val PERMISSION = "system:dev:verification:query"
    }
}
