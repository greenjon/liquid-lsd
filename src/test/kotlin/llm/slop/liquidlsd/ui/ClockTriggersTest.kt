package llm.slop.liquidlsd.ui

import io.mockk.every
import io.mockk.mockk
import llm.slop.liquidlsd.parameters.ModulatableParameter
import llm.slop.liquidlsd.rendering.Mixer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** The Clock row's trigger buttons stand for the very parameters the MST > CTRL rows list, under the same keys. */
class ClockTriggersTest {
    private fun p() = ModulatableParameter(0f, minClamp = 0f, maxClamp = 1f)

    // A Mixer needs a GL context to construct, so the trigger parameters are stubbed one by one.
    private val queuePrev = p(); private val queueNext = p()
    private val bgQueuePrev = p(); private val bgQueueNext = p()
    private val transQueuePrev = p(); private val transQueueNext = p()
    private val randDeckA = p(); private val randDeckB = p(); private val randDeckBG = p(); private val randDeckPV = p(); private val randAll = p()
    private val mixer = mockk<Mixer>(relaxed = true).also {
        every { it.queuePrev } returns queuePrev; every { it.queueNext } returns queueNext
        every { it.bgQueuePrev } returns bgQueuePrev; every { it.bgQueueNext } returns bgQueueNext
        every { it.transQueuePrev } returns transQueuePrev; every { it.transQueueNext } returns transQueueNext
        every { it.randDeckA } returns randDeckA; every { it.randDeckB } returns randDeckB
        every { it.randDeckBG } returns randDeckBG; every { it.randDeckPV } returns randDeckPV; every { it.randAll } returns randAll
    }

    @Test
    fun everyButtonIsTheSameParameterUnderTheSameKeyAsItsCtrlRow() {
        // The CTRL tab draws mixer.<name> under "Mixer/<name>" (see ParametersTabs.drawMixerCtrlTab).
        val expected = listOf(
            "Mixer/queuePrev" to queuePrev, "Mixer/queueNext" to queueNext,
            "Mixer/bgQueuePrev" to bgQueuePrev, "Mixer/bgQueueNext" to bgQueueNext,
            "Mixer/transQueuePrev" to transQueuePrev, "Mixer/transQueueNext" to transQueueNext,
            "Mixer/randDeckA" to randDeckA, "Mixer/randDeckB" to randDeckB, "Mixer/randDeckBG" to randDeckBG,
            "Mixer/randDeckPV" to randDeckPV, "Mixer/randAll" to randAll
        )
        val buttons = PerformanceClockTriggers.boundParameters(mixer)
        assertEquals(expected.map { it.first }, buttons.map { it.first })
        for (i in expected.indices) assertSame(expected[i].second, buttons[i].second, expected[i].first)
    }

    @Test
    fun tapTempoIsNotRepeatedAmongTheButtons() {
        val keys = PerformanceClockTriggers.boundParameters(mixer).map { it.first }
        assertTrue("Mixer/tapTempo" !in keys, "the TAP button already covers it")
    }

    @Test
    fun linesAreSizedForTheirButtons() {
        // Queue line: caption + three (label + prev + next) groups; randomize line: caption + five buttons.
        assertTrue(PerformanceClockTriggers.QUEUE_LINE_W > PerformanceClockTriggers.RAND_LINE_W)
        assertTrue(PerformanceClockControls.TEMPO_BLOCK_W + PerformanceClockTriggers.LEFT_GAP + PerformanceClockTriggers.QUEUE_LINE_W < 700f)
    }
}
