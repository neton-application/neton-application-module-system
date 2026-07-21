package controller.admin.message

import controller.admin.message.dto.MessageLogVO
import dto.PageResponse
import model.MessageChannel
import model.MessageLog
import table.MessageChannelTable
import table.MessageLogTable
import neton.core.annotations.*
import neton.core.http.NotFoundException
import neton.database.dsl.*

@Controller("/system/sms-log")
class SmsLogController {

    @Get("/page")
    @Permission("system:sms-log:query")
    suspend fun page(
        @Query pageNo: Int = 1,
        @Query pageSize: Int = 10,
        @Query channelId: Long? = null,
        @Query templateCode: String? = null,
        @Query receiver: String? = null,
        @Query sendStatus: Int? = null
    ): PageResponse<MessageLogVO> {
        // SMS-typed records 来自两类：
        // 1. 配置了 SMS channel 的真实发送（channelId in smsChannelIds）
        // 2. 无 channel 配置时由 [MessageSendLogic.sendVerificationCode] 写入的 dev / fallback
        //    记录（templateCode 以 `sms_` 开头）
        // 无论 SMS channel 表是否为空，过滤逻辑都覆盖这两类，保证后台始终能看到发码记录。
        val smsChannelIds = MessageChannelTable.query {
            where { MessageChannel::type eq "sms" }
        }.list().map { it.id }

        val result = MessageLogTable.query {
            where {
                and(
                    or(
                        // smsChannelIds 为空时跳过这一支（PredicateScope.or 会过滤掉 True），
                        // 退化为只按 sms_ templateCode 前缀匹配 dev / fallback 记录。
                        if (smsChannelIds.isNotEmpty()) MessageLog::channelId `in` smsChannelIds else Predicate.True,
                        MessageLog::templateCode like "sms_%",
                    ),
                    whenPresent(channelId) { MessageLog::channelId eq it },
                    whenNotBlank(templateCode) { MessageLog::templateCode eq it },
                    whenNotBlank(receiver) { MessageLog::receiver like "%$it%" },
                    whenPresent(sendStatus) { MessageLog::sendStatus eq it }
                )
            }
            orderBy(MessageLog::id.desc())
        }.page(pageNo, pageSize)

        val items = result.items.map { it.toVO() }
        return PageResponse(
            list = items,
            total = result.total,
            page = pageNo,
            size = pageSize,
            totalPages = ((result.total + pageSize - 1) / pageSize).toInt()
        )
    }

    private fun MessageLog.toVO() = MessageLogVO(
        id = id,
        channelId = channelId,
        templateId = templateId,
        templateCode = templateCode,
        receiver = receiver,
        content = content,
        params = params,
        sendStatus = sendStatus,
        sendTime = sendTime,
        errorMessage = errorMessage,
        userId = userId,
        userType = userType,
        createdAt = createdAt
    )
}
