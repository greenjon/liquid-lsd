package llm.slop.liquidlsd.midi

import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import llm.slop.liquidlsd.parameters.CvModulator
import llm.slop.liquidlsd.parameters.ModulatableParameter
import llm.slop.liquidlsd.rendering.Mixer
import llm.slop.liquidlsd.ui.MidiLearnTarget
import llm.slop.liquidlsd.ui.ParametersState
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MidiMappingManagerModulatorTest {

    private fun mockMixerWithParams(vararg params: Pair<String, ModulatableParameter>): Mixer {
        val mixer = mockk<Mixer>(relaxed = true)
        every { mixer.getParameterPaths(any()) } returns params.toList()
        return mixer
    }

    @BeforeTest
    fun setUp() {
        MidiMappingManager.loadProfile("midi_modulator_test")
        MidiMappingManager.clearAllMappings()
    }

    @AfterTest
    fun tearDown() {
        MidiMappingManager.clearAllMappings()
        MidiMappingManager.saveActiveProfile()
        MidiMappingManager.deleteProfile("midi_modulator_test")
        MidiMappingManager.loadProfile("default")
        unmockkAll()
    }

    @Test
    fun testFormatDisplayPath() {
        assertEquals("Mixer/crossfade", MidiMappingManager.formatDisplayPath("Mixer/crossfade"))
        assertEquals("Deck A/zoom [LFO 1 Speed]", MidiMappingManager.formatDisplayPath("Deck A/zoom:mod/0/subdivision"))
        assertEquals("Deck B/zoom [LFO 1 Depth]", MidiMappingManager.formatDisplayPath("Deck B/zoom:mod/0/depth"))
        assertEquals("Deck A/color [LFO 2 Morph]", MidiMappingManager.formatDisplayPath("Deck A/color:mod/1/morph"))
        assertEquals("Deck A/color [Mod 3 Hold]", MidiMappingManager.formatDisplayPath("Deck A/color:mod/2/hold"))
    }

    @Test
    fun testOnMidiEventDispatchesToModulatorPropertyContinuous() {
        val mod = CvModulator("lfo", subdivision = 1.0f)
        val param = ModulatableParameter(0.5f, minClamp = 0f, maxClamp = 10f)
        param.modulators.add(mod)
        val mixer = mockMixerWithParams("Deck A/zoom" to param)

        MidiMappingManager.addMapping(
            parameterPath = "Deck A/zoom:mod/0/subdivision",
            cc = 10,
            channel = 0,
            minVal = 0.5f,
            maxVal = 8.5f,
            messageType = MidiMessageType.CC,
            inputType = MidiInputType.CONTINUOUS_CC
        )

        // CC 10 value 127 = 1.0 normalized -> 8.5
        val eventMax = MidiEvent(channel = 0, type = MidiMessageType.CC, index = 10, rawValue = 127, normalizedValue = 1.0f)
        MidiMappingManager.onMidiEvent(eventMax, mixer)

        assertEquals(8.5f, mod.subdivision, absoluteTolerance = 1e-4f)
        // Base value remains unchanged
        assertEquals(0.5f, param.baseValue, absoluteTolerance = 1e-4f)

        // CC 10 value 0 = 0.0 normalized -> 0.5
        val eventMin = MidiEvent(channel = 0, type = MidiMessageType.CC, index = 10, rawValue = 0, normalizedValue = 0.0f)
        MidiMappingManager.onMidiEvent(eventMin, mixer)

        assertEquals(0.5f, mod.subdivision, absoluteTolerance = 1e-4f)
        assertEquals(0.5f, param.baseValue, absoluteTolerance = 1e-4f)
    }

    @Test
    fun testOnMidiEventDispatchesToModulatorPropertyRotary() {
        val mod = CvModulator("lfo", depth = 0.5f)
        val param = ModulatableParameter(0f, minClamp = 0f, maxClamp = 1f)
        param.modulators.add(mod)
        val mixer = mockMixerWithParams("Deck A/zoom" to param)

        MidiMappingManager.addMapping(
            parameterPath = "Deck A/zoom:mod/0/depth",
            cc = 20,
            channel = 0,
            minVal = 0f,
            maxVal = 1f,
            messageType = MidiMessageType.CC,
            inputType = MidiInputType.ROTARY_BINARY_OFFSET,
            stepSize = 0.05f
        )

        // Step up: rawValue 65 (+1 step = +0.05)
        val eventStepUp = MidiEvent(channel = 0, type = MidiMessageType.CC, index = 20, rawValue = 65, normalizedValue = 65f / 127f)
        MidiMappingManager.onMidiEvent(eventStepUp, mixer)

        assertEquals(0.55f, mod.depth, absoluteTolerance = 1e-4f)

        // Step down 2 steps: rawValue 62 (-2 steps = -0.10)
        val eventStepDown = MidiEvent(channel = 0, type = MidiMessageType.CC, index = 20, rawValue = 62, normalizedValue = 62f / 127f)
        MidiMappingManager.onMidiEvent(eventStepDown, mixer)

        assertEquals(0.45f, mod.depth, absoluteTolerance = 1e-4f)
    }

    @Test
    fun testModulatorPropertySoftTakeover() {
        val mod = CvModulator("lfo", morph = 0.8f)
        val param = ModulatableParameter(0f, minClamp = 0f, maxClamp = 1f)
        param.modulators.add(mod)
        val mixer = mockMixerWithParams("Deck A/zoom" to param)

        val path = "Deck A/zoom:mod/0/morph"
        MidiMappingManager.addMapping(
            parameterPath = path,
            cc = 30,
            channel = 0,
            minVal = 0f,
            maxVal = 1f,
            messageType = MidiMessageType.CC,
            inputType = MidiInputType.CONTINUOUS_CC,
            takeoverMode = TakeoverMode.SOFT_TAKEOVER
        )

        assertFalse(MidiMappingManager.isSoftTakeoverActive(path))

        // Send a distant value (0.2)
        val eventFar = MidiEvent(channel = 0, type = MidiMessageType.CC, index = 30, rawValue = 25, normalizedValue = 0.2f)
        MidiMappingManager.onMidiEvent(eventFar, mixer)

        // Should NOT have changed morph and soft takeover remains awaiting pickup
        assertEquals(0.8f, mod.morph, absoluteTolerance = 1e-4f)
        assertFalse(MidiMappingManager.isSoftTakeoverActive(path))

        // Now move control close to 0.8f (within 4% tolerance = 0.76 .. 0.84)
        val eventNear = MidiEvent(channel = 0, type = MidiMessageType.CC, index = 30, rawValue = 102, normalizedValue = 0.81f)
        MidiMappingManager.onMidiEvent(eventNear, mixer)

        // Takeover should now be active and morph updated
        assertTrue(MidiMappingManager.isSoftTakeoverActive(path))
        assertEquals(0.81f, mod.morph, absoluteTolerance = 1e-4f)
    }

    @Test
    fun testProcessGlobalMidiEventsWithModulatorPropertyLearn() {
        val state = ParametersState()
        val path = "Deck A/zoom:mod/0/slope"
        state.startMidiLearn(
            MidiLearnTarget.ModulatorProperty(
                fullPath = path,
                label = "Deck A/zoom (LFO 1 Asymmetry)",
                min = -1.0f,
                max = 1.0f
            )
        )
        assertTrue(state.isMidiTargetLearning(path))

        val mixer = mockMixerWithParams()
        val event = MidiEvent(channel = 1, type = MidiMessageType.CC, index = 44, rawValue = 64, normalizedValue = 64f / 127f)
        MidiEngine.receivedEvents.offer(event)

        MidiMappingManager.processGlobalMidiEvents(
            midiEnabled = true,
            parametersState = state,
            mixer = mixer,
            onTapTempo = {}
        )

        // MIDI learn target should have been consumed
        assertNull(state.midiLearnTarget)
        assertFalse(state.isMidiTargetLearning(path))

        // Mapping should exist in active profile
        val mapping = MidiMappingManager.getMappingForParameter(path)
        assertNotNull(mapping)
        assertEquals(44, mapping.cc)
        assertEquals(1, mapping.channel)
        assertEquals(-1.0f, mapping.minVal)
        assertEquals(1.0f, mapping.maxVal)
        assertEquals(MidiMessageType.CC, mapping.messageType)
    }
}
