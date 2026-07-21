package controller.admin.user

import dto.PageResponse
import controller.admin.user.dto.CreateUserRequest
import controller.admin.user.dto.UpdateUserRequest
import controller.admin.user.dto.UpdatePasswordRequest
import controller.admin.user.dto.UpdateStatusRequest
import controller.admin.user.dto.UserVO
import controller.admin.user.dto.UserWithRolesVO
import logic.UserLogic
import neton.core.annotations.*

@Controller("/system/user")
class UserController(
    private val userLogic: UserLogic
) {

    @Get("/page")
    @Permission("system:user:query")
    suspend fun page(
        @Query pageNo: Int = 1,
        @Query pageSize: Int = 10,
        @Query username: String? = null,
        @Query status: Int? = null,
        @Query mobile: String? = null
    ): PageResponse<UserVO> {
        return userLogic.page(pageNo, pageSize, username, status, mobile)
    }

    @Get("/simple-list")
    @Permission("system:user:query")
    suspend fun listAllSimple(): List<UserVO> {
        return userLogic.listAllSimple()
    }

    @Get("/get/{id}")
    @Permission("system:user:query")
    suspend fun get(@PathVariable id: Long): UserVO {
        return userLogic.getById(id)
    }

    @Get("/get/{id}/with-roles")
    @Permission("system:user:query")
    suspend fun getWithRoles(@PathVariable id: Long): UserWithRolesVO {
        return userLogic.getUserWithRoles(id)
    }

    @Post("/create")
    @Permission("system:user:create")
    suspend fun create(@Body request: CreateUserRequest): Long {
        return userLogic.create(request)
    }

    @Put("/update")
    @Permission("system:user:update")
    suspend fun update(@Body request: UpdateUserRequest) {
        userLogic.update(request)
    }

    @Delete("/delete/{id}")
    @Permission("system:user:delete")
    suspend fun delete(@PathVariable id: Long) {
        userLogic.delete(id)
    }

    @Put("/update-password")
    @Permission("system:user:update-password")
    suspend fun updatePassword(@Body request: UpdatePasswordRequest) {
        userLogic.updatePassword(request.id, request.newPassword)
    }

    @Put("/update-status/{id}")
    @Permission("system:user:update")
    suspend fun updateStatus(@PathVariable id: Long, @Body request: UpdateStatusRequest) {
        userLogic.updateStatus(id, request.status)
    }
}
