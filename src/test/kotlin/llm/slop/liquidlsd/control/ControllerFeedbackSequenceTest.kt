package llm.slop.liquidlsd.control

import io.mockk.mockk
import llm.slop.liquidlsd.midi.MidiEvent
import llm.slop.liquidlsd.midi.MidiMessageType
import llm.slop.liquidlsd.rendering.Mixer
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertTrue

/** Replays a real session shape through the manager, runtime and feedback together. */
class ControllerFeedbackSequenceTest {
    private class FakeSink : MidiSink {
        val sent = ArrayList<Triple<Int, Int, Int>>()
        override fun sendCc(channel: Int, cc: Int, value: Int) { sent += Triple(channel, cc, value) }
        fun drain() = sent.toList().also { sent.clear() }
    }

    /** One value per knob, shown as ring value; moves when turned. */
    private class Pad : KnobSurface, KnobLightSource {
        val values = FloatArray(16) { 0.5f }
        override fun turn(knob: Int, delta: Float) { values[knob] = (values[knob] + delta).coerceIn(0f, 1f) }
        override fun primary(knob: Int) {}
        override fun secondary(knob: Int) {}
        override fun showPage(pageId: String) {}
        override fun knobLights() = values.map { KnobLight(it, 0f, 0f, 1f) }
    }

    private val name = "Midi Fighter Twister [hw:1,0,0]"
    private val sink = FakeSink()
    private val pad = Pad()
    private val registry = CommandRegistry().also { GlobalCommands.registerAll(it); KnobCommands().register(it) }
    private val manager = ControllerManager(
        registry, ControllerProfileStore(createTempDirectory("controllers").toFile()),
        connectedDevices = { listOf(name) }, openSink = { _, _ -> sink }
    )
    private val ctx = CommandContext(mockk<Mixer>(relaxed = true), knobSurface = pad)
    private var clock = 0L

    private fun send(channel: Int, cc: Int, value: Int) = manager.handle(
        MidiEvent(channel, MidiMessageType.CC, cc, value, value / 127f, timestampMs = clock, deviceId = name), ctx
    )

    private fun frame(advanceMs: Long = 16) {
        clock += advanceMs
        manager.updateFeedback(pad, clock)
    }

    private fun ringWrites() = sink.drain().filter { it.first == 0 }

    @Test
    fun aTurnOnTheFirstBankIsEchoedToItsRing() {
        frame()
        sink.drain()
        send(0, 0, 65)                      // knob 1, one tick up
        frame()
        assertTrue(ringWrites().any { it.second == 0 && it.third == 65 }, "ring of knob 1 should follow")
    }

    @Test
    fun aTurnAfterSwitchingToBankTwoIsEchoedToThatBanksRing() {
        frame(); send(0, 0, 65); frame()
        sink.drain()

        send(3, 0, 0); send(3, 1, 127)      // press next-bank: leave bank 1, enter bank 2
        frame()
        val entry = sink.drain()
        assertTrue(entry.any { it.first == 0 && it.second == 16 }, "bank 2 is written on entry: $entry")

        // Settle past the staged rewrites so only the turn can produce a message.
        frame(2000); sink.drain()

        send(0, 16, 65)                     // knob 1 on bank 2, one tick up
        frame()
        val after = ringWrites()
        assertTrue(after.any { it.second == 16 }, "bank 2 knob 1's ring should be written after the turn: $after")
        assertTrue(after.none { it.second !in 16..31 }, "and only bank 2's numbers are used: $after")
    }

    @Test
    fun theSameHoldsOnBankThreeAndFour() {
        frame(); send(0, 0, 65); frame()
        for (bank in 1..3) {
            send(3, bank - 1, 0); send(3, bank, 127)
            frame(); frame(2000); sink.drain()
            val cc = 16 * bank + 4
            send(0, cc, 65)                 // knob 5 on that bank
            frame()
            val after = ringWrites()
            assertTrue(after.any { it.second == cc }, "bank ${bank + 1}: ring CC $cc should be written: $after")
        }
    }

    @Test
    fun tracingSinkForwardsEverything() {
        val inner = FakeSink()
        val traced = TracingSink(inner, "test")
        traced.sendCc(0, 16, 99)
        assertTrue(Triple(0, 16, 99) in inner.sent)
        assertTrue(traced.isHealthy)
    }
}
