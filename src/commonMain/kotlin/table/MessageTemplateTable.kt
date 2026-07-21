package table

import model.MessageTemplate
import model.MessageTemplateTableImpl
import neton.database.api.Table

object MessageTemplateTable : Table<MessageTemplate, Long> by MessageTemplateTableImpl
