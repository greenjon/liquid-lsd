package llm.slop.liquidlsd.cv

import llm.slop.liquidlsd.parameters.GenUnit
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class ClockAnchorsTest {

    @Test
    fun `a period change an hour in keeps the position and changes only the rate`() {
        val anchors = ClockAnchors()
        val before = anchors.cycles("m", GenUnit.TIME, 3600.0, 0.0, 2.0)
        assertEquals(1800.0, before, 1e-9)
        // clock / period alone would jump from 1800 to 1714.3 cycles here.
        assertEquals(before, anchors.cycles("m", GenUnit.TIME, 3600.0, 0.0, 2.1), 1e-9)
        assertEquals(before + 1.0 / 2.1, anchors.cycles("m", GenUnit.TIME, 3601.0, 0.0, 2.1), 1e-9)
    }

    @Test
    fun `look-ahead offsets do not move the anchor`() {
        val anchors = ClockAnchors()
        anchors.cycles("m", GenUnit.TIME, 100.0, 0.0, 1.0)
        assertEquals(102.0, anchors.cycles("m", GenUnit.TIME, 100.0, 2.0, 1.0), 1e-9)
        assertEquals(100.0, anchors.cycles("m", GenUnit.TIME, 100.0, 0.0, 1.0), 1e-9)
    }

    @Test
    fun `lfo 2 and a unit change keep their own anchors`() {
        val anchors = ClockAnchors()
        anchors.cycles("m", GenUnit.TIME, 50.0, 0.0, 1.0)
        anchors.cycles("m", GenUnit.TIME, 50.0, 0.0, 2.0)
        assertEquals(25.0, anchors.lfo2Cycles("m", GenUnit.TIME, 50.0, 0.0, 2.0), 1e-9)
        // Switching to frames starts that clock fresh rather than carrying a seconds-based shift.
        assertEquals(30.0, anchors.cycles("m", GenUnit.FRAME, 300.0, 0.0, 10.0), 1e-9)
    }
}
