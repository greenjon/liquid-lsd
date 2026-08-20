package llm.slop.liquidlsd.rendering

import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import llm.slop.liquidlsd.parameters.CvModulator
import llm.slop.liquidlsd.parameters.ModulatableParameter
import llm.slop.liquidlsd.parameters.MeterType
import llm.slop.liquidlsd.presets.PlayQueueManager
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CrossfadeTakeoverTest {

    @BeforeTest
    fun setUp() {
        PlayQueueManager.isAutoVJEnabled = false
    }

    @AfterTest
    fun tearDown() {
        unmockkAll()
    }

    private fun createTestMixer(): Pair<Mixer, ModulatableParameter> {
        val crossfade = ModulatableParameter(-1.0f, minClamp = -1.0f, maxClamp = 1.0f, meterType = MeterType.BIPOLAR)
        val mixer = mockk<Mixer>(relaxed = true)

        var isAutoFading = false
        var targetCrossfade = -1.0f

        every { mixer.crossfade } returns crossfade
        every { mixer.isAutoFading } answers { isAutoFading }
        every { mixer.isAutoFading = any() } answers { isAutoFading = firstArg() }
        every { mixer.targetCrossfade } answers { targetCrossfade }
        every { mixer.targetCrossfade = any() } answers { targetCrossfade = firstArg() }

        every { mixer.onCrossfadeManualTakeover() } answers {
            PlayQueueManager.isAutoVJEnabled = false
            mixer.isAutoFading = false
            val hasActiveNonMidiMods = crossfade.modulators.any { !it.sourceId.startsWith("midi_cc_") && !it.bypassed }
            if (hasActiveNonMidiMods) {
                val updated = crossfade.modulators.map { mod ->
                    if (!mod.sourceId.startsWith("midi_cc_")) mod.copy(bypassed = true) else mod
                }
                crossfade.modulators.clear()
                crossfade.modulators.addAll(updated)
            }
        }

        every { mixer.onCrossfadeCvUnmuted() } answers {
            crossfade.baseValue = 0.0f
            if (!crossfade.randomizeBase) {
                crossfade.baseMin = 0.0f
                crossfade.baseMax = 0.0f
            }
            mixer.targetCrossfade = 0.0f
        }

        return Pair(mixer, crossfade)
    }

    @Test
    fun testManualTakeoverDisarmsAutoVjAndHaltsFade() {
        val (mixer, _) = createTestMixer()

        PlayQueueManager.isAutoVJEnabled = true
        mixer.isAutoFading = true
        mixer.targetCrossfade = 1.0f

        mixer.onCrossfadeManualTakeover()

        assertFalse(PlayQueueManager.isAutoVJEnabled, "Auto-VJ should be disarmed on manual takeover")
        assertFalse(mixer.isAutoFading, "Auto-fading should be stopped on manual takeover")
    }

    @Test
    fun testManualTakeoverMutesNonMidiModulatorsAndPreservesMidi() {
        val (mixer, crossfade) = createTestMixer()

        val lfoMod = CvModulator(sourceId = "lfo", depth = 0.8f, bypassed = false)
        val audioMod = CvModulator(sourceId = "audio_bass", depth = 0.5f, bypassed = false)
        val midiMod = CvModulator(sourceId = "midi_cc_0_10", depth = 1.0f, bypassed = false)

        crossfade.modulators.add(lfoMod)
        crossfade.modulators.add(audioMod)
        crossfade.modulators.add(midiMod)

        mixer.onCrossfadeManualTakeover()

        val updatedLfo = crossfade.modulators.first { it.sourceId == "lfo" }
        val updatedAudio = crossfade.modulators.first { it.sourceId == "audio_bass" }
        val updatedMidi = crossfade.modulators.first { it.sourceId == "midi_cc_0_10" }

        assertTrue(updatedLfo.bypassed, "LFO modulator should be muted on manual takeover")
        assertTrue(updatedAudio.bypassed, "Audio modulator should be muted on manual takeover")
        assertFalse(updatedMidi.bypassed, "MIDI CC modulator should remain active on manual takeover")
    }

    @Test
    fun testCrossfadeCvUnmutedResetsBaseValueToZero() {
        val (mixer, crossfade) = createTestMixer()

        // Set to Deck A
        crossfade.set(-1.0f)
        mixer.targetCrossfade = -1.0f
        assertEquals(-1.0f, crossfade.baseValue)

        mixer.onCrossfadeCvUnmuted()

        assertEquals(0.0f, crossfade.baseValue, "Base value should snap to 0.0f on CV unmute")
        assertEquals(0.0f, crossfade.baseMin, "Base min should snap to 0.0f on CV unmute")
        assertEquals(0.0f, crossfade.baseMax, "Base max should snap to 0.0f on CV unmute")
        assertEquals(0.0f, mixer.targetCrossfade, "Target crossfade should snap to 0.0f on CV unmute")
    }
}
