package logic

import controller.admin.post.dto.CreatePostRequest
import controller.admin.post.dto.PostVO
import controller.admin.post.dto.UpdatePostRequest
import dto.PageResponse
import model.Post
import table.PostTable
import neton.core.http.BadRequestException
import neton.core.http.NotFoundException
import neton.database.dsl.*

import neton.logging.Logger

@neton.core.annotations.Logic(logger = "logic.post")
class PostLogic(
    private val log: Logger
) {

    suspend fun page(
        page: Int,
        size: Int,
        name: String? = null,
        code: String? = null,
        status: Int? = null
    ): PageResponse<PostVO> {
        val result = PostTable.query {
            where {
                and(
                    whenNotBlank(name) { Post::name like "%$it%" },
                    whenNotBlank(code) { Post::code like "%$it%" },
                    whenPresent(status) { Post::status eq it }
                )
            }
            orderBy(Post::sort.asc())
        }.page(page, size)

        val items = result.items.map { it.toVO() }
        return PageResponse(
            list = items,
            total = result.total,
            page = page,
            size = size,
            totalPages = ((result.total + size - 1) / size).toInt()
        )
    }

    suspend fun listAllSimple(): List<PostVO> {
        return PostTable.query {
            where { Post::status eq 1 }
            orderBy(Post::sort.asc())
        }.list().map { it.toVO() }
    }

    suspend fun getById(id: Long): PostVO {
        val post = PostTable.get(id)
            ?: throw NotFoundException("Post not found")
        return post.toVO()
    }

    suspend fun create(request: CreatePostRequest): Long {
        val existing = PostTable.oneWhere { Post::code eq request.code }
        if (existing != null) {
            throw BadRequestException("Post code already exists")
        }
        return PostTable.insert(
            Post(
                code = request.code,
                name = request.name,
                sort = request.sort,
                status = request.status
            )
        ).id
    }

    suspend fun update(request: UpdatePostRequest) {
        PostTable.get(request.id)
            ?: throw NotFoundException("Post not found")
        PostTable.update(
            Post(
                id = request.id,
                code = request.code,
                name = request.name,
                sort = request.sort,
                status = request.status
            )
        )
    }

    suspend fun delete(id: Long) {
        PostTable.get(id)
            ?: throw NotFoundException("Post not found")
        PostTable.destroy(id)
    }

    private fun Post.toVO() = PostVO(
        id = id,
        code = code,
        name = name,
        sort = sort,
        status = status
    )
}
