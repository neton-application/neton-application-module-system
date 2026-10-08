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
        assertEquals(listOf("site.name", "site.tagline"), SystemSettingKeys.definitions.map { it.key })
        assertEquals("", SystemSettingKeys.SITE_TAGLINE.parse(""))
        assertNull(SystemSettingKeys.SITE_NAME.parse(""), "站点名称不能清空")
    }
}
