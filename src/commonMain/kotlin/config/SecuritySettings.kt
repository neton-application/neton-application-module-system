package config

import neton.core.component.NetonContext
import neton.core.config.ConfigLoader
import neton.security.jwt.JwtAuthenticator

private const val APP_CONFIG_PATH = "config"
private const val JWT_SECRET_PATH = "security.jwt.secretKey"
private const val JWT_HEADER_NAME_PATH = "security.jwt.headerName"
private const val JWT_TOKEN_PREFIX_PATH = "security.jwt.tokenPrefix"
private const val SUPER_ADMIN_CODES_PATH = "security.super_admin_codes"
private val DEFAULT_SUPER_ADMIN_CODES = setOf("super_admin")

data class JwtRuntimeConfig(
    val secretKey: String,
    val headerName: String,
    val tokenPrefix: String
)

fun loadJwtRuntimeConfig(ctx: NetonContext): JwtRuntimeConfig {
    val appConfig = ConfigLoader.loadApplicationConfig(
        configPath = APP_CONFIG_PATH,
        environment = ConfigLoader.resolveEnvironment(ctx.args),
        args = ctx.args
    )

    val secretKey = requireUsableJwtSecret(ConfigLoader.getString(appConfig, JWT_SECRET_PATH))

    val headerName = ConfigLoader.getString(appConfig, JWT_HEADER_NAME_PATH)?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?: "Authorization"

    val tokenPrefix = ConfigLoader.getString(appConfig, JWT_TOKEN_PREFIX_PATH)?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?: "Bearer "

    return JwtRuntimeConfig(
        secretKey = secretKey,
        headerName = headerName,
        tokenPrefix = tokenPrefix
    )
}

/**
 * JWT 签名密钥的启动校验（评审 R03）。
 *
 * 仓库里的配置示例只放占位值，真实密钥必须由环境变量 [JWT_SECRET_ENV] 注入。占位值本身是
 * 公开的，拿它签名等于谁都能伪造令牌，所以**任何环境**下检测到占位值或过短的密钥都拒绝启动，
 * 而不是只在「生产」拒绝 —— 环境名默认就是 dev，靠它区分会让漏配的生产照样带病启动。
 */
internal fun requireUsableJwtSecret(raw: String?): String {
    val secret = raw?.trim().orEmpty()
    check(secret.isNotEmpty()) { "JWT secret is not configured. Set $JWT_SECRET_ENV." }
    check(!secret.contains("REPLACE_ME", ignoreCase = true)) {
        "JWT secret is still the placeholder from the sample config. Set $JWT_SECRET_ENV to a random value of at least $MIN_JWT_SECRET_LENGTH characters."
    }
    check(secret.length >= MIN_JWT_SECRET_LENGTH) {
        "JWT secret is too short (${secret.length} characters). Use at least $MIN_JWT_SECRET_LENGTH random characters in $JWT_SECRET_ENV."
    }
    return secret
}

/** 与框架配置覆盖规则一致：`NETON_` 前缀，`__` 表示一级。 */
const val JWT_SECRET_ENV = "NETON_SECURITY__JWT__SECRETKEY"

/** HS256 的密钥至少要有哈希输出那么长（32 字节）。 */
const val MIN_JWT_SECRET_LENGTH = 32

fun buildJwtAuthenticator(ctx: NetonContext): JwtAuthenticator {
    val runtimeConfig = loadJwtRuntimeConfig(ctx)
    return JwtAuthenticator(
        secretKey = runtimeConfig.secretKey,
        headerName = runtimeConfig.headerName,
        tokenPrefix = runtimeConfig.tokenPrefix
    )
}

/**
 * 加载超级管理员角色 code 集合（rbac-spec §7）。
 *
 * 配置：`security.super_admin_codes = ["super_admin"]`
 *   - 未配置：返回默认 `["super_admin"]`
 *   - 空数组：返回空集合 —— 明确意味着**禁用超级管理员判定**（rbac-spec §7.4）
 *   - 类型错误（非字符串数组）：抛出，启动 fail-fast
 */
