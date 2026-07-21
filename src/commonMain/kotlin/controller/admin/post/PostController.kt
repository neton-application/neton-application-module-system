package controller.admin.post

import controller.admin.post.dto.CreatePostRequest
import controller.admin.post.dto.PostVO
import controller.admin.post.dto.UpdatePostRequest
import logic.PostLogic
import neton.core.annotations.Controller
import neton.core.annotations.Get
import neton.core.annotations.Put
import neton.core.annotations.Delete
import neton.core.annotations.Permission
import neton.core.annotations.PathVariable
import neton.core.annotations.Query
import neton.core.annotations.Body

@Controller("/system/post")
class PostController(
    private val postLogic: PostLogic
) {

    @Get("/page")
    @Permission("system:post:query")
    suspend fun page(
        @Query pageNo: Int = 1,
        @Query pageSize: Int = 10,
        @Query name: String? = null,
        @Query code: String? = null,
        @Query status: Int? = null
    ) = postLogic.page(pageNo, pageSize, name, code, status)

    @Get("/simple-list")
    @Permission("system:post:query")
    suspend fun listAllSimple(): List<PostVO> = postLogic.listAllSimple()

    @Get("/get/{id}")
    @Permission("system:post:query")
    suspend fun get(@PathVariable id: Long): PostVO = postLogic.getById(id)

    @neton.core.annotations.Post("/create")
    @Permission("system:post:create")
    suspend fun create(@Body request: CreatePostRequest): Long = postLogic.create(request)

    @Put("/update")
    @Permission("system:post:update")
    suspend fun update(@Body request: UpdatePostRequest) = postLogic.update(request)

    @Delete("/delete/{id}")
    @Permission("system:post:delete")
    suspend fun delete(@PathVariable id: Long) = postLogic.delete(id)
}
