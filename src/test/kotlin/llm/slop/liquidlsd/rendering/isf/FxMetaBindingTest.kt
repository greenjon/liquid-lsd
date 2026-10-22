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

class FxMetaBindingCurveParityTest {
    @Test
    fun metaBindingMatchesMacroBindingForEveryCurveLinkModeAndInvert() {
        for (curve in MetaCurve.entries) for (mode in MetaLinkMode.entries) for (inv in listOf(false, true)) {
            val meta = FxMetaBinding("p", -2f, 6f, curve, inv, mode, stepCount = 5)
            val macro = llm.slop.liquidlsd.macro.MacroBinding(
                parameterId = "p", targetType = llm.slop.liquidlsd.macro.MacroTargetType.PARAM_BASE_VALUE,
                minVal = -2f, maxVal = 6f,
                curve = llm.slop.liquidlsd.macro.MacroCurveType.valueOf(curve.name), inverted = inv,
                linkMode = llm.slop.liquidlsd.macro.MacroLinkMode.valueOf(mode.name), stepCount = 5
            )
            var k = 0f
            while (k <= 1.0001f) {
                assertEquals(llm.slop.liquidlsd.macro.MacroCurve.mapToRange(k, macro), meta.mapKnobToTarget(k), 1e-5f, "$curve $mode inv=$inv k=$k")
                k += 0.05f
            }
        }
    }

    @Test
    fun knobForTargetInvertsExactCurvesWithFullLink() {
        for (curve in listOf(MetaCurve.LINEAR, MetaCurve.EXPONENTIAL, MetaCurve.LOGARITHMIC, MetaCurve.S_CURVE)) for (inv in listOf(false, true)) {
            val b = FxMetaBinding("p", 0f, 10f, curve, inv)
            for (k in listOf(0f, 0.2f, 0.5f, 0.9f, 1f)) {
                assertEquals(k, b.knobForTarget(b.mapKnobToTarget(k)), 1e-3f, "$curve inv=$inv k=$k")
            }
        }
    }

    @Test
    fun dtoRoundTripsNewCurvesAndStepCountAndOldDtosStillLoad() {
        val b = FxMetaBinding("p", 0f, 1f, MetaCurve.STEP, true, MetaLinkMode.TRIANGLE, stepCount = 3)
        assertEquals(b, b.toDto().toBinding())
        val old = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
            .decodeFromString(FxMetaBindingDto.serializer(), """{"targetParamName":"p","curve":"EXPONENTIAL"}""")
        assertEquals(8, old.toBinding().stepCount)
        assertEquals(MetaCurve.EXPONENTIAL, old.toBinding().curve)
    }
}

class FxKnobLayoutTest {
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

    private fun names(f: ISFFilter) = f.focusParameters.map { it.key }

    @Test
    fun withoutALayoutTheKnobsShowAuthoredOrderMinusWhatTheMetaknobOwns() {
        assertEquals(listOf("b", "c"), names(filter()))
    }

    @Test
    fun aLayoutIsExactlyWhatTheKnobsShowIncludingOwnedParameters() {
        val f = filter()
        f.applyKnobLayoutFromPreset(listOf("c", "a"))
        assertEquals(listOf("c", "a"), names(f))
    }

    @Test
    fun staleAndDuplicateNamesAreDroppedAndNullRestoresTheDefault() {
        val f = filter()
        f.applyKnobLayoutFromPreset(listOf("c", "gone", "c", "b"))
        assertEquals(listOf("c", "b"), f.knobLayout)
        f.applyKnobLayoutFromPreset(null)
        assertEquals(listOf("b", "c"), names(f))
    }

    @Test
    fun theCacheFollowsLinkChanges() {
        val f = filter()
        assertEquals(listOf("b", "c"), names(f))
        f.setParamLink("b", MetaLinkMode.FULL)
        assertEquals(listOf("c"), names(f))
    }

    @Test
    fun slotDtoCarriesTheLayoutAndOldDtosHaveNone() {
        val chain = FxChain("c")
        val f = filter()
        f.applyKnobLayoutFromPreset(listOf("b", "c"))
        chain.slots[0] = f
        val dto = chain.toFxSlotDto(0)!!
        assertEquals(listOf("b", "c"), dto.knobLayout)
        val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true; encodeDefaults = true }
        val round = json.decodeFromString(llm.slop.liquidlsd.models.FXSlotDto.serializer(), json.encodeToString(llm.slop.liquidlsd.models.FXSlotDto.serializer(), dto))
        assertEquals(listOf("b", "c"), round.knobLayout)
        val oldJson = kotlinx.serialization.json.JsonObject(
            json.parseToJsonElement(json.encodeToString(llm.slop.liquidlsd.models.FXSlotDto.serializer(), dto)).let { it as kotlinx.serialization.json.JsonObject }
                .filterKeys { it != "knobLayout" }
        ).toString()
        assertEquals(null, json.decodeFromString(llm.slop.liquidlsd.models.FXSlotDto.serializer(), oldJson).knobLayout)
    }

    @Test
    fun cloneKeepsTheLayout() {
        val f = filter()
        f.applyKnobLayoutFromPreset(listOf("c"))
        assertEquals(listOf("c"), f.clone().knobLayout)
    }
}

