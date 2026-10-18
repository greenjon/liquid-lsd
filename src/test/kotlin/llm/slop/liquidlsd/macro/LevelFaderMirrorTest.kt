package llm.slop.liquidlsd.macro

import io.mockk.every
import io.mockk.mockk
import llm.slop.liquidlsd.parameters.ModulatableParameter
import llm.slop.liquidlsd.parameters.ParameterResolver
import llm.slop.liquidlsd.parameters.SoftTakeover
import llm.slop.liquidlsd.rendering.Mixer
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LevelFaderMirrorTest {

    private val level = ModulatableParameter(1.0f, minClamp = 0f, maxClamp = 1f)
    private val knob = MacroControl(
        label = "LEVEL A",
        value = 1.0f,
        bindings = mutableListOf(MacroBinding(parameterId = "Mixer/levelA", targetType = MacroTargetType.PARAM_BASE_VALUE))
    )
    private val mixer = mockk<Mixer>(relaxed = true).also {
        every { it.getParameterPaths("Mixer") } returns listOf("Mixer/levelA" to level)
    }

    @BeforeTest
    fun setUp() {
        ParameterResolver.clearCache()
        MacroEngine.registerBank(null, MacroBank(knobs = listOf(knob)))
        MacroEngine.tick(mixer)
    }

    @AfterTest
    fun tearDown() {
        ParameterResolver.clearCache()
        MacroEngine.registerBank(null, MacroBank())
    }

    @Test
    fun knobDrivesFaderWhenKnobMoves() {
        knob.value = 0.4f
        MacroEngine.tick(mixer)
        assertEquals(0.4f, level.baseValue, 1e-5f)
    }

    @Test
    fun faderMovedBySliderIsNotOverwrittenAndKnobFollows() {
        level.baseValue = 0.25f // deck-tile slider
        MacroEngine.tick(mixer)
        assertEquals(0.25f, level.baseValue, 1e-5f)
        assertEquals(0.25f, knob.value, 1e-5f)
        // stays put on later ticks, and the knob can take over again
        MacroEngine.tick(mixer)
        assertEquals(0.25f, level.baseValue, 1e-5f)
        knob.value = 0.9f
        MacroEngine.tick(mixer)
        assertEquals(0.9f, level.baseValue, 1e-5f)
    }

    @Test
    fun softTakeoverCatchesOnCrossOrTolerance() {
        assertFalse(SoftTakeover.caught(0.1f, 0.2f, 0.8f, 0.04f))
        assertTrue(SoftTakeover.caught(0.7f, 0.9f, 0.8f, 0.04f)) // crossed
        assertTrue(SoftTakeover.caught(0.1f, 0.78f, 0.8f, 0.04f)) // close enough
        assertFalse(SoftTakeover.caught(null, 0.2f, 0.8f, 0.04f))
    }
}
