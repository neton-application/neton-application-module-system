package controller.admin.auth

import controller.admin.auth.dto.LoginRequest
import controller.admin.auth.dto.LoginResponse
import controller.admin.auth.dto.PermissionInfoVO
import controller.admin.auth.dto.SmsLoginRequest
import controller.admin.auth.dto.SendSmsCodeRequest
import controller.admin.auth.dto.ResetPasswordRequest
import controller.admin.auth.dto.SocialLoginRequest
import controller.admin.auth.dto.RefreshTokenRequest
import controller.admin.auth.dto.SocialRedirectVO
import logic.AuthLogic
import logic.PermissionLogic
import config.SessionCookie
import neton.core.annotations.*
import neton.core.component.NetonContext
import neton.core.http.HttpContext
import neton.core.interfaces.Identity

@Controller("/system/auth")
class AuthController(
    private val authLogic: AuthLogic,
    private val permissionLogic: PermissionLogic,
    private val ctx: NetonContext,
) {

    /**
     * 登录成功后把 access token 同时写进 HttpOnly Cookie。
     *
     * 响应体里仍然返回 token —— 移动端和服务间调用继续用 `Authorization` 头；
     * 浏览器端则完全不必经手它，Cookie 脚本读不到，XSS 也就偷不走会话。
     */
    private fun issueSession(http: HttpContext, response: LoginResponse) {
        http.response.header("Cache-Control", "no-store")
        if (response.status != "authenticated") {
            SessionCookie.clear(ctx, http)
            return
        }
        val ttlSeconds = ((requireNotNull(response.expiresTime) - nowMillis()) / 1000).coerceAtLeast(0)
        SessionCookie.issue(ctx, http, requireNotNull(response.accessToken), ttlSeconds)
    }

    private fun nowMillis(): Long = kotlin.time.Clock.System.now().toEpochMilliseconds()

    /**
     * 登录审计（评审 R18）：成功与失败都落 `system_login_logs`，此前这张表从没被写过。
     * 地址取框架解析后的 clientAddress，与网络准入、限流一致。审计写失败不影响登录本身。
     * 需要第二因子的中间态不记成功，等 MFA 验证完成再记。
     */
    private suspend fun <T : LoginResponse> audited(http: HttpContext, username: String?, block: suspend () -> T): T {
        val result = try {
            block()
        } catch (e: Exception) {
            recordLogin(http, username, null, LOGIN_FAILED)
            throw e
        }
        if (result.status == "authenticated") recordLogin(http, result.username ?: username, result.userId, LOGIN_SUCCEEDED)
        return result
    }

    private suspend fun recordLogin(http: HttpContext, username: String?, userId: Long?, result: Int) {
        runCatching {
            ctx.get(logic.LogLogic::class).recordLoginLog(
                model.LoginLog(
                    userId = userId,
                    username = username?.take(64),
                    userIp = http.request.clientAddress,
                    userAgent = http.request.userAgent?.take(512),
                    loginResult = result,
                ),
            )
        }
    }

    private companion object {
        const val LOGIN_SUCCEEDED = 0
        const val LOGIN_FAILED = 1
    }

    @Post("/mfa/enrollment")
    @AllowAnonymous
    @RateLimit(windowSeconds = 60, maxRequests = 10, scope = RateLimitScope.IP)
    suspend fun enrollment(http: HttpContext, @Body request: MfaRequest): logic.MfaEnrollment {
        http.response.header("Cache-Control", "no-store")
        return ctx.get(logic.MfaLogic::class).enrollment(request.challengeToken)
    }

    @Post("/mfa/verify")
    @AllowAnonymous
    @RateLimit(windowSeconds = 60, maxRequests = 10, scope = RateLimitScope.IP)
    suspend fun verifyMfa(http: HttpContext, @Body request: MfaRequest): LoginResponse =
        audited(http, null) { authLogic.verifyMfa(request.challengeToken, request.code) }.also { issueSession(http, it) }

    @Post("/login")
    @AllowAnonymous
    @RateLimit(windowSeconds = 300, maxRequests = 10, scope = RateLimitScope.IP, message = "Login attempts exceeded, please try again later")
    suspend fun login(http: HttpContext, @Body request: LoginRequest): LoginResponse {
        return audited(http, request.username) { authLogic.login(request) }.also { issueSession(http, it) }
    }

    @Post("/logout")
    suspend fun logout(http: HttpContext, identity: Identity) {
        val userId = identity.id.toLong()
        authLogic.logout(userId, (identity as? security.RealmIdentity)?.sessionId)
        SessionCookie.clear(ctx, http)
    }

    /** 全端退出：作废该管理员在所有设备上的会话（含当前会话）。 */
    @Post("/logout-all")
    suspend fun logoutAll(http: HttpContext, identity: Identity) {
        authLogic.revokeAllSessions(identity.id.toLong())
        SessionCookie.clear(ctx, http)
    }

    @Post("/refresh-token")
    @AllowAnonymous
    @RateLimit(windowSeconds = 60, maxRequests = 30, scope = RateLimitScope.IP, message = "Too many refresh attempts, please try again later")
    suspend fun refreshToken(http: HttpContext, @Body request: RefreshTokenRequest): LoginResponse {
        return authLogic.refreshToken(request.refreshToken).also { issueSession(http, it) }
    }

    @Get("/get-permission-info")
    suspend fun getPermissionInfo(identity: Identity): PermissionInfoVO {
        val userId = identity.id.toLong()
        return permissionLogic.getPermissionInfo(userId)
    }

    @Post("/sms-login")
    @AllowAnonymous
    @RateLimit(windowSeconds = 60, maxRequests = 5, scope = RateLimitScope.IP, message = "SMS login attempts exceeded, please try again later")
    suspend fun smsLogin(http: HttpContext, @Body request: SmsLoginRequest): LoginResponse {
        return audited(http, null) { authLogic.smsLogin(request.mobile, request.smsCode) }.also { issueSession(http, it) }
    }

    @Post("/send-sms-code")
    @AllowAnonymous
    @RateLimit(windowSeconds = 60, maxRequests = 5, scope = RateLimitScope.IP, message = "SMS code sending limit exceeded, please try again later")
    suspend fun sendSmsCode(@Body request: SendSmsCodeRequest) {
        authLogic.sendSmsCode(request.mobile, request.scene)
    }

    @Post("/reset-password")
    @AllowAnonymous
    @RateLimit(windowSeconds = 300, maxRequests = 5, scope = RateLimitScope.IP, message = "Password reset attempts exceeded, please try again later")
    suspend fun resetPassword(@Body request: ResetPasswordRequest) {
        authLogic.resetPassword(request.mobile, request.smsCode, request.newPassword)
    }

    @Get("/social-auth-redirect")
    @AllowAnonymous
    suspend fun socialAuthRedirect(type: String, redirectUri: String? = null): SocialRedirectVO {
        val url = authLogic.socialAuthRedirect(type, redirectUri ?: "")
        return SocialRedirectVO(url = url)
    }

    @Post("/social-login")
    @AllowAnonymous
    @RateLimit(windowSeconds = 300, maxRequests = 10, scope = RateLimitScope.IP, message = "Social login attempts exceeded, please try again later")
    suspend fun socialLogin(http: HttpContext, @Body request: SocialLoginRequest): LoginResponse {
        return authLogic.socialLogin(request.socialType, request.code, request.redirectUri ?: "").also { issueSession(http, it) }
    }
}
