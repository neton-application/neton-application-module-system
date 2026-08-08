package table

import model.SystemSetting
import model.SystemSettingTableImpl
import neton.database.api.Table

object SystemSettingTable : Table<SystemSetting, Long> by SystemSettingTableImpl
