package controller.admin.permission

import controller.admin.permission.dto.AssignRoleMenuRequest
import controller.admin.permission.dto.AssignUserRoleRequest
import logic.PermissionLogic
import neton.core.annotations.*

@Controller("/system/permission")
class PermissionController(
    private val permissionLogic: PermissionLogic
) {

    @Get("/list-role-menus")
    @Permission("system:permission:query")
    suspend fun listRoleMenus(@Query roleId: Long): List<Long> {
        return permissionLogic.listRoleMenus(roleId)
    }

    @Post("/assign-role-menu")
    @Permission("system:permission:assign")
    suspend fun assignRoleMenu(@Body request: AssignRoleMenuRequest) {
        permissionLogic.assignRoleMenu(request.roleId, request.menuIds)
    }

    @Get("/list-user-roles")
    @Permission("system:permission:query")
    suspend fun listUserRoles(@Query userId: Long): List<Long> {
        return permissionLogic.listUserRoles(userId)
    }

    @Post("/assign-user-role")
    @Permission("system:permission:assign")
    suspend fun assignUserRole(@Body request: AssignUserRoleRequest) {
        permissionLogic.assignUserRole(request.userId, request.roleIds)
    }
}
