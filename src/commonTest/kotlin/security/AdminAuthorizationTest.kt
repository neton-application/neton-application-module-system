package security

import kotlinx.coroutines.runBlocking
import model.Role
import kotlin.test.Test
import kotlin.test.assertEquals

class AdminAuthorizationTest {
    private val superAdmins = CodeMatchSuperAdminEvaluator(setOf("root"))

    @Test
    fun disabledRolesNeverReachPermissionLookup() = runBlocking {
        val result = resolveAdminAuthorization(
            listOf(
                Role(id = 1, code = "root", name = "Root", status = 0),
                Role(id = 2, code = "reader", name = "Reader"),
            ), superAdmins,
        ) { ids ->
            assertEquals(setOf(2L), ids)
            setOf("system:user:query")
        }
        assertEquals(setOf("reader"), result.roles)
        assertEquals(setOf("system:user:query"), result.permissions)
    }

    @Test
    fun disabledSuperAdminHasNoClaims() = runBlocking {
        val result = resolveAdminAuthorization(
            listOf(Role(id = 1, code = "root", name = "Root", status = 0)), superAdmins,
        ) { error("Disabled roles must not load permissions") }
        assertEquals(AdminAuthorization(emptySet(), emptySet()), result)
    }

    @Test
    fun customEnabledSuperAdminGetsWildcard() = runBlocking {
        val result = resolveAdminAuthorization(
            listOf(Role(id = 1, code = "root", name = "Root")), superAdmins,
        ) { error("Super admin does not need a menu lookup") }
        assertEquals(AdminAuthorization(setOf("root"), setOf("*:*:*")), result)
    }

    @Test
    fun disabledSuperAdminPolicyDoesNotGrantWildcard() = runBlocking {
        val result = resolveAdminAuthorization(
            listOf(Role(id = 1, code = "super_admin", name = "Admin")),
            CodeMatchSuperAdminEvaluator(emptySet()),
        ) { setOf("system:user:query") }
        assertEquals(setOf("system:user:query"), result.permissions)
    }
}
