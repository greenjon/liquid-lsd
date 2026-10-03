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
    private val nav = object : NavSurface {
        override val browsing get() = this@KnobCommandsBrowseTest.browsing
        override val browseSession get() = this@KnobCommandsBrowseTest.session
        override fun button(index: Int, shifted: Boolean) {}
        override fun browseStep(steps: Int) { this@KnobCommandsBrowseTest.steps += steps }
        override fun browseAccept(shifted: Boolean) {}
    }
    private val surface = object : KnobSurface {
        override fun turn(knob: Int, delta: Float) {}
        override fun primary(knob: Int) {}
        override fun secondary(knob: Int) {}
        override fun showPage(pageId: String) {}
    }
    private val ctx = CommandContext(mockk<Mixer>(relaxed = true), knobSurface = surface, navSurface = nav)
    private val half = KnobCommands.BROWSE_STEP * 0.6f

    @Test
    fun leftoverBrowseTravelDoesNotLeakIntoTheNextSession() {
        registry.execute("knob.1", CommandInput.Delta(half), ctx)
        assertEquals(0, steps)
        browsing = false
        registry.execute("knob.2", CommandInput.Delta(0.01f), ctx)   // a normal turn ends the session
        browsing = true
        registry.execute("knob.1", CommandInput.Delta(half), ctx)
        assertEquals(0, steps)                                         // 0.6 + 0.6 would have been one step
    }

    @Test
    fun restartingBrowsingWithNoOtherKnobEventStartsFromZero() {
        registry.execute("knob.1", CommandInput.Delta(half), ctx)
        assertEquals(0, steps)
        browsing = false
        browsing = true
        session++                                                      // a new browse session began
        registry.execute("knob.1", CommandInput.Delta(half), ctx)
        assertEquals(0, steps)                                         // would have been one step with carry-over
    }

    @Test
    fun travelWithinOneSessionStillAccumulates() {
        registry.execute("knob.1", CommandInput.Delta(half), ctx)
        registry.execute("knob.1", CommandInput.Delta(half), ctx)
        assertEquals(1, steps)
    }

    @Test
    fun clearHeldStateDropsBrowseTravel() {
        registry.execute("knob.1", CommandInput.Delta(half), ctx)
        registry.clearHeldState()
        registry.execute("knob.1", CommandInput.Delta(half), ctx)
        assertEquals(0, steps)
    }
}
