package llm.slop.liquidlsd.cv

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class SectionDropTest {
    @Test
    fun aDropIsDatedBackToTheNearestBeat() {
        // Called at beat 33.4, 1.5 s late at 120 bpm (3 beats): it landed on beat 30.
        assertEquals(30.0, SectionDrop.dropBeat(33.4, 1.5f, 120f))
    }

    @Test
    fun aDropIsNeverDatedIntoTheFuture() {
        assertEquals(33.6, SectionDrop.dropBeat(33.6, 0f, 120f))
    }

    @Test
    fun theEnvelopeFallsFromOneToZeroOverItsLength() {
        assertEquals(1f, SectionDrop.envelope(0.0, 16.0))
        assertEquals(0.25f, SectionDrop.envelope(8.0, 16.0))
        assertEquals(0f, SectionDrop.envelope(16.0, 16.0))
        assertEquals(0f, SectionDrop.envelope(-1.0, 16.0))
    }

    @Test
    fun aReportedDropStartsTheEnvelopeAndANewEngineDoesNot() {
        val d = SectionDrop()
        d.update(10.0, 0.0)
        assertEquals(0f, d.value)
        d.report(1, 0f)
        d.update(12.0, 0.0)
        assertEquals(1f, d.value)
        d.update(12.0 + 2 * CVRegistry.beatsPerBar, 0.0)
        assertEquals(0.25f, d.value, 1e-4f)
        d.report(0, 0f) // the audio engine started over
        d.update(100.0, 0.0)
        d.report(1, 0f)
        d.update(101.0, 0.0)
        assertEquals(1f, d.value)
    }
}
