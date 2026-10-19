package setting

/**
 * system 模块自己的全局配置定义（SYSTEM_CONFIG_SPEC）。
 *
 * 站点品牌：用户端页面左上角、登录页、页脚与浏览器标题里的产品名；
 * 人机验证：开关、公开站点 key 与需要验证的动作（spec auth-abuse-protection §6.1）。
 * 供应商 secret 不在这里 —— 配置表后台可读，secret 只从环境变量 `NETON_CAPTCHA_SECRET` 来。
 * 放在 system 而不是某个前端仓里写死，是为了让运营在后台「配置管理」里改名即生效，
 * 不用重新构建前端。匿名读取走 [controller.admin.setting.SitePublicController]。
 */
object SystemSettingKeys {

    const val CATEGORY = "system"

    val SITE_NAME: SettingDefinition<String> = SettingDefinition.string(
        category = CATEGORY,
        key = "system.site.name",
        default = "Neton",
        name = "站点名称",
        description = "用户端左上角、登录/注册页与浏览器标题里显示的产品名，最多 32 个字符。",
        maxLength = 32,
    )

    val SITE_TAGLINE: SettingDefinition<String> = SettingDefinition.string(
        category = CATEGORY,
        key = "system.site.tagline",
        default = "",
        name = "站点副标题",
        description = "显示在站点名称下方的一行小字（如 API GATEWAY），留空则不显示，最多 32 个字符。",
        allowBlank = true,
        maxLength = 32,
    )

    val CAPTCHA_ENABLED: SettingDefinition<Boolean> = SettingDefinition.boolean(
        category = CATEGORY,
        key = "system.security.captcha.enabled",
        default = false,
        name = "人机验证",
        description = "打开后，下面列出的动作要先通过 Cloudflare Turnstile 验证。需同时配置站点 key 与服务端环境变量 NETON_CAPTCHA_SECRET，" +
            "缺 secret 时这些动作一律拒绝（不会静默放行）。",
    )

    val CAPTCHA_SITE_KEY: SettingDefinition<String> = SettingDefinition.string(
        category = CATEGORY,
        key = "system.security.captcha.site_key",
        default = "",
        name = "人机验证站点 key",
        description = "Turnstile 后台给出的 site key（公开值，会下发给浏览器）。",
        allowBlank = true,
        maxLength = 128,
    )

    val CAPTCHA_ACTIONS: SettingDefinition<String> = SettingDefinition.string(
        category = CATEGORY,
        key = "system.security.captcha.actions",
        default = "register,send_code",
        name = "需要人机验证的动作",
        description = "逗号分隔：register 注册、send_code 匿名发送短信/邮件验证码、login 用户登录、admin_login 管理员登录。",
        allowBlank = true,
        maxLength = 128,
    )

    val definitions: List<SettingDefinition<*>> = listOf(SITE_NAME, SITE_TAGLINE, CAPTCHA_ENABLED, CAPTCHA_SITE_KEY, CAPTCHA_ACTIONS)
}
