package logic

import setting.SettingDefinition
import setting.SettingDefinitionRegistry
import model.SystemSetting
import neton.database.api.DbContext
import neton.database.dsl.*
import neton.logging.Logger
import table.SystemSettingTable

/**
 * 全局配置读写（SYSTEM_CONFIG_SPEC）。
 *
 * 读取只提供 [get]：**拿定义读，不拿字符串 key 读**。这样 key 打错编译不过、
 * 类型不会错、且一定有值 —— 三者都是运行期查起来很贵的问题。
 *
 * 不用 `@Logic`：它依赖 [SettingDefinitionRegistry]，而 KSP 的装配顺序是
 * `configs → logics(@Logic) → RuntimeBootstrap`，注册表要到 bootstrap 才齐。
 */
class SystemSettingLogic(
    private val log: Logger,
    private val db: DbContext,
    private val registry: SettingDefinitionRegistry,
) {

    /**
     * 读配置。读不到、值为空、解析失败或越界都退回定义里的默认值，所以返回非空。
     *
     * 这是刻意的：配置缺失只应意味着「后台还没改过」，不该让功能不可用；
     * 后台是自由文本输入，一个手滑的空格不该让业务接口 500。
     */
    suspend fun <T : Any> get(definition: SettingDefinition<T>): T {
        val row = SystemSettingTable.oneWhere { SystemSetting::settingKey eq definition.key }
            ?: return definition.default
        val parsed = definition.parse(row.value)
        if (parsed == null) {
            log.warn("system.config.unparsable key=${definition.key} raw=${row.value} fallback=${definition.defaultRaw}")
            return definition.default
        }
        return parsed
    }

    /** 写配置。值必须能被定义解析，否则拒绝 —— 让错误停在写入时，而不是等读取时静默退默认值。 */
    suspend fun <T : Any> set(definition: SettingDefinition<T>, raw: String): T {
        val parsed = definition.parse(raw)
            ?: throw neton.core.http.HttpException(
                neton.core.http.NetonErrorCode.INVALID_PARAMS,
                "「${definition.name}」的值不合法：${definition.description}",
            )
        val normalized = definition.render(parsed)
        val existing = SystemSettingTable.oneWhere { SystemSetting::settingKey eq definition.key }
        if (existing == null) {
            SystemSettingTable.insert(
                SystemSetting(
                    category = definition.category,
                    settingKey = definition.key,
                    value = normalized,
                    valueType = definition.valueType.ordinal,
                    name = definition.name,
                    remark = definition.description,
                )
            )
        } else {
            SystemSettingTable.update(existing.copy(value = normalized))
        }
        log.info("system.config.updated key=${definition.key} value=$normalized")
        return parsed
    }

    /** 按 key 写。给后台用——它拿到的是字符串 key，需要先查回定义再走 [set] 的校验。 */
    suspend fun setByKey(key: String, raw: String) {
        val definition = registry.find(key)
            ?: throw neton.core.http.HttpException(
                neton.core.http.NetonErrorCode.RESOURCE_NOT_FOUND,
                "未定义的配置项：$key。配置必须先在代码里声明，库里出现无定义的行等于没人知道它做什么",
            )
        @Suppress("UNCHECKED_CAST")
        set(definition as SettingDefinition<Any>, raw)
    }

    /**
     * 把缺失的定义按默认值写入库。**已存在的不覆盖** ——
     * 运营改过的值不能被一次发版重置回默认。
     */
    suspend fun syncDefinitions(): Int {
        val existing = SystemSettingTable.findAll().map { it.settingKey }.toSet()
        var created = 0
        for (definition in registry.definitions) {
            if (definition.key in existing) continue
            SystemSettingTable.insert(
                SystemSetting(
                    category = definition.category,
                    settingKey = definition.key,
                    value = definition.defaultRaw,
                    valueType = definition.valueType.ordinal,
                    name = definition.name,
                    remark = definition.description,
                )
            )
            created++
        }
        if (created > 0) log.info("system.config.synced created=$created")
        return created
    }

    suspend fun list(category: String? = null): List<SystemSetting> =
        SystemSettingTable.query {
            where { and(whenNotBlank(category) { SystemSetting::category eq it }) }
            orderBy(SystemSetting::category.asc(), SystemSetting::settingKey.asc())
        }.list()

    /** 定义清单：后台据此渲染控件与说明，并禁止手写任意 key。 */
    fun definitions(): List<SettingDefinition<*>> = registry.definitions
}
