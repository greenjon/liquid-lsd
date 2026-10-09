package llm.slop.liquidlsd.ui

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class ClockKnobFeedTest {
    private var beats = 0.0
    private var bpm = 120f
    private val feed = ClockKnobFeed({ beats }, { bpm }, { 40f }, { 200f }, {})

    private fun rgb(beats: Double): FloatArray {
        this.beats = beats
        val l = feed.light()
        return floatArrayOf(l.r, l.g, l.b)
    }

    @Test
    fun ringShowsTempoAcrossTheSearchRange() {
        bpm = 40f; assertEquals(0f, feed.light().value)
        bpm = 120f; assertEquals(0.5f, feed.light().value)
        bpm = 240f; assertEquals(1f, feed.light().value)
    }

    @Test
    fun downbeatFlashesOrangeOtherBeatsFlashPlumAndTheRestIsDim() {
        assertContentEquals(TangoPalette.ORANGE.normal, rgb(4.01))
        assertContentEquals(TangoPalette.PLUM.light, rgb(5.01))
        val rest = rgb(5.4)
        assertContentEquals(rest, rgb(6.4))
        assert(rest[0] < TangoPalette.PLUM.light[0])
    }

    @Test
    fun theFlashIsAShortFixedTimeNotAFixedShareOfTheBeat() {
        bpm = 60f; assertContentEquals(rgb(1.4), rgb(2.4))     // 90 ms is under a tenth of a beat at 60 BPM
        bpm = 200f; assertContentEquals(TangoPalette.PLUM.light, rgb(1.2)) // ... but 0.3 beat at 200
    }

    @Test
    fun restingRingIsDimmedOnlyBetweenFlashes() {
        beats = 1.01; assertEquals(1f, feed.light().ringBrightness)
        beats = 1.6; assertEquals(ClockKnobFeed.REST_RING_BRIGHTNESS, feed.light().ringBrightness)
    }
}
