package logic

import controller.admin.auth.dto.LoginRequest
import controller.admin.auth.dto.LoginResponse
import model.User
import table.UserTable
import security.AdminAuthorization
import neton.security.jwt.JwtAuthenticator
import neton.core.http.BadRequestException
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
    // 生产必传（module-system 已注入）；为空则退化 emptySet（仅框架 example 的极简构造）。
    private val permissionLogic: PermissionLogic? = null,
    private val messageSendLogic: MessageSendLogic? = null,
    private val socialUserLogic: SocialUserLogic? = null
) {

    companion object {
        const val ACCESS_TOKEN_EXPIRES = 7200L   // 2 hours in seconds
        const val REFRESH_TOKEN_EXPIRES = 604800L // 7 days in seconds
    }

    suspend fun login(request: LoginRequest): LoginResponse {
        var user = UserTable.oneWhere {
            User::username eq request.username
        } ?: throw BadRequestException("Invalid username or password")

        if (user.status == 0) {
            throw BadRequestException("User account is disabled")
        }

        val passwordVerification = PasswordHasher.verify(request.password, user.passwordHash)
        if (!passwordVerification.verified) {
            throw BadRequestException("Invalid username or password")
        }
        if (passwordVerification.needsRehash) {
            user = user.copy(passwordHash = PasswordHasher.hash(request.password))
            UserTable.update(user)
        }

        return completeFirstFactor(user)
    }

    suspend fun logout(userId: Long) {
        log.info("User logged out: $userId")
    }

    suspend fun refreshToken(refreshToken: String): LoginResponse {
        val verified = try { jwt.verifyToken(refreshToken) } catch (_: AuthenticationException) {
            throw BadRequestException("Invalid or expired refresh token")
        }
        val id = verified.identity.userId.value.toLong()
        if (verified.claimString("type") != "refresh" ||
            !mfa.accepts(id, verified.claimString("mfa_revision")?.toLongOrNull(), verified.claimString("mfa_verified") == "true")) {
            throw BadRequestException("Login required")
        }
        val user = UserTable.get(id) ?: throw BadRequestException("Login required")
        if (user.status != 1) throw BadRequestException("Login required")
        return issue(user, verified.claimString("mfa_verified") == "true", verified.claimString("mfa_revision")?.toLongOrNull() ?: 0)
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
        val user = UserTable.get(completed.userId) ?: throw BadRequestException("Login required")
        if (user.status != 1) throw BadRequestException("Login required")
        return issue(user, true, completed.revision)
    }

    private suspend fun issue(user: User, mfaVerified: Boolean, revision: Long): LoginResponse {
        if (!mfa.accepts(user.id, revision, mfaVerified)) throw BadRequestException("Login required")
        val authorization = permissionLogic?.resolveAuthorization(user.id)
            ?: AdminAuthorization(emptySet(), emptySet())
        val claims = mapOf<String, Any>("username" to user.username, "mfa_revision" to revision, "mfa_verified" to mfaVerified)
        val access = jwt.createToken(UserId(user.id.toULong()), authorization.roles, authorization.permissions,
            ACCESS_TOKEN_EXPIRES, claims + ("type" to "access"))
        val refresh = jwt.createToken(UserId(user.id.toULong()), emptySet(), emptySet(),
            REFRESH_TOKEN_EXPIRES, claims + ("type" to "refresh"))
        log.info("User authenticated: ${user.id} mfa=$mfaVerified")
        return LoginResponse(accessToken = access, refreshToken = refresh, userId = user.id,
            expiresTime = Clock.System.now().toEpochMilliseconds() + ACCESS_TOKEN_EXPIRES * 1000,
            username = user.username, nickname = user.nickname)
    }

    suspend fun smsLogin(mobile: String, smsCode: String): LoginResponse {
        val sendLogic = messageSendLogic
            ?: throw BadRequestException("SMS service not configured")

        if (!sendLogic.verifySmsCode(mobile, smsCode)) {
            throw BadRequestException("Invalid or expired SMS code")
        }

        // Find user by mobile
        val user = UserTable.oneWhere {
            User::mobile eq mobile
        } ?: throw NotFoundException("User not found with mobile: $mobile")

        if (user.status == 0) {
            throw BadRequestException("User account is disabled")
        }

        return completeFirstFactor(user)
    }

    suspend fun sendSmsCode(mobile: String, scene: String) {
        val sendLogic = messageSendLogic
            ?: throw BadRequestException("SMS service not configured")
        sendLogic.sendVerificationCode(mobile, scene)
    }

    suspend fun resetPassword(mobile: String, smsCode: String, newPassword: String) {
        val sendLogic = messageSendLogic
            ?: throw BadRequestException("SMS service not configured")

        if (!sendLogic.verifySmsCode(mobile, smsCode)) {
            throw BadRequestException("Invalid or expired SMS code")
        }

        val user = UserTable.oneWhere {
            User::mobile eq mobile
        } ?: throw NotFoundException("User not found with mobile: $mobile")

        val hashedPassword = PasswordHasher.hash(newPassword)
        UserTable.update(user.copy(passwordHash = hashedPassword))
        log.info("Password reset for user: ${user.username}")
    }

    /**
     * Get social auth redirect URL.
     */
    suspend fun socialAuthRedirect(socialType: String, redirectUri: String): String {
        val social = socialUserLogic
            ?: throw BadRequestException("Social login not configured")
        return social.getAuthRedirectUrl(socialType, redirectUri)
    }

    /**
     * Social login — exchange code for token. If the social account is bound
     * to an existing user, return their token. Otherwise throw error.
     */
    suspend fun socialLogin(socialType: String, code: String, redirectUri: String): LoginResponse {
        val social = socialUserLogic
            ?: throw BadRequestException("Social login not configured")

        val socialUser = social.socialLogin(socialType, code, redirectUri, userType = 1)

        if (socialUser.userId == 0L) {
            throw BadRequestException("Social account not bound to any admin user. Please bind first.")
        }

        val user = UserTable.get(socialUser.userId)
            ?: throw NotFoundException("Bound user not found")

        if (user.status == 0) {
            throw BadRequestException("User account is disabled")
        }

        return completeFirstFactor(user)
    }
}
