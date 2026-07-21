package table

import model.MessageLog
import model.MessageLogTableImpl
import neton.database.api.Table

object MessageLogTable : Table<MessageLog, Long> by MessageLogTableImpl
