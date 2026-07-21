package dto

import kotlinx.serialization.Serializable

@Serializable
data class PageResponse<T>(
    val list: List<T>,
    val total: Long,
    val page: Int,
    val size: Int,
    val totalPages: Int
)
