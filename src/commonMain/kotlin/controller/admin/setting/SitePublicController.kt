package controller.admin.setting

import kotlinx.serialization.Serializable
import logic.SystemSettingLogic
import neton.core.annotations.AllowAnonymous
import neton.core.annotations.Controller
import neton.core.annotations.Get
import setting.SystemSettingKeys

@Serializable
data class SitePublicVO(val name: String, val tagline: String, val captcha: logic.CaptchaPublicVO)

/**
 * 站点品牌的匿名读取：登录页在登录之前就要显示产品名，所以不能要求身份。
 * 只暴露 [SystemSettingKeys] 里明确属于「对外展示」的项，不是通用的配置读取口——
 * 别的配置可能是内部开关，不该匿名可读。
 *
 * 人机验证的公开部分（供应商、站点 key、需要验证的动作）也从这里下发：登录、注册页渲染前就要知道。
 */
@Controller("/system/site")
@AllowAnonymous
class SitePublicController(
    private val configLogic: SystemSettingLogic,
    private val captcha: logic.CaptchaService,
) {

    @Get("/public")
    suspend fun public(): SitePublicVO = SitePublicVO(
        name = configLogic.get(SystemSettingKeys.SITE_NAME),
        tagline = configLogic.get(SystemSettingKeys.SITE_TAGLINE),
        captcha = captcha.publicConfig(),
    )
}
