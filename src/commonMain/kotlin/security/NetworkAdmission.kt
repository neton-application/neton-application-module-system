package security

import logic.SystemErrorCodes
import neton.core.component.NetonContext
import neton.core.config.ConfigLoader
import neton.core.http.HttpContext
import neton.core.http.HttpStatus
import neton.core.interfaces.RequestAdmissionGate

/** Immutable startup snapshot: no database, DNS or credential checks on rejected requests. */
class NetworkAdmission(
    private val policies: Map<String, List<IpNetwork>>,
) : RequestAdmissionGate {
    fun permits(routeGroup: String?, address: String): Boolean =
        policies[routeGroup]?.any { it.contains(address) } ?: true

    override suspend fun allow(context: HttpContext, routeGroup: String?): Boolean {
        if (routeGroup !in policies) return true
        val ip = context.request.clientAddress
        if (permits(routeGroup, ip)) return true
        context.response.status = HttpStatus.FORBIDDEN
        context.response.json(mapOf("code" to neton.core.http.NetonErrorCode.PERMISSION_DENIED, "message" to SystemErrorCodes.IP_NOT_ALLOWED))
        return false
    }

    companion object {
        fun load(ctx: NetonContext): NetworkAdmission {
            val env = ConfigLoader.resolveEnvironment(ctx.args)
            val config = ConfigLoader.loadApplicationConfig("config", env, ctx.args)
            val policies = mutableMapOf<String, List<IpNetwork>>()
            for (group in listOf("admin", "app")) {
                val base = "security.network.$group"
                val rawEnabled = ConfigLoader.getConfigValue(config, "$base.enabled")
                val enabled = when (rawEnabled?.toString()?.lowercase()) {
                    null, "false" -> false
                    "true" -> true
                    else -> error("$base.enabled must be true or false")
                }
                val raw = ConfigLoader.getConfigValue(config, "$base.allowlist")
                require(raw == null || raw is String) { "$base.allowlist must be a comma-separated string" }
                val entries = (raw as? String).orEmpty()
                require(entries.length <= 16384) { "$base.allowlist is too large" }
                val networks = if (entries.isBlank()) emptyList() else entries.split(',').map {
                    require(it.isNotBlank()) { "$base.allowlist contains an empty entry" }
                    IpNetwork.parse(it)
                }
                require(networks.size <= 256) { "$base.allowlist supports at most 256 entries" }
                if (enabled) policies[group] = networks
            }
            return NetworkAdmission(policies.toMap())
        }
    }
}
