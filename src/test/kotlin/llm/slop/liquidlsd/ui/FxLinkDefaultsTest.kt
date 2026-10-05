package llm.slop.liquidlsd.ui

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class FxLinkDefaultsTest {
    @Test
    fun explicitPreferencesIgnoreControllers() {
        assertTrue(FxLinkDefaults.resolve(FxLinkDefault.LINKED) { true })
        assertFalse(FxLinkDefaults.resolve(FxLinkDefault.UNLINKED) { false })
    }

    @Test
    fun autoIsUnlinkedOnlyWithAKnobController() {
        assertFalse(FxLinkDefaults.resolve(FxLinkDefault.AUTO) { true })
        assertTrue(FxLinkDefaults.resolve(FxLinkDefault.AUTO) { false })
    }
}
