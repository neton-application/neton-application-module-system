package logic

import controller.admin.message.dto.NotificationTemplateVO
import dto.PageResponse
import model.NotificationTemplate
import table.NotificationTemplateTable
import neton.core.http.BadRequestException
import neton.core.http.NotFoundException
import neton.logging.Logger
import neton.database.dsl.*


class NotificationTemplateLogic(
    private val log: Logger,
    private val messageSendLogic: MessageSendLogic
) {

    suspend fun page(
        page: Int,
        size: Int,
        name: String? = null,
        code: String? = null,
        status: Int? = null
    ): PageResponse<NotificationTemplateVO> {
        val result = NotificationTemplateTable.query {
            where {
                and(
                    whenNotBlank(name) { NotificationTemplate::name like "%$it%" },
                    whenNotBlank(code) { NotificationTemplate::code like "%$it%" },
                    whenPresent(status) { NotificationTemplate::status eq it }
                )
            }
            orderBy(NotificationTemplate::id.desc())
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

    suspend fun getById(id: Long): NotificationTemplateVO {
        val template = NotificationTemplateTable.get(id)
            ?: throw NotFoundException("Notification template not found")
        return template.toVO()
    }

    suspend fun create(template: NotificationTemplate): Long {
        val existing = NotificationTemplateTable.oneWhere {
            NotificationTemplate::code eq template.code
        }
        if (existing != null) {
            throw BadRequestException("Notification template code already exists")
        }
        return NotificationTemplateTable.insert(template).id
    }

    suspend fun update(template: NotificationTemplate) {
        NotificationTemplateTable.get(template.id)
            ?: throw NotFoundException("Notification template not found")
        NotificationTemplateTable.update(template)
    }

    suspend fun delete(id: Long) {
        NotificationTemplateTable.get(id)
            ?: throw NotFoundException("Notification template not found")
        NotificationTemplateTable.destroy(id)
    }

    /**
     * Send a notification using the notification template.
     * Resolves the linked message template and sends via MessageSendLogic.
     */
    suspend fun send(
        notificationCode: String,
        receiver: String,
        params: Map<String, String>,
        userId: Long? = null,
        userType: Int = 0
    ): Boolean {
        val notification = NotificationTemplateTable.oneWhere {
            NotificationTemplate::code eq notificationCode
        } ?: throw NotFoundException("Notification template not found: $notificationCode")

        if (notification.status == 0) {
            log.warn("Notification template $notificationCode is disabled")
            return false
        }

        val messageTemplate = notification.messageTemplateId
        if (messageTemplate == 0L) {
            throw BadRequestException("No message template linked for notification: $notificationCode")
        }

        val template = logic.MessageTemplateLogic::class.let {
            // Resolve the linked message template code
            val tmpl = table.MessageTemplateTable.get(messageTemplate)
                ?: throw NotFoundException("Linked message template not found: $messageTemplate")
            tmpl
        }

        return messageSendLogic.sendByTemplate(
            templateCode = template.code,
            receiver = receiver,
            params = params,
            userId = userId,
            userType = userType
        )
    }

    private fun NotificationTemplate.toVO() = NotificationTemplateVO(
        id = id,
        name = name,
        code = code,
        type = type,
        messageTemplateId = messageTemplateId,
        params = params,
        status = status,
        remark = remark,
        createdAt = createdAt
    )
}
