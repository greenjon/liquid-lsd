package llm.slop.liquidlsd.rendering

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FlameUnitsTest {
    @Test fun everyContinuousParameterIsNormalised() {
        for ((name, p) in FlameGenome.buildParameters()) {
            val ok = p.steps != null || p.isAngle || (p.minClamp == 0f && p.maxClamp == 1f) || (p.minClamp == -1f && p.maxClamp == 1f)
            assertTrue(ok, "$name spans ${p.minClamp}..${p.maxClamp}: store 0..1, -1..1 or an angle (add a FlameUnits map)")
        }
    }

    @Test fun mapsRoundTripAndHitTheirEnds() {
        for (name in listOf("Zoom", "Magnify", "Exposure", "Breathe", "Warp", "Drift", "Gamma", "CenterX", "X2 Weight", "X1 PreScaleX", "XF PostY", "X3 V2 Weight")) {
            val m = FlameUnits.of(name)!!
            assertEquals(m.realMin, m.real(m.storedMin), 1e-4f * maxOf(1f, m.realMax), name)
            assertEquals(m.realMax, m.real(m.storedMax), 1e-4f * maxOf(1f, m.realMax), name)
            for (r in listOf(m.realMin, (m.realMin + m.realMax) / 2f, m.realMax)) assertEquals(r, m.real(m.stored(r)), 1e-3f * maxOf(1f, m.realMax), name)
        }
    }

    @Test fun neutralValuesSitAtTheMiddle() {
        for (name in listOf("Zoom", "Magnify", "Exposure", "Breathe", "Warp")) assertEquals(0.5f, FlameUnits.stored(name, 1f), 1e-5f, name)
        assertEquals(0f, FlameUnits.stored("CenterX", 0f))
        assertEquals(0.4f, FlameUnits.stored("Gamma", 2.2f), 1e-5f)
        assertEquals(1f, FlameUnits.real("Unmapped Name", 1f))
    }
}