fun loadSuperAdminCodes(ctx: NetonContext): Set<String> {
    val appConfig = ConfigLoader.loadApplicationConfig(
        configPath = APP_CONFIG_PATH,
        environment = ConfigLoader.resolveEnvironment(ctx.args),
        args = ctx.args
    )
    val raw = ConfigLoader.getConfigValue(appConfig, SUPER_ADMIN_CODES_PATH)
        ?: return DEFAULT_SUPER_ADMIN_CODES

    val list = raw as? List<*>
        ?: throw IllegalStateException(
            "$SUPER_ADMIN_CODES_PATH must be an array of strings, got: ${raw::class.simpleName}"
        )

    return list.map {
        (it as? String)?.trim()
            ?: throw IllegalStateException(
                "$SUPER_ADMIN_CODES_PATH must contain only strings, got: ${it?.let { v -> v::class.simpleName } ?: "null"}"
            )
    }.filter { it.isNotEmpty() }.toSet()
}

/**
 * 验证码发送策略（`[security.verification]`）。
 *
 * - `simulate`：没有短信 / 邮件通道时只存不发，仅供本地联调；生产必须为 false（默认）。
 * - `resend_cooldown_seconds`：同一接收方两次发码的最短间隔，默认 60。
 * - `daily_limit_per_recipient`：同一接收方每天最多发码次数，默认 10。
 * - `daily_limit_global`：每个通道全站每天最多发码条数，0（默认）= 不限；生产建议按短信预算设置。
 */
fun loadVerificationSettings(ctx: NetonContext): logic.VerificationCodeService.Settings {
    val appConfig = ConfigLoader.loadApplicationConfig(
        configPath = APP_CONFIG_PATH,
        environment = ConfigLoader.resolveEnvironment(ctx.args),
        args = ctx.args
    )
    val defaults = logic.VerificationCodeService.Settings()
    fun long(path: String, fallback: Long): Long =
        if (ConfigLoader.hasConfig(appConfig, path)) ConfigLoader.getInt(appConfig, path).toLong() else fallback
    return logic.VerificationCodeService.Settings(
        simulate = ConfigLoader.hasConfig(appConfig, "security.verification.simulate") &&
            ConfigLoader.getBoolean(appConfig, "security.verification.simulate"),
        resendCooldownSeconds = long("security.verification.resend_cooldown_seconds", defaults.resendCooldownSeconds),
        dailyLimitPerRecipient = long("security.verification.daily_limit_per_recipient", defaults.dailyLimitPerRecipient),
        dailyLimitGlobal = long("security.verification.daily_limit_global", defaults.dailyLimitGlobal),
    )
}

/**
 * 发行版的数据库支持矩阵（评审 R17）：**只支持 PostgreSQL**。
 *
 * 各模块的迁移脚本只有 `sql/postgresql/`，MFA、会话版本等也用到 PostgreSQL 的
 * `ON CONFLICT` / `FOR UPDATE`。配成别的驱动时表根本建不出来，不能让它带着残缺的表启动，
 * 所以在 system 初始化时直接拒绝，并说清楚原因。
 */
fun requireSupportedDatabase(ctx: NetonContext) {
    val config = ConfigLoader.loadModuleConfig(
        moduleName = "database",
        configPath = APP_CONFIG_PATH,
        environment = ConfigLoader.resolveEnvironment(ctx.args),
        args = ctx.args,
    ) ?: return
    @Suppress("UNCHECKED_CAST")
    val driver = ((config["default"] as? Map<String, Any?>)?.get("driver") as? String)?.trim()?.uppercase() ?: return
    check(driver == "POSTGRESQL") {
        "Unsupported database driver '$driver'. This distribution supports PostgreSQL only: module migrations " +
            "exist only for PostgreSQL and security features rely on its locking semantics. Set [default] driver = \"POSTGRESQL\" in database.conf."
    }
}
