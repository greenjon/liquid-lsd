package llm.slop.liquidlsd.rendering

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FlameEditorLayoutTest {
    private fun ids(p: Map<String, *>, keep: String? = null) = FlameGenome.editorTabs(
        @Suppress("UNCHECKED_CAST") (p as Map<String, llm.slop.liquidlsd.parameters.ModulatableParameter>), keep
    ).map { it.id }

    @Test fun everyParameterAppearsExactlyOnceWhenAllShown() {
        val p = FlameGenome.buildParameters()
        repeat(FlameGenome.MAX_XF) { p["X${it + 1} Weight"]!!.baseValue = 1f }
        val listed = FlameGenome.editorTabs(p, null).flatMap { t -> t.sections.flatMap { it.params } }
        assertEquals(listed.size, listed.toSet().size)
        assertEquals(p.keys.toSet(), listed.toSet())
    }

    @Test fun emptyXformsHideAndRevealInOrder() {
        val p = FlameGenome.buildParameters()
        assertEquals(listOf("Look", "Mods", "X1", "X2", "X3", "XF"), ids(p))   // starter has 3 active
        assertEquals(3, FlameGenome.firstHiddenXform(p))
        assertEquals("X4", FlameGenome.reveal(p, 3))
        assertTrue("X4" in ids(p))
        p["X4 Weight"]!!.baseValue = 0f
        assertTrue("X4" in ids(p, keep = "X4"))
        assertTrue("X4" !in ids(p))
    }
}
