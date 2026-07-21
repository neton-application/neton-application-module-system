package table

import model.NotifyMessage
import model.NotifyMessageTableImpl
import neton.database.api.Table

object NotifyMessageTable : Table<NotifyMessage, Long> by NotifyMessageTableImpl
