package security

import neton.core.interfaces.Identity
import neton.core.interfaces.RequestContext
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 通配权限算法契约测试 —— 锁定 rbac-spec §4.2 的 5 个分支 + 1 个反向。
 *
 * 这些测试不仅验证当前实现，更重要的是防止未来"好心扩展"
 * 通配语义（例如支持 `*:user:list` 中段通配）—— 那是 spec 明确禁止的。
 */
class WildcardPermissionEvaluatorTest {

    private val evaluator = WildcardPermissionEvaluator()

    private fun identity(vararg perms: String) = object : Identity {
        override val id = "test-user"
        override val roles: Set<String> = emptySet()
        override val permissions: Set<String> = perms.toSet()
    }

    private val ctx = object : RequestContext {
        override val path = "/admin/system/user/page"
        override val method = "GET"
        override val headers: Map<String, String> = emptyMap()
        override val routeGroup: String? = "admin-api"
    }

    // ─── 5 个 PASS 分支 ────────────────────────────────────────────────────

    @Test
    fun `global wildcard matches anything`() {
        val id = identity("*:*:*")
        assertTrue(evaluator.allowed(id, "system:user:list", ctx))
        assertTrue(evaluator.allowed(id, "anything:at:all", ctx))
    }

    @Test
    fun `module wildcard matches all under module`() {
        val id = identity("system:*:*")
        assertTrue(evaluator.allowed(id, "system:user:list", ctx))
        assertTrue(evaluator.allowed(id, "system:role:delete", ctx))
        // 跨模块不应命中
        assertFalse(evaluator.allowed(id, "member:user:list", ctx))
    }

    @Test
    fun `resource wildcard matches all actions under resource`() {
        val id = identity("system:user:*")
        assertTrue(evaluator.allowed(id, "system:user:list", ctx))
        assertTrue(evaluator.allowed(id, "system:user:delete", ctx))
        // 跨资源不应命中
        assertFalse(evaluator.allowed(id, "system:role:list", ctx))
    }

    @Test
    fun `exact match`() {
        val id = identity("system:user:list")
        assertTrue(evaluator.allowed(id, "system:user:list", ctx))
    }

    @Test
    fun `no match returns false`() {
        val id = identity("system:role:list")
        assertFalse(evaluator.allowed(id, "system:user:list", ctx))
    }

    // ─── 反向：禁止形式（防止未来扩展污染） ─────────────────────────────────

    @Test
    fun `mid-segment wildcard in identity must NOT match`() {
        // identity 持有 `*:user:list` 这种中段通配，按 spec §4.1 必须不被支持
        val id = identity("*:user:list")
        assertFalse(
            evaluator.allowed(id, "system:user:list", ctx),
            "*:user:list must NOT match system:user:list — mid-segment wildcards forbidden"
        )

        // 同样：system:*:list 中段通配不允许
        val id2 = identity("system:*:list")
        assertFalse(
            evaluator.allowed(id2, "system:user:list", ctx),
            "system:*:list must NOT match system:user:list — mid-segment wildcards forbidden"
        )
    }

    @Test
    fun `wildcard in Permission required string is rejected for non-super`() {
        // rbac-spec §4.3：注解里不允许写通配；非超管用户遇到含 * 段的 required 一律拒
        val plainId = identity("system:user:list")
        assertFalse(evaluator.allowed(plainId, "system:*:*", ctx))
        assertFalse(evaluator.allowed(plainId, "system:user:*", ctx))
    }

    @Test
    fun `non three-segment required is rejected for non-super`() {
        // 注：identity 含 *:*:* 时，全局通配会短路 — super admin 优先于 required 校验。
        // 这里只测 plain identity 场景下非法 required 必须拒。
        val plainId = identity("system:user:list")
        assertFalse(evaluator.allowed(plainId, "user:list", ctx), "two-segment required must be rejected")
        assertFalse(evaluator.allowed(plainId, "system::list", ctx), "empty middle segment must be rejected")
        assertFalse(evaluator.allowed(plainId, "system:user:list:extra", ctx), "four-segment required must be rejected")
    }
}
