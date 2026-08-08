package config

/** 配置值类型。决定后台用什么控件、以及怎么解析。 */
enum class ConfigValueType { STRING, INT, LONG, BOOLEAN, DECIMAL, JSON }

/**
 * 一个可由后台调整的全局配置项（SYSTEM_CONFIG_SPEC §2）。
 *
 * 四样东西全部必填：**key、默认值、值类型、说明**。没有说明的配置等于没有配置——
 * 半年后没人知道它改了会怎样，也就没人敢动它。
 *
 * 泛型带住类型，是为了让读取端 [ConfigReader.get] 直接返回 [T] 而不是字符串：
 * key 打错编译不过，类型不会错，且一定有值。
 */
class ConfigDefinition<T : Any> private constructor(
    val category: String,
    val key: String,
    val valueType: ConfigValueType,
    val default: T,
    val name: String,
    val description: String,
    private val parser: (String) -> T?,
    private val renderer: (T) -> String,
) {
    /** 解析失败返回 null，由读取方退回 [default]——后台是自由文本，手滑不该让接口 500。 */
    fun parse(raw: String): T? = runCatching { parser(raw.trim()) }.getOrNull()

    fun render(value: T): String = renderer(value)

    /** 默认值的字符串形式，同步入库时用 */
    val defaultRaw: String get() = renderer(default)

    override fun toString(): String = "ConfigDefinition($key: $valueType = $defaultRaw)"

    companion object {
        fun string(
            category: String,
            key: String,
            default: String,
            name: String,
            description: String,
        ): ConfigDefinition<String> = ConfigDefinition(
            category, key, ConfigValueType.STRING, default, name, description,
            parser = { it.ifEmpty { null } },
            renderer = { it },
        )

        /**
         * [min]/[max] 是**读取期**的护栏而不只是后台校验：后台可能被绕过（直接改库、
         * 数据搬迁），越界值到了业务代码里往往比缺失更难查，所以在这里就挡掉，
         * 让它退回默认值。
         */
        fun int(
            category: String,
            key: String,
            default: Int,
            name: String,
            description: String,
            min: Int = Int.MIN_VALUE,
            max: Int = Int.MAX_VALUE,
        ): ConfigDefinition<Int> {
            require(default in min..max) { "配置 $key 的默认值 $default 不在 [$min, $max] 内" }
            return ConfigDefinition(
                category, key, ConfigValueType.INT, default, name, description,
                parser = { it.toIntOrNull()?.takeIf { v -> v in min..max } },
                renderer = { it.toString() },
            )
        }

        fun long(
            category: String,
            key: String,
            default: Long,
            name: String,
            description: String,
            min: Long = Long.MIN_VALUE,
            max: Long = Long.MAX_VALUE,
        ): ConfigDefinition<Long> {
            require(default in min..max) { "配置 $key 的默认值 $default 不在 [$min, $max] 内" }
            return ConfigDefinition(
                category, key, ConfigValueType.LONG, default, name, description,
                parser = { it.toLongOrNull()?.takeIf { v -> v in min..max } },
                renderer = { it.toString() },
            )
        }

        /** 接受 true/false/1/0/yes/no，大小写不敏感——后台填什么的都有。 */
        fun boolean(
            category: String,
            key: String,
            default: Boolean,
            name: String,
            description: String,
        ): ConfigDefinition<Boolean> = ConfigDefinition(
            category, key, ConfigValueType.BOOLEAN, default, name, description,
            parser = {
                when (it.lowercase()) {
                    "true", "1", "yes", "on" -> true
                    "false", "0", "no", "off" -> false
                    else -> null
                }
            },
            renderer = { it.toString() },
        )

        /** 金额等定点数：存字符串避免二进制浮点误差，读取方自行转 BigDecimal 等价物。 */
        fun decimal(
            category: String,
            key: String,
            default: String,
            name: String,
            description: String,
        ): ConfigDefinition<String> = ConfigDefinition(
            category, key, ConfigValueType.DECIMAL, default, name, description,
            parser = { raw -> raw.takeIf { it.matches(Regex("^-?\\d+(\\.\\d+)?$")) } },
            renderer = { it },
        )

        /** 值本身是 JSON 文本；这里只保证非空，结构由读取方反序列化时负责。 */
        fun json(
            category: String,
            key: String,
            default: String,
            name: String,
            description: String,
        ): ConfigDefinition<String> = ConfigDefinition(
            category, key, ConfigValueType.JSON, default, name, description,
            parser = { it.ifEmpty { null } },
            renderer = { it },
        )
    }
}

/**
 * 配置定义注册中心。
 *
 * **可追加**而不是构造时定死：各模块在自己的 bootstrap 里 [register] 自己的定义，
 * 而模块的初始化顺序是 system 在前、业务模块在后。若做成不可变的、由构造参数传入，
 * system 早于所有业务模块拿到的必然是一张空表 —— 这个错误不会报错，
 * 只表现为「后台配置页里看不到任何业务模块的配置」。
 *
 * 注册时查重：两个模块用同一个 key 会互相覆盖，运行期只表现为「我改了配置但没生效」，
 * 极难定位，所以宁可启动失败。
 */
class ConfigDefinitionRegistry {

    private val byKey = mutableMapOf<String, ConfigDefinition<*>>()

    val definitions: List<ConfigDefinition<*>> get() = byKey.values.toList()

    fun register(definitions: List<ConfigDefinition<*>>) {
        for (definition in definitions) {
            val existing = byKey[definition.key]
            check(existing == null || existing === definition) {
                "配置 key 重复：${definition.key}（${existing?.category} 与 ${definition.category}）" +
                    " —— 两个模块用同一个 key 会互相覆盖，且运行期只表现为「改了没生效」"
            }
            byKey[definition.key] = definition
        }
    }

    fun find(key: String): ConfigDefinition<*>? = byKey[key]

    fun byCategory(category: String): List<ConfigDefinition<*>> =
        definitions.filter { it.category == category }
}
