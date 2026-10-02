package llm.slop.liquidlsd.control

import io.mockk.mockk
import llm.slop.liquidlsd.midi.MidiEvent
import llm.slop.liquidlsd.midi.MidiMessageType
import llm.slop.liquidlsd.rendering.Mixer
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ControllerRuntimeTest {
    private class FakeSurface : KnobSurface {
        val calls = ArrayList<String>()
        val turns = ArrayList<Pair<Int, Float>>()
        override fun turn(knob: Int, delta: Float) { turns += knob to delta; calls += "turn $knob" }
        override fun primary(knob: Int) { calls += "primary $knob" }
        override fun secondary(knob: Int) { calls += "secondary $knob" }
        override fun showPage(pageId: String) { calls += "page $pageId" }
    }

    private val surface = FakeSurface()
    private val registry = CommandRegistry().also { GlobalCommands.registerAll(it); KnobCommands().register(it) }
    private val ctx = CommandContext(mockk<Mixer>(relaxed = true), knobSurface = surface)
    private val shipped = ControllerProfileStore(createTempDirectory("controllers").toFile()).get("midi-fighter-twister")!!

    /** The shipped profile with its encoders switched to the factory absolute mode. */
    private val absoluteRuntime = ControllerRuntime(
        ControllerProfile.copyWithKnobMode(shipped.profile, EncoderMode.ABSOLUTE).compile(),
        registry
    )
    private val relativeRuntime = ControllerRuntime(shipped, registry)
    private var runtime = absoluteRuntime
    private var clock = 1_000L

    private fun send(channel: Int, cc: Int, value: Int, atMs: Long = clock.also { clock += 100 }): Boolean =
        runtime.handle(
            MidiEvent(channel, MidiMessageType.CC, cc, value, value / 127f, timestampMs = atMs, deviceId = "Midi Fighter Twister"),
            ctx
        )

    private fun turn(cc: Int, value: Int, atMs: Long = clock.also { clock += 100 }) = send(0, cc, value, atMs)
    private fun switch(cc: Int, down: Boolean) = send(1, cc, if (down) 127 else 0)
    private fun side(cc: Int, down: Boolean) = send(3, cc, if (down) 127 else 0)

    // --- Decoding helpers ---

    @Test
    fun relativeEncoderModesDecodeToSignedTicks() {
        val binary = EncoderMode.RELATIVE_BINARY_OFFSET
        assertEquals(1, ControllerRuntime.decodeRelative(binary, 65))
        assertEquals(-1, ControllerRuntime.decodeRelative(binary, 63))
        assertEquals(0, ControllerRuntime.decodeRelative(binary, 64))
        assertEquals(3, ControllerRuntime.decodeRelative(EncoderMode.RELATIVE_SIGNED_BIT, 3))
        assertEquals(-3, ControllerRuntime.decodeRelative(EncoderMode.RELATIVE_SIGNED_BIT, 67))
        assertEquals(2, ControllerRuntime.decodeRelative(EncoderMode.RELATIVE_TWOS_COMP, 2))
        assertEquals(-2, ControllerRuntime.decodeRelative(EncoderMode.RELATIVE_TWOS_COMP, 126))
        assertEquals(0, ControllerRuntime.decodeRelative(EncoderMode.ABSOLUTE, 99))
    }

    @Test
    fun accelerationRampsFromSlowToFast() {
        val f = ControllerRuntime::accelerationFactor
        assertEquals(1f, f(500, 4f))
        assertEquals(1f, f(ControllerRuntime.SLOW_MS, 4f))
        assertEquals(4f, f(ControllerRuntime.FAST_MS, 4f))
        assertEquals(4f, f(0, 4f))
        val mid = f((ControllerRuntime.FAST_MS + ControllerRuntime.SLOW_MS) / 2, 4f)
        assertTrue(mid > 1f && mid < 4f, "mid-speed boost should be between 1 and 4 but was $mid")
        assertEquals(1f, f(1, 1f), "maxBoost 1 disables acceleration")
    }

    // --- Relative turning (the shipped profile) ---

    private fun useRelative() { runtime = relativeRuntime }

    @Test
    fun shippedProfileReadsEncodersAsBinaryOffsetRelative() {
        useRelative()
        turn(0, 65)                            // one tick clockwise, even as the very first message
        turn(0, 63)                            // one tick back
        assertEquals(listOf(1f / 127f, -1f / 127f), surface.turns.map { it.second })
        assertEquals(listOf(0, 0), surface.turns.map { it.first })
    }

    @Test
    fun relativeMultiTickMessagesAndAccelerationCombine() {
        useRelative()
        turn(3, 68, atMs = 1_000)              // +4 ticks, nothing before it, so no boost
        turn(3, 66, atMs = 1_004)              // +2 ticks, 4 ms later: x4
        assertEquals(4f / 127f, surface.turns[0].second, 1e-6f)
        assertEquals(8f / 127f, surface.turns[1].second, 1e-6f)
    }

    @Test
    fun relativeCenterValueIsNoMovement() {
        useRelative()
        assertTrue(turn(0, 64))
        assertTrue(surface.turns.isEmpty())
    }

    @Test
    fun fineTurnAppliesToRelativeEncodersToo() {
        useRelative()
        send(1, 2, 127); turn(2, 65); send(1, 2, 0)
        assertEquals(1f / 127f * KnobCommands.FINE_FACTOR, surface.turns.single().second, 1e-6f)
        assertEquals(listOf("turn 2"), surface.calls)   // and no tap
    }

    // --- Absolute turning (factory mode, via a profile copy) ---

    @Test
    fun absoluteTurnsAreChangesFromThePreviousValue() {
        assertTrue(turn(0, 10))               // first message only locates the knob
        assertTrue(surface.turns.isEmpty())
        turn(0, 15)                           // slow, so no acceleration
        assertEquals(1, surface.turns.size)
        assertEquals(0, surface.turns[0].first)
        assertEquals(5f / 127f, surface.turns[0].second, 1e-6f)
        turn(0, 12)
        assertEquals(-3f / 127f, surface.turns[1].second, 1e-6f)
    }

    @Test
    fun fastTurnsAreAccelerated() {
        turn(2, 10, atMs = 1_000)
        turn(2, 11, atMs = 2_000)             // establishes the previous tick time
        turn(2, 12, atMs = 2_004)             // 4 ms later: full boost (x4)
        assertEquals(2, surface.turns.size)
        assertEquals(1f / 127f, surface.turns[0].second, 1e-6f)
        assertEquals(4f / 127f, surface.turns[1].second, 1e-6f)
        assertEquals(2, surface.turns[1].first)
    }

    @Test
    fun theSameKnobOnAnotherBankIsTheSameKnobWithItsOwnPosition() {
        turn(1, 50); turn(1, 51)              // bank 1, knob 2
        turn(17, 100); turn(17, 103)          // bank 2, knob 2 (CC 16 + 1); first message there only locates it
        assertEquals(listOf(1, 1), surface.turns.map { it.first })
        assertEquals(3f / 127f, surface.turns[1].second, 1e-6f)
    }

    // --- Banks ---

    @Test
    fun bankButtonsSelectThePageAndTrackTheActiveBank() {
        assertNull(runtime.activeBank)
        assertTrue(side3(0, 0))               // leaving bank 1: consumed, nothing happens
        assertTrue(side3(1, 127))             // entering bank 2
        assertEquals(1, runtime.activeBank)
        assertEquals(listOf("page perform.master"), surface.calls)
        assertTrue(side3(0, 127))
        assertEquals(listOf("page perform.master", "page perform.decks"), surface.calls)
        assertTrue(side3(2, 127))             // bank 3 has no page: the screen is left alone
        assertEquals(2, runtime.activeBank)
        assertEquals(2, surface.calls.size)
    }

    private fun side3(cc: Int, value: Int) = send(3, cc, value)

    @Test
    fun anyBankAwareInputRevealsTheActiveBank() {
        turn(40, 1)                            // CC 40 = bank 3, knob 9
        assertEquals(2, runtime.activeBank)
    }

    // --- Switch gestures ---

    @Test
    fun tapRunsThePrimaryAction() {
        assertTrue(switch(3, true))
        assertTrue(surface.calls.isEmpty())    // nothing until release
        switch(3, false)
        assertEquals(listOf("primary 3"), surface.calls)
    }

    @Test
    fun turningWhileHeldIsFineAndSuppressesTheTap() {
        switch(5, true)
        turn(5, 20)
        turn(5, 30)                            // +10 ticks, slow
        switch(5, false)
        assertEquals(listOf("turn 5"), surface.calls)
        assertEquals(10f / 127f * KnobCommands.FINE_FACTOR, surface.turns.single().second, 1e-6f)
        // And once released, turning is normal again.
        turn(5, 40)
        assertEquals(10f / 127f, surface.turns.last().second, 1e-6f)
    }

    @Test
    fun shiftedTapRunsTheSecondaryActionEvenIfShiftIsReleasedFirst() {
        side(10, true)                         // shift (CC 10, bank 1)
        switch(1, true)
        side(10, false)
        switch(1, false)
        assertEquals(listOf("secondary 1"), surface.calls)
        // Next tap without shift is primary again.
        switch(1, true); switch(1, false)
        assertEquals(listOf("secondary 1", "primary 1"), surface.calls)
    }

    @Test
    fun shiftOnAnotherBankStillCountsAsShift() {
        side(14, true)                         // shift on bank 2 (10 + 4)
        switch(1, true); switch(1, false)
        assertEquals(listOf("secondary 1"), surface.calls)
    }

    // --- What the profile does not consume ---

    @Test
    fun unboundAndUnknownInputsFallThroughToLegacyBindings() {
        assertFalse(side(8, true))             // side.1 has no binding yet
        assertFalse(side(8, false))
        assertFalse(send(5, 30, 127))          // not a Twister input at all
        assertFalse(send(0, 100, 5))           // CC beyond the 64 encoders
        assertTrue(surface.calls.isEmpty())
    }

    @Test
    fun modifierPressesAreConsumed() {
        assertTrue(side(10, true))
        assertTrue(side(10, false))
    }

    // --- Manager ---

    @Test
    fun managerRoutesByDeviceAndIgnoresUnknownOnes() {
        val store = ControllerProfileStore(createTempDirectory("controllers").toFile())
        val manager = ControllerManager(registry, store)
        fun event(device: String) = MidiEvent(0, MidiMessageType.CC, 0, 5, 5 / 127f, timestampMs = 1, deviceId = device)

        assertFalse(manager.handle(event(""), ctx))
        assertFalse(manager.handle(event("Launchpad Mini"), ctx))
        assertNull(manager.runtimeFor("Midi Fighter Twister [hw:2,0,0]"))
        assertTrue(manager.handle(event("Midi Fighter Twister [hw:2,0,0]"), ctx))
        assertNotNull(manager.runtimeFor("Midi Fighter Twister [hw:2,0,0]"))
        manager.reset()
        assertNull(manager.runtimeFor("Midi Fighter Twister [hw:2,0,0]"))
    }
}
