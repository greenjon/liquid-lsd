package llm.slop.liquidlsd.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CustomIconButtonTest {

    @Test
    fun testWaveShapeEnumContainsSquareDutyVariants() {
        val names = WaveShape.entries.map { it.name }
        assertTrue(names.contains("SQUARE"), "Must contain standard SQUARE")
        assertTrue(names.contains("SQUARE_10"), "Must contain SQUARE_10")
        assertTrue(names.contains("SQUARE_90"), "Must contain SQUARE_90")
    }

    @Test
    fun testWaveShapeEnumAllEntries() {
        assertEquals(
            listOf("SINE", "RAMP_UP", "RAMP_DOWN", "TRIANGLE", "SQUARE", "RANDOM", "SQUARE_10", "SQUARE_90"),
            WaveShape.entries.map { it.name }
        )
    }
}
