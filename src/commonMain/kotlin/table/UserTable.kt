package table

import model.User
import model.UserTableImpl
import neton.database.api.Table

object UserTable : Table<User, Long> by UserTableImpl
