package controller.admin.log

import dto.PageResponse
import logic.LogLogic
import model.LoginLog
import neton.core.annotations.*
import neton.core.http.NotFoundException

@Controller("/system/login-log")
class LoginLogController(
    private val logLogic: LogLogic
) {

    @Get("/page")
    @Permission("system:login-log:query")
    suspend fun page(
        @Query pageNo: Int = 1,
        @Query pageSize: Int = 10,
        @Query username: String? = null,
        @Query userIp: String? = null,
        @Query loginResult: Int? = null
    ): PageResponse<LoginLog> {
        return logLogic.pageLoginLogs(pageNo, pageSize, username, userIp, loginResult)
    }

    @Get("/get/{id}")
    @Permission("system:login-log:query")
    suspend fun get(@PathVariable id: Long): LoginLog {
        return logLogic.getLoginLogById(id)
            ?: throw NotFoundException("Login log not found")
    }
}
