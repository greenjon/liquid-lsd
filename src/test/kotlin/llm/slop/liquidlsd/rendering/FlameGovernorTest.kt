package llm.slop.liquidlsd.rendering

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FlameGovernorTest {
    private fun run(g: FlameGovernor, ms: Float, budget: Float, frames: Int) = repeat(frames) { g.feed(ms, budget) }

    @Test fun overBudgetShedsPointsAndSettlesNearTheBudget() {
        val g = FlameGovernor()
        // a deck whose cost is 8 ms at full points, linear in points, against a 3 ms budget
        repeat(600) { g.feed(8f * g.scale, 3f) }
        assertTrue(g.scale < 0.5f, "scale ${g.scale}")
        assertEquals(3f, 8f * g.scale, 0.8f)
    }

    @Test fun underBudgetKeepsFullQualityAndRecoversAfterALoadSpike() {
        val g = FlameGovernor()
        run(g, 1f, 3f, 200)
        assertEquals(1f, g.scale)
        run(g, 12f, 3f, 200)
        val low = g.scale
        assertTrue(low < 0.8f)
        run(g, 1f, 3f, 600)
        assertTrue(g.scale > low)
    }

    @Test fun zeroBudgetSwitchesItOffAndScaleStaysInRange() {
        val g = FlameGovernor()
        run(g, 50f, 1f, 2000)
        assertEquals(FlameGovernor.MIN_SCALE, g.scale)
        g.feed(50f, 0f)
        assertEquals(1f, g.scale)
    }
}
