package controller.admin.notice

import controller.admin.notice.dto.CreateNoticeRequest
import controller.admin.notice.dto.NoticeVO
import controller.admin.notice.dto.UpdateNoticeRequest
import logic.NoticeLogic
import neton.core.annotations.*

@Controller("/system/notice")
class NoticeController(
    private val noticeLogic: NoticeLogic
) {

    @Get("/page")
    @Permission("system:notice:query")
    suspend fun page(
        @Query pageNo: Int = 1,
        @Query pageSize: Int = 10,
        @Query title: String? = null,
        @Query type: Int? = null,
        @Query status: Int? = null
    ) = noticeLogic.page(pageNo, pageSize, title, type, status)

    @Get("/get/{id}")
    @Permission("system:notice:query")
    suspend fun get(@PathVariable id: Long): NoticeVO = noticeLogic.getById(id)

    @Post("/create")
    @Permission("system:notice:create")
    suspend fun create(@Body request: CreateNoticeRequest): Long = noticeLogic.create(request)

    @Put("/update")
    @Permission("system:notice:update")
    suspend fun update(@Body request: UpdateNoticeRequest) = noticeLogic.update(request)

    @Delete("/delete/{id}")
    @Permission("system:notice:delete")
    suspend fun delete(@PathVariable id: Long) = noticeLogic.delete(id)
}
