package logic

import controller.admin.notice.dto.CreateNoticeRequest
import controller.admin.notice.dto.NoticeVO
import controller.admin.notice.dto.UpdateNoticeRequest
import dto.PageResponse
import model.Notice
import table.NoticeTable
import neton.core.http.NotFoundException
import neton.database.dsl.*

import neton.logging.Logger

@neton.core.annotations.Logic(logger = "logic.notice")
class NoticeLogic(
    private val log: Logger
) {

    suspend fun page(
        page: Int,
        size: Int,
        title: String? = null,
        type: Int? = null,
        status: Int? = null
    ): PageResponse<NoticeVO> {
        val result = NoticeTable.query {
            where {
                and(
                    whenNotBlank(title) { Notice::title like "%$it%" },
                    whenPresent(type) { Notice::type eq it },
                    whenPresent(status) { Notice::status eq it }
                )
            }
            orderBy(Notice::id.desc())
        }.page(page, size)

        val items = result.items.map { it.toVO() }
        return PageResponse(
            list = items,
            total = result.total,
            page = page,
            size = size,
            totalPages = ((result.total + size - 1) / size).toInt()
        )
    }

    suspend fun getById(id: Long): NoticeVO {
        val notice = NoticeTable.get(id)
            ?: throw NotFoundException("Notice not found")
        return notice.toVO()
    }

    suspend fun create(request: CreateNoticeRequest): Long {
        return NoticeTable.insert(
            Notice(
                title = request.title,
                content = request.content,
                type = request.type,
                status = request.status
            )
        ).id
    }

    suspend fun update(request: UpdateNoticeRequest) {
        NoticeTable.get(request.id)
            ?: throw NotFoundException("Notice not found")
        NoticeTable.update(
            Notice(
                id = request.id,
                title = request.title,
                content = request.content,
                type = request.type,
                status = request.status
            )
        )
    }

    suspend fun delete(id: Long) {
        NoticeTable.get(id)
            ?: throw NotFoundException("Notice not found")
        NoticeTable.destroy(id)
    }

    private fun Notice.toVO() = NoticeVO(
        id = id,
        title = title,
        content = content,
        type = type,
        status = status,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}
