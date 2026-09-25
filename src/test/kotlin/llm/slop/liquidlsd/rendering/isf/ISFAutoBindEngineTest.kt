package llm.slop.liquidlsd.rendering.isf

import io.mockk.mockk
import kotlinx.serialization.json.JsonPrimitive
import llm.slop.liquidlsd.rendering.Shader
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ISFAutoBindEngineTest {

    private fun filterWithInputs(id: String, vararg inputs: ISFInput): ISFFilter {
        val header = ISFHeader(INPUTS = listOf(ISFInput(NAME = "inputImage", TYPE = "image")) + inputs)
        val shader = mockk<Shader>(relaxed = true)
        return ISFFilter(id, id, header, shader, contentHash = "test_$id")
    }

    @Test
    fun `identity at min sweeps min to max`() {
        val filter = filterWithInputs(
            "unbound1",
            ISFInput(NAME = "blur", TYPE = "float", MIN = JsonPrimitive(0.0f), MAX = JsonPrimitive(50.0f), DEFAULT = JsonPrimitive(0.0f), IDENTITY = JsonPrimitive(0.0f))
        )
        assertEquals("blur", filter.metaBinding.targetParamName)
        assertEquals(0f, filter.metaBinding.mapKnobToTarget(0f))
        assertEquals(50f, filter.metaBinding.mapKnobToTarget(1f))
    }

    @Test
    fun `identity at max sweeps inverted`() {
        val filter = filterWithInputs(
            "unbound2",
            ISFInput(NAME = "opacity", TYPE = "float", MIN = JsonPrimitive(0.0f), MAX = JsonPrimitive(1.0f), DEFAULT = JsonPrimitive(1.0f), IDENTITY = JsonPrimitive(1.0f))
        )
        assertEquals("opacity", filter.metaBinding.targetParamName)
        assertEquals(1f, filter.metaBinding.mapKnobToTarget(0f), 0.0001f)
        assertEquals(0f, filter.metaBinding.mapKnobToTarget(1f), 0.0001f)
    }

    @Test
    fun `identity interior sweeps toward the farther bound`() {
        val filter = filterWithInputs(
            "unbound3",
            ISFInput(NAME = "hueRotate", TYPE = "float", MIN = JsonPrimitive(-180.0f), MAX = JsonPrimitive(180.0f), DEFAULT = JsonPrimitive(0.0f), IDENTITY = JsonPrimitive(0.0f))
        )
        assertEquals("hueRotate", filter.metaBinding.targetParamName)
        // identity (0) is equidistant from both bounds in this case; either is a valid "farther" choice,
        // just confirm the sweep starts at identity and reaches one of the two extremes.
        assertEquals(0f, filter.metaBinding.mapKnobToTarget(0f), 0.0001f)
        assertTrue(filter.metaBinding.mapKnobToTarget(1f) == 180f || filter.metaBinding.mapKnobToTarget(1f) == -180f)
    }

    @Test
    fun `semantic name match wins when no identity declared`() {
        val filter = filterWithInputs(
            "unbound4",
            ISFInput(NAME = "someRandomName", TYPE = "float", MIN = JsonPrimitive(0.0f), MAX = JsonPrimitive(10.0f)),
            ISFInput(NAME = "intensity", TYPE = "float", MIN = JsonPrimitive(0.0f), MAX = JsonPrimitive(2.0f))
        )
        assertEquals("intensity", filter.metaBinding.targetParamName)
        assertEquals(0f, filter.metaBinding.minVal)
        assertEquals(2f, filter.metaBinding.maxVal)
    }

    @Test
    fun `time-like semantic names get an exponential curve`() {
        val filter = filterWithInputs(
            "unbound5",
            ISFInput(NAME = "decay", TYPE = "float", MIN = JsonPrimitive(0.0f), MAX = JsonPrimitive(1.0f))
        )
        assertEquals("decay", filter.metaBinding.targetParamName)
        assertEquals(MetaCurve.EXPONENTIAL, filter.metaBinding.curve)
    }

    @Test
    fun `single float falls back unconditionally`() {
        val filter = filterWithInputs(
            "unbound6",
            ISFInput(NAME = "weirdParam", TYPE = "float", MIN = JsonPrimitive(5.0f), MAX = JsonPrimitive(9.0f))
        )
        assertEquals("weirdParam", filter.metaBinding.targetParamName)
        assertEquals(5f, filter.metaBinding.minVal)
        assertEquals(9f, filter.metaBinding.maxVal)
    }

    @Test
    fun `multiple floats none normalized falls back to first declared`() {
        val filter = filterWithInputs(
            "unbound7",
            ISFInput(NAME = "radius", TYPE = "float", MIN = JsonPrimitive(0.0f), MAX = JsonPrimitive(100.0f)),
            ISFInput(NAME = "angle", TYPE = "float", MIN = JsonPrimitive(0.0f), MAX = JsonPrimitive(360.0f))
        )
        assertEquals("radius", filter.metaBinding.targetParamName)
    }

    @Test
    fun `multiple floats one normalized is preferred over first declared`() {
        val filter = filterWithInputs(
            "unbound8",
            ISFInput(NAME = "radius", TYPE = "float", MIN = JsonPrimitive(0.0f), MAX = JsonPrimitive(100.0f)),
            ISFInput(NAME = "fade", TYPE = "float", MIN = JsonPrimitive(0.0f), MAX = JsonPrimitive(1.0f))
        )
        assertEquals("fade", filter.metaBinding.targetParamName)
    }

    @Test
    fun `no float candidates falls back to dry-wet safety net`() {
        val filter = filterWithInputs(
            "unbound9",
            ISFInput(NAME = "center", TYPE = "point2D")
        )
        assertNull(filter.metaBinding.targetParamName)
        assertEquals(0f, filter.metaBinding.mapKnobToTarget(0f))
        assertEquals(1f, filter.metaBinding.mapKnobToTarget(1f))
    }

    @Test
    fun `metaknob starts at the position that reproduces the target's authored default`() {
        val filter = filterWithInputs(
            "unbound10",
            ISFInput(NAME = "amount", TYPE = "float", MIN = JsonPrimitive(0.0f), MAX = JsonPrimitive(1.0f), DEFAULT = JsonPrimitive(0.7f))
        )
        // amount matches semantic list -> bound MIN..MAX linear; default 0.7 should map back from knob position.
        assertEquals(0.7f, filter.parameters["amount"]?.baseValue)
        assertEquals(0.7f, filter.metaKnob.baseValue, 0.001f)
    }

    @Test
    fun `curated binding used for bundled invert filter`() {
        val header = ISFHeader(INPUTS = listOf(
            ISFInput(NAME = "inputImage", TYPE = "image"),
            ISFInput(NAME = "invertIntensity", TYPE = "float", MIN = JsonPrimitive(0.0f), MAX = JsonPrimitive(1.0f), DEFAULT = JsonPrimitive(1.0f))
        ))
        val shader = mockk<Shader>(relaxed = true)
        val filter = ISFFilter("invert", "Invert", header, shader)
        assertEquals("invertIntensity", filter.metaBinding.targetParamName)
    }

    @Test
    fun `rebinding persists and reloads via content hash override`() {
        val hash = "test_rebind_${System.nanoTime()}"
        val header = ISFHeader(INPUTS = listOf(
            ISFInput(NAME = "inputImage", TYPE = "image"),
            ISFInput(NAME = "alpha", TYPE = "float", MIN = JsonPrimitive(0.0f), MAX = JsonPrimitive(1.0f)),
            ISFInput(NAME = "beta", TYPE = "float", MIN = JsonPrimitive(0.0f), MAX = JsonPrimitive(1.0f))
        ))
        val shader = mockk<Shader>(relaxed = true)
        val filter1 = ISFFilter("unbound_rebind", "Unbound", header, shader, contentHash = hash)

        filter1.rebindMetaKnob(FxMetaBinding("beta", 0f, 1f), persistOverride = true)
        assertEquals("beta", filter1.metaBinding.targetParamName)

        // A fresh filter instance sharing the same content hash should pick up the persisted override.
        val filter2 = ISFFilter("unbound_rebind", "Unbound", header, shader, contentHash = hash)
        assertEquals("beta", filter2.metaBinding.targetParamName)

        ISFAutoBindEngine.deleteOverride(hash)
    }

    @Test
    fun `rebindMetaKnob does not implicitly write to disk`() {
        val hash = "test_no_implicit_${System.nanoTime()}"
        val header = ISFHeader(INPUTS = listOf(
            ISFInput(NAME = "inputImage", TYPE = "image"),
            ISFInput(NAME = "alpha", TYPE = "float", MIN = JsonPrimitive(0.0f), MAX = JsonPrimitive(1.0f)),
            ISFInput(NAME = "beta", TYPE = "float", MIN = JsonPrimitive(0.0f), MAX = JsonPrimitive(1.0f))
        ))
        val shader = mockk<Shader>(relaxed = true)
        val filter1 = ISFFilter("unbound_no_implicit", "Unbound", header, shader, contentHash = hash)

        // Default persistOverride is false
        filter1.rebindMetaKnob(FxMetaBinding("beta", 0f, 1f))
        assertEquals("beta", filter1.metaBinding.targetParamName)

        assertFalse(ISFAutoBindEngine.hasFilterDefault(filter1))

        // Fresh filter should NOT see the override
        val filter2 = ISFFilter("unbound_no_implicit", "Unbound", header, shader, contentHash = hash)
        assertEquals("alpha", filter2.metaBinding.targetParamName)
    }

    @Test
    fun `v2 multi-binding and parameter baselines round-trip`() {
        val hash = "test_v2_${System.nanoTime()}"
        val header = ISFHeader(INPUTS = listOf(
            ISFInput(NAME = "inputImage", TYPE = "image"),
            ISFInput(NAME = "gain", TYPE = "float", MIN = JsonPrimitive(0.0f), MAX = JsonPrimitive(2.0f), DEFAULT = JsonPrimitive(1.0f)),
            ISFInput(NAME = "cutoff", TYPE = "float", MIN = JsonPrimitive(20.0f), MAX = JsonPrimitive(20000.0f), DEFAULT = JsonPrimitive(1000.0f))
        ))
        val shader = mockk<Shader>(relaxed = true)
        val filter1 = ISFFilter("unbound_v2", "Unbound", header, shader, contentHash = hash)

        filter1.parameters["gain"]?.baseValue = 1.8f
        filter1.parameters["cutoff"]?.baseValue = 5000f
        filter1.applyMetaBindingsFromPreset(listOf(
            FxMetaBinding("gain", 0f, 2f),
            FxMetaBinding("cutoff", 20f, 20000f, curve = MetaCurve.EXPONENTIAL)
        ))

        ISFAutoBindEngine.saveFilterDefault(filter1)
        assertTrue(ISFAutoBindEngine.hasFilterDefault(filter1))

        // Fresh instance should restore both bindings and parameter baselines
        val filter2 = ISFFilter("unbound_v2", "Unbound", header, shader, contentHash = hash)
        assertEquals(1.8f, filter2.parameters["gain"]?.baseValue)
        assertEquals(5000f, filter2.parameters["cutoff"]?.baseValue)
        assertEquals(2, filter2.metaBindings.size)
        assertEquals("gain", filter2.metaBindings[0].targetParamName)
        assertEquals("cutoff", filter2.metaBindings[1].targetParamName)
        assertEquals(MetaCurve.EXPONENTIAL, filter2.metaBindings[1].curve)

        ISFAutoBindEngine.deleteFilterDefault(filter1)
        assertFalse(ISFAutoBindEngine.hasFilterDefault(filter1))
    }

    @Test
    fun `legacy single-binding json loads successfully`() {
        val hash = "test_legacy_${System.nanoTime()}"
        val header = ISFHeader(INPUTS = listOf(
            ISFInput(NAME = "inputImage", TYPE = "image"),
            ISFInput(NAME = "alpha", TYPE = "float", MIN = JsonPrimitive(0.0f), MAX = JsonPrimitive(1.0f)),
            ISFInput(NAME = "legacyParam", TYPE = "float", MIN = JsonPrimitive(0.0f), MAX = JsonPrimitive(5.0f))
        ))
        val shader = mockk<Shader>(relaxed = true)

        // Write a legacy single FxMetaBindingDto JSON directly to disk
        val legacyJson = """
            {
              "targetParamName": "legacyParam",
              "minVal": 0.0,
              "maxVal": 5.0,
              "curve": "LINEAR",
              "invert": false,
              "linkMode": "FULL",
              "enabled": true
            }
        """.trimIndent()
        val file = java.io.File(ISFAutoBindEngine.overridesDir, "$hash.json")
        file.parentFile?.mkdirs()
        file.writeText(legacyJson)

        val filter = ISFFilter("unbound_legacy", "Unbound", header, shader, contentHash = hash)
        assertEquals("legacyParam", filter.metaBinding.targetParamName)
        assertEquals(5.0f, filter.metaBinding.maxVal)

        ISFAutoBindEngine.deleteOverride(hash)
    }
}
