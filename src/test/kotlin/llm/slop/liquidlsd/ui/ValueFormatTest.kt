package llm.slop.liquidlsd.ui

import kotlin.test.Test
import kotlin.test.assertEquals

class ValueFormatTest {
    @Test
    fun unitRangesScaleToPercent() {
        assertEquals(100f, ValueFormat.scaleFor(0f, 1f))
        assertEquals(100f, ValueFormat.scaleFor(-1f, 1f))
        assertEquals(100f, ValueFormat.scaleFor(0.001f, 0.999f))
        assertEquals(1f, ValueFormat.scaleFor(0f, 0.5f))
        assertEquals(1f, ValueFormat.scaleFor(-100f, 100f))
        assertEquals(1f, ValueFormat.scaleFor(-3.14159f, 3.14159f, isAngle = true))
    }

    @Test
    fun percentShowsDecimalOnlyWhenNeeded() {
        assertEquals("37", ValueFormat.format(0.37f, 100f))
        assertEquals("37.4", ValueFormat.format(0.374f, 100f))
        assertEquals("0", ValueFormat.format(-0.0001f, 100f))
        assertEquals("-50", ValueFormat.format(-0.5f, 100f))
        assertEquals("0.375", ValueFormat.format(0.375f, 1f))
    }

    @Test
    fun knobFaceFormats() {
        assertEquals("37", ValueFormat.knob(0.374f))
        assertEquals("-50", ValueFormat.knob(-0.5f, -1f, 1f))
        assertEquals("3", ValueFormat.knob(3f, 0f, 8f))
        assertEquals("0.25", ValueFormat.knob(0.25f, 0f, 8f))
        assertEquals("1500.00", ValueFormat.knob(1500f, 0f, 2000f))
    }
}
