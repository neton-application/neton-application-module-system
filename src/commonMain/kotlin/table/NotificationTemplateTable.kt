package table

import model.NotificationTemplate
import model.NotificationTemplateTableImpl
import neton.database.api.Table

object NotificationTemplateTable : Table<NotificationTemplate, Long> by NotificationTemplateTableImpl
