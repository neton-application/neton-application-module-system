package table

import model.SocialUser
import model.SocialUserTableImpl
import neton.database.api.Table

object SocialUserTable : Table<SocialUser, Long> by SocialUserTableImpl
