package logic

import neton.core.http.ValidationError

/**
 * system 模块的业务错误机器码（spec application/system-error-codes）。
 *
 * 规则：异常的 `message` 只放这里的机器码（`^[A-Z][A-Z0-9_]{2,}$`），前端按码翻译
 * （模块语言包 `error.<CODE>`）。数字 `code` 仍取 `NetonErrorCode` 的类别（参数错 / 不存在 /
 * 冲突 / 无权限 / 服务不可用 …）。跟请求字段有关的细节放字段级错误 [field]，不拼进 `message`；
 * 只对排障有用的细节写日志，不进响应。
 *
 * 新增码：先加在这里，再补 spec 与 neton-application-front-system 两种语言的 `error.<CODE>`。
 */
object SystemErrorCodes {
    // ---- 登录与会话 ----
    /** 用户不存在 / 密码错误 / 账号停用统一成这一个码，不让人借此枚举管理员账号。 */
    const val INVALID_CREDENTIALS = "INVALID_CREDENTIALS"
    const val LOGIN_REQUIRED = "LOGIN_REQUIRED"
    const val REFRESH_TOKEN_INVALID = "REFRESH_TOKEN_INVALID"
    const val ACCOUNT_DISABLED = "ACCOUNT_DISABLED"
    /** 同一账号同一来源连续失败后的冷却（429 + Retry-After）。account 的终端用户登录共用。 */
    const val LOGIN_LOCKED = "LOGIN_LOCKED"
    const val LOGIN_RATE_LIMITED = "LOGIN_RATE_LIMITED"
    const val REFRESH_RATE_LIMITED = "REFRESH_RATE_LIMITED"
    const val SMS_LOGIN_RATE_LIMITED = "SMS_LOGIN_RATE_LIMITED"
    const val SMS_SEND_RATE_LIMITED = "SMS_SEND_RATE_LIMITED"
    const val PASSWORD_RESET_RATE_LIMITED = "PASSWORD_RESET_RATE_LIMITED"
    const val SOCIAL_LOGIN_RATE_LIMITED = "SOCIAL_LOGIN_RATE_LIMITED"

    // ---- 短信验证码 ----
    const val SMS_NOT_CONFIGURED = "SMS_NOT_CONFIGURED"
    const val SMS_SCENE_INVALID = "SMS_SCENE_INVALID"
    const val SMS_CODE_INVALID = "SMS_CODE_INVALID"
    const val SMS_CODE_LOCKED = "SMS_CODE_LOCKED"
    const val MOBILE_INVALID = "MOBILE_INVALID"

    // ---- 验证码服务（account 的终端用户验证码共用） ----
    const val VERIFICATION_UNAVAILABLE = "VERIFICATION_UNAVAILABLE"
    const val VERIFICATION_CHANNEL_UNAVAILABLE = "VERIFICATION_CHANNEL_UNAVAILABLE"
    const val VERIFICATION_SEND_FAILED = "VERIFICATION_SEND_FAILED"
    const val VERIFICATION_TOO_FREQUENT = "VERIFICATION_TOO_FREQUENT"
    const val VERIFICATION_DAILY_LIMIT = "VERIFICATION_DAILY_LIMIT"
    const val VERIFICATION_DAILY_BUDGET_EXHAUSTED = "VERIFICATION_DAILY_BUDGET_EXHAUSTED"

    // ---- 人机验证 ----
    /** 这个动作要求人机验证，请求没带凭证。 */
    const val CAPTCHA_REQUIRED = "CAPTCHA_REQUIRED"
    /** 凭证无效、过期、已用过，或签发给了别的动作。 */
    const val CAPTCHA_INVALID = "CAPTCHA_INVALID"
    /** 打开了人机验证但服务端没配 secret，或供应商不可达：拒绝而不是放行。 */
    const val CAPTCHA_UNAVAILABLE = "CAPTCHA_UNAVAILABLE"

    // ---- MFA ----
    const val MFA_NOT_CONFIGURED = "MFA_NOT_CONFIGURED"
    const val MFA_CHALLENGE_INVALID = "MFA_CHALLENGE_INVALID"
    const val MFA_CODE_INVALID = "MFA_CODE_INVALID"
    const val MFA_SELF_RESET_FORBIDDEN = "MFA_SELF_RESET_FORBIDDEN"
    const val MFA_RATE_LIMITED = "MFA_RATE_LIMITED"

    // ---- 第三方登录 ----
    const val SOCIAL_LOGIN_NOT_CONFIGURED = "SOCIAL_LOGIN_NOT_CONFIGURED"
    const val SOCIAL_PROVIDER_NOT_CONFIGURED = "SOCIAL_PROVIDER_NOT_CONFIGURED"
    const val SOCIAL_PROVIDER_NOT_IMPLEMENTED = "SOCIAL_PROVIDER_NOT_IMPLEMENTED"
    const val SOCIAL_USER_INFO_FAILED = "SOCIAL_USER_INFO_FAILED"
    const val SOCIAL_ACCOUNT_NOT_BOUND = "SOCIAL_ACCOUNT_NOT_BOUND"
    const val SOCIAL_ACCOUNT_ALREADY_BOUND = "SOCIAL_ACCOUNT_ALREADY_BOUND"
    const val SOCIAL_ACCOUNT_BOUND_TO_OTHER = "SOCIAL_ACCOUNT_BOUND_TO_OTHER"
    const val SOCIAL_BOUND_USER_NOT_FOUND = "SOCIAL_BOUND_USER_NOT_FOUND"
    const val SOCIAL_BINDING_NOT_FOUND = "SOCIAL_BINDING_NOT_FOUND"
    const val SOCIAL_USER_NOT_FOUND = "SOCIAL_USER_NOT_FOUND"

