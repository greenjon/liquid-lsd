package llm.slop.liquidlsd.parameters

import llm.slop.liquidlsd.cv.CVRegistry
import llm.slop.liquidlsd.cv.MutableCVSource
import kotlin.test.Test
import kotlin.test.assertEquals

class ModulatableParameterStepsTest {

    @Test
    fun snapIsIdentityWhenContinuous() {
        val p = ModulatableParameter(0.3f, minClamp = 0f, maxClamp = 3f)
        assertEquals(0.37f, p.snap(0.37f))
        assertEquals(0.37f, ModulatableParameter(0f, steps = 1).snap(0.37f))
    }

    @Test
    fun snapRoundsToNearestStep() {
        val p = ModulatableParameter(0f, minClamp = 0f, maxClamp = 3f, steps = 4)
        assertEquals(0f, p.snap(0.49f))
        assertEquals(1f, p.snap(0.5f))
        assertEquals(1f, p.snap(1.4f))
        assertEquals(3f, p.snap(2.6f))
        assertEquals(3f, p.snap(99f))
        assertEquals(0f, p.snap(-5f))
    }

    @Test
    fun twoStepsSplitAtHalf() {
        val p = ModulatableParameter(0f, minClamp = 0f, maxClamp = 1f, steps = 2)
        assertEquals(0f, p.snap(0.49f))
        assertEquals(1f, p.snap(0.51f))
    }

    @Test
    fun snapHandlesNegativeAndOffsetRanges() {
        val neg = ModulatableParameter(0f, minClamp = -1f, maxClamp = 1f, steps = 3)
        assertEquals(-1f, neg.snap(-0.6f))
        assertEquals(0f, neg.snap(0.2f))
        assertEquals(1f, neg.snap(0.7f))
        val offset = ModulatableParameter(2f, minClamp = 2f, maxClamp = 24f, steps = 23)
        assertEquals(7f, offset.snap(7.4f))
        assertEquals(8f, offset.snap(7.6f))
    }

    @Test
    fun evaluateSnapsWithoutModulators() {
        val p = ModulatableParameter(1.7f, minClamp = 0f, maxClamp = 3f, steps = 4)
        assertEquals(2f, p.evaluate())
        assertEquals(1.7f, p.baseValue) // base stays continuous
    }

    @Test
    fun evaluateWithModulatorNeverReturnsOffStepValue() {
        if (!CVRegistry.exists("steps_test_cv")) CVRegistry.register(MutableCVSource("steps_test_cv", 0f))
        val p = ModulatableParameter(0f, minClamp = 0f, maxClamp = 3f, steps = 4)
        p.modulators.add(CvModulator(sourceId = "steps_test_cv", depth = 1f))
        var cv = 0f
        while (cv <= 1f) {
            CVRegistry.updatePushedValue("steps_test_cv", cv)
            val v = p.evaluate()
            assertEquals(Math.round(v).toFloat(), v, "cv=$cv gave off-step $v")
            cv += 0.013f
        }
    }

    @Test
    fun cloneCopiesStepsAndLabels() {
        val p = ModulatableParameter(0f, minClamp = 0f, maxClamp = 2f, steps = 3, labels = listOf("a", "b", "c"))
        val c = p.clone()
        assertEquals(3, c.steps)
        assertEquals(listOf("a", "b", "c"), c.labels)
    }
}
