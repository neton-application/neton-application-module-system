package controller.admin.message

import controller.admin.message.dto.CreateMessageChannelRequest
import controller.admin.message.dto.MessageChannelVO
import controller.admin.message.dto.UpdateMessageChannelRequest
import dto.PageResponse
import logic.MessageChannelLogic
import model.MessageChannel
import kotlinx.serialization.Serializable
import neton.core.annotations.*
import neton.validation.annotations.Min
import neton.validation.annotations.NotBlank
import neton.validation.annotations.Size

@Serializable
data class SmsChannelSendTestRequest(
    @property:Min(1)
    val channelId: Long,

    @property:NotBlank
    @property:Size(min = 2, max = 256)
    val receiver: String,

    @property:NotBlank
    @property:Size(min = 1, max = 4000)
    val content: String
)

@Controller("/system/sms-channel")
class SmsChannelController(
    private val messageChannelLogic: MessageChannelLogic
) {

    @Get("/page")
    @Permission("system:sms-channel:query")
    suspend fun page(
        @Query pageNo: Int = 1,
        @Query pageSize: Int = 10,
        @Query name: String? = null,
        @Query status: Int? = null
    ): PageResponse<MessageChannelVO> {
        return messageChannelLogic.page(pageNo, pageSize, name, type = "sms", status)
    }

    @Get("/get/{id}")
    @Permission("system:sms-channel:query")
    suspend fun get(@PathVariable id: Long): MessageChannelVO {
        return messageChannelLogic.getById(id)
    }

    @Get("/simple-list")
    @Permission("system:sms-channel:query")
    suspend fun simpleList(): List<MessageChannelVO> {
        return messageChannelLogic.listByType("sms")
    }

    @Post("/create")
    @Permission("system:sms-channel:create")
    suspend fun create(@Body request: CreateMessageChannelRequest): Long {
        return messageChannelLogic.create(
            MessageChannel(
                name = request.name,
                code = request.code,
                type = "sms",
                config = request.config,
                status = request.status,
                remark = request.remark
            )
        )
    }

    @Put("/update")
    @Permission("system:sms-channel:update")
    suspend fun update(@Body request: UpdateMessageChannelRequest) {
        messageChannelLogic.update(
            MessageChannel(
                id = request.id,
                name = request.name,
                code = request.code,
                type = "sms",
                config = request.config,
                status = request.status,
                remark = request.remark
            )
        )
    }

    @Delete("/delete/{id}")
    @Permission("system:sms-channel:delete")
    suspend fun delete(@PathVariable id: Long) {
        messageChannelLogic.delete(id)
    }

    @Post("/send-test")
    @Permission("system:sms-channel:update")
    suspend fun sendTest(@Body request: SmsChannelSendTestRequest): Boolean {
        return messageChannelLogic.sendTest(request.channelId, request.receiver, request.content)
    }
}
