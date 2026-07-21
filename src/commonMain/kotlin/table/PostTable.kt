package table

import model.Post
import model.PostTableImpl
import neton.database.api.Table

object PostTable : Table<Post, Long> by PostTableImpl
