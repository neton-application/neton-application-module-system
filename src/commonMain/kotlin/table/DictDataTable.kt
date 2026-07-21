package table

import model.DictData
import model.DictDataTableImpl
import neton.database.api.Table

object DictDataTable : Table<DictData, Long> by DictDataTableImpl
