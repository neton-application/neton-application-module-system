package security

import model.Role

data class AdminAuthorization(
    val roles: Set<String>,
    val permissions: Set<String>,
)

internal suspend fun resolveAdminAuthorization(
    assignedRoles: List<Role>,
    superAdminEvaluator: SuperAdminEvaluator,
    loadPermissions: suspend (Set<Long>) -> Set<String>,
): AdminAuthorization {
    val enabled = assignedRoles.filter { it.status == 1 }
    val codes = enabled.map { it.code }.toSet()
    val permissions = when {
        enabled.isEmpty() -> emptySet()
        superAdminEvaluator.isSuperAdmin(codes) -> setOf("*:*:*")
        else -> loadPermissions(enabled.map { it.id }.toSet()).toSet()
    }
    return AdminAuthorization(codes, permissions)
}
