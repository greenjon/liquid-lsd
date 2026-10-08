package llm.slop.liquidlsd.ui

import llm.slop.liquidlsd.parameters.MeterType
import llm.slop.liquidlsd.parameters.ModulatableParameter
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DiscreteTicksTest {

    @Test
    fun tickAnglesSpanTheSweepEvenly() {
        assertContentEquals(floatArrayOf(0f, 1f, 2f), DiscreteTicks.angles(3, 0f, 2f))
        assertContentEquals(floatArrayOf(0f, 4f), DiscreteTicks.angles(2, 0f, 4f))
    }

    @Test
    fun noTicksWhenContinuousTrivialOrCrowded() {
        assertEquals(0, DiscreteTicks.angles(null, 0f, 1f).size)
        assertEquals(0, DiscreteTicks.angles(1, 0f, 1f).size)
        assertEquals(DiscreteTicks.MAX_TICKS, DiscreteTicks.angles(DiscreteTicks.MAX_TICKS, 0f, 1f).size)
        assertEquals(0, DiscreteTicks.angles(DiscreteTicks.MAX_TICKS + 1, 0f, 1f).size)
    }

    @Test
    fun readoutUsesLabelOrSnappedInteger() {
        val labeled = ModulatableParameter(0f, minClamp = 0f, maxClamp = 2f, steps = 3, labels = listOf("Off", "Soft", "Hard"))
        assertEquals("Soft", DiscreteTicks.readout(labeled, 0.9f))
        assertEquals("Hard", DiscreteTicks.readout(labeled, 5f))
        val plain = ModulatableParameter(2f, minClamp = 2f, maxClamp = 24f, steps = 23)
        assertEquals("7", DiscreteTicks.readout(plain, 7.2f))
        assertNull(DiscreteTicks.readout(ModulatableParameter(0f), 0.5f))
    }

    @Test
    fun defaultMeterIsDiscreteOnlyWithSteps() {
        assertEquals(MeterType.DISCRETE, ModulatableParameter.defaultMeter(0f, 4))
        assertEquals(MeterType.MONOPOLAR, ModulatableParameter.defaultMeter(0f, null))
        assertEquals(MeterType.BIPOLAR, ModulatableParameter.defaultMeter(-1f, null))
    }

    @Test
    fun discreteKnobUsesBoundedSweepNotWrapAround() {
        val discrete = MacroKnobWidget.valueToAngleRadians(0f, MeterType.DISCRETE)
        assertEquals(MacroKnobWidget.valueToAngleRadians(0f, MeterType.MONOPOLAR), discrete)
        assertTrue(discrete != MacroKnobWidget.valueToAngleRadians(0f, MeterType.ENDLESS))
    }

    @Test
    fun choicesUseLabelsElseStepNumbers() {
        val labeled = ModulatableParameter(0f, minClamp = 0f, maxClamp = 2f, steps = 3, labels = listOf("a", "b", "c"))
        assertEquals(listOf("a", "b", "c"), DiscreteTicks.choices(labeled))
        val offset = ModulatableParameter(2f, minClamp = 2f, maxClamp = 5f, steps = 4)
        assertEquals(listOf("2", "3", "4", "5"), DiscreteTicks.choices(offset))
        val negative = ModulatableParameter(0f, minClamp = -1f, maxClamp = 1f, steps = 3)
        assertEquals(listOf("-1", "0", "1"), DiscreteTicks.choices(negative))
    }

    @Test
    fun noChoicesWhenContinuousOrTooMany() {
        assertNull(DiscreteTicks.choices(ModulatableParameter(0f)))
        assertNull(DiscreteTicks.choices(ModulatableParameter(0f, minClamp = 0f, maxClamp = 30f, steps = DiscreteTicks.MAX_TICKS + 1)))
    }

    @Test
    fun stepValueInvertsStepIndex() {
        val p = ModulatableParameter(0f, minClamp = 2f, maxClamp = 24f, steps = 23)
        for (i in 0 until 23) assertEquals(i, DiscreteTicks.stepIndex(p, DiscreteTicks.stepValue(p, i)))
        assertEquals(24f, DiscreteTicks.stepValue(p, 99))
        assertEquals(2f, DiscreteTicks.stepValue(p, -3))
    }
}
