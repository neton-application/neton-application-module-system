package controller.admin.message

import controller.admin.message.dto.MessageLogVO
import dto.PageResponse
import model.MessageChannel
import model.MessageLog
import table.MessageChannelTable
import table.MessageLogTable
import neton.core.annotations.*
import neton.database.dsl.*

@Controller("/system/mail-log")
class MailLogController {

    @Get("/page")
    @Permission("system:mail-log:query")
    suspend fun page(
        @Query pageNo: Int = 1,
        @Query pageSize: Int = 10,
        @Query channelId: Long? = null,
        @Query templateCode: String? = null,
        @Query receiver: String? = null,
        @Query sendStatus: Int? = null
    ): PageResponse<MessageLogVO> {
        val emailChannelIds = MessageChannelTable.query {
            where { MessageChannel::type eq "email" }
        }.list().map { it.id }

        if (emailChannelIds.isEmpty()) {
            return PageResponse(list = emptyList(), total = 0, page = pageNo, size = pageSize, totalPages = 0)
        }

        val result = MessageLogTable.query {
            where {
                and(
                    MessageLog::channelId `in` emailChannelIds,
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
