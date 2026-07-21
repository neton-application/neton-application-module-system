package config

import neton.core.component.NetonContext
import neton.core.config.ConfigLoader

/**
 * Unified token 运行时配置（spec TOKEN_UNIFICATION_SPEC v1.3 §10.1）。
 *
 * **Phase A 默认值锁定**（绝对不能改成开启状态而误进生产）：
 * - `enabled = false` —— unified token 路径默认 dormant；现网 HS256 行为不变
 * - `verifyLegacyJwt = true` —— legacy member token 仍接受
 *
 * 与 `verifyLegacyJwt` 的 4 种组合在 [neton.security.jwt.DispatchingJwtAuthenticator] 里写明，
 * 其中 `(false, false)` 启动时 fail-fast。
 *
 * URL 默认值有意指向本机 9090（开发默认）；生产部署必须通过 application.conf 或 env 覆盖。
 */
public data class UnifiedTokenRuntimeConfig(
    val enabled: Boolean = false,
    val verifyLegacyJwt: Boolean = true,
    val jwksUrl: String = DEFAULT_JWKS_URL,
    val introspectUrl: String = DEFAULT_INTROSPECT_URL,
    val introspectEnabled: Boolean = true,
    val sessionCacheTtlSeconds: Long = 30,
    val jwksCacheTtlSeconds: Long = 3600,
) {
    public companion object {
        public const val DEFAULT_JWKS_URL: String = "http://localhost:9090/api/service/auth/jwks"
        public const val DEFAULT_INTROSPECT_URL: String = "http://localhost:9090/api/service/auth/introspect"

        public const val PATH_ENABLED: String = "security.unified_token.enabled"
        public const val PATH_VERIFY_LEGACY: String = "security.unified_token.verify_legacy_jwt"
        public const val PATH_JWKS_URL: String = "security.unified_token.jwks_url"
        public const val PATH_INTROSPECT_URL: String = "security.unified_token.introspect_url"
        public const val PATH_INTROSPECT_ENABLED: String = "security.unified_token.introspect_enabled"
        public const val PATH_SESSION_CACHE_TTL: String = "security.unified_token.session_cache_ttl_secs"
        public const val PATH_JWKS_CACHE_TTL: String = "security.unified_token.jwks_cache_ttl_secs"
    }
}

private const val APP_CONFIG_PATH = "config"

/**
 * 加载 unified token 配置。任何缺省字段使用 [UnifiedTokenRuntimeConfig] 的默认值。
 *
 * **不**抛异常。flag 不存在 → 默认 `enabled=false`，dormant 路径走起来。
 */
public fun loadUnifiedTokenRuntimeConfig(ctx: NetonContext): UnifiedTokenRuntimeConfig {
    val appConfig = ConfigLoader.loadApplicationConfig(
        configPath = APP_CONFIG_PATH,
        environment = ConfigLoader.resolveEnvironment(ctx.args),
        args = ctx.args,
    )
    return UnifiedTokenRuntimeConfig(
        enabled = readBool(appConfig, UnifiedTokenRuntimeConfig.PATH_ENABLED, default = false),
        verifyLegacyJwt = readBool(
            appConfig,
            UnifiedTokenRuntimeConfig.PATH_VERIFY_LEGACY,
            default = true,
        ),
        jwksUrl = ConfigLoader.getString(appConfig, UnifiedTokenRuntimeConfig.PATH_JWKS_URL)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: UnifiedTokenRuntimeConfig.DEFAULT_JWKS_URL,
        introspectUrl = ConfigLoader.getString(
            appConfig,
            UnifiedTokenRuntimeConfig.PATH_INTROSPECT_URL,
        )
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: UnifiedTokenRuntimeConfig.DEFAULT_INTROSPECT_URL,
        introspectEnabled = readBool(
            appConfig,
            UnifiedTokenRuntimeConfig.PATH_INTROSPECT_ENABLED,
            default = true,
        ),
        sessionCacheTtlSeconds = readLong(
            appConfig,
            UnifiedTokenRuntimeConfig.PATH_SESSION_CACHE_TTL,
            default = 30L,
        ),
        jwksCacheTtlSeconds = readLong(
            appConfig,
            UnifiedTokenRuntimeConfig.PATH_JWKS_CACHE_TTL,
            default = 3600L,
        ),
    )
}

/** [ConfigLoader.getBoolean] 在 path 缺失时抛异常；这里包装成"缺失返默认"。 */
private fun readBool(config: Map<out String, Any?>?, path: String, default: Boolean): Boolean {
    if (!ConfigLoader.hasConfig(config, path)) return default
    return ConfigLoader.getBoolean(config, path)
}

/** ConfigLoader 没有 getLong；用 getConfigValue 拿原值再转。 */
private fun readLong(config: Map<out String, Any?>?, path: String, default: Long): Long {
    val raw = ConfigLoader.getConfigValue(config, path) ?: return default
    return when (raw) {
        is Number -> raw.toLong()
        is String -> raw.toLongOrNull() ?: default
        else -> default
    }
}
