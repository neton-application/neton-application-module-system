package logic

import neton.core.http.NetonErrorCode
import neton.core.http.HttpException
import model.MessageChannel
import model.MessageLog
import table.MessageChannelTable
import table.MessageLogTable
import neton.core.http.NotFoundException
import neton.logging.Logger
import neton.database.dsl.*

import kotlin.time.Clock

/**
 * 消息投递：按模板 / 通道把内容发出去并写发送记录。
 *
 * 验证码不在这里生成、存储或核验 —— 那是 [VerificationCodeService] 的事；这里只负责把它送达。
 * 发送记录里不允许出现秘密：投递验证码时，正文与参数里的码在落库前替换成 [REDACTED]（评审 R07）。
 */
class MessageSendLogic(
    private val log: Logger,
    private val messageChannelLogic: MessageChannelLogic,
    private val messageTemplateLogic: MessageTemplateLogic,
) {

    enum class Delivery { SENT, FAILED, UNCONFIGURED }

    /**
     * 投递一条验证码。优先用模板 [templateCode]；邮件没有模板时退回到任一启用的邮箱通道发默认文案。
     * 都没有 → [Delivery.UNCONFIGURED]，调用方据此报服务不可用（开发环境的模拟通道根本不走到这里）。
     */
    suspend fun deliverCode(channelType: String, receiver: String, templateCode: String, code: String): Delivery {
        val template = runCatching { messageTemplateLogic.getByCode(templateCode) }.getOrNull()
        if (template != null) {
            return if (sendByTemplate(templateCode, receiver, mapOf("code" to code), redact = setOf(code))) Delivery.SENT else Delivery.FAILED
        }
        if (channelType != "email") return Delivery.UNCONFIGURED
        val channel = messageChannelLogic.listRawByType("email").firstOrNull { it.status == 1 } ?: return Delivery.UNCONFIGURED
        val provider = messageChannelLogic.getProvider("email") ?: return Delivery.UNCONFIGURED
        val content = "Subject: 验证码\n您的验证码是 $code，5 分钟内有效。如非本人操作请忽略。"
        val logged = content.replace(code, REDACTED)
        return try {
            val ok = provider.send(receiver, content, channel.config ?: "{}")
            logMessage(channel.id, null, templateCode, receiver, logged, null, if (ok) 0 else 1, null, null, 0)
            if (ok) Delivery.SENT else Delivery.FAILED
        } catch (e: Exception) {
            log.error("message.code.send_failed", mapOf("channel" to "email", "error" to (e.message ?: "")))
            logMessage(channel.id, null, templateCode, receiver, logged, null, 1, e.message, null, 0)
            Delivery.FAILED
        }
    }

    /**
     * Send a message using a template code and parameters.
     * Flow: lookup template → lookup channel → render content → send via provider → log result
     *
     * [redact] 里的值（如验证码）在写发送记录前从正文和参数里抹掉；发送本身用原文。
     */
    suspend fun sendByTemplate(
        templateCode: String,
        receiver: String,
        params: Map<String, String>,
        userId: Long? = null,
        userType: Int = 0,
        redact: Set<String> = emptySet(),
    ): Boolean {
        val template = messageTemplateLogic.getByCode(templateCode)
            ?: throw NotFoundException(SystemErrorCodes.MESSAGE_TEMPLATE_NOT_FOUND)

        val channel = MessageChannelTable.get(template.channelId)
            ?: throw NotFoundException(SystemErrorCodes.MESSAGE_CHANNEL_NOT_FOUND)

        val content = messageTemplateLogic.renderContent(template.content, params)

        val provider = messageChannelLogic.getProvider(channel.type)
            ?: throw HttpException(NetonErrorCode.SERVICE_UNAVAILABLE, SystemErrorCodes.MESSAGE_PROVIDER_UNAVAILABLE)

        val loggedContent = redact.fold(content) { acc, secret -> if (secret.isEmpty()) acc else acc.replace(secret, REDACTED) }
        val loggedParams = params.mapValues { (_, v) -> if (v in redact) REDACTED else v }.toString()

        val success = try {
            provider.send(receiver, content, channel.config ?: "{}")
        } catch (e: Exception) {
            log.error("message.send_failed", mapOf("channel" to channel.type, "template" to templateCode, "error" to (e.message ?: "")))
            logMessage(channel.id, template.id, templateCode, receiver, loggedContent,
                loggedParams, 1, e.message, userId, userType)
            return false
        }

        logMessage(channel.id, template.id, templateCode, receiver, loggedContent,
            loggedParams, if (success) 0 else 1, null, userId, userType)

        return success
    }

    private suspend fun logMessage(
        channelId: Long,
        templateId: Long?,
        templateCode: String?,
        receiver: String,
        content: String?,
        params: String?,
        sendStatus: Int,
        errorMessage: String?,
        userId: Long?,
        userType: Int
    ) {
        val messageLog = MessageLog(
            channelId = channelId,
            templateId = templateId,
            templateCode = templateCode,
            receiver = receiver,
            content = content,
            params = params,
            sendStatus = sendStatus,
            sendTime = Clock.System.now().toEpochMilliseconds(),
            errorMessage = errorMessage,
            userId = userId,
            userType = userType
        )
        try {
            MessageLogTable.insert(messageLog)
        } catch (e: Exception) {
            log.error("Failed to log message: ${e.message}")
        }
    }

    companion object {
        const val REDACTED = "******"
    }
}