class FxMetaLinkEdgeCasesTest {

    private fun filter(id: String = "edge", contentHash: String? = null): ISFFilter {
        val header = ISFHeader(INPUTS = listOf(
            ISFInput(NAME = "inputImage", TYPE = "image"),
            ISFInput(NAME = "amount", TYPE = "float", MIN = JsonPrimitive(0f), MAX = JsonPrimitive(1f), DEFAULT = JsonPrimitive(0.5f))
        ))
        return ISFFilter(id, id, header, mockk<Shader>(relaxed = true), contentHash = contentHash)
    }

    @Test
    fun nothingLinkedDrivesNothingAndStaysEmptyThroughASave() {
        val fx = filter()
        fx.applyMetaBindingsFromPreset(emptyList())
        fx.dryWet.baseValue = 0.6f
        fx.metaKnob.baseValue = 0.1f
        fx.update()
        assertEquals(0.6f, fx.dryWet.baseValue, 1e-6f, "no Dry/Wet safety net sneaks in")

        val chain = FxChain("t")
        chain.slots[0] = fx
        val dto = chain.toFxSlotDto(0)!!
        assertEquals(emptyList<FxMetaBindingDto>(), dto.metaBindings)
        assertEquals(null, dto.metaBinding)
    }

    @Test
    fun aLinkToAMissingParameterDrivesNothing() {
        val fx = filter()
        fx.applyMetaBindingsFromPreset(listOf(FxMetaBinding("renamedAway", 0f, 1f)))
        fx.dryWet.baseValue = 0.6f
        fx.metaKnob.baseValue = 0f
        fx.update()
        assertEquals(0.6f, fx.dryWet.baseValue, 1e-6f)
        org.junit.jupiter.api.Assertions.assertTrue(fx.isMissingTarget(fx.metaBindings.single()))
    }

    @Test
    fun theDryWetSafetyNetLocksDryWet() {
        val fx = filter()
        fx.applyMetaBindingsFromPreset(listOf(FxMetaBinding.DRY_WET_SAFETY_NET))
        fx.metaKnob.baseValue = 0.25f
        fx.update()
        assertEquals(0.25f, fx.dryWet.baseValue, 1e-6f)
        assertEquals("Metaknob", fx.dryWet.metaDrivenBy)

        fx.applyMetaBindingsFromPreset(emptyList())
        fx.update()
        assertEquals(null, fx.dryWet.metaDrivenBy, "released once the link is gone")
    }

    @Test
    fun aUserShaderSharingAStockIdDoesNotGetTheCuratedLink() {
        // "bloom" is curated to bloomIntensity, which this shader doesn't have.
        val fx = filter(id = "bloom")
        assertEquals("amount", fx.metaBinding.targetParamName)
    }

    @Test
    fun aResetPlacesASeveralTargetMetaknobAtTheBestCompromise() {
        val header = ISFHeader(INPUTS = listOf(
            ISFInput(NAME = "inputImage", TYPE = "image"),
            ISFInput(NAME = "a", TYPE = "float", MIN = JsonPrimitive(0f), MAX = JsonPrimitive(1f), DEFAULT = JsonPrimitive(0f)),
            ISFInput(NAME = "b", TYPE = "float", MIN = JsonPrimitive(0f), MAX = JsonPrimitive(1f), DEFAULT = JsonPrimitive(0.3f))
        ))
        val fx = ISFFilter("multi", "multi", header, mockk<Shader>(relaxed = true))
        // a sweeps the second half only, so any knob position in the first half reproduces its default 0; b sweeps the
        // whole travel. Only knob 0.3 reproduces both, which placing the knob from the first target alone can miss.
        fx.applyMetaBindingsFromPreset(listOf(
            FxMetaBinding("a", 0f, 1f, linkMode = MetaLinkMode.SECOND_HALF),
            FxMetaBinding("b", 0f, 1f)
        ))
        fx.reset()
        fx.update()
        assertEquals(0.3f, fx.metaKnob.baseValue, 0.006f)
        assertEquals(0f, fx.parameters["a"]!!.baseValue, 0.02f, "a keeps its default")
        assertEquals(0.3f, fx.parameters["b"]!!.baseValue, 0.02f, "b keeps its default")
    }

    @Test
    fun exponentialNamesMatchWordsNotSubstrings() {
        org.junit.jupiter.api.Assertions.assertTrue(ISFAutoBindEngine.isExponentialName("fbDecay"))
        org.junit.jupiter.api.Assertions.assertTrue(ISFAutoBindEngine.isExponentialName("scroll_speed"))
        org.junit.jupiter.api.Assertions.assertTrue(ISFAutoBindEngine.isExponentialName("Rate"))
        org.junit.jupiter.api.Assertions.assertFalse(ISFAutoBindEngine.isExponentialName("saturate"))
        org.junit.jupiter.api.Assertions.assertFalse(ISFAutoBindEngine.isExponentialName("iterations"))
    }
}
