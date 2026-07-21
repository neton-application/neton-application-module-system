package logic

import controller.admin.message.dto.MessageTemplateVO
import dto.PageResponse
import model.MessageChannel
import model.MessageTemplate
import table.MessageChannelTable
import table.MessageTemplateTable
import neton.core.http.BadRequestException
import neton.core.http.NotFoundException
import neton.logging.Logger
import neton.database.dsl.*


@neton.core.annotations.Logic(logger = "logic.message-template")
class MessageTemplateLogic(
    private val log: Logger
) {

    suspend fun page(
        page: Int,
        size: Int,
        name: String? = null,
        code: String? = null,
        type: Int? = null,
        status: Int? = null
    ): PageResponse<MessageTemplateVO> {
        val result = MessageTemplateTable.query {
            where {
                and(
                    whenNotBlank(name) { MessageTemplate::name like "%$it%" },
                    whenNotBlank(code) { MessageTemplate::code like "%$it%" },
                    whenPresent(type) { MessageTemplate::type eq it },
                    whenPresent(status) { MessageTemplate::status eq it }
                )
            }
            orderBy(MessageTemplate::id.desc())
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

    suspend fun getById(id: Long): MessageTemplateVO {
        val template = MessageTemplateTable.get(id)
            ?: throw NotFoundException("Message template not found")
        return template.toVO()
    }

    suspend fun getByCode(code: String): MessageTemplate? {
        return MessageTemplateTable.oneWhere {
            MessageTemplate::code eq code
        }
    }

    suspend fun create(template: MessageTemplate): Long {
        val existing = MessageTemplateTable.oneWhere {
            MessageTemplate::code eq template.code
        }
        if (existing != null) {
            throw BadRequestException("Template code already exists")
        }
        return MessageTemplateTable.insert(template).id
    }

    suspend fun update(template: MessageTemplate) {
        MessageTemplateTable.get(template.id)
            ?: throw NotFoundException("Message template not found")
        MessageTemplateTable.update(template)
    }

    suspend fun delete(id: Long) {
        MessageTemplateTable.get(id)
            ?: throw NotFoundException("Message template not found")
        MessageTemplateTable.destroy(id)
    }

    suspend fun pageByChannelType(
        page: Int,
        size: Int,
        channelType: String,
        name: String? = null,
        code: String? = null,
        status: Int? = null
    ): PageResponse<MessageTemplateVO> {
        val channelIds = MessageChannelTable.query {
            where { MessageChannel::type eq channelType }
        }.list().map { it.id }

        if (channelIds.isEmpty()) {
            return PageResponse(list = emptyList(), total = 0, page = page, size = size, totalPages = 0)
        }

        val result = MessageTemplateTable.query {
            where {
                and(
                    MessageTemplate::channelId `in` channelIds,
                    whenNotBlank(name) { MessageTemplate::name like "%$it%" },
                    whenNotBlank(code) { MessageTemplate::code like "%$it%" },
                    whenPresent(status) { MessageTemplate::status eq it }
                )
            }
            orderBy(MessageTemplate::id.desc())
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

    /**
     * Render template content by replacing {{variable}} placeholders with params.
     */
    fun renderContent(content: String, params: Map<String, String>): String {
        var rendered = content
        for ((key, value) in params) {
            rendered = rendered.replace("{{$key}}", value)
        }
        return rendered
    }

    private fun MessageTemplate.toVO() = MessageTemplateVO(
        id = id,
        name = name,
        code = code,
        content = content,
        params = params,
        channelId = channelId,
        type = type,
        status = status,
        remark = remark,
        createdAt = createdAt
    )
}
