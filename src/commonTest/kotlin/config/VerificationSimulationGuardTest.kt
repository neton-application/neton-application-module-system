package config

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** 模拟通道的环境闸（spec auth-abuse-protection §5.2）。 */
class VerificationSimulationGuardTest {

    @Test
    fun simulationOffNeedsNoEnvironment() {
        requireSimulationAllowed(false, null)
        requireSimulationAllowed(false, "prod")
    }

    @Test
    fun simulationAllowedOnlyInExplicitDevelopmentEnvironments() {
        for (env in listOf("dev", "DEV", " local ", "test")) requireSimulationAllowed(true, env)
    }

    @Test
    fun productionLikeEnvironmentsRefuseToStart() {
        for (env in listOf("prod", "production", "staging", "PROD", "")) {
            val e = assertFailsWith<IllegalStateException>(env) { requireSimulationAllowed(true, env) }
            assertTrue(e.message!!.contains("simulate"), e.message)
        }
    }

    @Test
    fun implicitDefaultEnvironmentIsNotTrusted() {
        // 没声明环境时框架默认按 dev 跑，但这也是生产机忘配 NETON_ENV 的样子。
        val e = assertFailsWith<IllegalStateException> { requireSimulationAllowed(true, null) }
        assertTrue(e.message!!.contains("NETON_ENV=dev"), e.message)
    }

    @Test
    fun cliEnvironmentIsExplicit() {
        assertEquals("test", explicitEnvironment(arrayOf("--env=test")))
        assertEquals("prod", explicitEnvironment(arrayOf("--env= prod ")))
    }
}
