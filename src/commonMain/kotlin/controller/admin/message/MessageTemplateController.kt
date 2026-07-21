package controller.admin.message

import controller.admin.message.dto.CreateMessageTemplateRequest
import controller.admin.message.dto.MessageTemplateVO
import dto.PageResponse
import controller.admin.message.dto.SendMessageRequest
import controller.admin.message.dto.UpdateMessageTemplateRequest
import logic.MessageSendLogic
import logic.MessageTemplateLogic
import model.MessageTemplate
import neton.core.annotations.*

@Controller("/system/message-template")
class MessageTemplateController(
    private val messageTemplateLogic: MessageTemplateLogic,
    private val messageSendLogic: MessageSendLogic
) {

    @Get("/page")
    @Permission("system:message-template:query")
    suspend fun page(
        @Query pageNo: Int = 1,
        @Query pageSize: Int = 10,
        @Query name: String? = null,
        @Query code: String? = null,
        @Query type: Int? = null,
        @Query status: Int? = null
    ): PageResponse<MessageTemplateVO> {
        return messageTemplateLogic.page(pageNo, pageSize, name, code, type, status)
    }

    @Get("/get/{id}")
    @Permission("system:message-template:query")
    suspend fun get(@PathVariable id: Long): MessageTemplateVO {
        return messageTemplateLogic.getById(id)
    }

    @Post("/create")
    @Permission("system:message-template:create")
    suspend fun create(@Body request: CreateMessageTemplateRequest): Long {
        return messageTemplateLogic.create(
            MessageTemplate(
                name = request.name,
                code = request.code,
                content = request.content,
                params = request.params,
                channelId = request.channelId,
                type = request.type,
                status = request.status,
                remark = request.remark
            )
        )
    }

    @Put("/update")
    @Permission("system:message-template:update")
    suspend fun update(@Body request: UpdateMessageTemplateRequest) {
        messageTemplateLogic.update(
            MessageTemplate(
                id = request.id,
                name = request.name,
                code = request.code,
                content = request.content,
                params = request.params,
                channelId = request.channelId,
                type = request.type,
                status = request.status,
                remark = request.remark
            )
        )
    }

    @Delete("/delete/{id}")
    @Permission("system:message-template:delete")
    suspend fun delete(@PathVariable id: Long) {
        messageTemplateLogic.delete(id)
    }

    @Post("/send")
    @Permission("system:message-template:update")
    suspend fun send(@Body request: SendMessageRequest): Boolean {
        return messageSendLogic.sendByTemplate(
            templateCode = request.templateCode,
            receiver = request.receiver,
            params = request.params
        )
    }
}
