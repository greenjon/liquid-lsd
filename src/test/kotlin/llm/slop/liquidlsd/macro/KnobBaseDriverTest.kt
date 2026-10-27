package llm.slop.liquidlsd.macro

import io.mockk.every
import io.mockk.mockk
import llm.slop.liquidlsd.parameters.ModulatableParameter
import llm.slop.liquidlsd.parameters.ParameterResolver
import llm.slop.liquidlsd.rendering.Mixer
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A randomize of a knob-driven parameter moves the knob, so the knob does not overwrite it on the next tick. */
class KnobBaseDriverTest {

    private val scale = ModulatableParameter(0.5f, minClamp = 0f, maxClamp = 2f, randomizeBase = true).apply {
        baseMin = 0.5f
        baseMax = 1.5f
    }
    private val knob = MacroControl(
        label = "SCALE",
        value = 0.25f,
        bindings = mutableListOf(MacroBinding(parameterId = "Deck A/Scale", targetType = MacroTargetType.PARAM_BASE_VALUE, minVal = 0f, maxVal = 2f))
    )
    private val mixer = mockk<Mixer>(relaxed = true).also {
        every { it.getParameterPaths("Mixer") } returns listOf("Deck A/Scale" to scale)
    }

    // MacroEngine is a process-wide singleton; set aside every resident bank for the test (see LevelFaderMirrorTest).
    private val setAside = mutableMapOf<String?, MacroBank>()

    @BeforeTest
    fun setUp() {
        ParameterResolver.clearCache()
        for (id in MacroEngine.CANONICAL_BANK_IDS + null) {
            MacroEngine.getBank(id)?.let { setAside[id] = it; MacroEngine.unregisterBank(id) }
        }
        MacroEngine.registerBank(null, MacroBank(knobs = listOf(knob)))
        MacroEngine.tick(mixer)
    }

    @AfterTest
    fun tearDown() {
        ParameterResolver.clearCache()
        MacroEngine.unregisterBank(null)
        setAside.forEach { (id, bank) -> MacroEngine.registerBank(id, bank) }
    }

    @Test
    fun randomizedValueSurvivesTheNextTick() {
        assertEquals(0.5f, scale.baseValue, 1e-5f)
        scale.randomizeBaseValue(kotlin.random.Random(1))
        val rolled = scale.baseValue
        assertTrue(rolled in 0.5f..1.5f)
        assertEquals(rolled / 2f, knob.value, 1e-5f)
        MacroEngine.tick(mixer)
        assertEquals(rolled, scale.baseValue, 1e-5f)
    }

    @Test
    fun unboundParameterLosesItsDriver() {
        knob.bindings.clear()
        MacroEngine.invalidate()
        MacroEngine.tick(mixer)
        assertNull(scale.baseDriver)
    }
}
