package logic

import controller.admin.dept.dto.CreateDeptRequest
import controller.admin.dept.dto.DeptVO
import controller.admin.dept.dto.UpdateDeptRequest
import model.Dept
import table.DeptTable
import neton.core.http.BadRequestException
import neton.core.http.NotFoundException
import neton.database.dsl.*

import neton.logging.Logger

@neton.core.annotations.Logic(logger = "logic.dept")
class DeptLogic(
    private val log: Logger
) {

    suspend fun list(): List<DeptVO> {
        val depts = DeptTable.query {
            orderBy(Dept::sort.asc())
        }.list()
        return buildTree(depts, 0)
    }

    suspend fun listAllSimple(): List<DeptVO> {
        return DeptTable.query {
            where { Dept::status eq 1 }
            orderBy(Dept::sort.asc())
        }.list().map { it.toVO(children = null) }
    }

    suspend fun getById(id: Long): DeptVO {
        val dept = DeptTable.get(id)
            ?: throw NotFoundException("Department not found")
        return dept.toVO(children = null)
    }

    suspend fun create(request: CreateDeptRequest): Long {
        return DeptTable.insert(
            Dept(
                name = request.name,
                parentId = request.parentId,
                sort = request.sort,
                leaderUserId = request.leaderUserId,
                status = request.status
            )
        ).id
    }

    suspend fun update(request: UpdateDeptRequest) {
        if (request.parentId == request.id) {
            throw BadRequestException("Parent department cannot be itself")
        }

        DeptTable.get(request.id)
            ?: throw NotFoundException("Department not found")

        DeptTable.update(
            Dept(
                id = request.id,
                name = request.name,
                parentId = request.parentId,
                sort = request.sort,
                leaderUserId = request.leaderUserId,
                status = request.status
            )
        )
    }

    suspend fun delete(id: Long) {
        val children = DeptTable.query {
            where { Dept::parentId eq id }
        }.list()

        if (children.isNotEmpty()) {
            throw BadRequestException("Cannot delete department with children")
        }

        DeptTable.destroy(id)
    }

    suspend fun deleteList(ids: List<Long>) {
        ids.forEach { delete(it) }
    }

    private fun buildTree(depts: List<Dept>, parentId: Long): List<DeptVO> {
        return depts
            .filter { it.parentId == parentId }
            .map { dept ->
                val children = buildTree(depts, dept.id)
                dept.toVO(children = children.ifEmpty { null })
            }
    }

    private fun Dept.toVO(children: List<DeptVO>?) = DeptVO(
        id = id,
        name = name,
        parentId = parentId,
        sort = sort,
        status = status,
        children = children
    )
}
