package llm.slop.liquidlsd.ui

import io.mockk.mockk
import kotlinx.serialization.json.JsonPrimitive
import llm.slop.liquidlsd.rendering.Shader
import llm.slop.liquidlsd.rendering.isf.FxMetaBinding
import llm.slop.liquidlsd.rendering.isf.ISFFilter
import llm.slop.liquidlsd.rendering.isf.ISFHeader
import llm.slop.liquidlsd.rendering.isf.ISFInput
import llm.slop.liquidlsd.rendering.isf.MetaLinkMode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FxMetaEditTest {
    private fun filter(): ISFFilter {
        val header = ISFHeader(INPUTS = listOf(
            ISFInput(NAME = "inputImage", TYPE = "image"),
            ISFInput(NAME = "cutoff", TYPE = "float", MIN = JsonPrimitive(0.0f), MAX = JsonPrimitive(1.0f), DEFAULT = JsonPrimitive(0.0f)),
            ISFInput(NAME = "resonance", TYPE = "float", MIN = JsonPrimitive(0.0f), MAX = JsonPrimitive(1.0f), DEFAULT = JsonPrimitive(0.0f))
        ))
        return ISFFilter("f", "F", header, mockk<Shader>(relaxed = true))
    }

    @Test
    fun editResyncsAndPushesARestoreThatPutsTheBindingsBack() {
        val fx = filter()
        val original = fx.metaBindings.toList()
        var resyncs = 0
        val undo = mutableListOf<() -> Unit>()

        val changed = FxMetaEdit.apply(fx, { undo += it }, { resyncs++ }) { it.setParamLink("resonance", MetaLinkMode.TRIANGLE) }

        assertTrue(changed)
        assertEquals(1, resyncs, "a newly owned parameter leaves the focus knobs")
        assertEquals(1, undo.size)
        assertEquals(MetaLinkMode.TRIANGLE, fx.getBindingForParam("resonance")?.linkMode)

        undo.single().invoke()
        assertEquals(original, fx.metaBindings.toList())
        assertEquals(2, resyncs, "undo re-syncs the knobs too")
    }

    @Test
    fun noOpEditLeavesUndoAndKnobsAlone() {
        val fx = filter()
        fx.rebindMetaKnob(FxMetaBinding.DRY_WET_SAFETY_NET)
        var resyncs = 0
        val undo = mutableListOf<() -> Unit>()

        val changed = FxMetaEdit.apply(fx, { undo += it }, { resyncs++ }) { it.rebindMetaKnob(FxMetaBinding.DRY_WET_SAFETY_NET) }

        assertFalse(changed)
        assertEquals(0, resyncs)
        assertTrue(undo.isEmpty())
    }
}

class FxMetaEditGestureTest {
    private fun filter(): ISFFilter {
        val header = ISFHeader(INPUTS = listOf(
            ISFInput(NAME = "inputImage", TYPE = "image"),
            ISFInput(NAME = "cutoff", TYPE = "float", MIN = JsonPrimitive(0.0f), MAX = JsonPrimitive(1.0f), DEFAULT = JsonPrimitive(0.0f))
        ))
        return ISFFilter("f", "F", header, mockk<Shader>(relaxed = true))
    }

    @Test
    fun aDragIsOneUndoStepAndRestoresTheStateFromBeforeIt() {
        val fx = filter()
        fx.setParamLink("cutoff", MetaLinkMode.FULL)
        val start = fx.metaBindings.toList()
        val undo = mutableListOf<() -> Unit>()
        var resyncs = 0
        FxMetaEdit.endGestureIfMouseUp(false)

        for (v in listOf(0.1f, 0.2f, 0.3f)) {
            FxMetaEdit.apply(fx, { undo += it }, { resyncs++ }, gesture = "drag") { FxMetaEdit.replace(it, it.getBindingForParam("cutoff")!!.copy(minVal = v)) }
        }
        assertEquals(1, undo.size, "one undo step for the whole drag")
        assertEquals(0, resyncs, "min/max edits do not move the focus knobs")

        undo.single().invoke()
        assertEquals(start, fx.metaBindings.toList())

        // After the mouse is released the next drag is a new step.
        FxMetaEdit.endGestureIfMouseUp(false)
        FxMetaEdit.apply(fx, { undo += it }, { resyncs++ }, gesture = "drag") { FxMetaEdit.replace(it, it.getBindingForParam("cutoff")!!.copy(maxVal = 0.5f)) }
        assertEquals(2, undo.size)
    }

