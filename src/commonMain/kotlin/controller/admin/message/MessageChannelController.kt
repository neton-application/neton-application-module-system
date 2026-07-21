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
data class ChannelSendTestRequest(
    @property:Min(1)
    val channelId: Long,

    @property:NotBlank
    @property:Size(min = 2, max = 256)
    val receiver: String,

    @property:NotBlank
    @property:Size(min = 1, max = 4000)
    val content: String
)

@Controller("/system/message-channel")
class MessageChannelController(
    private val messageChannelLogic: MessageChannelLogic
) {

    @Get("/page")
    @Permission("system:message-channel:query")
    suspend fun page(
        @Query pageNo: Int = 1,
        @Query pageSize: Int = 10,
        @Query name: String? = null,
        @Query type: String? = null,
        @Query status: Int? = null
    ): PageResponse<MessageChannelVO> {
        return messageChannelLogic.page(pageNo, pageSize, name, type, status)
    }

    @Get("/get/{id}")
    @Permission("system:message-channel:query")
    suspend fun get(@PathVariable id: Long): MessageChannelVO {
        return messageChannelLogic.getById(id)
    }

    @Get("/simple-list")
    @Permission("system:message-channel:query")
    suspend fun listAllSimple(): List<MessageChannelVO> {
        return messageChannelLogic.listAllSimple()
    }

    @Post("/create")
    @Permission("system:message-channel:create")
    suspend fun create(@Body request: CreateMessageChannelRequest): Long {
        return messageChannelLogic.create(
            MessageChannel(
                name = request.name,
                code = request.code,
                type = request.type,
                config = request.config,
                status = request.status,
                remark = request.remark
            )
        )
    }

    @Put("/update")
    @Permission("system:message-channel:update")
    suspend fun update(@Body request: UpdateMessageChannelRequest) {
        messageChannelLogic.update(
            MessageChannel(
                id = request.id,
                name = request.name,
                code = request.code,
                type = request.type,
                config = request.config,
                status = request.status,
                remark = request.remark
            )
        )
    }

    @Delete("/delete/{id}")
    @Permission("system:message-channel:delete")
    suspend fun delete(@PathVariable id: Long) {
        messageChannelLogic.delete(id)
    }

    @Post("/send-test")
    @Permission("system:message-channel:update")
    suspend fun sendTest(@Body request: ChannelSendTestRequest): Boolean {
        return messageChannelLogic.sendTest(request.channelId, request.receiver, request.content)
    }
}
