package llm.slop.liquidlsd.control

import kotlinx.serialization.json.Json
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ControllerFeedbackTest {
    private class FakeSink : MidiSink {
        val sent = ArrayList<Triple<Int, Int, Int>>()
        override fun sendCc(channel: Int, cc: Int, value: Int) { sent += Triple(channel, cc, value) }
        fun drain(): List<Triple<Int, Int, Int>> = sent.toList().also { sent.clear() }
    }

    private val twister = ControllerProfileStore(createTempDirectory("controllers").toFile()).get("midi-fighter-twister")!!
    private val sink = FakeSink()

    private val feedback = ControllerFeedback(twister.profile.compile(), sink)

    private fun lights(vararg overrides: Pair<Int, KnobLight?>): List<KnobLight?> {
        val out = MutableList<KnobLight?>(16) { null }
        for ((i, l) in overrides) out[i] = l
        return out
    }

    private val blue = KnobLight(0.5f, 0f, 0f, 1f)

    // --- Hue wheel ---

    @Test
    fun hueWheelMapsPrimaryColoursAlongTheWheel() {
        val w = HueWheel()
        assertEquals(1, w.valueFor(0f, 0f, 1f), "blue is the start of the wheel")
        assertEquals(22, w.valueFor(0f, 1f, 1f), "cyan")
        assertEquals(43, w.valueFor(0f, 1f, 0f), "green")
        assertEquals(63, w.valueFor(1f, 1f, 0f), "yellow (180 / 2.88 = 62.5 rounds down in float)")
        assertEquals(84, w.valueFor(1f, 0f, 0f), "red")
        assertEquals(105, w.valueFor(1f, 0f, 1f), "magenta")
    }

    @Test
    fun hueWheelHandlesBlackGreyAndBrightnessOfTheSameHue() {
        val w = HueWheel()
        assertEquals(w.off, w.valueFor(0f, 0f, 0f))
        assertEquals(w.white, w.valueFor(0.8f, 0.8f, 0.8f))
        assertEquals(w.valueFor(1f, 0f, 0f), w.valueFor(0.4f, 0f, 0f), "brightness does not change the hue")
    }

    @Test
    fun hueWheelCanRunTheOtherWayAndStaysInRange() {
        val reversed = HueWheel(hueAtMin = 0f, degreesPerStep = 2.88f)
        assertEquals(1, reversed.valueFor(1f, 0f, 0f))
        assertEquals(43, reversed.valueFor(0f, 1f, 0f), "green is 120 degrees on")
        for (hue in 0..359 step 7) {
            val rad = Math.toRadians(hue.toDouble())
            val v = HueWheel().valueFor(
                (0.5 + 0.5 * Math.cos(rad)).toFloat(), (0.5 + 0.5 * Math.cos(rad - 2.094)).toFloat(), (0.5 + 0.5 * Math.cos(rad + 2.094)).toFloat()
            )
            assertTrue(v in 1..126 || v == 127, "hue $hue gave $v")
        }
    }

    // --- Sending ---

    @Test
    fun firstUpdateWritesEveryRingAndLedOnEveryBankWhileTheBankIsUnknown() {
        feedback.update(lights(0 to blue), nowMs = 0)
        val sent = sink.drain()
        assertEquals(64 * 2, sent.size)
        // Knob 1 on each of the four banks: ring on ch1 (0), LED on ch2 (1), CC = knob + 16 * bank.
        for (bank in 0..3) {
            assertTrue(Triple(0, bank * 16, 64) in sent, "ring bank $bank")
            assertTrue(Triple(1, bank * 16, 1) in sent, "blue LED bank $bank")
        }
        // Knobs with nothing on them are zero and dark.
        assertTrue(Triple(0, 5, 0) in sent)
        assertTrue(Triple(1, 5, 0) in sent)
    }

    @Test
    fun anUnchangedPageSendsNothing() {
        feedback.update(lights(0 to blue), nowMs = 0)
        sink.drain()
        feedback.update(lights(0 to blue), nowMs = 10)
        assertEquals(emptyList(), sink.drain())
    }

    @Test
    fun aValueChangeSendsOneRingMessagePerBank() {
        feedback.update(lights(3 to KnobLight(0f, 1f, 0f, 0f)), nowMs = 0)
        sink.drain()
        feedback.update(lights(3 to KnobLight(1f, 1f, 0f, 0f)), nowMs = 10)
        assertEquals(setOf(Triple(0, 3, 127), Triple(0, 19, 127), Triple(0, 35, 127), Triple(0, 51, 127)), sink.drain().toSet())
    }

    @Test
    fun aColourChangeSendsOnlyTheLed() {
        feedback.update(lights(2 to KnobLight(0.5f, 1f, 0f, 0f)), nowMs = 0)
        sink.drain()
        feedback.update(lights(2 to KnobLight(0.5f, 0f, 1f, 0f)), nowMs = 10)
        val sent = sink.drain()
        assertEquals(4, sent.size)
        assertTrue(sent.all { it.first == 1 && it.third == 43 }, sent.toString())
    }

    @Test
    fun anUnlitKnobKeepsItsRingButGoesDark() {
        feedback.update(lights(1 to KnobLight(0.25f, 1f, 0f, 0f)), nowMs = 0)
        sink.drain()
        feedback.update(lights(1 to KnobLight(0.25f, 1f, 0f, 0f, lit = false)), nowMs = 10)
        assertEquals(setOf(Triple(1, 1, 0), Triple(1, 17, 0), Triple(1, 33, 0), Triple(1, 49, 0)), sink.drain().toSet())
    }

    @Test
    fun aKnobThatDisappearsGoesToZeroAndDark() {
        feedback.update(lights(4 to blue), nowMs = 0)
        sink.drain()
        feedback.update(lights(), nowMs = 10)
        val sent = sink.drain()
        assertEquals(8, sent.size)
        assertTrue(sent.all { it.third == 0 }, sent.toString())
    }

    @Test
    fun resyncRewritesEverything() {
        feedback.update(lights(0 to blue), nowMs = 0)
        sink.drain()
        feedback.resync()
        feedback.update(lights(0 to blue), nowMs = 10)
        assertEquals(128, sink.drain().size)
    }

    @Test
    fun valuesAreClampedToTheMidiRange() {
        feedback.update(lights(0 to KnobLight(7f, 0f, 0f, 1f), 1 to KnobLight(-3f, 0f, 0f, 1f)), nowMs = 0)
        val sent = sink.drain()
        assertTrue(Triple(0, 0, 127) in sent)
        assertTrue(Triple(0, 1, 0) in sent)
    }

    @Test
    fun aProfileWithoutOutputSendsNothing() {
        val plain = Json.decodeFromString<ControllerProfile>(
            """{"id":"x","inputs":[{"id":"knob","kind":"ENCODER","channel":0,"cc":0,"count":4}]}""").compile()
        ControllerFeedback(plain, sink).update(lights(0 to blue), nowMs = 0)
        assertEquals(emptyList(), sink.drain())
    }

    @Test
    fun ringsDefaultToTheEncodersChannelAndCanBeMoved() {
        val custom = Json.decodeFromString<ControllerProfile>(
            """{"id":"x","inputs":[{"id":"knob","kind":"ENCODER","channel":5,"cc":10,"count":2}],
                "output":{"knobs":{"input":"knob"}}}""").compile()
        ControllerFeedback(custom, sink).update(lights(0 to blue), nowMs = 0)
        assertEquals(listOf(Triple(5, 10, 64), Triple(5, 11, 0)), sink.drain(), "no colourChannel means no LED messages")

        val moved = Json.decodeFromString<ControllerProfile>(
            """{"id":"x","inputs":[{"id":"knob","kind":"ENCODER","channel":5,"cc":10,"count":1}],
                "output":{"knobs":{"input":"knob","ringChannel":2,"colorChannel":3}}}""").compile()
        ControllerFeedback(moved, sink).update(lights(0 to blue), nowMs = 0)
        assertEquals(listOf(Triple(2, 10, 64), Triple(3, 10, 1)), sink.drain())
    }


    // --- Active bank, settle and heartbeat ---

    @Test
    fun withAKnownBankOnlyThatBanksEncodersAreWritten() {
        feedback.update(lights(0 to blue), activeBank = 1, nowMs = 0)
        val sent = sink.drain()
        assertEquals(16 * 2, sent.size)
        assertTrue(sent.all { it.second in 16..31 }, "bank 2 is CC 16..31: $sent")
        assertTrue(Triple(0, 16, 64) in sent)
        assertTrue(Triple(1, 16, 1) in sent)
    }

    @Test
    fun enteringABankRewritesAllOfItEvenIfNothingChanged() {
        feedback.update(lights(0 to blue), activeBank = 0, nowMs = 0)
        sink.drain()
        feedback.update(lights(0 to blue), activeBank = 0, nowMs = 10)
        assertEquals(emptyList(), sink.drain())

        feedback.update(lights(0 to blue), activeBank = 2, nowMs = 20)
        val sent = sink.drain()
        assertEquals(16 * 2, sent.size)
        assertTrue(sent.all { it.second in 32..47 }, sent.toString())

        // Switching back rewrites bank 1 again: the device may have shown its own colours meanwhile.
        feedback.update(lights(0 to blue), activeBank = 0, nowMs = 30)
        assertEquals(16 * 2, sink.drain().size)
    }

    @Test
    fun learningTheBankAfterAnUnknownStartRewritesThatBank() {
        feedback.update(lights(0 to blue), activeBank = null, nowMs = 0)
        assertEquals(128, sink.drain().size)
        feedback.update(lights(0 to blue), activeBank = 3, nowMs = 10)
        assertEquals(16 * 2, sink.drain().size)
    }

    private val bankSize = 16 * 2

    /** Gets past the staged rewrites that follow connecting, so the next steps start from a quiet device. */
    private fun settleIn(bank: Int = 0, light: KnobLight = blue) {
        feedback.update(lights(0 to light), activeBank = bank, nowMs = 0)
        feedback.update(lights(0 to light), activeBank = bank, nowMs = 2000)
        sink.drain()
    }

    @Test
    fun connectingAndSwitchingBankAreFollowedByStagedRewrites() {
        val (first, second, third) = ControllerFeedback.BANK_REWRITES_MS.toList()
        feedback.update(lights(0 to blue), activeBank = 0, nowMs = 0)
        assertEquals(bankSize, sink.drain().size)
        for (due in listOf(first, second, third)) {
            feedback.update(lights(0 to blue), activeBank = 0, nowMs = due - 1)
            assertEquals(emptyList(), sink.drain(), "not before $due")
            feedback.update(lights(0 to blue), activeBank = 0, nowMs = due)
            assertEquals(bankSize, sink.drain().size, "rewrite at $due")
        }
        feedback.update(lights(0 to blue), activeBank = 0, nowMs = third + 500)
        assertEquals(emptyList(), sink.drain(), "then quiet")

        // The same schedule follows a bank switch.
        feedback.update(lights(0 to blue), activeBank = 1, nowMs = 5000)
        assertEquals(bankSize, sink.drain().size)
        feedback.update(lights(0 to blue), activeBank = 1, nowMs = 5000 + first)
        assertEquals(bankSize, sink.drain().size)
    }

    @Test
    fun theActiveBankIsRewrittenOnceAfterTheLastChange() {
        settleIn()
        feedback.update(lights(0 to KnobLight(0.75f, 0f, 0f, 1f)), activeBank = 0, nowMs = 2100)   // a real change
        assertEquals(1, sink.drain().size)

        val now = 2100 + ControllerFeedback.SETTLE_MS
        feedback.update(lights(0 to KnobLight(0.75f, 0f, 0f, 1f)), activeBank = 0, nowMs = now - 1)
        assertEquals(emptyList(), sink.drain(), "not yet")
        feedback.update(lights(0 to KnobLight(0.75f, 0f, 0f, 1f)), activeBank = 0, nowMs = now)
        val settle = sink.drain()
        assertEquals(bankSize, settle.size, "the whole active bank, once")
        assertTrue(Triple(0, 0, 95) in settle, "with the latest ring value (0.75 * 127)")

        feedback.update(lights(0 to KnobLight(0.75f, 0f, 0f, 1f)), activeBank = 0, nowMs = now + 500)
        assertEquals(emptyList(), sink.drain(), "and not again")
    }

    @Test
    fun continuousChangesPushTheSettleRewriteBack() {
        settleIn()
        for ((i, v) in listOf(0.6f, 0.7f, 0.8f).withIndex()) {
            feedback.update(lights(0 to KnobLight(v, 0f, 0f, 1f)), activeBank = 0, nowMs = 2050L + 50L * i)
            assertEquals(1, sink.drain().size, "only the changed ring")
        }
        // Last change at t=2150: the settle rewrite is due at 2150 + SETTLE_MS, not earlier.
        val due = 2150 + ControllerFeedback.SETTLE_MS
        feedback.update(lights(0 to KnobLight(0.8f, 0f, 0f, 1f)), activeBank = 0, nowMs = due - 1)
        assertEquals(emptyList(), sink.drain())
        feedback.update(lights(0 to KnobLight(0.8f, 0f, 0f, 1f)), activeBank = 0, nowMs = due)
        assertEquals(bankSize, sink.drain().size)
    }

    @Test
    fun theHeartbeatRewritesTheActiveBankEvenWhenNothingChanges() {
        settleIn()
        feedback.update(lights(0 to blue), activeBank = 0, nowMs = ControllerFeedback.HEARTBEAT_MS - 1)
        assertEquals(emptyList(), sink.drain())
        feedback.update(lights(0 to blue), activeBank = 0, nowMs = ControllerFeedback.HEARTBEAT_MS)
        assertEquals(bankSize, sink.drain().size)
        feedback.update(lights(0 to blue), activeBank = 0, nowMs = ControllerFeedback.HEARTBEAT_MS + 1)
        assertEquals(emptyList(), sink.drain())
        feedback.update(lights(0 to blue), activeBank = 0, nowMs = 2 * ControllerFeedback.HEARTBEAT_MS)
        assertEquals(bankSize, sink.drain().size, "and keeps beating")
    }

    @Test
    fun eachBanksOwnNumbersAreUsed() {
        // Checked with amidi on the hardware: CC 16 lights bank 2's knob 1 live, CC 0 does nothing visible.
        feedback.update(lights(0 to blue), activeBank = 1, nowMs = 0)
        val sent = feedback.let { sink.drain() }
        assertEquals(16 * 2, sent.size)
        assertTrue(Triple(0, 16, 64) in sent && Triple(1, 16, 1) in sent)
    }

    // --- Profile ---

    @Test
    fun twisterProfileDeclaresRingAndColourFeedback() {
        val fb = twister.profile.output.knobs
        assertNotNull(fb)
        assertEquals("knob", fb.input)
        assertEquals(1, fb.colorChannel)
        assertEquals(emptyList(), twister.problems)
    }

    @Test
    fun badOutputConfigIsReported() {
        fun problems(output: String) = Json.decodeFromString<ControllerProfile>(
            """{"id":"x","inputs":[{"id":"knob","kind":"ENCODER","channel":0,"cc":0},{"id":"b","kind":"BUTTON","channel":0,"cc":9}],
                "output":$output}""").compile().problems

        assertTrue(problems("""{"knobs":{"input":"nope"}}""").any { "is not an input" in it })
        assertTrue(problems("""{"knobs":{"input":"b"}}""").any { "must be an ENCODER" in it })
        assertTrue(problems("""{"knobs":{"input":"knob","colorChannel":16}}""").any { "colorChannel" in it })
        assertTrue(problems("""{"knobs":{"input":"knob","color":{"min":50,"max":10}}}""").any { "min <= max" in it })
        assertTrue(problems("""{"knobs":{"input":"knob","color":{"degreesPerStep":0}}}""").any { "degreesPerStep" in it })
        assertEquals(emptyList(), problems("""{"knobs":{"input":"knob","colorChannel":1}}"""))
    }
}
