package table

import model.MessageChannel
import model.MessageChannelTableImpl
import neton.database.api.Table

object MessageChannelTable : Table<MessageChannel, Long> by MessageChannelTableImpl
