package init

import neton.core.component.NetonContext
import neton.logging.LoggerFactory
import neton.security.jwt.JwtAuthenticator
import config.buildJwtAuthenticator
import config.loadSuperAdminCodes
import security.CodeMatchSuperAdminEvaluator
import security.SuperAdminEvaluator

import logic.*
import logic.provider.*

// MANIFEST-P3: 手写 runtime bootstrap。9 个纯单-Logger logic 已标 @Logic →
// 生成的 SystemLogicInitializer 装配 (manifest 顺序: logics → 本 bootstrap → routes)。
// moduleId/路由 由 KSP manifest; system 不持有 migration (DB-MIG-7A: SQL 合并到 infra)。
// 这里留: jwt + superAdmin evaluator + providers + 7 个带依赖的复杂 logic
// (MessageChannel/MessageSend/SocialUser/NotificationTemplate/Auth/Permission/Dict)。
object SystemRuntimeBootstrap {

    fun initialize(ctx: NetonContext) {
        val loggerFactory = ctx.get(LoggerFactory::class)
        // ===== 创建共享服务 =====
        val jwt = ctx.getOrNull(JwtAuthenticator::class) ?: buildJwtAuthenticator(ctx)
        ctx.bind(JwtAuthenticator::class, jwt)

        // 配置定义注册表：装配层可覆盖（bind 一个含各模块定义的实例）；
        // 这里 bindIfAbsent 保证未注册任何定义的部署也能启动，读取一律退回代码默认值。
        // 空注册表先绑上，业务模块在各自 bootstrap 里往里 register；
        // SystemSettingLogic 持有的是同一个实例，所以后注册的定义它也看得到。
        val configRegistry = setting.SettingDefinitionRegistry()
        ctx.bind(setting.SettingDefinitionRegistry::class, configRegistry)
        ctx.bind(
            logic.SystemSettingLogic::class,
            logic.SystemSettingLogic(
                log = loggerFactory.get("logic.system.config"),
                db = ctx.get(neton.database.api.DbContext::class),
                registry = configRegistry,
            ),
        )

        // SuperAdminEvaluator —— rbac-spec §7。启动日志只打 count, 不打具体 codes。
        val superAdminCodes = loadSuperAdminCodes(ctx)
        val securityLog = loggerFactory.get("security")
        securityLog.info("security.super_admin_codes.loaded count=${superAdminCodes.size}")
        val superAdminEvaluator: SuperAdminEvaluator = CodeMatchSuperAdminEvaluator(superAdminCodes)
        ctx.bind(SuperAdminEvaluator::class, superAdminEvaluator)

        // ===== 创建 Provider =====
        val outboundHttp = ctx.getOrNull(neton.http.client.HttpClient::class) ?: error(
            "module-system needs an HttpClient bound in NetonContext (SmsProvider). Build one in the " +
                "application with HttpClient.create { } and bind(HttpClient::class, it).",
        )
        val smsProvider = SmsProvider(loggerFactory.get("provider.sms"), outboundHttp)
        val emailProvider = EmailProvider(loggerFactory.get("provider.email"))
        val messageProviders = mapOf<String, MessageProvider>("sms" to smsProvider, "email" to emailProvider)

        val googleProvider = GoogleSocialProvider(loggerFactory.get("provider.google"))
        val telegramProvider = TelegramSocialProvider(loggerFactory.get("provider.telegram"))
        val socialProviders = mapOf<String, SocialProvider>("google" to googleProvider, "telegram" to telegramProvider)

        // ===== 带依赖的复杂 Logic (非 @Logic: provider map / nullable redis /
        //       inter-logic 依赖 / inline SimpleCache) =====
        // MessageTemplateLogic 已是 @Logic → ctx.get 取用。
        val messageTemplateLogic = ctx.get(MessageTemplateLogic::class)
        val messageChannelLogic = MessageChannelLogic(loggerFactory.get("logic.message-channel"), messageProviders)
        val redis = ctx.getOrNull(neton.redis.RedisClient::class)
        val messageSendLogic = MessageSendLogic(loggerFactory.get("logic.message-send"), messageChannelLogic, messageTemplateLogic, redis)
        val socialUserLogic = SocialUserLogic(loggerFactory.get("logic.social-user"), socialProviders)
        val notificationTemplateLogic = NotificationTemplateLogic(loggerFactory.get("logic.notification-template"), messageSendLogic)

        ctx.bind(MessageChannelLogic::class, messageChannelLogic)
        ctx.bind(MessageSendLogic::class, messageSendLogic)
        ctx.bind(SocialUserLogic::class, socialUserLogic)
        ctx.bind(NotificationTemplateLogic::class, notificationTemplateLogic)

        val permissionLogic = PermissionLogic(
            loggerFactory.get("logic.permission"),
            superAdminEvaluator,
            ctx.get(neton.database.api.DbContext::class),
        )
        ctx.bind(PermissionLogic::class, permissionLogic)
        // AuthLogic 登录时用 permissionLogic 解析权限写进 JWT（P0 granular RBAC）。
        ctx.bind(AuthLogic::class, AuthLogic(loggerFactory.get("logic.auth"), jwt, permissionLogic, messageSendLogic, socialUserLogic))
        ctx.bind(DictLogic::class, DictLogic(loggerFactory.get("logic.dict"), infra.SimpleCache()))
    }

}
