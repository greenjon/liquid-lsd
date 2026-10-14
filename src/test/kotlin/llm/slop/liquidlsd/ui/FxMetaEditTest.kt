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

        val changed = FxMetaEdit.apply(fx, { undo += it }, { resyncs++ }) { it.setParamLink("cutoff", MetaLinkMode.TRIANGLE) }

        assertTrue(changed)
        assertEquals(1, resyncs)
        assertEquals(1, undo.size)
        assertEquals(MetaLinkMode.TRIANGLE, fx.getBindingForParam("cutoff")?.linkMode)

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
