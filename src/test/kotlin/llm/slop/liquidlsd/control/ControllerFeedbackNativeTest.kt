package llm.slop.liquidlsd.control

import kotlinx.serialization.json.Json
import llm.slop.liquidlsd.parameters.MeterType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ControllerFeedbackNativeTest {
    /** Records every message in arrival order, as text. */
    private class RecordingSink : MidiSink {
        val log = ArrayList<String>()
        override fun sendCc(channel: Int, cc: Int, value: Int) { log += "cc $channel/$cc=$value" }
        override fun sendSysex(bytes: ByteArray) { log += "sysex " + bytes.joinToString(" ") { "%02X".format(it) } }
        fun drain(): List<String> = log.toList().also { log.clear() }
    }

    private val profile = Json.decodeFromString<ControllerProfile>(
        """{"id":"x","inputs":[{"id":"knob","kind":"ENCODER","channel":0,"cc":0,"count":16}],
            "banks":{"count":2,"pages":["a","b"],"virtual":true},
            "output":{"knobs":{"ringChannel":0,"colorChannel":1,"indicatorChannel":5},"native":{}}}"""
    )
    private val sink = RecordingSink()
    private val feedback = ControllerFeedback(profile.compile(), sink)

    private fun lights(vararg pairs: Pair<Int, KnobLight?>): List<KnobLight?> {
        val out = MutableList<KnobLight?>(16) { null }
        for ((i, l) in pairs) out[i] = l
        return out
    }

    private val red = KnobLight(0.5f, 1f, 0f, 0f, meterType = MeterType.BIPOLAR)

    @Test fun enterPrecedesStyleColourAndRing() {
        feedback.enterNativeMode()
        feedback.update(lights(0 to red))
        val log = sink.drain()
        assertEquals("sysex F0 00 01 79 05 00 01 F7", log.first())
        val k0 = log.filter { it.startsWith("sysex F0 00 01 79 05 01 00 00 ") || it.startsWith("sysex F0 00 01 79 05 01 01 00 ") || it.startsWith("cc 0/0=") }
        assertEquals(
            listOf("sysex F0 00 01 79 05 01 00 00 01 01 7F F7", "sysex F0 00 01 79 05 01 01 00 7F 00 00 F7", "cc 0/0=64"),
            k0
        )
    }

    @Test fun oneTargetPerKnobWithNoBrightnessOrColourCc() {
        feedback.update(lights(3 to red))
        val ccs = sink.drain().filter { it.startsWith("cc") }
        assertEquals(16, ccs.size, "one ring CC per knob, no per-bank copies")
        assertTrue(ccs.all { it.startsWith("cc 0/") }, ccs.toString())
    }

    @Test fun styleAndColourAreResentOnlyOnChange() {
        feedback.update(lights(0 to red)); sink.drain()
        feedback.update(lights(0 to red.copy(value = 0.75f)))
        assertEquals(listOf("cc 0/0=95"), sink.drain(), "only the ring moved")
        feedback.update(lights(0 to red.copy(value = 0.75f, meterType = MeterType.ENDLESS)))
        assertEquals(listOf("sysex F0 00 01 79 05 01 00 00 00 00 7F F7"), sink.drain(),
            "a new meter type sends just the style")
        feedback.update(lights(0 to red.copy(value = 0.75f, meterType = MeterType.ENDLESS, g = 1f)))
        assertEquals(listOf("sysex F0 00 01 79 05 01 01 00 7F 7F 00 F7"), sink.drain(), "a new colour sends just the LED")
    }

    @Test fun unlitOrMissingKnobGoesBlack() {
        feedback.update(lights(0 to red.copy(lit = false)))
        assertTrue("sysex F0 00 01 79 05 01 01 00 00 00 00 F7" in sink.drain())
    }

    @Test fun resyncRewritesEverything() {
        feedback.update(lights(0 to red)); sink.drain()
        feedback.resync()
        feedback.update(lights(0 to red))
        val log = sink.drain()
        assertTrue("sysex F0 00 01 79 05 01 00 00 01 01 7F F7" in log)
        assertTrue("cc 0/0=64" in log)
    }

    @Test fun leaveSendsLeaveAndStockProfilesIgnoreNativeCalls() {
        feedback.leaveNativeMode()
        val log = sink.drain()
        assertEquals(17, log.size)
        assertEquals("sysex F0 00 01 79 05 01 01 00 00 00 00 F7", log.first(), "LEDs go black first")
        assertEquals("sysex F0 00 01 79 05 01 01 0F 00 00 00 F7", log[15])
        assertEquals("sysex F0 00 01 79 05 00 00 F7", log.last(), "then the device is released")
        val stockSink = RecordingSink()
        val stock = ControllerFeedback(
            Json.decodeFromString<ControllerProfile>("""{"id":"y","inputs":[{"id":"knob","kind":"ENCODER","channel":0,"cc":0,"count":2}],"output":{"knobs":{}}}""").compile(),
            stockSink
        )
        stock.enterNativeMode(); stock.leaveNativeMode()
        assertTrue(stockSink.drain().isEmpty())
    }

    @Test fun markerLightLeavesTheRingOffAndKeepsItsColour() {
        feedback.update(lights(2 to KnobLight(1f, 0f, 1f, 0f, meterType = MeterType.ENDLESS, ringBrightness = 0.5f, marker = true)))
        val log = sink.drain()
        assertTrue("sysex F0 00 01 79 05 01 01 02 00 7F 00 F7" in log, "the LED keeps the target colour")
        assertTrue("cc 0/2=0" in log, "the ring stays empty")
    }
}