    @Test
    fun enablingOrRemovingATargetResyncsBecauseItChangesWhichParametersTheKnobsShow() {
        val fx = filter()
        fx.setParamLink("cutoff", MetaLinkMode.FULL)
        var resyncs = 0
        FxMetaEdit.apply(fx, {}, { resyncs++ }) { FxMetaEdit.replace(it, it.getBindingForParam("cutoff")!!.copy(enabled = false)) }
        assertEquals(1, resyncs)
        FxMetaEdit.apply(fx, {}, { resyncs++ }) { FxMetaEdit.remove(it, "cutoff") }
        assertEquals(1, resyncs, "a disabled binding owned nothing, so removing it changes nothing")
    }
}

class FxKnobLayoutEditTest {
    private fun filter(): ISFFilter {
        val header = ISFHeader(INPUTS = listOf(
            ISFInput(NAME = "inputImage", TYPE = "image"),
            ISFInput(NAME = "a", TYPE = "float", MIN = JsonPrimitive(0.0f), MAX = JsonPrimitive(1.0f), DEFAULT = JsonPrimitive(0.0f)),
            ISFInput(NAME = "b", TYPE = "float", MIN = JsonPrimitive(0.0f), MAX = JsonPrimitive(1.0f), DEFAULT = JsonPrimitive(0.0f)),
            ISFInput(NAME = "c", TYPE = "float", MIN = JsonPrimitive(0.0f), MAX = JsonPrimitive(1.0f), DEFAULT = JsonPrimitive(0.0f))
        ))
        val f = ISFFilter("f", "F", header, mockk<Shader>(relaxed = true))
        f.applyMetaBindingsFromPreset(listOf(FxMetaBinding("a", 0f, 1f)))
        return f
    }

    @Test
    fun moveHideShowResetEditTheOrderAndResyncOnlyWhenTheKnobsChange() {
        val fx = filter()
        var resyncs = 0
        val undo = mutableListOf<() -> Unit>()
        fun edit(c: (ISFFilter) -> Unit) = FxMetaEdit.apply(fx, { undo += it }, { resyncs++ }, change = c)

        assertEquals(listOf("b", "c"), FxMetaEdit.shownKnobs(fx))
        assertTrue(edit { FxMetaEdit.moveKnob(it, 0, 1) })
        assertEquals(listOf("c", "b"), FxMetaEdit.shownKnobs(fx))
        assertEquals(1, resyncs)

        assertFalse(edit { FxMetaEdit.moveKnob(it, 1, 1) }, "moving the last one down is a no-op")

        assertTrue(edit { FxMetaEdit.hideKnob(it, "c") })
        assertEquals(listOf("b"), FxMetaEdit.shownKnobs(fx))
        assertEquals(listOf("a", "c"), FxMetaEdit.hiddenKnobs(fx), "a is hidden by the Metaknob link, c by the user")

        assertTrue(edit { FxMetaEdit.showKnob(it, "a") })
        assertEquals(listOf("b", "a"), FxMetaEdit.shownKnobs(fx))

        undo.last().invoke()
        assertEquals(listOf("b"), FxMetaEdit.shownKnobs(fx))
        undo.first().invoke()
        assertEquals(null, fx.knobLayout, "undoing the first edit restores the default (no explicit layout)")

        assertTrue(edit { FxMetaEdit.moveKnob(it, 0, 1) })
        assertTrue(edit { FxMetaEdit.resetKnobs(it) })
        assertEquals(listOf("b", "c"), FxMetaEdit.shownKnobs(fx))
    }
}
