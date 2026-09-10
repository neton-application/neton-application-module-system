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
        val ttlSeconds = ((response.expiresTime - nowMillis()) / 1000).coerceAtLeast(0)
        SessionCookie.issue(ctx, http, response.accessToken, ttlSeconds)
    }

    private fun nowMillis(): Long = kotlin.time.Clock.System.now().toEpochMilliseconds()

    @Post("/login")
    @AllowAnonymous
    @RateLimit(windowSeconds = 300, maxRequests = 10, scope = RateLimitScope.IP, message = "Login attempts exceeded, please try again later")
    suspend fun login(http: HttpContext, @Body request: LoginRequest): LoginResponse {
        return authLogic.login(request).also { issueSession(http, it) }
    }

    @Post("/logout")
    suspend fun logout(http: HttpContext, identity: Identity) {
        val userId = identity.id.toLong()
        authLogic.logout(userId)
        SessionCookie.clear(ctx, http)
    }

    @Post("/refresh-token")
    @AllowAnonymous
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
    suspend fun smsLogin(@Body request: SmsLoginRequest): LoginResponse {
        return authLogic.smsLogin(request.mobile, request.smsCode)
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
    suspend fun socialLogin(@Body request: SocialLoginRequest): LoginResponse {
        return authLogic.socialLogin(request.socialType, request.code, request.redirectUri ?: "")
    }
}
