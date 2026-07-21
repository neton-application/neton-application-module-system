package logic

import controller.admin.message.dto.NotifyMessageVO
import dto.PageResponse
import model.NotifyMessage
import table.NotifyMessageTable
import neton.core.http.NotFoundException
import neton.database.dsl.*

import neton.logging.Logger

@neton.core.annotations.Logic(logger = "logic.notify-message")
class NotifyMessageLogic(
    private val log: Logger
) {

    suspend fun page(
        page: Int,
        size: Int,
        userId: Long? = null,
        templateCode: String? = null,
        templateType: Int? = null,
        readStatus: Int? = null
    ): PageResponse<NotifyMessageVO> {
        val result = NotifyMessageTable.query {
            where {
                and(
                    whenPresent(userId) { NotifyMessage::userId eq it },
                    whenNotBlank(templateCode) { NotifyMessage::templateCode eq it },
                    whenPresent(templateType) { NotifyMessage::templateType eq it },
                    whenPresent(readStatus) { NotifyMessage::readStatus eq it }
                )
            }
            orderBy(NotifyMessage::id.desc())
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

    suspend fun myPage(
        userId: Long,
        page: Int,
        size: Int,
        readStatus: Int? = null
    ): PageResponse<NotifyMessageVO> {
        val result = NotifyMessageTable.query {
            where {
                and(
                    NotifyMessage::userId eq userId,
                    whenPresent(readStatus) { NotifyMessage::readStatus eq it }
                )
            }
            orderBy(NotifyMessage::id.desc())
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

    suspend fun updateRead(ids: List<Long>) {
        for (id in ids) {
            val msg = NotifyMessageTable.get(id) ?: continue
            NotifyMessageTable.update(msg.copy(readStatus = 1))
        }
    }

    suspend fun updateAllRead(userId: Long) {
        val unread = NotifyMessageTable.query {
            where {
                and(
                    NotifyMessage::userId eq userId,
                    NotifyMessage::readStatus eq 0
                )
            }
        }.list()
        for (msg in unread) {
            NotifyMessageTable.update(msg.copy(readStatus = 1))
        }
    }

    suspend fun getUnreadList(userId: Long): List<NotifyMessageVO> {
        return NotifyMessageTable.query {
            where {
                and(
                    NotifyMessage::userId eq userId,
                    NotifyMessage::readStatus eq 0
                )
            }
            orderBy(NotifyMessage::id.desc())
            limitOffset(10, 0)
        }.list().map { it.toVO() }
    }

    suspend fun getUnreadCount(userId: Long): Long {
        return NotifyMessageTable.query {
            where {
                and(
                    NotifyMessage::userId eq userId,
                    NotifyMessage::readStatus eq 0
                )
            }
        }.list().size.toLong()
    }

    private fun NotifyMessage.toVO() = NotifyMessageVO(
        id = id,
        userId = userId,
        userType = userType,
        templateId = templateId,
        templateCode = templateCode,
        templateType = templateType,
        templateNickname = templateNickname,
        templateContent = templateContent,
        templateParams = templateParams,
        readStatus = readStatus,
        readTime = readTime,
        createdAt = createdAt
    )
}
