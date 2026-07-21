package controller.admin.log

import dto.PageResponse
import logic.LogLogic
import model.OperateLog
import neton.core.annotations.*
import neton.core.http.NotFoundException

@Controller("/system/operate-log")
class OperateLogController(
    private val logLogic: LogLogic
) {

    @Get("/page")
    @Permission("system:operate-log:query")
    suspend fun page(
        @Query pageNo: Int = 1,
        @Query pageSize: Int = 10,
        @Query module: String? = null,
        @Query userId: Long? = null,
        @Query operateType: Int? = null
    ): PageResponse<OperateLog> {
        return logLogic.pageOperateLogs(pageNo, pageSize, module, userId, operateType)
    }

    @Get("/get/{id}")
    @Permission("system:operate-log:query")
    suspend fun get(@PathVariable id: Long): OperateLog {
        return logLogic.getOperateLogById(id)
            ?: throw NotFoundException("Operate log not found")
    }
}
