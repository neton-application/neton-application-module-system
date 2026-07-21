package security

import neton.core.interfaces.Identity
import neton.core.interfaces.PermissionEvaluator
import neton.core.interfaces.RequestContext

/**
 * 通配权限评估器 —— 应用脚手架对 [PermissionEvaluator] 的实现。
 *
 * 冻结的匹配顺序（rbac-spec §4.2）：
 *   1. `*:*:*`              → 全局匹配（super admin）
 *   2. `<module>:*:*`       → 整模块管理员
 *   3. `<module>:<res>:*`   → 资源管理员
 *   4. `<module>:<res>:<a>` → 精确匹配
 *   5. 其他                  → 拒绝
 *
 * 严格不支持的形式（rbac-spec §4.1）：
 *   - 中段通配：`*:user:list` / `system:*:list`
 *   - 单字符 `*`
 *
 * `@Permission("xxx")` 注解中**不允许**写通配（rbac-spec §4.3）。
 * 注解里若出现非三段式串，统一视为非法 required，拒绝。
 */
class WildcardPermissionEvaluator : PermissionEvaluator {

    override fun allowed(identity: Identity, permission: String, context: RequestContext): Boolean {
        // 全局通配
        if (GLOBAL_WILDCARD in identity.permissions) return true

        // required 必须严格三段式（不允许通配）
        val parts = permission.split(":")
        if (parts.size != 3 || parts.any { it.isEmpty() || it == WILDCARD }) return false

        val (m, r, a) = parts
        // 模块通配
        if ("$m:$WILDCARD:$WILDCARD" in identity.permissions) return true
        // 资源通配
        if ("$m:$r:$WILDCARD" in identity.permissions) return true
        // 精确匹配
        return permission in identity.permissions
    }

    private companion object {
        const val WILDCARD = "*"
        const val GLOBAL_WILDCARD = "*:*:*"
    }
}
