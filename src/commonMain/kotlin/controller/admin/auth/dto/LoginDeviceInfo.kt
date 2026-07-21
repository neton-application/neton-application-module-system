package controller.admin.auth.dto

import kotlinx.serialization.Serializable

/**
 * 登录请求携带的设备信息。
 *
 * 所有 application 用户面登录入口（密码 / SMS / 社交）共用一份；access/refresh 的 JWT claim
 * 与之绑定 [deviceId]，refresh 时必须比对一致才允许续签（防止跨设备 token 复用）。
 *
 * `deviceId` / `platform` 等都是 client 自报，application 不强校验值的合法性，仅校验前后一致；
 * 真正绑定到 IM session 由 `privchat-server.IssueImTokenRequest.device_info` 决策。
 */
@Serializable
data class LoginDeviceInfo(
    /** 客户端持久化的设备唯一标识（Web localStorage / App KeyChain）；首次登录可空，server 会回写。 */
    val deviceId: String? = null,
    /** 端类型：android / ios / web / desktop_mac / ...（→ server `app_id`）。 */
    val platform: String? = null,
    val deviceName: String? = null,
    val deviceModel: String? = null,
    val osVersion: String? = null,
    val appVersion: String? = null,
    val ipAddress: String? = null,
)
