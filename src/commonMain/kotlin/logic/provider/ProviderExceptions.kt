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

/**
 * 消息渠道（短信 apiUrl、邮件 endpoint / SMTP 主机）配置的地址被运维出站策略拒绝：解析到回环 / 内网 / 链路本地、
 * 端口不在放行列表、或解析不出。一个包都没发出去。放行内网中继要在部署配置 application.conf `[egress]` 里做。
 */
class MessageEndpointForbiddenException :
    HttpException(NetonErrorCode.SERVICE_UNAVAILABLE, logic.SystemErrorCodes.MESSAGE_ENDPOINT_FORBIDDEN)
