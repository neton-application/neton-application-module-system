package table

import model.DictType
import model.DictTypeTableImpl
import neton.database.api.Table

object DictTypeTable : Table<DictType, Long> by DictTypeTableImpl
