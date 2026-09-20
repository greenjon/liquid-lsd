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
}
