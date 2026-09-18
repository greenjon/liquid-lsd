package llm.slop.liquidlsd.osc

import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import llm.slop.liquidlsd.macro.MacroBank
import llm.slop.liquidlsd.macro.MacroEngine
import llm.slop.liquidlsd.parameters.ModulatableParameter
import llm.slop.liquidlsd.rendering.Mixer
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class OscMappingManagerTest {

    private fun mockMixerWithParams(vararg params: Pair<String, ModulatableParameter>): Mixer {
        val mixer = mockk<Mixer>(relaxed = true)
        every { mixer.getParameterPaths(any()) } returns params.toList()
        return mixer
    }

    @BeforeTest
    fun setUp() {
        OscMappingManager.loadProfile("osc_mapping_manager_test")
        OscMappingManager.clearAllMappings()
        MacroEngine.registerBank(MacroEngine.DECK_A, MacroBank())
        OscLearnState.cancelLearn()
    }

    @AfterTest
    fun tearDown() {
        OscMappingManager.clearAllMappings()
        OscMappingManager.saveActiveProfile()
        OscMappingManager.deleteProfile("osc_mapping_manager_test")
        OscMappingManager.loadProfile("default")
        MacroEngine.registerBank(MacroEngine.DECK_A, MacroBank())
        OscLearnState.cancelLearn()
        unmockkAll()
    }

    // --- Profile & path security ---

    @Test
    fun testSanitiseOscProfileNameRejectsPathTraversal() {
        assertFailsWith<IllegalArgumentException> { sanitiseOscProfileName("../external/profile") }
        assertFailsWith<IllegalArgumentException> { sanitiseOscProfileName(" ../ ") }
        assertFailsWith<IllegalArgumentException> { sanitiseOscProfileName("Live Set 01") }
        assertEquals("Live_Set_01", sanitiseOscProfileName("Live_Set_01"))
    }

    @Test
    fun testOscProfileFileStaysUnderOscDirectory() {
        val oscDir = createTempDirectory().toFile()
        val threw = runCatching { oscProfileFile(oscDir, "../outside") }.isFailure
        assertTrue(threw)
    }

    @Test
    fun testAddUpdateRemoveMapping() {
        assertFalse(OscMappingManager.hasMapping("/1/fader1"))

        OscMappingManager.addMapping("/1/fader1", OscControlMapping(parameterPath = "Deck A/zoom", minVal = 0f, maxVal = 2f))
        assertTrue(OscMappingManager.hasMapping("/1/fader1"))
        assertEquals("Deck A/zoom", OscMappingManager.getMappingForAddress("/1/fader1")?.parameterPath)

        OscMappingManager.updateMapping("/1/fader1", OscControlMapping(parameterPath = "Deck A/zoom", minVal = 0f, maxVal = 4f))
        assertEquals(4f, OscMappingManager.getMappingForAddress("/1/fader1")?.maxVal)

        OscMappingManager.removeMapping("/1/fader1")
        assertFalse(OscMappingManager.hasMapping("/1/fader1"))
    }

    // --- Parameter dispatch shaping ---

    @Test
    fun testOnOscMessageDispatchesToMappedParameterWithMinMaxScaling() {
        val param = ModulatableParameter(0f, minClamp = 0f, maxClamp = 10f)
        val mixer = mockMixerWithParams("Test/param" to param)

        OscMappingManager.addMapping("/1/fader1", OscControlMapping(parameterPath = "Test/param", minVal = 0f, maxVal = 10f))
        OscMappingManager.onOscMessage(OscMessage("/1/fader1", listOf(0.5f)), mixer)

        assertEquals(5.0f, param.baseValue, absoluteTolerance = 1e-4f)
    }

    @Test
    fun testOnOscMessageAppliesInversion() {
        val param = ModulatableParameter(0f, minClamp = 0f, maxClamp = 1f)
        val mixer = mockMixerWithParams("Test/param" to param)

        OscMappingManager.addMapping("/1/fader1", OscControlMapping(parameterPath = "Test/param", minVal = 0f, maxVal = 1f, inverted = true))
        OscMappingManager.onOscMessage(OscMessage("/1/fader1", listOf(0.25f)), mixer)

        assertEquals(0.75f, param.baseValue, absoluteTolerance = 1e-4f)
    }

    @Test
    fun testUnmappedAddressIsIgnored() {
        val param = ModulatableParameter(0.3f, minClamp = 0f, maxClamp = 1f)
        val mixer = mockMixerWithParams("Test/param" to param)

        OscMappingManager.onOscMessage(OscMessage("/1/unmapped", listOf(0.9f)), mixer)

        assertEquals(0.3f, param.baseValue, absoluteTolerance = 1e-4f)
    }

    @Test
    fun testXyPadUnpacksMultiArgMessageIntoIndividualParameters() {
        val paramX = ModulatableParameter(0f, minClamp = 0f, maxClamp = 1f)
        val paramY = ModulatableParameter(0f, minClamp = 0f, maxClamp = 1f)
        val mixer = mockMixerWithParams("Test/x" to paramX, "Test/y" to paramY)

        OscMappingManager.addMapping("/2/xy/0", OscControlMapping(parameterPath = "Test/x", minVal = 0f, maxVal = 1f))
        OscMappingManager.addMapping("/2/xy/1", OscControlMapping(parameterPath = "Test/y", minVal = 0f, maxVal = 1f))

        OscMappingManager.onOscMessage(OscMessage("/2/xy", listOf(0.2f, 0.8f)), mixer)

        assertEquals(0.2f, paramX.baseValue, absoluteTolerance = 1e-4f)
        assertEquals(0.8f, paramY.baseValue, absoluteTolerance = 1e-4f)
    }

    @Test
    fun testSoftTakeoverWithholdsUpdateUntilPhysicalValueCrosses() {
        val param = ModulatableParameter(0.5f, minClamp = 0f, maxClamp = 1f)
        val mixer = mockMixerWithParams("Test/param" to param)

        OscMappingManager.addMapping(
            "/1/fader1",
            OscControlMapping(parameterPath = "Test/param", minVal = 0f, maxVal = 1f, takeoverMode = OscTakeoverMode.SOFT_TAKEOVER)
        )

        // Physical control is far from the current parameter value (0.5) -> withheld.
        OscMappingManager.onOscMessage(OscMessage("/1/fader1", listOf(0.05f)), mixer)
        assertEquals(0.5f, param.baseValue, absoluteTolerance = 1e-4f)

        // Physical control moves close enough to the current value -> takeover engages.
        OscMappingManager.onOscMessage(OscMessage("/1/fader1", listOf(0.49f)), mixer)
        assertEquals(0.49f, param.baseValue, absoluteTolerance = 1e-2f)
    }

    @Test
    fun testSlewSmoothingConvergesGraduallyOnUpdate() {
        val param = ModulatableParameter(0f, minClamp = 0f, maxClamp = 1f)
        val mixer = mockMixerWithParams("Test/param" to param)

        OscMappingManager.addMapping("/1/fader1", OscControlMapping(parameterPath = "Test/param", minVal = 0f, maxVal = 1f, slewMs = 100f))
        OscMappingManager.onOscMessage(OscMessage("/1/fader1", listOf(1.0f)), mixer)

        // With slew active, the raw message should not jump the parameter immediately.
        assertEquals(0f, param.baseValue, absoluteTolerance = 1e-4f)

        Thread.sleep(20)
        OscMappingManager.update(mixer)

        assertTrue(param.baseValue > 0f && param.baseValue < 1f)
    }

    // --- Macro bridge forwarding ---

    @Test
    fun testMacroKnobAddressForwardsToMacroOscBridge() {
        val mixer = mockMixerWithParams()
        val bank = MacroEngine.getBank(MacroEngine.DECK_A)!!

        OscMappingManager.onOscMessage(OscMessage("/macro/deckA/knob/1", listOf(0.33f)), mixer)

        assertEquals(0.33f, bank.knobs[0].value, absoluteTolerance = 1e-4f)
    }

    @Test
    fun testMacroSwitchAddressForwardsToMacroOscBridge() {
        val mixer = mockMixerWithParams()
        val bank = MacroEngine.getBank(MacroEngine.DECK_A)!!

        OscMappingManager.onOscMessage(OscMessage("/macro/deckA/switch/1", listOf(1.0f)), mixer)

        assertTrue(bank.switches[0].value > 0.5f)
    }

    // --- OSC Learn integration ---

    @Test
    fun testLearnModeCapturesIncomingAddressInsteadOfDispatching() {
        val param = ModulatableParameter(0f, minClamp = 0f, maxClamp = 1f)
        val mixer = mockMixerWithParams("Test/param" to param)

        OscLearnState.startLearn("Test/param", minVal = 0f, maxVal = 1f)
        assertTrue(OscLearnState.isLearning())

        OscMappingManager.onOscMessage(OscMessage("/1/rotary1", listOf(0.6f)), mixer)

        assertFalse(OscLearnState.isLearning())
        val mapping = OscMappingManager.getMappingForAddress("/1/rotary1")
        assertNotNull(mapping)
        assertEquals("Test/param", mapping.parameterPath)
        // Learn only creates the binding; it does not also apply this first message's value.
        assertEquals(0f, param.baseValue, absoluteTolerance = 1e-4f)
    }
}
