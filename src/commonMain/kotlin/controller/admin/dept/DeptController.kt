package controller.admin.dept

import controller.admin.dept.dto.CreateDeptRequest
import controller.admin.dept.dto.DeptVO
import controller.admin.dept.dto.UpdateDeptRequest
import logic.DeptLogic
import neton.core.annotations.*

@Controller("/system/dept")
class DeptController(
    private val deptLogic: DeptLogic
) {

    @Get("/list")
    @Permission("system:dept:query")
    suspend fun list(): List<DeptVO> = deptLogic.list()

    @Get("/simple-list")
    @Permission("system:dept:query")
    suspend fun listAllSimple(): List<DeptVO> = deptLogic.listAllSimple()

    @Get("/get/{id}")
    @Permission("system:dept:query")
    suspend fun get(@PathVariable id: Long): DeptVO = deptLogic.getById(id)

    @Post("/create")
    @Permission("system:dept:create")
    suspend fun create(@Body request: CreateDeptRequest): Long = deptLogic.create(request)

    @Put("/update")
    @Permission("system:dept:update")
    suspend fun update(@Body request: UpdateDeptRequest) = deptLogic.update(request)

    @Delete("/delete/{id}")
    @Permission("system:dept:delete")
    suspend fun delete(@PathVariable id: Long) = deptLogic.delete(id)

    @Delete("/delete-list")
    @Permission("system:dept:delete")
    suspend fun deleteList(@Query ids: String) {
        deptLogic.deleteList(ids.split(",").mapNotNull { it.trim().toLongOrNull() })
    }
}
