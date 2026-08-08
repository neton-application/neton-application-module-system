package controller.admin.setting

import controller.admin.setting.dto.SystemSettingVO
import controller.admin.setting.dto.UpdateSystemSettingRequest
import logic.SystemSettingLogic
import neton.core.annotations.Body
import neton.core.annotations.Controller
import neton.core.annotations.Get
import neton.core.annotations.Permission
import neton.core.annotations.Post
import neton.core.annotations.Query

/**
 * 配置管理（SYSTEM_CONFIG_SPEC §4）。
 *
 * 列表以**代码里的定义**为准，库里的行只提供当前值 —— 这样后台永远看得到全部可配项，
 * 包括还没同步入库的；反过来，库里若有无定义的行，它不会出现在这里，也改不动。
 */
@Controller("/system/setting")
class SystemSettingController(
    private val configLogic: SystemSettingLogic,
) {

    @Get("/list")
    @Permission("system:setting:query")
    suspend fun list(@Query category: String? = null): List<SystemSettingVO> {
        val stored = configLogic.list(category).associateBy { it.settingKey }
        return configLogic.definitions()
            .filter { category.isNullOrBlank() || it.category == category }
            .map { definition ->
                val current = stored[definition.key]?.value ?: definition.defaultRaw
                SystemSettingVO(
                    category = definition.category,
                    key = definition.key,
                    value = current,
                    valueType = definition.valueType.ordinal,
                    name = definition.name,
                    description = definition.description,
                    defaultValue = definition.defaultRaw,
                    isDefault = current == definition.defaultRaw,
                )
            }
    }

    /** 改值。不合法直接拒 —— 让错误停在写入时，而不是等读取时静默退回默认。 */
    @Post("/update")
    @Permission("system:setting:update")
    suspend fun update(@Body request: UpdateSystemSettingRequest) {
        configLogic.setByKey(request.key, request.value)
    }

    /** 把缺失定义按默认值写入库；已存在的不覆盖，运营改过的值不会被发版重置。 */
    @Post("/sync")
    @Permission("system:setting:update")
    suspend fun sync(): Int = configLogic.syncDefinitions()
}
