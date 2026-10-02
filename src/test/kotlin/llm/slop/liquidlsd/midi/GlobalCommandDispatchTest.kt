package llm.slop.liquidlsd.midi

import io.mockk.mockk
import llm.slop.liquidlsd.rendering.Mixer
import llm.slop.liquidlsd.ui.ParametersState
import kotlin.test.Test
import kotlin.test.assertEquals

/** Global-action mappings dispatched through the command registry from the MIDI event queue. */
class GlobalCommandDispatchTest {
    private fun cc(index: Int, value: Int, device: String = "test-device") = MidiEvent(
        channel = 0, type = MidiMessageType.CC, index = index,
        rawValue = value, normalizedValue = value / 127f, deviceId = device
    )

    private fun dispatch(vararg events: MidiEvent, onTap: () -> Unit = {}): MidiMappingManager.GlobalMidiDeltas {
        events.forEach { MidiEngine.receivedEvents.offer(it) }
        return MidiMappingManager.processGlobalMidiEvents(
            midiEnabled = true,
            parametersState = ParametersState(),
            mixer = mockk<Mixer>(relaxed = true),
            onTapTempo = onTap
        )
    }

    private inline fun withProfile(name: String, body: () -> Unit) {
        MidiMappingManager.loadProfile(name)
        try {
            body()
        } finally {
            MidiMappingManager.clearAllMappings()
            MidiMappingManager.deleteProfile(name)
            MidiMappingManager.loadProfile("default")
        }
    }

    @Test
    fun queueNextAndPrevProduceNetDeltaOncePerPress() = withProfile("global_queue_test") {
        MidiMappingManager.addMapping("Global/queueNext", cc = 60, channel = 0)
        MidiMappingManager.addMapping("Global/queuePrev", cc = 61, channel = 0)

        // Held high across repeated messages counts once.
        assertEquals(1, dispatch(cc(60, 127), cc(60, 127)).queueDelta)
        // Release, press again: counts again.
        assertEquals(1, dispatch(cc(60, 0), cc(60, 127)).queueDelta)
        // Next and prev in one frame net out.
        assertEquals(0, dispatch(cc(60, 0), cc(60, 127), cc(61, 127)).queueDelta)
        dispatch(cc(60, 0), cc(61, 0))
    }

    @Test
    fun backgroundAndTransitionQueuesHaveSeparateDeltas() = withProfile("global_bgqueue_test") {
        MidiMappingManager.addMapping("Global/bgQueuePrev", cc = 62, channel = 0)
        MidiMappingManager.addMapping("Global/transQueueNext", cc = 63, channel = 0)

        val deltas = dispatch(cc(62, 127), cc(63, 127))
        assertEquals(0, deltas.queueDelta)
        assertEquals(-1, deltas.bgQueueDelta)
        assertEquals(1, deltas.transQueueDelta)
        dispatch(cc(62, 0), cc(63, 0))
    }

    @Test
    fun tapTempoCallsBackOncePerPress() = withProfile("global_tap_test") {
        MidiMappingManager.addMapping("Global/tapTempo", cc = 64, channel = 0)
        var taps = 0

        dispatch(cc(64, 127), cc(64, 127), onTap = { taps++ })
        dispatch(cc(64, 0), cc(64, 127), onTap = { taps++ })
        dispatch(cc(64, 0))
        assertEquals(2, taps)
    }

    @Test
    fun unmappedOrWrongChannelEventsDoNothing() = withProfile("global_none_test") {
        MidiMappingManager.addMapping("Global/queueNext", cc = 65, channel = 2)
        val deltas = dispatch(cc(65, 127), cc(66, 127))
        assertEquals(0, deltas.queueDelta)
    }

    @Test
    fun eventsCarryTheirSourceDevice() {
        assertEquals("test-device", cc(1, 1).deviceId)
        assertEquals("", MidiEvent(0, MidiMessageType.CC, 1, 1, 1f / 127f).deviceId)
    }
}
