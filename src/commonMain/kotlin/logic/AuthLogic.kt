package logic

import controller.admin.auth.dto.LoginRequest
import controller.admin.auth.dto.LoginResponse
import model.User
import table.UserTable
import table.UserRoleTable
import table.RoleTable
import model.UserRole
import model.Role
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
        val user = UserTable.oneWhere {
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
            UserTable.update(user.copy(passwordHash = PasswordHasher.hash(request.password)))
        }

        // Fetch user roles for the token
        val userRoles = UserRoleTable.query {
            where { UserRole::userId eq user.id }
        }.list()
        val roleIds = userRoles.map { it.roleId }
        val roles = if (roleIds.isNotEmpty()) {
            RoleTable.query {
                where { Role::id `in` roleIds }
            }.list().map { it.code }.toSet()
        } else emptySet()

        val accessToken = jwt.createToken(
            userId = UserId(user.id.toULong()),
            roles = roles,
            // P0 granular RBAC：把解析出的权限写进 access token（super→*:*:*，普通→role_menu 子集）。
            permissions = permissionLogic?.resolvePermissions(user.id) ?: emptySet(),
            expiresInSeconds = ACCESS_TOKEN_EXPIRES,
            extraClaims = mapOf("type" to "access", "username" to user.username)
        )
        val refreshToken = jwt.createToken(
            userId = UserId(user.id.toULong()),
            roles = emptySet(),
            permissions = emptySet(),
            expiresInSeconds = REFRESH_TOKEN_EXPIRES,
            extraClaims = mapOf("type" to "refresh", "username" to user.username)
        )

        log.info("User logged in: ${user.username}")

        return LoginResponse(
            accessToken = accessToken,
            refreshToken = refreshToken,
            userId = user.id,
            expiresTime = Clock.System.now().toEpochMilliseconds() + ACCESS_TOKEN_EXPIRES * 1000,
            username = user.username,
            nickname = user.nickname
        )
    }

    suspend fun logout(userId: Long) {
        log.info("User logged out: $userId")
    }

    suspend fun refreshToken(refreshToken: String): LoginResponse {
        val verifiedToken = try {
            jwt.verifyToken(refreshToken)
        } catch (_: AuthenticationException) {
            throw BadRequestException("Invalid or expired refresh token")
        }
        if (verifiedToken.claimString("type") != "refresh") {
            throw BadRequestException("Invalid or expired refresh token")
        }
        val userId = verifiedToken.identity.userId.value.toLong()

        val user = UserTable.get(userId)
            ?: throw NotFoundException("User not found")

        if (user.status == 0) {
            throw BadRequestException("User account is disabled")
        }

        // Fetch user roles
        val userRoles = UserRoleTable.query {
            where { UserRole::userId eq user.id }
        }.list()
        val roleIds = userRoles.map { it.roleId }
        val roles = if (roleIds.isNotEmpty()) {
            RoleTable.query {
                where { Role::id `in` roleIds }
            }.list().map { it.code }.toSet()
        } else emptySet()

        val newAccessToken = jwt.createToken(
            userId = UserId(user.id.toULong()),
            roles = roles,
            // P0 granular RBAC：把解析出的权限写进 access token（super→*:*:*，普通→role_menu 子集）。
            permissions = permissionLogic?.resolvePermissions(user.id) ?: emptySet(),
            expiresInSeconds = ACCESS_TOKEN_EXPIRES,
            extraClaims = mapOf("type" to "access", "username" to user.username)
        )
        val newRefreshToken = jwt.createToken(
            userId = UserId(user.id.toULong()),
            roles = emptySet(),
            permissions = emptySet(),
            expiresInSeconds = REFRESH_TOKEN_EXPIRES,
            extraClaims = mapOf("type" to "refresh", "username" to user.username)
        )

        return LoginResponse(
            accessToken = newAccessToken,
            refreshToken = newRefreshToken,
            userId = user.id,
            expiresTime = Clock.System.now().toEpochMilliseconds() + ACCESS_TOKEN_EXPIRES * 1000,
            username = user.username,
            nickname = user.nickname
        )
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

        // Fetch user roles
        val userRoles = UserRoleTable.query {
            where { UserRole::userId eq user.id }
        }.list()
        val roleIds = userRoles.map { it.roleId }
        val roles = if (roleIds.isNotEmpty()) {
            RoleTable.query {
                where { Role::id `in` roleIds }
            }.list().map { it.code }.toSet()
        } else emptySet()

        val accessToken = jwt.createToken(
            userId = UserId(user.id.toULong()),
            roles = roles,
            // P0 granular RBAC：把解析出的权限写进 access token（super→*:*:*，普通→role_menu 子集）。
            permissions = permissionLogic?.resolvePermissions(user.id) ?: emptySet(),
            expiresInSeconds = ACCESS_TOKEN_EXPIRES,
            extraClaims = mapOf("type" to "access", "username" to user.username)
        )
        val refreshToken = jwt.createToken(
            userId = UserId(user.id.toULong()),
            roles = emptySet(),
            permissions = emptySet(),
            expiresInSeconds = REFRESH_TOKEN_EXPIRES,
            extraClaims = mapOf("type" to "refresh", "username" to user.username)
        )

        log.info("User SMS logged in: ${user.username}")

        return LoginResponse(
            accessToken = accessToken,
            refreshToken = refreshToken,
            userId = user.id,
            expiresTime = Clock.System.now().toEpochMilliseconds() + ACCESS_TOKEN_EXPIRES * 1000,
            username = user.username,
            nickname = user.nickname
        )
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

        // Fetch user roles
        val userRoles = UserRoleTable.query {
            where { UserRole::userId eq user.id }
        }.list()
        val roleIds = userRoles.map { it.roleId }
        val roles = if (roleIds.isNotEmpty()) {
            RoleTable.query {
                where { Role::id `in` roleIds }
            }.list().map { it.code }.toSet()
        } else emptySet()

        val accessToken = jwt.createToken(
            userId = UserId(user.id.toULong()),
            roles = roles,
            // P0 granular RBAC：把解析出的权限写进 access token（super→*:*:*，普通→role_menu 子集）。
            permissions = permissionLogic?.resolvePermissions(user.id) ?: emptySet(),
            expiresInSeconds = ACCESS_TOKEN_EXPIRES,
            extraClaims = mapOf("type" to "access", "username" to user.username)
        )
        val refreshToken = jwt.createToken(
            userId = UserId(user.id.toULong()),
            roles = emptySet(),
            permissions = emptySet(),
            expiresInSeconds = REFRESH_TOKEN_EXPIRES,
            extraClaims = mapOf("type" to "refresh", "username" to user.username)
        )

        log.info("User social logged in: ${user.username} via $socialType")

        return LoginResponse(
            accessToken = accessToken,
            refreshToken = refreshToken,
            userId = user.id,
            expiresTime = Clock.System.now().toEpochMilliseconds() + ACCESS_TOKEN_EXPIRES * 1000,
            username = user.username,
            nickname = user.nickname
        )
    }
}
