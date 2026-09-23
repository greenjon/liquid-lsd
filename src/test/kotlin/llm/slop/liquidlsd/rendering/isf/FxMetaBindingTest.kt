package llm.slop.liquidlsd.rendering.isf

import io.mockk.mockk
import kotlinx.serialization.json.JsonPrimitive
import llm.slop.liquidlsd.rendering.FxChain
import llm.slop.liquidlsd.rendering.Shader
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class FxMetaBindingTest {

    @Test
    fun testMetaLinkModesTransferMath() {
        val full = FxMetaBinding(targetParamName = "p", minVal = 0f, maxVal = 10f, linkMode = MetaLinkMode.FULL)
        assertEquals(0f, full.mapKnobToTarget(0f), 1e-5f)
        assertEquals(5f, full.mapKnobToTarget(0.5f), 1e-5f)
        assertEquals(10f, full.mapKnobToTarget(1f), 1e-5f)

        val firstHalf = FxMetaBinding(targetParamName = "p", minVal = 0f, maxVal = 10f, linkMode = MetaLinkMode.FIRST_HALF)
        assertEquals(0f, firstHalf.mapKnobToTarget(0f), 1e-5f)
        assertEquals(5f, firstHalf.mapKnobToTarget(0.25f), 1e-5f)
        assertEquals(10f, firstHalf.mapKnobToTarget(0.5f), 1e-5f)
        assertEquals(10f, firstHalf.mapKnobToTarget(0.75f), 1e-5f)
        assertEquals(10f, firstHalf.mapKnobToTarget(1f), 1e-5f)

        val secondHalf = FxMetaBinding(targetParamName = "p", minVal = 0f, maxVal = 10f, linkMode = MetaLinkMode.SECOND_HALF)
        assertEquals(0f, secondHalf.mapKnobToTarget(0f), 1e-5f)
        assertEquals(0f, secondHalf.mapKnobToTarget(0.25f), 1e-5f)
        assertEquals(0f, secondHalf.mapKnobToTarget(0.5f), 1e-5f)
        assertEquals(5f, secondHalf.mapKnobToTarget(0.75f), 1e-5f)
        assertEquals(10f, secondHalf.mapKnobToTarget(1f), 1e-5f)

        val triangle = FxMetaBinding(targetParamName = "p", minVal = 0f, maxVal = 10f, linkMode = MetaLinkMode.TRIANGLE)
        assertEquals(0f, triangle.mapKnobToTarget(0f), 1e-5f)
        assertEquals(5f, triangle.mapKnobToTarget(0.25f), 1e-5f)
        assertEquals(10f, triangle.mapKnobToTarget(0.5f), 1e-5f)
        assertEquals(5f, triangle.mapKnobToTarget(0.75f), 1e-5f)
        assertEquals(0f, triangle.mapKnobToTarget(1f), 1e-5f)

        val bipolar = FxMetaBinding(targetParamName = "p", minVal = 0f, maxVal = 10f, linkMode = MetaLinkMode.BIPOLAR)
        assertEquals(10f, bipolar.mapKnobToTarget(0f), 1e-5f)
        assertEquals(5f, bipolar.mapKnobToTarget(0.25f), 1e-5f)
        assertEquals(0f, bipolar.mapKnobToTarget(0.5f), 1e-5f)
        assertEquals(5f, bipolar.mapKnobToTarget(0.75f), 1e-5f)
        assertEquals(10f, bipolar.mapKnobToTarget(1f), 1e-5f)
    }

    @Test
    fun testMetaLinkInversion() {
        val firstHalfInv = FxMetaBinding(targetParamName = "p", minVal = 0f, maxVal = 10f, linkMode = MetaLinkMode.FIRST_HALF, invert = true)
        assertEquals(10f, firstHalfInv.mapKnobToTarget(0f), 1e-5f)
        assertEquals(5f, firstHalfInv.mapKnobToTarget(0.25f), 1e-5f)
        assertEquals(0f, firstHalfInv.mapKnobToTarget(0.5f), 1e-5f)
        assertEquals(0f, firstHalfInv.mapKnobToTarget(1f), 1e-5f)

        val triangleInv = FxMetaBinding(targetParamName = "p", minVal = 0f, maxVal = 10f, linkMode = MetaLinkMode.TRIANGLE, invert = true)
        assertEquals(10f, triangleInv.mapKnobToTarget(0f), 1e-5f)
        assertEquals(0f, triangleInv.mapKnobToTarget(0.5f), 1e-5f)
        assertEquals(10f, triangleInv.mapKnobToTarget(1f), 1e-5f)
    }

    @Test
    fun testISFFilterMultiParameterLinking() {
        val header = ISFHeader(INPUTS = listOf(
            ISFInput(NAME = "inputImage", TYPE = "image"),
            ISFInput(NAME = "cutoff", TYPE = "float", MIN = JsonPrimitive(0.0f), MAX = JsonPrimitive(1.0f), DEFAULT = JsonPrimitive(0.0f)),
            ISFInput(NAME = "resonance", TYPE = "float", MIN = JsonPrimitive(0.0f), MAX = JsonPrimitive(1.0f), DEFAULT = JsonPrimitive(0.0f))
        ))
        val shader = mockk<Shader>(relaxed = true)
        val filter = ISFFilter("filter1", "Filter 1", header, shader)

        // Configure multi-parameter linking: cutoff on first half, resonance on triangle
        filter.setParamLink("cutoff", MetaLinkMode.FIRST_HALF)
        filter.setParamLink("resonance", MetaLinkMode.TRIANGLE)

        assertEquals(2, filter.metaBindings.size)

        // Turn metaknob to 0.25
        filter.metaKnob.set(0.25f)
        filter.update()
        assertEquals(0.5f, filter.parameters["cutoff"]!!.baseValue, 1e-5f)
        assertEquals(0.5f, filter.parameters["resonance"]!!.baseValue, 1e-5f)

        // Turn metaknob to 0.5 (center)
        filter.metaKnob.set(0.5f)
        filter.update()
        assertEquals(1.0f, filter.parameters["cutoff"]!!.baseValue, 1e-5f)
        assertEquals(1.0f, filter.parameters["resonance"]!!.baseValue, 1e-5f)

        // Turn metaknob to 0.75
        filter.metaKnob.set(0.75f)
        filter.update()
        assertEquals(1.0f, filter.parameters["cutoff"]!!.baseValue, 1e-5f)
        assertEquals(0.5f, filter.parameters["resonance"]!!.baseValue, 1e-5f)

        // Turn metaknob to 1.0 (full)
        filter.metaKnob.set(1.0f)
        filter.update()
        assertEquals(1.0f, filter.parameters["cutoff"]!!.baseValue, 1e-5f)
        assertEquals(0.0f, filter.parameters["resonance"]!!.baseValue, 1e-5f)
    }

    @Test
    fun testFxChainPresetRoundtripPreservesMultiBindings() {
        val chain = FxChain("Chain 1")
        val header = ISFHeader(INPUTS = listOf(
            ISFInput(NAME = "inputImage", TYPE = "image"),
            ISFInput(NAME = "cutoff", TYPE = "float", MIN = JsonPrimitive(0.0f), MAX = JsonPrimitive(1.0f), DEFAULT = JsonPrimitive(0.0f)),
            ISFInput(NAME = "resonance", TYPE = "float", MIN = JsonPrimitive(0.0f), MAX = JsonPrimitive(1.0f), DEFAULT = JsonPrimitive(0.0f))
        ))
        val shader = mockk<Shader>(relaxed = true)
        val filter = ISFFilter("filter1", "Filter 1", header, shader)
        filter.setParamLink("cutoff", MetaLinkMode.FIRST_HALF)
        filter.setParamLink("resonance", MetaLinkMode.TRIANGLE)

        chain.slots[0] = filter
        val slotDto = chain.toFxSlotDto(0)
        org.junit.jupiter.api.Assertions.assertNotNull(slotDto)
        assertEquals(2, slotDto!!.metaBindings?.size)
        assertEquals(MetaLinkMode.FIRST_HALF.name, slotDto.metaBindings!![0].linkMode)
        assertEquals(MetaLinkMode.TRIANGLE.name, slotDto.metaBindings!![1].linkMode)

        // Restore onto another filter instance
        val restoredFilter = ISFFilter("filter1", "Filter 1", header, shader)
        val restoredBindings = slotDto.metaBindings!!.map { it.toBinding() }
        restoredFilter.applyMetaBindingsFromPreset(restoredBindings)

        assertEquals(2, restoredFilter.metaBindings.size)
        assertEquals(MetaLinkMode.FIRST_HALF, restoredFilter.getBindingForParam("cutoff")?.linkMode)
        assertEquals(MetaLinkMode.TRIANGLE, restoredFilter.getBindingForParam("resonance")?.linkMode)
    }
}
