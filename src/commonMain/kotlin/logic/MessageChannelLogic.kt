package logic

import neton.core.http.NetonErrorCode
import neton.core.http.HttpException
import controller.admin.message.dto.MessageChannelVO
import dto.PageResponse
import logic.provider.MessageProvider
import logic.provider.SmsProvider
import logic.provider.EmailProvider
import model.MessageChannel
import table.MessageChannelTable
import neton.core.http.BadRequestException
import neton.core.http.NotFoundException
import neton.logging.Logger
import neton.database.dsl.*


class MessageChannelLogic(
    private val log: Logger,
    private val providers: Map<String, MessageProvider> = emptyMap()
) {

    suspend fun page(
        page: Int,
        size: Int,
        name: String? = null,
        type: String? = null,
        status: Int? = null
    ): PageResponse<MessageChannelVO> {
        val result = MessageChannelTable.query {
            where {
                and(
                    whenNotBlank(name) { MessageChannel::name like "%$it%" },
                    whenNotBlank(type) { MessageChannel::type eq it },
                    whenPresent(status) { MessageChannel::status eq it }
                )
            }
            orderBy(MessageChannel::id.desc())
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

    suspend fun getById(id: Long): MessageChannelVO {
        val channel = MessageChannelTable.get(id)
            ?: throw NotFoundException(SystemErrorCodes.MESSAGE_CHANNEL_NOT_FOUND)
        return channel.toVO()
    }

    suspend fun getRawById(id: Long): MessageChannel {
        return MessageChannelTable.get(id)
            ?: throw NotFoundException(SystemErrorCodes.MESSAGE_CHANNEL_NOT_FOUND)
    }

    suspend fun pageRaw(
        page: Int,
        size: Int,
        name: String? = null,
        type: String? = null,
        status: Int? = null
    ): PageResponse<MessageChannel> {
        val result = MessageChannelTable.query {
            where {
                and(
                    whenNotBlank(name) { MessageChannel::name like "%$it%" },
                    whenNotBlank(type) { MessageChannel::type eq it },
                    whenPresent(status) { MessageChannel::status eq it }
                )
            }
            orderBy(MessageChannel::id.desc())
        }.page(page, size)
        return PageResponse(result.items, result.total, page, size,
            if (size > 0) ((result.total + size - 1) / size).toInt() else 0)
    }

    suspend fun listRawByType(type: String): List<MessageChannel> {
        return MessageChannelTable.query {
            where {
                and(
                    MessageChannel::status eq 1,
                    MessageChannel::type eq type
                )
            }
            orderBy(MessageChannel::id.asc())
        }.list()
    }

    suspend fun create(channel: MessageChannel): Long {
        val existing = MessageChannelTable.oneWhere {
            MessageChannel::code eq channel.code
        }
        if (existing != null) {
            throw HttpException(NetonErrorCode.RESOURCE_ALREADY_EXISTS, SystemErrorCodes.MESSAGE_CHANNEL_CODE_EXISTS, SystemErrorCodes.field("code", SystemErrorCodes.MESSAGE_CHANNEL_CODE_EXISTS))
        }
        return MessageChannelTable.insert(channel).id
    }

    suspend fun update(channel: MessageChannel) {
        MessageChannelTable.get(channel.id)
            ?: throw NotFoundException(SystemErrorCodes.MESSAGE_CHANNEL_NOT_FOUND)
        MessageChannelTable.update(channel)
    }

    suspend fun delete(id: Long) {
        MessageChannelTable.get(id)
            ?: throw NotFoundException(SystemErrorCodes.MESSAGE_CHANNEL_NOT_FOUND)
        MessageChannelTable.destroy(id)
    }

    suspend fun listAllSimple(): List<MessageChannelVO> {
        return MessageChannelTable.query {
            where { MessageChannel::status eq 1 }
            orderBy(MessageChannel::id.asc())
        }.list().map { it.toVO() }
    }

    suspend fun listByType(type: String): List<MessageChannelVO> {
        return MessageChannelTable.query {
            where {
                and(
                    MessageChannel::status eq 1,
                    MessageChannel::type eq type
                )
            }
            orderBy(MessageChannel::id.asc())
        }.list().map { it.toVO() }
    }

    /**
     * Send a test message through a channel.
     */
    suspend fun sendTest(channelId: Long, receiver: String, content: String): Boolean {
        val channel = MessageChannelTable.get(channelId)
            ?: throw NotFoundException(SystemErrorCodes.MESSAGE_CHANNEL_NOT_FOUND)

        val provider = getProvider(channel.type)
            ?: throw HttpException(NetonErrorCode.SERVICE_UNAVAILABLE, SystemErrorCodes.MESSAGE_PROVIDER_UNAVAILABLE)

        return provider.send(receiver, content, channel.config ?: "{}")
    }

    fun getProvider(type: String): MessageProvider? {
        return providers[type]
    }

    fun getChannelByCode(code: String): suspend () -> MessageChannel? = {
        MessageChannelTable.oneWhere {
            MessageChannel::code eq code
        }
    }

    private fun MessageChannel.toVO() = MessageChannelVO(
        id = id,
        name = name,
        code = code,
        type = type,
        config = config,
        status = status,
        remark = remark,
        createdAt = createdAt
    )
}
