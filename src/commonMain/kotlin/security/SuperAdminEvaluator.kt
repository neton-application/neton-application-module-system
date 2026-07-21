package security

/**
 * 判断角色集合是否构成超级管理员 —— rbac-spec §7。
 *
 * 此 SPI 把"哪些 role code 视为 super admin"的策略与 [logic.PermissionLogic]
 * 解耦，避免硬编码字符串。未来可替换为：
 *   - 多租户 super admin
 *   - 平台级 super admin
 *   - 基于外部 IAM 的判定
 */
fun interface SuperAdminEvaluator {
    fun isSuperAdmin(roleCodes: Set<String>): Boolean
}

/**
 * 默认实现：角色 code 命中 [superAdminCodes] 集合即为超级管理员。
 *
 * `superAdminCodes` 为空集合表示**禁用超级管理员判定** —— rbac-spec §7.4 + 配置策略。
 */
class CodeMatchSuperAdminEvaluator(
    private val superAdminCodes: Set<String>
) : SuperAdminEvaluator {
    override fun isSuperAdmin(roleCodes: Set<String>): Boolean =
        roleCodes.any { it in superAdminCodes }
}
