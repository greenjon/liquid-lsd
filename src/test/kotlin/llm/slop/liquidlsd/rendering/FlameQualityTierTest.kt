package llm.slop.liquidlsd.rendering

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FlameQualityTierTest {
    @Test fun mediumIsTheReferenceAndKeepsBrightness() {
        assertEquals(1f, FlameQualityTier.MEDIUM.passExposure)
        assertEquals(FlameQualityTier.REFERENCE_PASSES, FlameQualityTier.MEDIUM.passes)
    }

    @Test fun fewerPassesBrightenAndMorePassesDim() {
        assertEquals(2f, FlameQualityTier.LOW.passExposure)
        assertEquals(0.5f, FlameQualityTier.HIGH.passExposure)
    }

    @Test fun tiersAreOrderedByCost() {
        val t = FlameQualityTier.entries
        for (i in 1 until t.size) assertTrue(t[i].points * t[i].passes >= t[i - 1].points * t[i - 1].passes)
        assertTrue(t.all { it.points in 0.05f..1f && it.passes in 1..16 })
    }

    @Test fun softnessIsALookParameterWithTodaysSizeAsDefault() {
        val p = FlameGenome.buildParameters().getValue("Softness")
        assertEquals(3f, FlameUnits.real("Softness", p.baseValue), 1e-5f)
        assertEquals(1f, FlameUnits.of("Softness")!!.realMin)
        assertEquals(10f, FlameUnits.of("Softness")!!.realMax)
    }

    @Test fun qualityIsNotAGenomeParameter() {
        val names = FlameGenome.buildParameters().keys
        assertFalse(listOf("Points", "Passes", "Kernel").any { it in names })
    }
}
