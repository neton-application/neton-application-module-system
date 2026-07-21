package logic.provider

/**
 * Unified message provider interface for SMS, Email, Telegram, etc.
 */
interface MessageProvider {
    /** Provider type identifier (e.g. "sms_aliyun", "email_smtp", "telegram") */
    val type: String

    /**
     * Send a message through this provider.
     * @param receiver The target (phone number, email address, telegram chat id)
     * @param content The rendered message content
     * @param config JSON config string from MessageChannel
     * @return true if sent successfully
     */
    suspend fun send(receiver: String, content: String, config: String): Boolean
}

/**
 * Social login provider interface for Google, Telegram, etc.
 */
interface SocialProvider {
    /** Social type identifier (e.g. "google", "telegram") */
    val type: String

    /**
     * Get the OAuth2 redirect URL for the social login.
     * @param config JSON config from channel/app settings
     * @param redirectUri Callback URI after authorization
     * @return The authorization URL to redirect the user to
     */
    suspend fun getAuthRedirectUrl(config: String, redirectUri: String): String

    /**
     * Exchange authorization code for social user info.
     * @param config JSON config
     * @param code Authorization code from callback
     * @param redirectUri The same redirect URI used for authorization
     * @return SocialUserInfo with open_id, nickname, avatar
     */
    suspend fun getUserInfo(config: String, code: String, redirectUri: String): SocialUserInfo
}

data class SocialUserInfo(
    val openId: String,
    val nickname: String? = null,
    val avatar: String? = null,
    val rawUserInfo: String? = null,
    val rawTokenInfo: String? = null
)
