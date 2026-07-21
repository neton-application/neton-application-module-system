package logic

import dto.PageResponse
import controller.admin.role.dto.RoleVO
import controller.admin.user.dto.CreateUserRequest
import controller.admin.user.dto.UpdateUserRequest
import controller.admin.user.dto.UserVO
import controller.admin.user.dto.UserWithRolesVO
import model.Role
import model.User
import model.UserRole
import table.RoleTable
import table.UserRoleTable
import table.UserTable
import neton.core.http.BadRequestException
import neton.core.http.NotFoundException
import neton.logging.Logger
import neton.database.dsl.*
import neton.security.password.PasswordHasher


@neton.core.annotations.Logic(logger = "logic.user")
class UserLogic(
    private val log: Logger
) {

    suspend fun page(
        page: Int,
        size: Int,
        username: String? = null,
        status: Int? = null,
        mobile: String? = null
    ): PageResponse<UserVO> {
        val result = UserTable.query {
            where {
                and(
                    whenNotBlank(username) { User::username like "%$it%" },
                    whenPresent(status) { User::status eq it },
                    whenNotBlank(mobile) { User::mobile like "%$it%" }
                )
            }
            orderBy(User::id.desc())
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

    suspend fun listAllSimple(): List<UserVO> {
        val users = UserTable.query {
            where {
                User::status eq 1
            }
            orderBy(User::id.asc())
        }.list()

        return users.map { it.toVO() }
    }

    suspend fun getById(id: Long): UserVO {
        val user = UserTable.get(id)
            ?: throw NotFoundException("User not found")
        return user.toVO()
    }

    suspend fun getUserWithRoles(id: Long): UserWithRolesVO {
        val user = UserTable.get(id)
            ?: throw NotFoundException("User not found")

        val userRoles = UserRoleTable.query {
            where { UserRole::userId eq id }
        }.list()

        val roleIds = userRoles.map { it.roleId }
        val roles = if (roleIds.isNotEmpty()) {
            RoleTable.query {
                where { Role::id `in` roleIds }
            }.list().map { role ->
                RoleVO(
                    id = role.id,
                    code = role.code,
                    name = role.name,
                    description = role.description,
                    status = role.status
                )
            }
        } else emptyList()

        return UserWithRolesVO(
            id = user.id,
            username = user.username,
            nickname = user.nickname,
            status = user.status,
            roles = roles,
            createdAt = user.createdAt,
            updatedAt = user.updatedAt
        )
    }

    suspend fun create(request: CreateUserRequest): Long {
        val existing = UserTable.oneWhere {
            User::username eq request.username
        }

        if (existing != null) {
            throw BadRequestException("Username already exists")
        }

        val user = User(
            username = request.username,
            passwordHash = PasswordHasher.hash(request.password),
            nickname = request.nickname,
            email = request.email,
            mobile = request.mobile,
            gender = request.gender,
            deptId = request.deptId ?: 0,
            remark = request.remark,
            status = request.status
        )
        return UserTable.insert(user).id
    }

    suspend fun update(request: UpdateUserRequest) {
        val existing = UserTable.get(request.id)
            ?: throw NotFoundException("User not found")

        val updated = existing.copy(
            nickname = request.nickname ?: existing.nickname,
            email = request.email ?: existing.email,
            mobile = request.mobile ?: existing.mobile,
            gender = request.gender ?: existing.gender,
            deptId = request.deptId ?: existing.deptId,
            remark = request.remark ?: existing.remark,
            status = request.status ?: existing.status
        )
        UserTable.update(updated)
    }

    suspend fun delete(id: Long) {
        UserTable.destroy(id)
    }

    suspend fun updatePassword(id: Long, newPassword: String) {
        val user = UserTable.get(id)
            ?: throw NotFoundException("User not found")

        UserTable.update(user.copy(passwordHash = PasswordHasher.hash(newPassword)))
    }

    suspend fun updateStatus(id: Long, status: Int) {
        val user = UserTable.get(id)
            ?: throw NotFoundException("User not found")

        UserTable.update(user.copy(status = status))
    }

    private fun User.toVO() = UserVO(
        id = id,
        username = username,
        nickname = nickname,
        email = email,
        mobile = mobile,
        avatar = avatar,
        gender = gender,
        deptId = deptId,
        remark = remark,
        status = status,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}
