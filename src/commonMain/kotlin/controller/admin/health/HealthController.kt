package controller.admin.health

import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import neton.core.annotations.AllowAnonymous
import neton.core.annotations.Controller
import neton.core.annotations.Get
import neton.core.component.NetonContext
import neton.core.http.HttpContext
import neton.core.http.HttpException
import neton.core.http.NetonErrorCode
import neton.database.api.DbContext
import neton.redis.RedisClient

@Serializable
data class HealthReport(
    /** `ready`；存活探针固定为 `alive`。 */
    val status: String,
    /** 依赖名 → `ok` / `unavailable` / `not_configured`。只给状态，不暴露主机、版本等细节。 */
    val checks: Map<String, String> = emptyMap(),
)

/**
 * 存活与就绪探针（评审 R18）。
 *
 * 此前根路径的 health 永远返回 ok，不能说明数据库、Redis 是否可用，负载均衡和后台页面都据此误判。
 * - `live`：进程在跑就返回，不碰任何依赖（给重启策略用，依赖抖动不该触发重启）。
 * - `ready`：实际探测数据库与 Redis，每项最多等 [PROBE_TIMEOUT_MS]。全部可用返回 200 + `ready`；
 *   任一不可用返回 503，消息为 `degraded database=ok,redis=unavailable`（给流量调度和后台状态灯用）。
 *
 * 匿名可访问：探针本来就由未登录的基础设施调用。
 */
@Controller("/system/health")
class HealthController(private val ctx: NetonContext) {

    @Get("/live")
    @AllowAnonymous
    suspend fun live(): HealthReport = HealthReport(status = "alive")

    @Get("/ready")
    @AllowAnonymous
    suspend fun ready(http: HttpContext): HealthReport {
        val checks = linkedMapOf(
            "database" to probe { ctx.getOrNull(DbContext::class)?.let { it.fetchAll("SELECT 1 AS ok"); true } },
            "redis" to probe { ctx.getOrNull(RedisClient::class)?.let { it.set(PROBE_KEY, "1"); it.getValue(PROBE_KEY) == "1" } },
        )
        http.response.header("Cache-Control", "no-store")
        if (checks.values.any { it == UNAVAILABLE }) {
            // 成功响应一律是 200；要让负载均衡看到 503，只能走错误路径。状态写在消息里，格式稳定。
            throw HttpException(NetonErrorCode.SERVICE_UNAVAILABLE, "degraded " + checks.entries.joinToString(",") { "${it.key}=${it.value}" })
        }
        return HealthReport(status = "ready", checks = checks)
    }

    /** null = 该依赖未装配；true = 可用；false / 超时 / 异常 = 不可用。 */
    private suspend fun probe(check: suspend () -> Boolean?): String = try {
        when (withTimeoutOrNull(PROBE_TIMEOUT_MS) { check() ?: NOT_CONFIGURED_MARK }) {
            true -> OK
            NOT_CONFIGURED_MARK -> NOT_CONFIGURED
            else -> UNAVAILABLE
        }
    } catch (_: Exception) {
        UNAVAILABLE
    }

    private companion object {
        const val PROBE_TIMEOUT_MS = 2_000L
        const val PROBE_KEY = "health:probe"
        const val OK = "ok"
        const val UNAVAILABLE = "unavailable"
        const val NOT_CONFIGURED = "not_configured"
        val NOT_CONFIGURED_MARK = Any()
    }
}
