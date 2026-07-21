package controller.admin.message

import controller.admin.message.dto.CreateMessageTemplateRequest
import controller.admin.message.dto.MessageTemplateVO
import controller.admin.message.dto.SendMessageRequest
import controller.admin.message.dto.UpdateMessageTemplateRequest
import dto.PageResponse
import logic.MessageSendLogic
import logic.MessageTemplateLogic
import model.MessageTemplate
import kotlinx.serialization.Serializable
import neton.core.annotations.*
import neton.validation.annotations.NotBlank
import neton.validation.annotations.Size

@Serializable
data class MailSendRequest(
    @property:NotBlank
    @property:Size(min = 2, max = 128)
    val templateCode: String,

    @property:Size(min = 1, max = 50)
    val toMails: List<String>,

    @property:Size(min = 0, max = 50)
    val ccMails: List<String> = emptyList(),

    @property:Size(min = 0, max = 50)
    val bccMails: List<String> = emptyList(),
    val templateParams: Map<String, String> = emptyMap()
)

@Controller("/system/mail-template")
class MailTemplateController(
    private val messageTemplateLogic: MessageTemplateLogic,
    private val messageSendLogic: MessageSendLogic
) {

    @Get("/page")
    @Permission("system:mail-template:query")
    suspend fun page(
        @Query pageNo: Int = 1,
        @Query pageSize: Int = 10,
        @Query name: String? = null,
        @Query code: String? = null,
        @Query status: Int? = null
    ): PageResponse<MessageTemplateVO> {
        return messageTemplateLogic.pageByChannelType(pageNo, pageSize, "email", name, code, status)
    }

    @Get("/get/{id}")
    @Permission("system:mail-template:query")
    suspend fun get(@PathVariable id: Long): MessageTemplateVO {
        return messageTemplateLogic.getById(id)
    }

    @Post("/create")
    @Permission("system:mail-template:create")
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
    @Permission("system:mail-template:update")
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
    @Permission("system:mail-template:delete")
    suspend fun delete(@PathVariable id: Long) {
        messageTemplateLogic.delete(id)
    }

    @Post("/send-mail")
    @Permission("system:mail-template:update")
    suspend fun sendMail(@Body request: MailSendRequest): Boolean {
        return messageSendLogic.sendByTemplate(
            templateCode = request.templateCode,
            receiver = request.toMails.joinToString(","),
            params = request.templateParams
        )
    }
}
