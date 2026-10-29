package llm.slop.liquidlsd.ui

import llm.slop.liquidlsd.rendering.FlameGenome
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The flame's named variation choice is locked like the other choice widgets when something drives its base value. */
class StructuredEditorLockTest {
    @Test fun choiceIsFreeUntilAMetaknobDrivesIt() {
        val p = FlameGenome.buildParameters().getValue("X1 V1 Id")
        val key = "Deck A/Fractal Flame/X1 V1 Id"
        assertFalse(StructuredEditorPanel.isBaseLocked(key, p))
        p.metaDrivenBy = "Metaknob"
        assertTrue(StructuredEditorPanel.isBaseLocked(key, p))
    }
}
