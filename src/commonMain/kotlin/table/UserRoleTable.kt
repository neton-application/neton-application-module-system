package table

import model.UserRole
import model.UserRoleTableImpl
import neton.database.api.Table

object UserRoleTable : Table<UserRole, Long> by UserRoleTableImpl
