package controller.admin.role

import dto.PageResponse
import controller.admin.role.dto.CreateRoleRequest
import controller.admin.role.dto.RoleVO
import controller.admin.role.dto.UpdateRoleRequest
import logic.RoleLogic
import model.Role
import neton.core.annotations.*

@Controller("/system/role")
class RoleController(
    private val roleLogic: RoleLogic
) {

    @Get("/page")
    @Permission("system:role:query")
    suspend fun page(
        @Query pageNo: Int = 1,
        @Query pageSize: Int = 10,
        @Query name: String? = null,
        @Query code: String? = null,
        @Query status: Int? = null
    ): PageResponse<RoleVO> {
        return roleLogic.page(pageNo, pageSize, name, code, status)
    }

    @Get("/simple-list")
    @Permission("system:role:query")
    suspend fun listAllSimple(): List<RoleVO> {
        return roleLogic.listAllSimple()
    }

    @Get("/get/{id}")
    @Permission("system:role:query")
    suspend fun get(@PathVariable id: Long): RoleVO {
        return roleLogic.getById(id)
    }

    @Post("/create")
    @Permission("system:role:create")
    suspend fun create(@Body request: CreateRoleRequest): Long {
        return roleLogic.create(
            Role(
                code = request.code,
                name = request.name,
                description = request.description,
                sort = request.sort,
                status = request.status
            )
        )
    }

    @Put("/update")
    @Permission("system:role:update")
    suspend fun update(@Body request: UpdateRoleRequest) {
        roleLogic.update(
            Role(
                id = request.id,
                code = request.code,
                name = request.name,
                description = request.description,
                sort = request.sort,
                status = request.status
            )
        )
    }

    @Delete("/delete/{id}")
    @Permission("system:role:delete")
    suspend fun delete(@PathVariable id: Long) {
        roleLogic.delete(id)
    }
}
