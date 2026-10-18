package logic

import security.AdminTokenRealm
import security.TokenRealm
import controller.admin.auth.dto.LoginRequest
import controller.admin.auth.dto.LoginResponse
import model.User
import table.UserTable
import security.LoginThrottle
import security.SessionRevocation
import neton.security.jwt.JwtAuthenticator
import neton.core.http.BadRequestException
import neton.core.http.HttpException
import neton.core.http.NetonErrorCode
import neton.core.http.NotFoundException
import neton.logging.Logger
import neton.database.dsl.*

import kotlin.time.Clock
import neton.security.identity.AuthenticationException
import neton.security.identity.UserId
import neton.security.password.PasswordHasher

class AuthLogic(
    private val log: Logger,
    private val jwt: JwtAuthenticator,
    private val mfa: MfaLogic,
    private val verificationCodes: VerificationCodeService? = null,
    private val socialUserLogic: SocialUserLogic? = null,
    private val sessions: AdminSessionLogic? = null,
    private val revocation: SessionRevocation = SessionRevocation(null),
    private val throttle: LoginThrottle = LoginThrottle(null),
) {

    /** 用户不存在时用它做一次等价的哈希校验，让失败耗时与真实账号一致。 */
    private val dummyHash: String by lazy { PasswordHasher.hash("timing-equalizer-not-a-password") }

    companion object {
        const val ACCESS_TOKEN_EXPIRES = 7200L   // 2 hours in seconds
        const val REFRESH_TOKEN_EXPIRES = 604800L // 7 days in seconds

        const val LOGIN_FAILED = SystemErrorCodes.INVALID_CREDENTIALS
        const val SMS_PURPOSE_LOGIN = "login"
        const val SMS_PURPOSE_RESET_PASSWORD = "reset_password"
        private val SMS_PURPOSES = setOf(SMS_PURPOSE_LOGIN, SMS_PURPOSE_RESET_PASSWORD)
    }

    /**
     * 用户名密码登录（评审 R08）：
     * - 用户不存在、密码错误、账号停用一律同一句失败提示，不让人借此枚举管理员账号；
     * - 用户不存在时也做一次等价的哈希校验，响应时间不暴露账号是否存在；
     * - 按账号计失败次数，连续失败后冷却，挡住换 IP 慢速猜密码。
     */
    suspend fun login(request: LoginRequest, clientIp: String? = null): LoginResponse {
        throttle.ensureNotLocked(AdminTokenRealm.NAME, request.username, clientIp)
        val found = UserTable.oneWhere { User::username eq request.username }
        val verification = PasswordHasher.verify(request.password, found?.passwordHash ?: dummyHash)
        if (found == null || !verification.verified || found.status != 1) {
            val accountFailures = throttle.recordFailure(AdminTokenRealm.NAME, request.username, clientIp)
            log.info("auth.admin.login_failed", mapOf("reason" to when {
                found == null -> "unknown_user"; !verification.verified -> "bad_password"; else -> "disabled"
            }))
            // 跨来源的失败只记安全事件，不锁号（spec auth-abuse-protection §4.1）。
            if (accountFailures == LoginThrottle.ACCOUNT_RISK_THRESHOLD) {
                log.warn("security.login.account_risk", mapOf("realm" to AdminTokenRealm.NAME, "userId" to (found?.id ?: 0L)))
            }
            throw BadRequestException(LOGIN_FAILED)
        }
        throttle.reset(AdminTokenRealm.NAME, request.username, clientIp)
        var user: User = found
        if (verification.needsRehash) {
            user = user.copy(passwordHash = PasswordHasher.hash(request.password))
            UserTable.update(user)
        }
        return completeFirstFactor(user)
    }

    /**
     * 退出当前会话：吊销令牌里的 `ssn`，同一次登录签出的 access 与 refresh 一起失效（评审 R04）。
     * 没有 Redis 无法按会话吊销时，退回到作废该管理员全部会话 —— 宁可多踢，不能退出了令牌还能用。
     */
    suspend fun logout(userId: Long, sessionId: String?) {
        if (sessionId != null && revocation.available) {
            revocation.revokeSession(AdminTokenRealm.NAME, sessionId, REFRESH_TOKEN_EXPIRES)
        } else {
            sessions?.revokeAll(userId)
        }
        log.info("auth.admin.logout", mapOf("userId" to userId, "scope" to if (sessionId != null && revocation.available) "session" else "all"))
    }

    /** 全端退出 / 改密 / 管理员重置密码之后调用：该管理员已签发的所有令牌立即失效。 */
    suspend fun revokeAllSessions(userId: Long) {
        sessions?.revokeAll(userId)
    }

    suspend fun refreshToken(refreshToken: String): LoginResponse {
        val verified = try { jwt.verifyToken(refreshToken) } catch (_: AuthenticationException) {
            throw BadRequestException(SystemErrorCodes.REFRESH_TOKEN_INVALID)
        }
        val id = verified.identity.userId.value.toLong()
        // 域必须是管理员：同一把密钥也签终端用户令牌，两边 id 会撞（评审 R01）。
        if (verified.claimString(TokenRealm.CLAIM) != AdminTokenRealm.NAME ||
            verified.claimString(TokenRealm.TYPE_CLAIM) != TokenRealm.TYPE_REFRESH ||
            !mfa.accepts(id, verified.claimString("mfa_revision")?.toLongOrNull(), verified.claimString("mfa_verified") == "true")) {
            throw BadRequestException(SystemErrorCodes.LOGIN_REQUIRED)
        }
        val user = UserTable.get(id) ?: throw BadRequestException(SystemErrorCodes.LOGIN_REQUIRED)
        if (user.status != 1) throw BadRequestException(SystemErrorCodes.LOGIN_REQUIRED)
        val sessionVersion = sessions?.sessionVersion(id) ?: 0L
        if (verified.claimString(AdminTokenRealm.SESSION_VERSION_CLAIM)?.toLongOrNull() != sessionVersion) {
            throw BadRequestException(SystemErrorCodes.LOGIN_REQUIRED)
        }
        val sessionId = verified.claimString(SessionRevocation.SESSION_CLAIM) ?: throw BadRequestException(SystemErrorCodes.LOGIN_REQUIRED)
        if (revocation.isRevoked(AdminTokenRealm.NAME, sessionId)) throw BadRequestException(SystemErrorCodes.LOGIN_REQUIRED)
        // 刷新令牌只能用一次：并发窗口外的重复使用说明它被复制了，整个会话作废（评审 R04）。
        val jti = verified.claimString(SessionRevocation.TOKEN_ID_CLAIM) ?: throw BadRequestException(SystemErrorCodes.LOGIN_REQUIRED)
        val nowSeconds = Clock.System.now().toEpochMilliseconds() / 1000
        if (revocation.claimRefresh(AdminTokenRealm.NAME, jti, REFRESH_TOKEN_EXPIRES, nowSeconds) == SessionRevocation.RefreshUse.REUSED) {
            revocation.revokeSession(AdminTokenRealm.NAME, sessionId, REFRESH_TOKEN_EXPIRES)
            log.warn("auth.admin.refresh_reused", mapOf("userId" to id))
            throw BadRequestException(SystemErrorCodes.LOGIN_REQUIRED)
        }
        return issue(user, verified.claimString("mfa_verified") == "true", verified.claimString("mfa_revision")?.toLongOrNull() ?: 0, sessionId)
    }

    private suspend fun completeFirstFactor(user: User): LoginResponse {
        val challenge = mfa.challenge(user.id, user.passwordHash)
        if (challenge != null) return LoginResponse(
            status = challenge.status, challengeToken = challenge.challengeToken,
            challengeExpiresAt = challenge.challengeExpiresAt,
        )
        return issue(user, false, mfa.revision(user.id))
    }

    suspend fun verifyMfa(token: String, code: String): LoginResponse {
        val completed = mfa.verify(token, code)
        val user = UserTable.get(completed.userId) ?: throw BadRequestException(SystemErrorCodes.LOGIN_REQUIRED)
        if (user.status != 1) throw BadRequestException(SystemErrorCodes.LOGIN_REQUIRED)
        return issue(user, true, completed.revision)
    }

    /**
     * 签发一对令牌。角色与权限**不写进令牌**：管理员域每次请求按当前授权版本从服务端解析
     * （评审 R05），令牌里那份签发后就不会变，留着只会被误当成权威。
     *
     * [sessionId] 为空表示新登录，生成新的会话 id；刷新时沿用原会话，退出时据此整体吊销。
     */
    private suspend fun issue(user: User, mfaVerified: Boolean, revision: Long, sessionId: String? = null): LoginResponse {
        if (!mfa.accepts(user.id, revision, mfaVerified)) throw BadRequestException(SystemErrorCodes.LOGIN_REQUIRED)
        val claims = mapOf<String, Any>(
            TokenRealm.CLAIM to AdminTokenRealm.NAME,
            SessionRevocation.SESSION_CLAIM to (sessionId ?: SessionRevocation.newId()),
            AdminTokenRealm.SESSION_VERSION_CLAIM to (sessions?.sessionVersion(user.id) ?: 0L),
            "username" to user.username, "mfa_revision" to revision, "mfa_verified" to mfaVerified,
        )
        val uid = UserId(user.id.toULong())
        val access = jwt.createToken(uid, emptySet(), emptySet(), ACCESS_TOKEN_EXPIRES,
            claims + mapOf(TokenRealm.TYPE_CLAIM to TokenRealm.TYPE_ACCESS, SessionRevocation.TOKEN_ID_CLAIM to SessionRevocation.newId()))
        val refresh = jwt.createToken(uid, emptySet(), emptySet(), REFRESH_TOKEN_EXPIRES,
            claims + mapOf(TokenRealm.TYPE_CLAIM to TokenRealm.TYPE_REFRESH, SessionRevocation.TOKEN_ID_CLAIM to SessionRevocation.newId()))
        log.info("auth.admin.issued", mapOf("userId" to user.id, "mfa" to mfaVerified, "refresh" to (sessionId != null)))
        return LoginResponse(accessToken = access, refreshToken = refresh, userId = user.id,
            expiresTime = Clock.System.now().toEpochMilliseconds() + ACCESS_TOKEN_EXPIRES * 1000,
            username = user.username, nickname = user.nickname)
    }

    suspend fun smsLogin(mobile: String, smsCode: String): LoginResponse {
        requireSmsCode(mobile, SMS_PURPOSE_LOGIN, smsCode)
        val user = UserTable.oneWhere { User::mobile eq mobile }
        if (user == null || user.status != 1) throw BadRequestException(LOGIN_FAILED)
        return completeFirstFactor(user)
    }

    /**
     * 管理员短信验证码，只开放登录与重置密码两种用途。手机号不属于任何管理员时照常返回、不发送：
     * 既不让人借此枚举管理员手机号，也不替陌生号码付短信费。
     */
    suspend fun sendSmsCode(mobile: String, scene: String) {
        val codes = verificationCodes ?: throw HttpException(NetonErrorCode.SERVICE_UNAVAILABLE, SystemErrorCodes.SMS_NOT_CONFIGURED)
        if (scene !in SMS_PURPOSES) throw BadRequestException(SystemErrorCodes.SMS_SCENE_INVALID, SystemErrorCodes.field("scene", SystemErrorCodes.SMS_SCENE_INVALID))
        if (UserTable.oneWhere { User::mobile eq mobile }?.status != 1) {
            log.info("auth.admin.sms_skipped_unknown_mobile", mapOf("scene" to scene))
            return
        }
        codes.issue(AdminTokenRealm.NAME, scene, VerificationCodeService.Channel.SMS, mobile, "sms_verification_$scene")
    }

    suspend fun resetPassword(mobile: String, smsCode: String, newPassword: String) {
        requireSmsCode(mobile, SMS_PURPOSE_RESET_PASSWORD, smsCode)
        val user = UserTable.oneWhere { User::mobile eq mobile } ?: throw BadRequestException(SystemErrorCodes.SMS_CODE_INVALID, SystemErrorCodes.field("smsCode", SystemErrorCodes.SMS_CODE_INVALID))
        UserTable.update(user.copy(passwordHash = PasswordHasher.hash(newPassword)))
        // 重置密码通常意味着怀疑被盗用：已登录的会话全部作废（评审 R04）。
        sessions?.revokeAll(user.id)
        log.info("auth.admin.password_reset", mapOf("userId" to user.id))
    }

    private suspend fun requireSmsCode(mobile: String, purpose: String, code: String) {
        val codes = verificationCodes ?: throw HttpException(NetonErrorCode.SERVICE_UNAVAILABLE, SystemErrorCodes.SMS_NOT_CONFIGURED)
        when (codes.verify(AdminTokenRealm.NAME, purpose, VerificationCodeService.Channel.SMS, mobile, code)) {
            VerificationCodeService.Result.OK -> Unit
            VerificationCodeService.Result.LOCKED -> throw BadRequestException(SystemErrorCodes.SMS_CODE_LOCKED, SystemErrorCodes.field("smsCode", SystemErrorCodes.SMS_CODE_LOCKED))
            else -> throw BadRequestException(SystemErrorCodes.SMS_CODE_INVALID, SystemErrorCodes.field("smsCode", SystemErrorCodes.SMS_CODE_INVALID))
        }
    }

    /**
     * Get social auth redirect URL.
     */
    suspend fun socialAuthRedirect(socialType: String, redirectUri: String): String {
        val social = socialUserLogic
            ?: throw HttpException(NetonErrorCode.SERVICE_UNAVAILABLE, SystemErrorCodes.SOCIAL_LOGIN_NOT_CONFIGURED)
        return social.getAuthRedirectUrl(socialType, redirectUri)
    }

    /**
     * Social login — exchange code for token. If the social account is bound
     * to an existing user, return their token. Otherwise throw error.
     */
    suspend fun socialLogin(socialType: String, code: String, redirectUri: String): LoginResponse {
        val social = socialUserLogic
            ?: throw HttpException(NetonErrorCode.SERVICE_UNAVAILABLE, SystemErrorCodes.SOCIAL_LOGIN_NOT_CONFIGURED)

        val socialUser = social.socialLogin(socialType, code, redirectUri, userType = 1)

        if (socialUser.userId == 0L) {
            throw BadRequestException(SystemErrorCodes.SOCIAL_ACCOUNT_NOT_BOUND)
        }

        val user = UserTable.get(socialUser.userId)
            ?: throw NotFoundException(SystemErrorCodes.SOCIAL_BOUND_USER_NOT_FOUND)

        if (user.status == 0) {
            throw BadRequestException(SystemErrorCodes.ACCOUNT_DISABLED)
        }

        return completeFirstFactor(user)
    }
}
