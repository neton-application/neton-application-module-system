package setting

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class SettingDefinitionStringTest {

    private fun def(allowBlank: Boolean = false, maxLength: Int = Int.MAX_VALUE, default: String = "x") =
        SettingDefinition.string("t", "t.k", default, "n", "d", allowBlank = allowBlank, maxLength = maxLength)

    @Test
    fun blankRejectedByDefault() {
        assertNull(def().parse(""))
        assertNull(def().parse("   "), "trim 后为空也算空")
    }

    @Test
    fun blankAcceptedWhenAllowed() {
        assertEquals("", def(allowBlank = true).parse(""))
        assertEquals("", def(allowBlank = true, default = "").defaultRaw)
    }

    @Test
    fun maxLengthEnforced() {
        assertEquals("abcd", def(maxLength = 4).parse("abcd"))
        assertNull(def(maxLength = 4).parse("abcde"))
    }

    @Test
    fun invalidDefaultsFailAtDeclaration() {
        assertFailsWith<IllegalArgumentException> { def(default = "") }
        assertFailsWith<IllegalArgumentException> { def(maxLength = 2, default = "abc") }
    }

    @Test
    fun siteKeysAreWellFormed() {
        assertEquals(
            listOf(
                "system.site.name", "system.site.tagline",
                "system.security.captcha.enabled", "system.security.captcha.site_key", "system.security.captcha.actions",
            ),
            SystemSettingKeys.definitions.map { it.key },
        )
        assertEquals("system", SystemSettingKeys.CATEGORY)
        assertEquals("", SystemSettingKeys.SITE_TAGLINE.parse(""))
        assertNull(SystemSettingKeys.SITE_NAME.parse(""), "站点名称不能清空")
        // 人机验证默认关闭：没配 secret 的部署升级后不能突然把注册挡住
        assertEquals(false, SystemSettingKeys.CAPTCHA_ENABLED.default)
    }

    @Test
    fun keyMustStartWithModuleName() {
        assertFailsWith<IllegalArgumentException> { SettingDefinition.string("system", "site.name", "x", "n", "d") }
        assertFailsWith<IllegalArgumentException> { SettingDefinition.string("system", "system.", "x", "n", "d") }
        assertFailsWith<IllegalArgumentException> { SettingDefinition.boolean("member", "membership.x", true, "n", "d") }
    }
}
