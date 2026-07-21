package controller.admin.message

import controller.admin.message.dto.MessageLogVO
import dto.PageResponse
import model.MessageLog
import table.MessageLogTable
import neton.core.annotations.*
import neton.core.http.NotFoundException
import neton.database.dsl.*


@Controller("/system/message-log")
class MessageLogController {

    @Get("/page")
    @Permission("system:message-log:query")
    suspend fun page(
        @Query pageNo: Int = 1,
        @Query pageSize: Int = 10,
        @Query channelId: Long? = null,
        @Query templateCode: String? = null,
        @Query receiver: String? = null,
        @Query sendStatus: Int? = null
    ): PageResponse<MessageLogVO> {
        val result = MessageLogTable.query {
            where {
                and(
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

    @Get("/get/{id}")
    @Permission("system:message-log:query")
    suspend fun get(@PathVariable id: Long): MessageLogVO {
        val log = MessageLogTable.get(id)
            ?: throw NotFoundException("Message log not found")
        return log.toVO()
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
