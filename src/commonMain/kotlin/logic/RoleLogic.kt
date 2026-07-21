package logic

import dto.PageResponse
import controller.admin.role.dto.RoleVO
import model.Role
import model.RoleMenu
import table.RoleMenuTable
import table.RoleTable
import neton.core.http.BadRequestException
import neton.core.http.NotFoundException
import neton.logging.Logger
import neton.database.api.DbContext
import neton.database.dsl.*


@neton.core.annotations.Logic(logger = "logic.role")
class RoleLogic(
    private val log: Logger,
    private val db: DbContext,
) {

    suspend fun page(
        page: Int,
        size: Int,
        name: String? = null,
        code: String? = null,
        status: Int? = null
    ): PageResponse<RoleVO> {
        val result = RoleTable.query {
            where {
                and(
                    whenNotBlank(name) { Role::name like "%$it%" },
                    whenNotBlank(code) { Role::code like "%$it%" },
                    whenPresent(status) { Role::status eq it }
                )
            }
            orderBy(Role::sort.asc())
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

    suspend fun listAllSimple(): List<RoleVO> {
        val roles = RoleTable.query {
            where {
                Role::status eq 1
            }
            orderBy(Role::sort.asc())
        }.list()

        return roles.map { it.toVO() }
    }

    suspend fun getById(id: Long): RoleVO {
        val role = RoleTable.get(id)
            ?: throw NotFoundException("Role not found")
        return role.toVO()
    }

    suspend fun create(role: Role): Long {
        val existing = RoleTable.oneWhere {
            Role::code eq role.code
        }

        if (existing != null) {
            throw BadRequestException("Role code already exists")
        }

        return RoleTable.insert(role).id
    }

    suspend fun update(role: Role) {
        RoleTable.get(role.id)
            ?: throw NotFoundException("Role not found")

        RoleTable.update(role)
    }

    suspend fun delete(id: Long) {
        RoleTable.get(id)
            ?: throw NotFoundException("Role not found")

        // Remove associated role-menu mappings + delete role in a single transaction
        db.transaction {
            RoleMenuTable.query {
                where { RoleMenu::roleId eq id }
            }.list().forEach { RoleMenuTable.destroy(it.id) }

            RoleTable.destroy(id)
        }
    }

    suspend fun assignMenus(roleId: Long, menuIds: List<Long>) {
        RoleTable.get(roleId)
            ?: throw NotFoundException("Role not found")

        // Remove existing mappings + insert new mappings in a single transaction
        db.transaction {
            val existingMappings = RoleMenuTable.query {
                where { RoleMenu::roleId eq roleId }
            }.list()
            existingMappings.forEach { RoleMenuTable.destroy(it.id) }

            menuIds.forEach { menuId ->
                RoleMenuTable.insert(
                    RoleMenu(roleId = roleId, menuId = menuId)
                )
            }
        }

        log.info("Assigned ${menuIds.size} menus to role $roleId")
    }

    private fun Role.toVO() = RoleVO(
        id = id,
        code = code,
        name = name,
        description = description,
        status = status
    )
}
