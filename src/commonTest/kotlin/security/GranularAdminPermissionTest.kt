package security

import neton.core.interfaces.Identity
import neton.core.interfaces.RequestContext
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * P0 granular RBAC 契约：非-super 管理员只能做被授予的动作。
 *
 * 背景：登录时 `AuthLogic` 用 `PermissionLogic.resolvePermissions(userId)` 把用户
 * role_menu 对应的 `menu.permission` 子集写进 JWT `perms` claim；请求时框架把
 * `identity.permissions` 交给 [WildcardPermissionEvaluator] 做精确判定。
 *
 * 本测试锁定「财务专员(finance_clerk)」场景：只授 `pay:withdraw:list` + `pay:withdraw:mark-paid`，
 * 就只能列表 + 标记打款，**不能** reveal / approve / mark-failed。
 * （resolvePermissions 从 DB 解析子集由 granular-admin-perm-e2e.sh 端到端覆盖；
 *   这里在纯单元层锁定「拿到子集后的 enforcement 语义」，防止未来 evaluator 回归。）
 */
class GranularAdminPermissionTest {

    private val evaluator = WildcardPermissionEvaluator()

    private fun identity(vararg perms: String) = object : Identity {
        override val id = "finance-clerk"
        override val roles: Set<String> = setOf("finance_clerk")
        override val permissions: Set<String> = perms.toSet()
    }

    private val ctx = object : RequestContext {
        override val path = "/admin/wallet/withdraw/page"
        override val method = "GET"
        override val headers: Map<String, String> = emptyMap()
        override val routeGroup: String? = "admin-api"
    }

    // finance_clerk resolvePermissions 的产物：恰好这两个，不多不少。
    private val clerk = identity("pay:withdraw:list", "pay:withdraw:mark-paid")

    @Test
    fun `finance_clerk can access granted actions`() {
        assertTrue(evaluator.allowed(clerk, "pay:withdraw:list", ctx), "有 list → 提现列表可访问")
        assertTrue(evaluator.allowed(clerk, "pay:withdraw:mark-paid", ctx), "有 mark-paid → 可标记打款")
    }

    @Test
    fun `finance_clerk denied ungranted money actions`() {
        assertFalse(evaluator.allowed(clerk, "pay:bank-card:reveal", ctx), "无 reveal → 不能解密银行卡")
        assertFalse(evaluator.allowed(clerk, "pay:withdraw:approve", ctx), "无 approve → 不能审核通过")
        assertFalse(evaluator.allowed(clerk, "pay:withdraw:mark-failed", ctx), "无 mark-failed → 不能标记失败")
        assertFalse(evaluator.allowed(clerk, "pay:withdraw:reject", ctx), "无 reject → 不能驳回")
    }

    @Test
    fun `finance_clerk subset does not leak into a wildcard`() {
        // 防止未来把「持有某模块任一权限」误扩成模块通配。
        assertFalse(evaluator.allowed(clerk, "pay:withdraw:detail", ctx), "未授予的同模块动作仍必须拒")
    }
}
