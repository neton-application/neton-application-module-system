package config

import neton.core.component.NetonContext
import neton.core.config.NetonConfig
import neton.core.config.NetonConfigurer
import neton.core.interfaces.SecurityBuilder
import neton.logging.LoggerFactory
import neton.security.jwt.JwtAuthenticator
import neton.security.jwt.UnifiedTokenAuthenticator
import security.AdminTokenRealm
import security.RealmAuthenticator
import security.TokenRealmRegistry
import security.WildcardPermissionEvaluator

/**
 * 所有发行版共用的 HTTP 鉴权装配。
 *
 * 此前每个发行版各自复制一份 SecurityConfig + 认证桥，四份已经漂移：有的没有管理员令牌
 * 校验、没有 MFA 门禁，权限评估器也各不相同。鉴权是管理员安全的基础能力，归 system；
 * 发行版只负责装配模块，不再自带鉴权代码。
 *
 * - 本应用 HS256 令牌按身份域限定使用范围（[security.TokenRealm]）。管理员域在这里登记，
 *   账号域由 account 模块在自己的 bootstrap 里登记。
 * - 外部统一令牌（RS256/JWKS）由提供它的产品模块（如 privchat，order=-10）bind
 *   [UnifiedTokenAuthenticator]；只有声明接受外部令牌的域才会用它。
 */
@NetonConfig("security", order = 0)
class SystemSecurityConfig : NetonConfigurer<SecurityBuilder> {
    override fun configure(ctx: NetonContext, target: SecurityBuilder) {
        val jwtConfig = loadJwtRuntimeConfig(ctx)
        val unifiedConfig = loadUnifiedTokenRuntimeConfig(ctx)

        // 签发方（system 的 AuthLogic、account 的内置适配器）在各自 bootstrap 里复用这一个实例。
        val legacy = ctx.getOrNull(JwtAuthenticator::class) ?: JwtAuthenticator(
            secretKey = jwtConfig.secretKey,
            headerName = jwtConfig.headerName,
            tokenPrefix = jwtConfig.tokenPrefix,
        ).also { ctx.bind(JwtAuthenticator::class, it) }
        val unified = ctx.getOrNull(UnifiedTokenAuthenticator::class)
        check(!unifiedConfig.enabled || unified != null) {
            "security.unified_token.enabled=true but no module bound UnifiedTokenAuthenticator. " +
                "Either install a module that provides it (it must run at security order < 0) or set enabled=false."
        }

        val realms = ctx.getOrNull(TokenRealmRegistry::class) ?: TokenRealmRegistry().also {
            ctx.bind(TokenRealmRegistry::class, it)
        }
        // 依赖都在 system 的 bootstrap 里创建（晚于本阶段），请求期再取。
        realms.register(
            AdminTokenRealm(
                sessions = { ctx.get(logic.AdminSessionLogic::class) },
                revocation = { ctx.get(security.SessionRevocation::class) },
                authorization = { userId -> ctx.get(logic.PermissionLogic::class).resolveAuthorization(userId) },
            ),
        )

        target.registerAuthenticator(
            RealmAuthenticator(
                legacy = legacy,
                unified = unified.takeIf { unifiedConfig.enabled },
                realms = realms,
                headerName = jwtConfig.headerName,
                tokenPrefix = jwtConfig.tokenPrefix,
                sessionCookieName = SessionCookie.name(ctx),
                log = ctx.getOrNull(LoggerFactory::class)?.get("security.realm"),
            ),
        )
        target.bindDefaultGuard()
        // Role-to-permission resolution belongs to module-system, including custom super-admin codes.
        target.setPermissionEvaluator(WildcardPermissionEvaluator())
    }
}
