package llm.slop.liquidlsd.rendering

import llm.slop.liquidlsd.parameters.CvModulator
import llm.slop.liquidlsd.parameters.MeterType
import llm.slop.liquidlsd.parameters.ModulatableParameter
import kotlin.math.PI
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MorphStateTest {

    @Test
    fun testShortestPathLerpAngle() {
        val pi = PI.toFloat()
        // Crossing the -PI / PI wrap
        val a = pi - 0.1f
        val b = -pi + 0.1f
        // The midpoint should be near PI / -PI
        val mid = MorphMath.shortestPathLerpAngle(a, b, 0.5f)
        assertTrue(abs(abs(mid) - pi) < 0.01f, "Expected midpoint near PI boundary but got $mid")

        // Standard lerp within [-PI, PI]
        val midStd = MorphMath.shortestPathLerpAngle(0f, 1f, 0.5f)
        assertEquals(0.5f, midStd, 0.0001f)
    }

    @Test
    fun testShortestPathLerpHue() {
        // Crossing 0 / 1 wrap
        val a = 0.9f
        val b = 0.1f
        val mid = MorphMath.shortestPathLerpHue(a, b, 0.5f)
        assertEquals(0.0f, mid, 0.0001f)

        // Standard hue lerp
        val midStd = MorphMath.shortestPathLerpHue(0.2f, 0.6f, 0.5f)
        assertEquals(0.4f, midStd, 0.0001f)
    }

    @Test
    fun testDeckMorphControllerInterpolation() {
        val param = ModulatableParameter(baseValue = 0.2f, minClamp = 0f, maxClamp = 1f, randomizeBase = true).apply {
            baseMin = 0.0f
            baseMax = 1.0f
        }
        val mod = CvModulator(
            sourceId = "lfo",
            depth = 0.3f,
            subdivision = 1.0f,
            randomizeDepth = true,
            depthMin = 0.0f,
            depthMax = 1.0f
        )
        param.modulators.add(mod)

        val controller = DeckMorphController { listOf(param) }
        controller.initFromCurrentState()

        // Set explicit state values for verification
        val s0 = controller.state0.parameters[param]!!
        val s1 = controller.state1.parameters[param]!!
        s0.baseValue = 0.1f
        s0.modulators[0].depth = 0.2f
        s1.baseValue = 0.9f
        s1.modulators[0].depth = 0.8f

        // Evaluate at V = 0.0
        controller.update(0.0f)
        assertEquals(0.1f, param.baseValue, 0.0001f)
        assertEquals(0.2f, param.modulators[0].depth, 0.0001f)

        // Evaluate at V = 0.5
        controller.update(0.5f)
        assertEquals(0.5f, param.baseValue, 0.0001f)
        assertEquals(0.5f, param.modulators[0].depth, 0.0001f)

        // Evaluate at V = 1.0
        controller.update(1.0f)
        assertEquals(0.9f, param.baseValue, 0.0001f)
        assertEquals(0.8f, param.modulators[0].depth, 0.0001f)
    }

    @Test
    fun testFlipFlopBoundaryLatchingAndHysteresis() {
        val param = ModulatableParameter(baseValue = 0.5f, minClamp = 0f, maxClamp = 1f, randomizeBase = true).apply {
            baseMin = 0.0f
            baseMax = 1.0f
        }
        val controller = DeckMorphController { listOf(param) }
        controller.initFromCurrentState()

        assertEquals(BoundaryTarget.READY_FOR_ONE, controller.latchTarget)

        // Ascend to 0.99
        controller.update(0.995f)
        assertEquals(BoundaryTarget.READY_FOR_ZERO, controller.latchTarget)

        val s0ValAfterOne = controller.state0.parameters[param]!!.baseValue

        // Jitter near 1.0 should not re-roll state0 again
        controller.update(1.0f)
        controller.update(0.995f)
        assertEquals(BoundaryTarget.READY_FOR_ZERO, controller.latchTarget)
        assertEquals(s0ValAfterOne, controller.state0.parameters[param]!!.baseValue)

        // Descend to 0.01
        controller.update(0.005f)
        assertEquals(BoundaryTarget.READY_FOR_ONE, controller.latchTarget)

        val s1ValAfterZero = controller.state1.parameters[param]!!.baseValue

        // Jitter near 0.0 should not re-roll state1 again
        controller.update(0.0f)
        controller.update(0.005f)
        assertEquals(BoundaryTarget.READY_FOR_ONE, controller.latchTarget)
        assertEquals(s1ValAfterZero, controller.state1.parameters[param]!!.baseValue)
    }
}
