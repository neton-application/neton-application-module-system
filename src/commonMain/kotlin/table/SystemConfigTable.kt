package table

import model.SystemConfig
import model.SystemConfigTableImpl
import neton.database.api.Table

object SystemConfigTable : Table<SystemConfig, Long> by SystemConfigTableImpl
