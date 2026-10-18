package logic.provider

import logic.SystemErrorCodes
import neton.logging.Logger

/**
 * Google OAuth2 social login provider (v1 placeholder — no real OAuth2 integration yet).
 *
 * Does NOT fake success: a real implementation would parse `config` for
 * client_id/client_secret, redirect to Google, exchange the code at
 * https://oauth2.googleapis.com/token and fetch userinfo. Until then both methods
 * throw [ProviderNotImplementedException] rather than returning a placeholder URL or an
 * empty-`openId` [SocialUserInfo] — an empty identity must never enter the login path.
 */
class GoogleSocialProvider(
    private val log: Logger
) : SocialProvider {

    override val type: String = "google"

    override suspend fun getAuthRedirectUrl(config: String, redirectUri: String): String {
        log.warn("social.google.not_implemented method=getAuthRedirectUrl")
        throw ProviderNotImplementedException(SystemErrorCodes.SOCIAL_PROVIDER_NOT_IMPLEMENTED)
    }

    override suspend fun getUserInfo(config: String, code: String, redirectUri: String): SocialUserInfo {
        log.warn("social.google.not_implemented method=getUserInfo")
        throw ProviderNotImplementedException(SystemErrorCodes.SOCIAL_PROVIDER_NOT_IMPLEMENTED)
    }
}
