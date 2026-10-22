package llm.slop.liquidlsd.ui

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class FxLinkDefaultsTest {
    @Test
    fun preferenceMapsToTheStartingLinkState() {
        assertTrue(FxLinkDefaults.resolve(FxLinkDefault.LINKED))
        assertFalse(FxLinkDefaults.resolve(FxLinkDefault.UNLINKED))
    }
}
