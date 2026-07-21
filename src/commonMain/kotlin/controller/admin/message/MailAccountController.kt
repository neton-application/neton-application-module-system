package controller.admin.message

import controller.admin.message.dto.CreateMailAccountRequest
import controller.admin.message.dto.MailAccountVO
import controller.admin.message.dto.UpdateMailAccountRequest
import dto.PageResponse
import logic.MessageChannelLogic
import neton.core.annotations.*

@Controller("/system/mail-account")
class MailAccountController(
    private val messageChannelLogic: MessageChannelLogic
) {

    @Get("/page")
    @Permission("system:mail-account:query")
    suspend fun page(
        @Query pageNo: Int = 1,
        @Query pageSize: Int = 10,
        @Query mail: String? = null,
        @Query status: Int? = null
    ): PageResponse<MailAccountVO> {
        val result = messageChannelLogic.pageRaw(pageNo, pageSize, mail, type = "email", status)
        val items = result.list.map { MailAccountVO.fromChannel(it) }
        return PageResponse(list = items, total = result.total, page = pageNo, size = pageSize,
            totalPages = ((result.total + pageSize - 1) / pageSize).toInt())
    }

    @Get("/get/{id}")
    @Permission("system:mail-account:query")
    suspend fun get(@PathVariable id: Long): MailAccountVO {
        return MailAccountVO.fromChannel(messageChannelLogic.getRawById(id))
    }

    @Get("/simple-list")
    @Permission("system:mail-account:query")
    suspend fun simpleList(): List<MailAccountVO> {
        return messageChannelLogic.listRawByType("email").map { MailAccountVO.fromChannel(it) }
    }

    @Post("/create")
    @Permission("system:mail-account:create")
    suspend fun create(@Body request: CreateMailAccountRequest): Long {
        return messageChannelLogic.create(
            MailAccountVO(
                mail = request.mail,
                username = request.username,
                password = request.password,
                host = request.host,
                port = request.port,
                sslEnable = request.sslEnable,
                starttlsEnable = request.starttlsEnable,
                status = request.status,
                remark = request.remark
            ).toChannel()
        )
    }

    @Put("/update")
    @Permission("system:mail-account:update")
    suspend fun update(@Body request: UpdateMailAccountRequest) {
        messageChannelLogic.update(
            MailAccountVO(
                id = request.id,
                mail = request.mail,
                username = request.username,
                password = request.password,
                host = request.host,
                port = request.port,
                sslEnable = request.sslEnable,
                starttlsEnable = request.starttlsEnable,
                status = request.status,
                remark = request.remark
            ).toChannel()
        )
    }

    @Delete("/delete/{id}")
    @Permission("system:mail-account:delete")
    suspend fun delete(@PathVariable id: Long) {
        messageChannelLogic.delete(id)
    }

    @Delete("/delete-list")
    @Permission("system:mail-account:delete")
    suspend fun deleteList(@Query ids: String) {
        ids.split(",").mapNotNull { it.trim().toLongOrNull() }.forEach { messageChannelLogic.delete(it) }
    }
}
