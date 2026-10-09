package llm.slop.liquidlsd.control

import io.mockk.mockk
import llm.slop.liquidlsd.rendering.Mixer
import kotlin.test.Test
import kotlin.test.assertEquals

class KnobCommandsPressDownTest {
    private val registry = CommandRegistry().also { KnobCommands().register(it) }
    private val downs = mutableListOf<Int>()
    private val primaries = mutableListOf<Int>()
    private val surface = object : KnobSurface {
        override fun turn(knob: Int, delta: Float) {}
        override fun primary(knob: Int) { primaries += knob }
        override fun secondary(knob: Int) {}
        override fun showPage(pageId: String) {}
        override fun pressDown(knob: Int): Boolean { downs += knob; return knob == 13 }
    }
    private val ctx = CommandContext(mockk<Mixer>(relaxed = true), knobSurface = surface)

    private fun tap(n: Int) {
        registry.execute("knob.$n.press", CommandInput.Press(true), ctx)
        registry.execute("knob.$n.press", CommandInput.Press(false), ctx)
    }

    @Test
    fun aKnobThatActsOnPressDownDoesNotAlsoRunItsTapOnRelease() {
        tap(14)                       // knob index 13
        assertEquals(listOf(13), downs)
        assertEquals(emptyList(), primaries)
    }

    @Test
    fun otherKnobsStillTapOnRelease() {
        tap(3)
        assertEquals(listOf(2), primaries)
    }
}
