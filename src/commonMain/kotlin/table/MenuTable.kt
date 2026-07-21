package table

import model.Menu
import model.MenuTableImpl
import neton.database.api.Table

object MenuTable : Table<Menu, Long> by MenuTableImpl
