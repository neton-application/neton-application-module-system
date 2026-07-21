package logic

import dto.PageResponse
import model.LoginLog
import model.OperateLog
import table.LoginLogTable
import table.OperateLogTable
import neton.logging.Logger
import neton.database.dsl.*


@neton.core.annotations.Logic(logger = "logic.log")
class LogLogic(
    private val log: Logger
) {

    // --- Login Log ---

    suspend fun recordLoginLog(loginLog: LoginLog): Long {
        return LoginLogTable.insert(loginLog).id
    }

    suspend fun pageLoginLogs(
        page: Int,
        size: Int,
        username: String? = null,
        userIp: String? = null,
        loginResult: Int? = null
    ): PageResponse<LoginLog> {
        val result = LoginLogTable.query {
            where {
                and(
                    whenNotBlank(username) { LoginLog::username like "%$it%" },
                    whenNotBlank(userIp) { LoginLog::userIp like "%$it%" },
                    whenPresent(loginResult) { LoginLog::loginResult eq it }
                )
            }
            orderBy(LoginLog::id.desc())
        }.page(page, size)

        return PageResponse(
            list = result.items,
            total = result.total,
            page = page,
            size = size,
            totalPages = ((result.total + size - 1) / size).toInt()
        )
    }

    suspend fun getLoginLogById(id: Long): LoginLog? {
        return LoginLogTable.get(id)
    }

    // --- Operate Log ---

    suspend fun recordOperateLog(operateLog: OperateLog): Long {
        return OperateLogTable.insert(operateLog).id
    }

    suspend fun pageOperateLogs(
        page: Int,
        size: Int,
        module: String? = null,
        userId: Long? = null,
        operateType: Int? = null
    ): PageResponse<OperateLog> {
        val result = OperateLogTable.query {
            where {
                and(
                    whenNotBlank(module) { OperateLog::module like "%$it%" },
                    whenPresent(userId) { OperateLog::userId eq it },
                    whenPresent(operateType) { OperateLog::operateType eq it }
                )
            }
            orderBy(OperateLog::id.desc())
        }.page(page, size)

        return PageResponse(
            list = result.items,
            total = result.total,
            page = page,
            size = size,
            totalPages = ((result.total + size - 1) / size).toInt()
        )
    }

    suspend fun getOperateLogById(id: Long): OperateLog? {
        return OperateLogTable.get(id)
    }
}
