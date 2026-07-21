package logic

import controller.admin.social.dto.SocialUserVO
import logic.provider.SocialProvider
import logic.provider.SocialUserInfo
import model.SocialUser
import table.SocialUserTable
import neton.core.http.BadRequestException
import neton.core.http.NotFoundException
import neton.logging.Logger
import neton.database.dsl.*


class SocialUserLogic(
    private val log: Logger,
    private val socialProviders: Map<String, SocialProvider> = emptyMap()
) {

    /**
     * Get OAuth2 redirect URL for a social login type.
     */
    suspend fun getAuthRedirectUrl(socialType: String, redirectUri: String): String {
        val provider = socialProviders[socialType]
            ?: throw BadRequestException("Social provider not configured: $socialType")

        // In production, config would come from MessageChannel or app settings
        return provider.getAuthRedirectUrl("{}", redirectUri)
    }

    /**
     * Exchange authorization code for social user info, then find or create binding.
     * Returns the bound userId (0 if not yet bound to any user).
     */
    suspend fun socialLogin(
        socialType: String,
        code: String,
        redirectUri: String,
        userType: Int = 1  // 1=admin, 2=member
    ): SocialUser {
        val provider = socialProviders[socialType]
            ?: throw BadRequestException("Social provider not configured: $socialType")

        val userInfo = provider.getUserInfo("{}", code, redirectUri)

        if (userInfo.openId.isBlank()) {
            throw BadRequestException("Failed to get social user info")
        }

        // Find existing binding
        val existing = SocialUserTable.oneWhere {
            and(
                SocialUser::socialType eq socialType,
                SocialUser::openId eq userInfo.openId,
                SocialUser::userType eq userType
            )
        }

        if (existing != null) {
            // Update token info
            SocialUserTable.update(existing.copy(
                token = userInfo.rawTokenInfo,
                rawTokenInfo = userInfo.rawTokenInfo,
                nickname = userInfo.nickname ?: existing.nickname,
                avatar = userInfo.avatar ?: existing.avatar,
                rawUserInfo = userInfo.rawUserInfo ?: existing.rawUserInfo
            ))
            return existing
        }

        // Create new social user (not bound to any user yet)
        val socialUser = SocialUser(
            userId = 0,
            userType = userType,
            socialType = socialType,
            openId = userInfo.openId,
            token = userInfo.rawTokenInfo,
            rawTokenInfo = userInfo.rawTokenInfo,
            nickname = userInfo.nickname,
            avatar = userInfo.avatar,
            rawUserInfo = userInfo.rawUserInfo
        )
        return SocialUserTable.insert(socialUser)
    }

    /**
     * Bind a social account to an existing user.
     */
    suspend fun bind(
        userId: Long,
        userType: Int,
        socialType: String,
        code: String,
        redirectUri: String
    ): SocialUserVO {
        val provider = socialProviders[socialType]
            ?: throw BadRequestException("Social provider not configured: $socialType")

        val userInfo = provider.getUserInfo("{}", code, redirectUri)
        if (userInfo.openId.isBlank()) {
            throw BadRequestException("Failed to get social user info")
        }

        // Check if already bound
        val existing = SocialUserTable.oneWhere {
            and(
                SocialUser::userId eq userId,
                SocialUser::userType eq userType,
                SocialUser::socialType eq socialType
            )
        }

        if (existing != null) {
            throw BadRequestException("Social account already bound")
        }

        // Check if this social account is bound to another user
        val otherBinding = SocialUserTable.oneWhere {
            and(
                SocialUser::socialType eq socialType,
                SocialUser::openId eq userInfo.openId,
                SocialUser::userType eq userType
            )
        }

        if (otherBinding != null && otherBinding.userId != 0L) {
            throw BadRequestException("This social account is already bound to another user")
        }

        val socialUser = if (otherBinding != null) {
            // Update the unbound record to bind to this user
            val updated = otherBinding.copy(userId = userId)
            SocialUserTable.update(updated)
            updated
        } else {
            val newUser = SocialUser(
                userId = userId,
                userType = userType,
                socialType = socialType,
                openId = userInfo.openId,
                nickname = userInfo.nickname,
                avatar = userInfo.avatar,
                rawUserInfo = userInfo.rawUserInfo,
                rawTokenInfo = userInfo.rawTokenInfo
            )
            SocialUserTable.insert(newUser)
        }

        return socialUser.toVO()
    }

    /**
     * Unbind a social account from a user.
     */
    suspend fun unbind(userId: Long, userType: Int, socialType: String) {
        val existing = SocialUserTable.oneWhere {
            and(
                SocialUser::userId eq userId,
                SocialUser::userType eq userType,
                SocialUser::socialType eq socialType
            )
        } ?: throw NotFoundException("Social binding not found")

        SocialUserTable.destroy(existing.id)
    }

    /**
     * List social bindings for a user.
     */
    suspend fun listByUser(userId: Long, userType: Int): List<SocialUserVO> {
        return SocialUserTable.query {
            where {
                and(
                    SocialUser::userId eq userId,
                    SocialUser::userType eq userType
                )
            }
        }.list().map { it.toVO() }
    }

    /**
     * Get social user by id.
     */
    suspend fun getById(id: Long): SocialUserVO {
        val socialUser = SocialUserTable.get(id)
            ?: throw NotFoundException("Social user not found")
        return socialUser.toVO()
    }

    private fun SocialUser.toVO() = SocialUserVO(
        id = id,
        userId = userId,
        userType = userType,
        socialType = socialType,
        openId = openId,
        nickname = nickname,
        avatar = avatar,
        createdAt = createdAt
    )
}
