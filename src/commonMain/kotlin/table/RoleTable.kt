package table

import model.Role
import model.RoleTableImpl
import neton.database.api.Table

object RoleTable : Table<Role, Long> by RoleTableImpl
