package security

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SuperAdminEvaluatorTest {

    @Test
    fun `default code matches super_admin role`() {
        val ev = CodeMatchSuperAdminEvaluator(setOf("super_admin"))
        assertTrue(ev.isSuperAdmin(setOf("super_admin", "user")))
        assertFalse(ev.isSuperAdmin(setOf("user", "ops")))
    }

    @Test
    fun `multiple super admin codes`() {
        val ev = CodeMatchSuperAdminEvaluator(setOf("super_admin", "root"))
        assertTrue(ev.isSuperAdmin(setOf("root")))
        assertTrue(ev.isSuperAdmin(setOf("super_admin")))
        assertFalse(ev.isSuperAdmin(setOf("admin")))
    }

    @Test
    fun `empty superAdminCodes disables super admin detection`() {
        val ev = CodeMatchSuperAdminEvaluator(emptySet())
        assertFalse(ev.isSuperAdmin(setOf("super_admin")))
        assertFalse(ev.isSuperAdmin(setOf("root")))
    }

    @Test
    fun `empty roleCodes never super admin`() {
        val ev = CodeMatchSuperAdminEvaluator(setOf("super_admin"))
        assertFalse(ev.isSuperAdmin(emptySet()))
    }
}
