package llm.slop.liquidlsd.rendering

import llm.slop.liquidlsd.parameters.MeterType
import kotlin.test.Test
import kotlin.test.assertEquals

class ParamMetaTest {
    private fun meta(min: Float, type: String? = null) = ParamMeta("p", 0f, min, 1f, type)

    @Test fun missingTypeFollowsTheRange() {
        assertEquals(MeterType.BIPOLAR, meta(-1f).meterType())
        assertEquals(MeterType.MONOPOLAR, meta(0f).meterType())
    }

    @Test fun explicitTypeWinsAndUnknownTypeFallsBackToTheRange() {
        assertEquals(MeterType.ENDLESS, meta(-1f, "ENDLESS").meterType())
        assertEquals(MeterType.MONOPOLAR, meta(-1f, "MONOPOLAR").meterType())
        assertEquals(MeterType.BIPOLAR, meta(-1f, "nonsense").meterType())
    }
}
