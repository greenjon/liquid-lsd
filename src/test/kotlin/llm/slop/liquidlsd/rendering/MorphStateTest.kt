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

    @Test
    fun testDeckMorphControllerRespectsRandomizeBaseDisabled() {
        val param = ModulatableParameter(baseValue = 0.35f, minClamp = 0f, maxClamp = 1f, randomizeBase = false)
        val controller = DeckMorphController { listOf(param) }
        controller.initFromCurrentState()

        // Explicitly set divergent state snapshots (simulating a previous roll)
        val s0 = controller.state0.parameters[param]!!
        val s1 = controller.state1.parameters[param]!!
        s0.baseValue = 0.1f
        s1.baseValue = 0.9f

        // When randomizeBase is false, update(v) must NOT overwrite param.baseValue
        controller.update(0.0f)
        assertEquals(0.35f, param.baseValue, 0.0001f)
        assertEquals(0.35f, s0.baseValue, 0.0001f)

        controller.update(0.5f)
        assertEquals(0.35f, param.baseValue, 0.0001f)

        controller.update(1.0f)
        assertEquals(0.35f, param.baseValue, 0.0001f)
        assertEquals(0.35f, s1.baseValue, 0.0001f)

        // User edits base value via slider: subsequent updates must preserve it
        param.baseValue = 0.72f
        controller.update(0.3f)
        assertEquals(0.72f, param.baseValue, 0.0001f)
        assertEquals(0.72f, controller.state0.parameters[param]!!.baseValue, 0.0001f)
        assertEquals(0.72f, controller.state1.parameters[param]!!.baseValue, 0.0001f)
    }

    @Test
    fun testDeckMorphControllerDisablingRandomizeMidMorph() {
        val param = ModulatableParameter(baseValue = 0.5f, minClamp = 0f, maxClamp = 1f, randomizeBase = true)
        val controller = DeckMorphController { listOf(param) }
        controller.initFromCurrentState()

        val s0 = controller.state0.parameters[param]!!
        val s1 = controller.state1.parameters[param]!!
        s0.baseValue = 0.2f
        s1.baseValue = 0.8f

        // Actively morphing while randomizeBase is true
        controller.update(0.5f)
        assertEquals(0.5f, param.baseValue, 0.0001f)

        // User toggles off randomization mid-morph in UI
        param.randomizeBase = false

        // Now updates to v must freeze the parameter at current value rather than morphing to 0.2 or 0.8
        controller.update(0.0f)
        assertEquals(0.5f, param.baseValue, 0.0001f)

        controller.update(1.0f)
        assertEquals(0.5f, param.baseValue, 0.0001f)

        // User adjusts slider to 0.42f
        param.baseValue = 0.42f
        controller.update(0.25f)
        assertEquals(0.42f, param.baseValue, 0.0001f)
    }

    @Test
    fun testDeckMorphControllerRespectsModulatorRandomizeFlags() {
        val param = ModulatableParameter(baseValue = 0.5f, minClamp = 0f, maxClamp = 1f, randomizeBase = true)
        val mod = CvModulator(
            sourceId = "lfo",
            depth = 0.4f,
            subdivision = 2.0f,
            slope = 0.5f,
            randomizeDepth = true,
            randomizeSubdivision = false,
            randomizeSlope = false
        )
        param.modulators.add(mod)

        val controller = DeckMorphController { listOf(param) }
        controller.initFromCurrentState()

        val s0 = controller.state0.parameters[param]!!.modulators[0]
        val s1 = controller.state1.parameters[param]!!.modulators[0]
        s0.depth = 0.1f
        s0.subdivision = 8.0f // Stale/divergent value
        s0.slope = 0.1f
        s1.depth = 0.9f
        s1.subdivision = 16.0f // Stale/divergent value
        s1.slope = 0.9f

        controller.update(0.5f)

        // depth is randomized -> interpolated
        assertEquals(0.5f, mod.depth, 0.0001f)

        // subdivision and slope are NOT randomized -> preserved and snapshots synced
        assertEquals(2.0f, mod.subdivision, 0.0001f)
        assertEquals(0.5f, mod.slope, 0.0001f)
        assertEquals(2.0f, s0.subdivision, 0.0001f)
        assertEquals(2.0f, s1.subdivision, 0.0001f)
        assertEquals(0.5f, s0.slope, 0.0001f)
        assertEquals(0.5f, s1.slope, 0.0001f)
    }
}
