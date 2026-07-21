package table

import model.RoleMenu
import model.RoleMenuTableImpl
import neton.database.api.Table

object RoleMenuTable : Table<RoleMenu, Long> by RoleMenuTableImpl
