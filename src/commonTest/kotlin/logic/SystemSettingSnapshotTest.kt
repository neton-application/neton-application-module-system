package logic

import setting.SettingDefinition
import kotlin.test.*

class SystemSettingSnapshotTest {
    private val enabled = SettingDefinition.boolean("example", "example.enabled", false, "Enabled", "Enabled")
    private val count = SettingDefinition.int("example", "example.count", 3, "Count", "Count", min = 1, max = 5)

    @Test fun snapshotUsesTypedDefinitionsAndDefensiveDefaults() {
        val snapshot = SystemSettingSnapshot("example", mapOf("example.enabled" to "true", "example.count" to "99"))
        assertTrue(snapshot.get(enabled))
        assertEquals(3, snapshot.get(count))
        assertFalse(SystemSettingSnapshot("example", emptyMap()).get(enabled))
    }

    @Test fun anotherCategoryCannotReadSnapshotValues() {
        val other = SettingDefinition.boolean("other", "other.enabled", false, "Enabled", "Enabled")
        assertFailsWith<IllegalArgumentException> { SystemSettingSnapshot("example", emptyMap()).get(other) }
    }
}
