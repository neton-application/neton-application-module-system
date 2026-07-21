package table

import model.OperateLog
import model.OperateLogTableImpl
import neton.database.api.Table

object OperateLogTable : Table<OperateLog, Long> by OperateLogTableImpl
