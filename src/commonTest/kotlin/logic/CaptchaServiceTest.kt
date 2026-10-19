package logic

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class CaptchaServiceTest {

    @Test
    fun passesWhenTheTokenWasIssuedForThisAction() {
        val raw = """{"success":true,"action":"register","hostname":"neton.cloud","error-codes":[]}"""
        assertEquals(CaptchaService.Verdict.Pass, CaptchaService.judge(raw, CaptchaAction.REGISTER))
    }

    @Test
    fun rejectsATokenIssuedForAnotherAction() {
        // 注册页拿到的凭证不能拿去发短信
        val raw = """{"success":true,"action":"register"}"""
        assertIs<CaptchaService.Verdict.Reject>(CaptchaService.judge(raw, CaptchaAction.SEND_CODE))
    }

    @Test
    fun rejectsAFailedOrReusedToken() {
        val raw = """{"success":false,"error-codes":["timeout-or-duplicate"]}"""
        assertIs<CaptchaService.Verdict.Reject>(CaptchaService.judge(raw, CaptchaAction.LOGIN))
    }

    @Test
    fun unreadableResponseIsNotAPass() {
        assertEquals(CaptchaService.Verdict.Malformed, CaptchaService.judge("<html>", CaptchaAction.LOGIN))
        assertEquals(CaptchaService.Verdict.Malformed, CaptchaService.judge("""{"action":"login"}""", CaptchaAction.LOGIN))
    }

    @Test
    fun actionListIgnoresCaseSpacingAndUnknownEntries() {
        assertEquals(
            setOf(CaptchaAction.REGISTER, CaptchaAction.ADMIN_LOGIN),
            CaptchaService.parseActions(" Register , admin_login,bogus,,"),
        )
        assertEquals(emptySet(), CaptchaService.parseActions(""))
    }
}
