package llm.slop.liquidlsd.control

import io.mockk.mockk
import llm.slop.liquidlsd.rendering.Mixer
import kotlin.test.Test
import kotlin.test.assertEquals

class NavCommandsTest {
    private class FakeNav(override var browsing: Boolean) : NavSurface {
        val calls = ArrayList<String>()
        override fun button(index: Int, shifted: Boolean) { calls += "button $index${if (shifted) " shifted" else ""}" }
        override fun browseStep(steps: Int) { calls += "step $steps" }
        override fun browseAccept(shifted: Boolean) { calls += if (shifted) "accept shifted" else "accept" }
    }

    private class FakeKnobs : KnobSurface {
        val calls = ArrayList<String>()
        override fun turn(knob: Int, delta: Float) { calls += "turn $knob" }
        override fun primary(knob: Int) { calls += "primary $knob" }
        override fun secondary(knob: Int) { calls += "secondary $knob" }
        override fun showPage(pageId: String) {}
    }

    private val registry = CommandRegistry().also { KnobCommands().register(it); NavCommands.register(it) }
    private val knobs = FakeKnobs()
    private fun ctx(nav: FakeNav?) = CommandContext(mockk<Mixer>(relaxed = true), knobSurface = knobs, navSurface = nav)
    private fun press(id: String, c: CommandContext) {
        registry.execute(id, CommandInput.Press(true), c)
        registry.execute(id, CommandInput.Press(false), c)
    }

    @Test
    fun sideButtonsReachTheNavSurface() {
        val nav = FakeNav(browsing = false)
        val c = ctx(nav)
        press("nav.button.1", c)
        press("nav.button.3.alt", c)
        assertEquals(listOf("button 0", "button 2 shifted"), nav.calls)
    }

    @Test
    fun sideButtonsAreNoOpsWithoutASurface() {
        press("nav.button.2", ctx(null))
    }

    @Test
    fun knobOneStepsAndAcceptsWhileBrowsing() {
        val nav = FakeNav(browsing = true)
        val c = ctx(nav)
        val tick = 1f / 127f
        // Below one step nothing happens, then the remainder carries over.
        registry.execute("knob.1", CommandInput.Delta(3 * tick), c)
        assertEquals(emptyList(), nav.calls)
        registry.execute("knob.1", CommandInput.Delta(2 * tick), c)
        assertEquals(listOf("step 1"), nav.calls)
        registry.execute("knob.1", CommandInput.Delta(-8 * tick), c)
        assertEquals("step -1", nav.calls[1].takeIf { nav.calls.size == 2 } ?: nav.calls.toString())
        press("knob.1.press", c)
        press("knob.1.press_alt", c)
        assertEquals(listOf("accept", "accept shifted"), nav.calls.takeLast(2))
        assertEquals(emptyList(), knobs.calls)
    }

    @Test
    fun otherKnobsAndNonBrowsingKeepTheirPerformMeaning() {
        val nav = FakeNav(browsing = true)
        registry.execute("knob.2", CommandInput.Delta(0.01f), ctx(nav))
        press("knob.2.press", ctx(nav))
        val idle = FakeNav(browsing = false)
        registry.execute("knob.1", CommandInput.Delta(0.01f), ctx(idle))
        press("knob.1.press", ctx(idle))
        assertEquals(listOf("turn 1", "primary 1", "turn 0", "primary 0"), knobs.calls)
        assertEquals(emptyList(), nav.calls + idle.calls)
    }
}
