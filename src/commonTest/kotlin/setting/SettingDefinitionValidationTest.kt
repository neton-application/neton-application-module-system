package setting

import kotlin.test.*

class SettingDefinitionValidationTest {
    @Test fun validationAppliesToNormalizedReadsAndWrites() {
        val definition = SettingDefinition.string("test", "test.provider", "AUTO", "Provider", "Map provider")
            .validated { it in setOf("AUTO", "GOOGLE", "AMAP") }
        assertEquals("GOOGLE", definition.parse(" GOOGLE "))
        assertNull(definition.parse("UNKNOWN"))
        assertEquals("AUTO", definition.defaultRaw)
    }

    @Test fun validationCannotIntroduceAnInvalidDefault() {
        assertFailsWith<IllegalArgumentException> {
            SettingDefinition.int("test", "test.count", 1, "Count", "Count").validated { it > 1 }
        }
    }

    @Test fun validatorsComposeAndRetainBaseGuards() {
        val definition = SettingDefinition.string("test", "test.name", "a", "Name", "Name", maxLength = 4)
            .validated { it.startsWith("a") }.validated { !it.contains("!") }
        assertEquals("abc", definition.parse("abc"))
        assertNull(definition.parse("abcde"))
        assertNull(definition.parse("b"))
        assertNull(definition.parse("a!"))
    }
}
