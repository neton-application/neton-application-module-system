package logic

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import neton.core.http.HttpException
import neton.core.http.NetonErrorCode
import neton.http.client.HttpClient
import neton.http.client.HttpClientBody
import neton.http.client.HttpClientMethod
import neton.http.client.HttpClientRequest
import neton.http.client.HttpClientTimeouts
import neton.logging.Logger
import setting.SystemSettingKeys

/** 需要人机验证的动作。值与后台配置 `system.security.captcha.actions` 里的写法一致。 */
enum class CaptchaAction(val value: String) {
    REGISTER("register"),
    SEND_CODE("send_code"),
    LOGIN("login"),
    ADMIN_LOGIN("admin_login");
}

/** 下发给浏览器的公开配置：没有 secret，不暴露别的设置。provider 为空串表示未启用。 */
@Serializable
data class CaptchaPublicVO(
    val provider: String,
    val siteKey: String,
    val actions: List<String>,
)

/**
 * 人机验证（spec auth-abuse-protection §6.1）。供应商目前是 Cloudflare Turnstile。
 *
 * 规则：
 * - **后端决定、后端核验**：前端只负责拿凭证；不带凭证或凭证不过一律拒绝，前端关掉组件绕不过去。
 * - **凭证绑定动作**：组件渲染时带上 action，核验时比对，注册页拿到的凭证不能拿去发短信。
 *   一次性与有效期由 Turnstile 保证（同一凭证二次核验返回 `timeout-or-duplicate`）。
 * - **不可用时拒绝**：打开了却没配 secret、或供应商不可达，都返回 [SystemErrorCodes.CAPTCHA_UNAVAILABLE]，
 *   绝不静默放行 —— 那样攻击者只要让验证服务超时就能绕过。
 *
 * secret 只从环境变量 `NETON_CAPTCHA_SECRET` 读取（配置表后台可读，不放 secret）；
 * `NETON_CAPTCHA_VERIFY_URL` 可以把核验地址指到测试桩。
 */
class CaptchaService(
    private val log: Logger,
    private val http: HttpClient,
    private val settings: SystemSettingLogic,
    private val secret: String?,
    private val verifyUrl: String = DEFAULT_VERIFY_URL,
) {

    suspend fun publicConfig(): CaptchaPublicVO {
        if (!settings.get(SystemSettingKeys.CAPTCHA_ENABLED)) return CaptchaPublicVO("", "", emptyList())
        return CaptchaPublicVO(
            provider = PROVIDER,
            siteKey = settings.get(SystemSettingKeys.CAPTCHA_SITE_KEY),
            actions = parseActions(settings.get(SystemSettingKeys.CAPTCHA_ACTIONS)).map { it.value },
        )
    }

    /** 该动作需要验证时核验 [token]，不过就抛异常；不需要验证时直接返回。 */
    suspend fun require(action: CaptchaAction, token: String?, remoteIp: String?) {
        if (!settings.get(SystemSettingKeys.CAPTCHA_ENABLED)) return
        if (action !in parseActions(settings.get(SystemSettingKeys.CAPTCHA_ACTIONS))) return
        val key = secret?.trim().orEmpty()
        if (key.isEmpty()) {
            log.error("captcha.not_configured action=${action.value}: set NETON_CAPTCHA_SECRET or turn captcha off")
            throw unavailable()
        }
        val response = token?.trim().orEmpty()
        if (response.isEmpty()) throw HttpException(NetonErrorCode.INVALID_PARAMS, SystemErrorCodes.CAPTCHA_REQUIRED)
        if (response.length > MAX_TOKEN_LENGTH) throw invalid()

        val body = buildMap {
            put("secret", JsonPrimitive(key))
            put("response", JsonPrimitive(response))
            remoteIp?.takeIf { it.isNotBlank() && it != "unknown" }?.let { put("remoteip", JsonPrimitive(it)) }
        }
        val raw = try {
            val resp = http.request(
                HttpClientRequest(
                    method = HttpClientMethod.Post,
                    url = verifyUrl,
                    body = HttpClientBody.Json(JsonObject(body).toString()),
                    timeout = HttpClientTimeouts(requestMillis = VERIFY_TIMEOUT_MILLIS),
                )
            )
            if (resp.statusCode !in 200..299) {
                log.error("captcha.verify.http_error status=${resp.statusCode} action=${action.value}")
                throw unavailable()
            }
            resp.body
        } catch (e: HttpException) {
            throw e
        } catch (e: Exception) {
            log.error("captcha.verify.request_failed action=${action.value}: ${e.message}")
            throw unavailable()
        }

        when (val verdict = judge(raw, action)) {
            Verdict.Pass -> Unit
            is Verdict.Reject -> {
                log.warn("captcha.verify.rejected", mapOf("action" to action.value, "reason" to verdict.reason))
                throw invalid()
            }
            Verdict.Malformed -> {
                log.error("captcha.verify.malformed_response action=${action.value}")
                throw unavailable()
            }
        }
    }

    sealed interface Verdict {
        data object Pass : Verdict
        data class Reject(val reason: String) : Verdict
        data object Malformed : Verdict
    }

    private fun invalid() = HttpException(NetonErrorCode.INVALID_PARAMS, SystemErrorCodes.CAPTCHA_INVALID)
    private fun unavailable() = HttpException(NetonErrorCode.SERVICE_UNAVAILABLE, SystemErrorCodes.CAPTCHA_UNAVAILABLE)

    companion object {
        const val PROVIDER = "turnstile"
        const val DEFAULT_VERIFY_URL = "https://challenges.cloudflare.com/turnstile/v0/siteverify"
        const val ENV_SECRET = "NETON_CAPTCHA_SECRET"
        const val ENV_VERIFY_URL = "NETON_CAPTCHA_VERIFY_URL"
        /** Turnstile 凭证最长 2048 字符，超出的直接判无效，不必打给供应商。 */
        const val MAX_TOKEN_LENGTH = 2048
        const val VERIFY_TIMEOUT_MILLIS = 5_000L
        const val HEADER = "X-Captcha-Token"

        /** siteverify 的响应 → 结论。纯函数，便于测试。 */
        fun judge(raw: String, action: CaptchaAction): Verdict {
            val obj = runCatching { json.parseToJsonElement(raw).jsonObject }.getOrNull() ?: return Verdict.Malformed
            val success = obj["success"]?.jsonPrimitive?.booleanOrNull ?: return Verdict.Malformed
            if (!success) {
                val codes = runCatching { obj["error-codes"].toString() }.getOrDefault("")
                return Verdict.Reject("failed $codes")
            }
            // 凭证签发时的 action 必须就是这次的动作；拿别处的凭证来用就拒绝
            val issuedFor = obj["action"]?.jsonPrimitive?.contentOrNull
            if (issuedFor != action.value) return Verdict.Reject("action mismatch issued=$issuedFor")
            return Verdict.Pass
        }

        private val json = Json { ignoreUnknownKeys = true }

        /** 后台填的动作列表：逗号分隔、忽略大小写与空白，不认识的写法丢弃。 */
        fun parseActions(raw: String): Set<CaptchaAction> = raw.split(',')
            .map { it.trim().lowercase() }
            .mapNotNull { v -> CaptchaAction.entries.firstOrNull { it.value == v } }
            .toSet()
    }
}
