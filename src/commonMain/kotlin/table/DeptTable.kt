package table

import model.Dept
import model.DeptTableImpl
import neton.database.api.Table

object DeptTable : Table<Dept, Long> by DeptTableImpl
