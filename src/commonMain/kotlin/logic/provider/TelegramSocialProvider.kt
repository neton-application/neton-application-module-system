package logic.provider

import logic.SystemErrorCodes
import neton.logging.Logger

/**
 * Telegram social login provider (v1 placeholder — no real widget integration yet).
 *
 * Telegram uses a widget-based login (hash validation), not standard OAuth2. Does NOT
 * fake success: until the widget hash validation and user extraction are implemented,
 * both methods throw [ProviderNotImplementedException] rather than returning an empty
 * URL or an empty-`openId` [SocialUserInfo] — an empty identity must never enter the login path.
 */
class TelegramSocialProvider(
    private val log: Logger
) : SocialProvider {

    override val type: String = "telegram"

    override suspend fun getAuthRedirectUrl(config: String, redirectUri: String): String {
        log.warn("social.telegram.not_implemented method=getAuthRedirectUrl")
        throw ProviderNotImplementedException(SystemErrorCodes.SOCIAL_PROVIDER_NOT_IMPLEMENTED)
    }

    override suspend fun getUserInfo(config: String, code: String, redirectUri: String): SocialUserInfo {
        log.warn("social.telegram.not_implemented method=getUserInfo")
        throw ProviderNotImplementedException(SystemErrorCodes.SOCIAL_PROVIDER_NOT_IMPLEMENTED)
    }
}
