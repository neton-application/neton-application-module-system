package controller.admin.message

import controller.admin.message.dto.CreateMessageTemplateRequest
import controller.admin.message.dto.MessageTemplateVO
import controller.admin.message.dto.SendMessageRequest
import controller.admin.message.dto.UpdateMessageTemplateRequest
import dto.PageResponse
import logic.MessageSendLogic
import logic.MessageTemplateLogic
import model.MessageTemplate
import neton.core.annotations.*

@Controller("/system/sms-template")
class SmsTemplateController(
    private val messageTemplateLogic: MessageTemplateLogic,
    private val messageSendLogic: MessageSendLogic
) {

    @Get("/page")
    @Permission("system:sms-template:query")
    suspend fun page(
        @Query pageNo: Int = 1,
        @Query pageSize: Int = 10,
        @Query name: String? = null,
        @Query code: String? = null,
        @Query status: Int? = null
    ): PageResponse<MessageTemplateVO> {
        return messageTemplateLogic.pageByChannelType(pageNo, pageSize, "sms", name, code, status)
    }

    @Get("/get/{id}")
    @Permission("system:sms-template:query")
    suspend fun get(@PathVariable id: Long): MessageTemplateVO {
        return messageTemplateLogic.getById(id)
    }

    @Post("/create")
    @Permission("system:sms-template:create")
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
    @Permission("system:sms-template:update")
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
    @Permission("system:sms-template:delete")
    suspend fun delete(@PathVariable id: Long) {
        messageTemplateLogic.delete(id)
    }

    @Post("/send-sms")
    @Permission("system:sms-template:update")
    suspend fun sendSms(@Body request: SendMessageRequest): Boolean {
        return messageSendLogic.sendByTemplate(
            templateCode = request.templateCode,
            receiver = request.receiver,
            params = request.params
        )
    }
}
