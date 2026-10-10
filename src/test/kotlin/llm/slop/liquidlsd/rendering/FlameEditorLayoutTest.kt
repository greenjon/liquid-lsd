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
        // only the variation parameters the chosen variation uses are listed (the starter uses parameterless ones)
        assertEquals(p.keys.filterNot { Regex(""" V\d [ABCD]$""").containsMatchIn(it) }.toSet(), listed.toSet())
    }

    @Test fun parametricVariationListsItsOwnParametersWithLabels() {
        val p = FlameGenome.buildParameters()
        p["X1 V1 Id"]!!.baseValue = FlameVariations.NAMES.indexOf("ngon").toFloat()
        val sec = FlameGenome.editorTabs(p, "X1").first { it.id == "X1" }.sections.first { it.title == "Variation 1" }
        assertEquals(listOf("X1 V1 Id", "X1 V1 Weight", "X1 V1 A", "X1 V1 B", "X1 V1 C", "X1 V1 D"), sec.params)
        assertEquals("Sides", sec.rowLabels["X1 V1 B"])
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
