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
