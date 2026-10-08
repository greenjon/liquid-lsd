package llm.slop.liquidlsd.control

import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ControllerManagerFeedbackTest {
    private class FakeSink : MidiSink {
        val sent = ArrayList<Triple<Int, Int, Int>>()
        var closed = false
        var healthy = true
        override fun sendCc(channel: Int, cc: Int, value: Int) { sent += Triple(channel, cc, value) }
        override val isHealthy: Boolean get() = healthy
        override fun close() { closed = true }
    }

    private val twisterName = "Midi Fighter Twister [hw:2,0,0]"
    private var connected = listOf(twisterName)
    private val opened = ArrayList<FakeSink>()
    private val intervals = ArrayList<Int>()
    private var openFails = false
    private val registry = CommandRegistry().also { GlobalCommands.registerAll(it); KnobCommands().register(it) }
    private val manager = ControllerManager(
        registry,
        ControllerProfileStore(createTempDirectory("controllers").toFile()),
        connectedDevices = { connected },
        openSink = { _, intervalMs -> if (openFails) null else FakeSink().also { opened += it; intervals += intervalMs } }
    )
    private val source = object : KnobLightSource {
        var lights: List<KnobLight?> = listOf(KnobLight(0.5f, 0f, 0f, 1f))
        override fun knobLights() = lights
    }

    @Test
    fun aConnectedControllerGetsAFullPushOnTheFirstUpdate() {
        manager.updateFeedback(source, nowMs = 0)
        assertEquals(1, opened.size)
        assertEquals(192, opened[0].sent.size)
        assertNotNull(manager.feedbackFor(twisterName))
    }

    @Test
    fun laterUpdatesOnlySendChanges() {
        manager.updateFeedback(source, nowMs = 0)
        opened[0].sent.clear()
        manager.updateFeedback(source, nowMs = 16)
        assertTrue(opened[0].sent.isEmpty())
        source.lights = listOf(KnobLight(1f, 0f, 0f, 1f))
        manager.updateFeedback(source, nowMs = 32)
        assertEquals(4, opened[0].sent.size)
    }

    @Test
    fun devicesWithoutAProfileAreIgnored() {
        connected = listOf("Launchpad Mini", "Midi Through")
        manager.updateFeedback(source, nowMs = 0)
        assertTrue(opened.isEmpty())
    }

    @Test
    fun aDeviceThatLeavesIsClosedAndANewConnectionGetsAFreshPush() {
        manager.updateFeedback(source, nowMs = 0)
        connected = emptyList()
        manager.updateFeedback(source, nowMs = ControllerManager.SCAN_INTERVAL_MS)
        assertTrue(opened[0].closed)
        assertNull(manager.feedbackFor(twisterName))

        connected = listOf(twisterName)
        manager.updateFeedback(source, nowMs = 2 * ControllerManager.SCAN_INTERVAL_MS)
        assertEquals(2, opened.size)
        assertEquals(192, opened[1].sent.size)
    }

    @Test
    fun anUnhealthyPortIsDroppedAndReopened() {
        manager.updateFeedback(source, nowMs = 0)
        opened[0].healthy = false
        manager.updateFeedback(source, nowMs = ControllerManager.SCAN_INTERVAL_MS)
        assertTrue(opened[0].closed)
        assertEquals(2, opened.size)
        assertEquals(192, opened[1].sent.size)
    }

    @Test
    fun aFailedOpenIsRetriedOnlyAfterTheBackoff() {
        openFails = true
        manager.updateFeedback(source, nowMs = 0)
        assertNull(manager.feedbackFor(twisterName))

        openFails = false
        manager.updateFeedback(source, nowMs = ControllerManager.SCAN_INTERVAL_MS)       // too soon
        assertTrue(opened.isEmpty())
        manager.updateFeedback(source, nowMs = ControllerManager.RETRY_OPEN_MS + 1)
        assertEquals(1, opened.size)
        assertNotNull(manager.feedbackFor(twisterName))
    }

    @Test
    fun deviceListIsOnlyPolledOncePerScanInterval() {
        var polls = 0
        val counting = ControllerManager(
            registry, ControllerProfileStore(createTempDirectory("controllers").toFile()),
            connectedDevices = { polls++; connected }, openSink = { _, _ -> FakeSink() }
        )
        for (t in 0L..900L step 100L) counting.updateFeedback(source, nowMs = t)
        assertEquals(1, polls)
        counting.updateFeedback(source, nowMs = ControllerManager.SCAN_INTERVAL_MS)
        assertEquals(2, polls)
    }

    @Test
    fun resetClosesFeedbackAndStartsOver() {
        manager.updateFeedback(source, nowMs = 0)
        manager.reset()
        assertTrue(opened[0].closed)
        assertFalse(manager.feedbackFor(twisterName) != null)
        manager.updateFeedback(source, nowMs = 1)
        assertEquals(2, opened.size)
    }

    @Test
    fun onceTheRuntimeKnowsTheBankOnlyThatBankIsWritten() {
        manager.updateFeedback(source, nowMs = 0)
        assertEquals(192, opened[0].sent.size)
        opened[0].sent.clear()

        val ctx = CommandContext(io.mockk.mockk(relaxed = true))
        val enterBank2 = llm.slop.liquidlsd.midi.MidiEvent(
            3, llm.slop.liquidlsd.midi.MidiMessageType.CC, 1, 127, 1f, timestampMs = 1, deviceId = twisterName
        )
        assertTrue(manager.handle(enterBank2, ctx))
        assertEquals(1, manager.runtimeFor(twisterName)?.activeBank)

        manager.updateFeedback(source, nowMs = 16)
        assertEquals(48, opened[0].sent.size)
        assertTrue(opened[0].sent.all { it.second in 16..31 }, opened[0].sent.toString())
    }

    @Test
    fun theProfilesMinimumIntervalIsHandedToTheSink() {
        manager.updateFeedback(source, nowMs = 0)
        assertEquals(listOf(2), intervals, "the shipped Twister profile paces at 2 ms")
    }
}
