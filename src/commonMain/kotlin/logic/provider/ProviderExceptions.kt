package logic.provider

import neton.core.http.HttpException
import neton.core.http.NetonErrorCode

/**
 * A message/social provider was invoked but its real integration is not implemented.
 *
 * v1 ships structural placeholders for SMS / Email / Google / Telegram that do NOT
 * call any external API. Those placeholders MUST NOT fake success (return `true` from
 * `send()`, or an empty-`openId` [SocialUserInfo] from `getUserInfo()`) — a fake success
 * on the login / verification-code path silently breaks auth and message delivery.
 *
 * Instead they throw this. Maps to HTTP 503 Service Unavailable via
 * [NetonErrorCode.SERVICE_UNAVAILABLE]. For message providers the throw is caught by
 * `MessageSendLogic` and recorded as a delivery failure; for social providers it
 * propagates to the controller as an honest 503.
 *
 * To exercise these paths in dev/test, inject an explicitly-named provider double —
 * do not rely on a silently-succeeding placeholder.
 */
class ProviderNotImplementedException(message: String) :
    HttpException(NetonErrorCode.SERVICE_UNAVAILABLE, message)
