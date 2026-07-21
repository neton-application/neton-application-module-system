package controller.admin.menu

import controller.admin.menu.dto.CreateMenuRequest
import controller.admin.menu.dto.MenuVO
import controller.admin.menu.dto.UpdateMenuRequest
import logic.MenuLogic
import model.Menu
import neton.core.annotations.*

@Controller("/system/menu")
class MenuController(
    private val menuLogic: MenuLogic
) {

    @Get("/list")
    @Permission("system:menu:query")
    suspend fun list(): List<MenuVO> {
        return menuLogic.listFlat()
    }

    @Get("/simple-list")
    @Permission("system:menu:query")
    suspend fun listAllSimple(): List<MenuVO> {
        return menuLogic.listAllSimple()
    }

    @Get("/get/{id}")
    @Permission("system:menu:query")
    suspend fun get(@PathVariable id: Long): MenuVO {
        return menuLogic.getById(id)
    }

    @Post("/create")
    @Permission("system:menu:create")
    suspend fun create(@Body request: CreateMenuRequest): Long {
        return menuLogic.create(
            Menu(
                name = request.name,
                permission = request.permission,
                type = request.type,
                parentId = request.parentId,
                path = request.path,
                component = request.component,
                icon = request.icon,
                sort = request.sort,
                status = request.status
            )
        )
    }

    @Put("/update")
    @Permission("system:menu:update")
    suspend fun update(@Body request: UpdateMenuRequest) {
        menuLogic.update(
            Menu(
                id = request.id,
                name = request.name,
                permission = request.permission,
                type = request.type,
                parentId = request.parentId,
                path = request.path,
                component = request.component,
                icon = request.icon,
                sort = request.sort,
                status = request.status
            )
        )
    }

    @Delete("/delete/{id}")
    @Permission("system:menu:delete")
    suspend fun delete(@PathVariable id: Long) {
        menuLogic.delete(id)
    }

    @Delete("/delete-list")
    @Permission("system:menu:delete")
    suspend fun deleteList(@Query ids: String) {
        ids.split(",").mapNotNull { it.trim().toLongOrNull() }.forEach { menuLogic.delete(it) }
    }
}
