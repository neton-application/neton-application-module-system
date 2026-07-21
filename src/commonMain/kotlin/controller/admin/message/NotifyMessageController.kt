package controller.admin.message

import controller.admin.message.dto.NotifyMessageVO
import dto.PageResponse
import logic.NotifyMessageLogic
import neton.core.annotations.*
import neton.core.interfaces.Identity

@Controller("/system/notify-message")
class NotifyMessageController(
    private val messageLogic: NotifyMessageLogic
) {

    @Get("/page")
    @Permission("system:notify-message:query")
    suspend fun page(
        @Query pageNo: Int = 1,
        @Query pageSize: Int = 10,
        @Query userId: Long? = null,
        @Query templateCode: String? = null,
        @Query templateType: Int? = null,
        @Query readStatus: Int? = null
    ): PageResponse<NotifyMessageVO> =
        messageLogic.page(pageNo, pageSize, userId, templateCode, templateType, readStatus)

    @Get("/my-page")
    @Permission("system:notify-message:query")
    suspend fun myPage(
        @Query pageNo: Int = 1,
        @Query pageSize: Int = 10,
        @Query readStatus: Int? = null,
        identity: Identity
    ): PageResponse<NotifyMessageVO> =
        messageLogic.myPage(identity.id.toLong(), pageNo, pageSize, readStatus)

    @Put("/update-read")
    @Permission("system:notify-message:update")
    suspend fun updateRead(@Query ids: String) {
        messageLogic.updateRead(ids.split(",").mapNotNull { it.trim().toLongOrNull() })
    }

    @Put("/update-all-read")
    @Permission("system:notify-message:update")
    suspend fun updateAllRead(identity: Identity) {
        messageLogic.updateAllRead(identity.id.toLong())
    }

    @Get("/get-unread-list")
    @Permission("system:notify-message:query")
    suspend fun getUnreadList(identity: Identity): List<NotifyMessageVO> =
        messageLogic.getUnreadList(identity.id.toLong())

    @Get("/get-unread-count")
    @Permission("system:notify-message:query")
    suspend fun getUnreadCount(identity: Identity): Long =
        messageLogic.getUnreadCount(identity.id.toLong())
}
