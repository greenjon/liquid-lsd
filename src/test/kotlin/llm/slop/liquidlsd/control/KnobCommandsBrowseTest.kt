package llm.slop.liquidlsd.control

import io.mockk.mockk
import llm.slop.liquidlsd.rendering.Mixer
import kotlin.test.Test
import kotlin.test.assertEquals

class KnobCommandsBrowseTest {
    private val registry = CommandRegistry().also { KnobCommands().register(it) }
    private var browsing = true
    private var steps = 0
    private var session = 0
    private var live: Set<SendTarget> = emptySet()
    private var rowLive = false
    private var switchable: Set<SendTarget> = emptySet()
    private val switched = mutableListOf<SendTarget>()
    private val sent = mutableListOf<SendTarget>()
    private val primaries = mutableListOf<Int>()
    private val nav = object : NavSurface {
        override val browsing get() = this@KnobCommandsBrowseTest.browsing
        override val browseSession get() = this@KnobCommandsBrowseTest.session
        override fun button(index: Int, shifted: Boolean) {}
        override fun browseStep(steps: Int) { this@KnobCommandsBrowseTest.steps += steps }
        override fun browseAccept(shifted: Boolean) {}
        override val browseLiveKnobs get() = if (this@KnobCommandsBrowseTest.rowLive) 4 else 0
        override val sendTargets get() = this@KnobCommandsBrowseTest.live
        override fun browseSend(target: SendTarget) { sent += target }
        override val switchTargets get() = this@KnobCommandsBrowseTest.switchable
        override fun browseSwitch(target: SendTarget) { switched += target }
    }
    private val surface = object : KnobSurface {
        override fun turn(knob: Int, delta: Float) {}
        override fun primary(knob: Int) { primaries += knob }
        override fun secondary(knob: Int) {}
        override fun showPage(pageId: String) {}
    }
    private val ctx = CommandContext(mockk<Mixer>(relaxed = true), knobSurface = surface, navSurface = nav)
    private val half = KnobCommands.BROWSE_STEP * 0.6f

    @Test
    fun leftoverBrowseTravelDoesNotLeakIntoTheNextSession() {
        registry.execute("knob.16", CommandInput.Delta(half), ctx)
        assertEquals(0, steps)
        browsing = false
        registry.execute("knob.2", CommandInput.Delta(0.01f), ctx)   // a normal turn ends the session
        browsing = true
        registry.execute("knob.16", CommandInput.Delta(half), ctx)
        assertEquals(0, steps)                                         // 0.6 + 0.6 would have been one step
    }

    @Test
    fun restartingBrowsingWithNoOtherKnobEventStartsFromZero() {
        registry.execute("knob.16", CommandInput.Delta(half), ctx)
        assertEquals(0, steps)
        browsing = false
        browsing = true
        session++                                                      // a new browse session began
        registry.execute("knob.16", CommandInput.Delta(half), ctx)
        assertEquals(0, steps)                                         // would have been one step with carry-over
    }

    @Test
    fun travelWithinOneSessionStillAccumulates() {
        registry.execute("knob.16", CommandInput.Delta(half), ctx)
        registry.execute("knob.16", CommandInput.Delta(half), ctx)
        assertEquals(1, steps)
    }

    @Test
    fun clearHeldStateDropsBrowseTravel() {
        registry.execute("knob.16", CommandInput.Delta(half), ctx)
        registry.clearHeldState()
        registry.execute("knob.16", CommandInput.Delta(half), ctx)
        assertEquals(0, steps)
    }

    @Test
    fun anAcceleratedTurnStillMovesOneItem() {
        registry.execute("knob.16", CommandInput.Delta(KnobCommands.BROWSE_STEP * 4f), ctx)
        assertEquals(1, steps)
        registry.execute("knob.16", CommandInput.Delta(-KnobCommands.BROWSE_STEP * 3f), ctx)
        assertEquals(0, steps)
    }

    private fun tap(n: Int, alt: Boolean = false) {
        val name = if (alt) "knob.$n.press_alt" else "knob.$n.press"
        registry.execute(name, CommandInput.Press(true), ctx)
        registry.execute(name, CommandInput.Press(false), ctx)
    }

    @Test
    fun sendKnobsAreRowThreePlusKnob13() {
        assertEquals(listOf(8, 9, 10, 11), listOf(SendTarget.A, SendTarget.B, SendTarget.BG, SendTarget.PV).map { it.knob })
        assertEquals(12, SendTarget.MASTER.knob)
    }

    @Test
    fun tappingALiveSendKnobSendsToItsTarget() {
        live = SendTarget.entries.toSet()
        tap(9); tap(10); tap(11); tap(12); tap(13)
        assertEquals(listOf(SendTarget.A, SendTarget.B, SendTarget.BG, SendTarget.PV, SendTarget.MASTER), sent)
        assertEquals(emptyList(), primaries)
    }

    @Test
    fun shiftTapOnASwitchKnobSwitchesWithoutSendingEvenWithNothingToSend() {
        switchable = SendTarget.entries.toSet()
        tap(9, alt = true); tap(13, alt = true)
        assertEquals(listOf(SendTarget.A, SendTarget.MASTER), switched)
        assertEquals(emptyList(), sent)
        live = SendTarget.entries.toSet()
        tap(10) // a plain tap still sends
        assertEquals(listOf(SendTarget.B), sent)
        assertEquals(2, switched.size)
    }

    @Test
    fun aSendKnobThatIsNotLiveIsInert() {
        live = setOf(SendTarget.A)
        tap(13); tap(10)
        assertEquals(emptyList(), sent)
        tap(9, alt = true)
        assertEquals(listOf(SendTarget.A), sent)
    }

    @Test
    fun otherKnobsStayInertAndTurnsNeverSend() {
        live = SendTarget.entries.toSet()
        tap(3); tap(8); tap(16)
        registry.execute("knob.1", CommandInput.Delta(0.5f), ctx)
        assertEquals(emptyList(), sent)
    }

    @Test
    fun inThePickerRowOneStaysOnTheRowAndRowThreeSends() {
        rowLive = true
        live = SendTarget.entries.toSet()
        tap(1); tap(4); tap(9)
        assertEquals(listOf(0, 3), primaries)
        assertEquals(listOf(SendTarget.A), sent)
    }
}