    // ---- 用户 ----
    const val USER_NOT_FOUND = "USER_NOT_FOUND"
    const val USERNAME_EXISTS = "USERNAME_EXISTS"
    const val OLD_PASSWORD_INCORRECT = "OLD_PASSWORD_INCORRECT"
    const val NEW_PASSWORD_SAME_AS_OLD = "NEW_PASSWORD_SAME_AS_OLD"

    // ---- 角色 / 部门 / 岗位 / 菜单 ----
    const val ROLE_NOT_FOUND = "ROLE_NOT_FOUND"
    const val ROLE_CODE_EXISTS = "ROLE_CODE_EXISTS"
    const val DEPT_NOT_FOUND = "DEPT_NOT_FOUND"
    const val DEPT_PARENT_SELF = "DEPT_PARENT_SELF"
    const val DEPT_HAS_CHILDREN = "DEPT_HAS_CHILDREN"
    const val POST_NOT_FOUND = "POST_NOT_FOUND"
    const val POST_CODE_EXISTS = "POST_CODE_EXISTS"
    const val MENU_NOT_FOUND = "MENU_NOT_FOUND"
    const val MENU_PARENT_SELF = "MENU_PARENT_SELF"
    const val MENU_HAS_CHILDREN = "MENU_HAS_CHILDREN"

    // ---- 字典 ----
    const val DICT_TYPE_NOT_FOUND = "DICT_TYPE_NOT_FOUND"
    const val DICT_TYPE_EXISTS = "DICT_TYPE_EXISTS"
    const val DICT_TYPE_IMMUTABLE = "DICT_TYPE_IMMUTABLE"
    const val DICT_TYPE_HAS_DATA = "DICT_TYPE_HAS_DATA"
    const val DICT_DATA_NOT_FOUND = "DICT_DATA_NOT_FOUND"

    // ---- 通知公告 / 站内信模板 ----
    const val NOTICE_NOT_FOUND = "NOTICE_NOT_FOUND"
    const val NOTIFY_TEMPLATE_NOT_FOUND = "NOTIFY_TEMPLATE_NOT_FOUND"
    const val NOTIFY_TEMPLATE_CODE_EXISTS = "NOTIFY_TEMPLATE_CODE_EXISTS"
    const val NOTIFY_TEMPLATE_UNLINKED = "NOTIFY_TEMPLATE_UNLINKED"

    // ---- 消息通道 / 模板 / 发送 ----
    const val MESSAGE_TEMPLATE_NOT_FOUND = "MESSAGE_TEMPLATE_NOT_FOUND"
    const val MESSAGE_TEMPLATE_CODE_EXISTS = "MESSAGE_TEMPLATE_CODE_EXISTS"
    const val MESSAGE_CHANNEL_NOT_FOUND = "MESSAGE_CHANNEL_NOT_FOUND"
    const val MESSAGE_CHANNEL_CODE_EXISTS = "MESSAGE_CHANNEL_CODE_EXISTS"
    const val MESSAGE_PROVIDER_UNAVAILABLE = "MESSAGE_PROVIDER_UNAVAILABLE"
    const val SMS_PROVIDER_NOT_CONFIGURED = "SMS_PROVIDER_NOT_CONFIGURED"
    const val SMTP_TRANSPORT_UNAVAILABLE = "SMTP_TRANSPORT_UNAVAILABLE"
    const val EMAIL_VENDOR_UNSUPPORTED = "EMAIL_VENDOR_UNSUPPORTED"
    /** 消息渠道配置的地址（短信 apiUrl、邮件 endpoint、SMTP 主机）被运维出站策略 `[egress]` 拒绝。 */
    const val MESSAGE_ENDPOINT_FORBIDDEN = "MESSAGE_ENDPOINT_FORBIDDEN"

    // ---- 日志 ----
    const val MESSAGE_LOG_NOT_FOUND = "MESSAGE_LOG_NOT_FOUND"
    const val OPERATE_LOG_NOT_FOUND = "OPERATE_LOG_NOT_FOUND"
    const val LOGIN_LOG_NOT_FOUND = "LOGIN_LOG_NOT_FOUND"

    // ---- 系统配置 ----
    const val SETTING_NOT_DEFINED = "SETTING_NOT_DEFINED"
    const val SETTING_VALUE_INVALID = "SETTING_VALUE_INVALID"

    // ---- 网络准入 / 健康检查 / 开发工具 ----
    const val IP_NOT_ALLOWED = "IP_NOT_ALLOWED"
    /** 就绪检查失败；各依赖的状态放在字段级错误里（path = 依赖名，code = [DEPENDENCY_UNAVAILABLE]）。 */
    const val SERVICE_DEGRADED = "SERVICE_DEGRADED"
    const val DEPENDENCY_UNAVAILABLE = "DEPENDENCY_UNAVAILABLE"
    const val SIMULATED_CODE_NOT_FOUND = "SIMULATED_CODE_NOT_FOUND"
    const val VERIFICATION_CHANNEL_INVALID = "VERIFICATION_CHANNEL_INVALID"
    const val RECEIVER_REQUIRED = "RECEIVER_REQUIRED"

    /** 字段级错误：`message` 与 `code` 都是机器码，`path` 指向请求里的字段。 */
    fun field(path: String, code: String): List<ValidationError> = listOf(ValidationError(path, code, code))
}
