package setting

/**
 * system 模块自己的全局配置定义（SYSTEM_CONFIG_SPEC）。
 *
 * 目前只有站点品牌：用户端页面左上角、登录页、页脚与浏览器标题里的产品名。
 * 放在 system 而不是某个前端仓里写死，是为了让运营在后台「配置管理」里改名即生效，
 * 不用重新构建前端。匿名读取走 [controller.admin.setting.SitePublicController]。
 */
object SystemSettingKeys {

    const val CATEGORY = "site"

    val SITE_NAME: SettingDefinition<String> = SettingDefinition.string(
        category = CATEGORY,
        key = "site.name",
        default = "Neton",
        name = "站点名称",
        description = "用户端左上角、登录/注册页与浏览器标题里显示的产品名，最多 32 个字符。",
        maxLength = 32,
    )

    val SITE_TAGLINE: SettingDefinition<String> = SettingDefinition.string(
        category = CATEGORY,
        key = "site.tagline",
        default = "",
        name = "站点副标题",
        description = "显示在站点名称下方的一行小字（如 API GATEWAY），留空则不显示，最多 32 个字符。",
        allowBlank = true,
        maxLength = 32,
    )

    val definitions: List<SettingDefinition<*>> = listOf(SITE_NAME, SITE_TAGLINE)
}
