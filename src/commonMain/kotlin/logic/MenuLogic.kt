package logic

import controller.admin.menu.dto.MenuVO
import model.Menu
import table.MenuTable
import neton.core.http.BadRequestException
import neton.core.http.NotFoundException
import neton.logging.Logger
import neton.database.dsl.*


@neton.core.annotations.Logic(logger = "logic.menu")
class MenuLogic(
    private val log: Logger,
    private val adminSessions: AdminSessionLogic,
) {

    suspend fun listFlat(): List<MenuVO> {
        val menus = MenuTable.query {
            orderBy(Menu::sort.asc())
        }.list()

        return menus.map { it.toVO(children = null) }
    }

    suspend fun listTree(): List<MenuVO> {
        val menus = MenuTable.query {
            orderBy(Menu::sort.asc())
        }.list()

        return buildTree(menus, 0)
    }

    suspend fun listAllSimple(): List<MenuVO> {
        val menus = MenuTable.query {
            where {
                Menu::status eq 1
            }
            orderBy(Menu::sort.asc())
        }.list()

        return menus.map { it.toVO(children = null) }
    }

    suspend fun getById(id: Long): MenuVO {
        val menu = MenuTable.get(id)
            ?: throw NotFoundException("Menu not found")
        return menu.toVO(children = null)
    }

    suspend fun create(menu: Menu): Long {
        return MenuTable.insert(menu).id
    }

    suspend fun update(menu: Menu) {
        val existing = MenuTable.get(menu.id)
            ?: throw NotFoundException("Menu not found")

        if (menu.parentId == menu.id) {
            throw BadRequestException("Parent menu cannot be itself")
        }

        // 全列 update：请求构造的 Menu 不带时间戳，必须保留原行 created_at/updated_at，
        // 否则写 null 触发 23502 非空约束（menu/update 500 的根因）。
        MenuTable.update(menu.copy(createdAt = existing.createdAt, updatedAt = existing.updatedAt))
        // 菜单停用或改权限串，所有持有它的角色立即失去对应权限（评审 R05）。
        adminSessions.bumpAuthorization()
    }

    suspend fun delete(id: Long) {
        // Check if menu has children
        val children = MenuTable.query {
            where { Menu::parentId eq id }
        }.list()

        if (children.isNotEmpty()) {
            throw BadRequestException("Cannot delete menu with children, please delete children first")
        }

        MenuTable.destroy(id)
        adminSessions.bumpAuthorization()
    }

    private fun buildTree(menus: List<Menu>, parentId: Long): List<MenuVO> {
        return menus
            .filter { it.parentId == parentId }
            .map { menu ->
                val children = buildTree(menus, menu.id)
                menu.toVO(children = children.ifEmpty { null })
            }
    }

    private fun Menu.toVO(children: List<MenuVO>?) = MenuVO(
        id = id,
        name = name,
        permission = permission,
        type = type,
        parentId = parentId,
        path = path,
        component = component,
        icon = icon,
        sort = sort,
        status = status,
        visible = visible,
        keepAlive = true,
        children = children
    )
}
