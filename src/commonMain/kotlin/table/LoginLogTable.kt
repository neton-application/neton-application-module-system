package table

import model.LoginLog
import model.LoginLogTableImpl
import neton.database.api.Table

object LoginLogTable : Table<LoginLog, Long> by LoginLogTableImpl
