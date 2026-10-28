package llm.slop.liquidlsd.audio

import org.junit.jupiter.api.Test
import kotlin.math.PI
import kotlin.math.sin
import kotlin.test.assertEquals

/** The same synthetic level curves as web/tools/energy.test.mjs. */
class SectionWatchTest {
    private class Event(val t: Double, val event: String)

    /** Runs `segments` (seconds, bass, amp) at 60 fps with a little wobble; returns the events. */
    private fun run(vararg segments: Triple<Int, Float, Float>): Pair<SectionWatch, List<Event>> {
        val w = SectionWatch()
        val events = mutableListOf<Event>()
        var t = 0.0
        for ((secs, bass, amp) in segments) {
            repeat(secs * 60) {
                val wobble = 1f + 0.15f * sin(t * 2 * PI * 2.1).toFloat()
                val before = w.section
                val drops = w.drops
                w.update(bass * wobble, amp * wobble, 1f / 60)
                if (w.drops != drops) events += Event(t, "drop")
                else if (w.section != before && w.section == SectionWatch.Section.BREAKDOWN) events += Event(t, "breakdown")
                t += 1.0 / 60
            }
        }
        return w to events
    }

    @Test
    fun aBreakdownThenADrop() {
        val (w, events) = run(Triple(40, 0.6f, 0.5f), Triple(16, 0.1f, 0.25f), Triple(10, 0.65f, 0.55f))
        assertEquals(listOf("breakdown", "drop"), events.map { it.event })
        assert(events[0].t in 40.0..48.0) { "breakdown at ${events[0].t}" }
        assert(events[1].t in 56.0..59.0) { "drop at ${events[1].t}" }
        assertEquals(1, w.drops)
    }

    @Test
    fun aShortDipIsNeither() {
        assertEquals(emptyList(), run(Triple(40, 0.6f, 0.5f), Triple(2, 0.1f, 0.2f), Triple(20, 0.6f, 0.5f)).second.map { it.event })
    }

    @Test
    fun aDipThatRecoversTooSoonEndsTheBreakdownWithoutADrop() {
        assertEquals(listOf("breakdown"), run(Triple(40, 0.6f, 0.5f), Triple(6, 0.1f, 0.2f), Triple(20, 0.6f, 0.5f)).second.map { it.event })
    }

    @Test
    fun nothingDuringTheWarmUpOrInSilence() {
        assertEquals(emptyList(), run(Triple(8, 0.6f, 0.5f), Triple(8, 0.05f, 0.05f), Triple(3, 0.6f, 0.5f)).second.map { it.event })
        assertEquals(emptyList(), run(Triple(60, 0.01f, 0.01f), Triple(20, 0.0f, 0.0f)).second.map { it.event })
    }

    @Test
    fun aLongQuietStretchBecomesTheNewNormal() {
        val (w, events) = run(Triple(40, 0.6f, 0.5f), Triple(70, 0.15f, 0.2f), Triple(5, 0.2f, 0.25f))
        assertEquals(listOf("breakdown"), events.map { it.event })
        assertEquals(SectionWatch.Section.NORMAL, w.section)
    }

    @Test
    fun aLouderSongIsAdoptedAsTheNewNormal() {
        val (w, events) = run(Triple(40, 0.2f, 0.2f), Triple(6, 0.6f, 0.5f))
        assertEquals(emptyList(), events.map { it.event })
        assertEquals(w.short, w.long, 0.05f)
    }
}
