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
        config.requireSupportedDatabase(ctx)
        ctx.bind(neton.core.interfaces.RequestAdmissionGate::class, security.NetworkAdmission.load(ctx))
        val loggerFactory = ctx.get(LoggerFactory::class)
        // ===== 创建共享服务 =====
        val jwt = ctx.getOrNull(JwtAuthenticator::class) ?: buildJwtAuthenticator(ctx)
        ctx.bind(JwtAuthenticator::class, jwt)

        // 配置定义注册表：装配层可覆盖（bind 一个含各模块定义的实例）；
        // 这里 bindIfAbsent 保证未注册任何定义的部署也能启动，读取一律退回代码默认值。
        // 空注册表先绑上，业务模块在各自 bootstrap 里往里 register；
        // SystemSettingLogic 持有的是同一个实例，所以后注册的定义它也看得到。
        val configRegistry = setting.SettingDefinitionRegistry()
        configRegistry.register(setting.SystemSettingKeys.definitions)
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
        // 人机验证：secret 只从环境变量来；开关、站点 key、动作在后台配置（SystemSettingKeys.CAPTCHA_*）
        ctx.bind(logic.CaptchaService::class, logic.CaptchaService(
            log = loggerFactory.get("security.captcha"),
            http = outboundHttp,
            settings = ctx.get(logic.SystemSettingLogic::class),
            secret = neton.core.config.getEnv(logic.CaptchaService.ENV_SECRET),
            verifyUrl = neton.core.config.getEnv(logic.CaptchaService.ENV_VERIFY_URL)?.trim()?.takeIf { it.isNotEmpty() }
                ?: logic.CaptchaService.DEFAULT_VERIFY_URL,
        ))
        // 运维出站策略（application.conf [egress]）：全进程唯一的一份，所有受控客户端都从这里建。
        // 后台运行期可改的出站地址（短信 apiUrl、邮件 endpoint / SMTP 主机、支付网关、网关渠道……）一律走受控客户端；
        // 地址写死在部署配置里的（人机验证、AI、存储、推送）继续用应用的普通客户端。
        val egress = bindEgress(ctx, loggerFactory)
        // 短信 / 邮件的地址都在后台的消息渠道配置里：用受控客户端（system 建、system 关）。
        val messageHttp = egress.create { connectMillis = 3_000; requestMillis = 15_000; socketMillis = 15_000 }
        ctx.lifecycle.register("system-message-http-client", object : neton.core.component.NetonLifecycle {
            override suspend fun start() = Unit
            override suspend fun stop() = messageHttp.close()
        })
        val smsProvider = SmsProvider(loggerFactory.get("provider.sms"), messageHttp)
        val emailProvider = EmailProvider(
            loggerFactory.get("provider.email"), messageHttp, ctx.getOrNull(neton.http.mail.SmtpTransport::class),
            smtpPolicy = egress.settings.smtpPolicy,
        )
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
        // 会话吊销（退出、刷新令牌单次使用）与管理员会话版本 / 授权版本，见 spec application/identity-realm。
        val sessionRevocation = security.SessionRevocation(redis)
        ctx.bind(security.SessionRevocation::class, sessionRevocation)
        val adminSessionLogic = ctx.get(logic.AdminSessionLogic::class)
        val messageSendLogic = MessageSendLogic(loggerFactory.get("logic.message-send"), messageChannelLogic, messageTemplateLogic)
        val verificationCodes = VerificationCodeService(
            log = loggerFactory.get("logic.verification"),
            redis = redis,
            delivery = messageSendLogic,
            settings = config.loadVerificationSettings(ctx),
        )
        ctx.bind(VerificationCodeService::class, verificationCodes)
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
            adminSessionLogic,
        )
        ctx.bind(PermissionLogic::class, permissionLogic)
        // 权限不写进令牌：管理员域每次请求按授权版本解析（security.AdminTokenRealm）。
        val mfa = MfaLogic(ctx.get(neton.database.api.DbContext::class), neton.core.config.getEnv("NETON_MFA_MASTER_KEY"), loggerFactory.get("security.mfa"))
        ctx.bind(MfaLogic::class, mfa)
        ctx.bind(AuthLogic::class, AuthLogic(
            log = loggerFactory.get("logic.auth"), jwt = jwt, mfa = mfa,
            verificationCodes = verificationCodes, socialUserLogic = socialUserLogic,
            sessions = adminSessionLogic, revocation = sessionRevocation,
            throttle = security.LoginThrottle(redis, loginThrottleKey(ctx)),
        ))
        ctx.bind(DictLogic::class, DictLogic(loggerFactory.get("logic.dict"), infra.SimpleCache()))
    }

}

/**
 * 绑定全进程唯一的 [neton.http.client.GuardedHttpClientProvider]（宿主可先绑定自己的）：策略来自
 * application.conf `[egress]`（[neton.http.client.EgressSettings]），客户端经应用绑定的 `HttpClientProvider` 建
 * （模块不带引擎）。system 是每个宿主第一个初始化的模块，网关、支付、infra 都在它之后取用。
 * 放行列表非空时启动打 WARN：那是运维有意打开的内网通路，要让人看得见。
 */
internal fun bindEgress(ctx: NetonContext, loggerFactory: LoggerFactory): neton.http.client.GuardedHttpClientProvider {
    val bound = ctx.getOrNull(neton.http.client.GuardedHttpClientProvider::class)
    val egress = bound ?: neton.http.client.GuardedHttpClientProvider(
        neton.http.client.EgressSettings.load(ctx.args),
    ) { block ->
        val provider = ctx.getOrNull(neton.http.client.HttpClientProvider::class) ?: error(
            "module-system needs an HttpClientProvider bound in NetonContext (guarded outbound clients). Bind one in " +
                "the application: bind(HttpClientProvider::class, HttpClientProvider { HttpClient.create(it) }).",
        )
        provider.create(block)
    }.also { ctx.bind(neton.http.client.GuardedHttpClientProvider::class, it) }
    val log = loggerFactory.get("security.egress")
    if (egress.settings.isRelaxed) {
        log.warn(
            "egress.allowlist.enabled: guarded outbound requests may reach the listed non-public targets",
            mapOf("settings" to egress.settings.describe()),
        )
    } else {
        log.info("egress.policy public-only", mapOf("settings" to egress.settings.describe()))
    }
    return egress
}

/**
 * 登录冷却键的摘要密钥：从 JWT 密钥派生并加用途前缀，账号标识不会以明文进入缓存键。
 * account 模块用同一个函数，两个身份域的键空间仍由 realm 前缀区分。
 */
fun loginThrottleKey(ctx: NetonContext): ByteArray =
    neton.security.crypto.HmacSha256.sign(
        config.loadJwtRuntimeConfig(ctx).secretKey.encodeToByteArray(),
        "neton.login-throttle.v1".encodeToByteArray(),
    )

