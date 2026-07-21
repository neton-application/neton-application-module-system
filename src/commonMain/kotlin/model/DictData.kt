package model

import kotlinx.serialization.Serializable
import neton.database.annotations.Table
import neton.database.annotations.Id
import neton.database.annotations.CreatedAt
import neton.database.annotations.UpdatedAt

@Serializable
@Table("system_dict_data")
data class DictData(
    @Id
    val id: Long = 0,
    val dictType: String,
    val label: String,
    val value: String,
    val sort: Int = 0,
    val status: Int = 1,
    val remark: String? = null,
    @CreatedAt
    val createdAt: String? = null,
    @UpdatedAt
    val updatedAt: String? = null
)
