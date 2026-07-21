package table

import model.Notice
import model.NoticeTableImpl
import neton.database.api.Table

object NoticeTable : Table<Notice, Long> by NoticeTableImpl
