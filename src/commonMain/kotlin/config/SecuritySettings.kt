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

    val secretKey = ConfigLoader.getString(appConfig, JWT_SECRET_PATH)?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?: throw IllegalStateException(
            "Missing JWT secret. Configure security.jwt.secretKey or NETON__SECURITY__JWT__SECRET_KEY."
        )

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
