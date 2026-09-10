package llm.slop.liquidlsd.link

import java.util.concurrent.atomic.AtomicReference
import kotlin.test.*

class BTrackToLinkDampingTest {

    private lateinit var filter: BTrackToLinkDamping
    private val committedBpm = AtomicReference<Double?>(null)
    private val alignedBeat = AtomicReference<Double?>(null)

    @BeforeTest
    fun setUp() {
        committedBpm.set(null)
        alignedBeat.set(null)

        val sink = object : AudioTempoEventSink {
            override fun onTempoCommitted(bpm: Double) {
                committedBpm.set(bpm)
            }

            override fun onBeatAligned(beatTime: Double, microsecondTimestamp: Long, quantum: Double) {
                alignedBeat.set(beatTime)
            }
        }

        filter = BTrackToLinkDamping(
            minBpm = 60.0,
            maxBpm = 200.0,
            medianWindowSize = 5,
            emaAlpha = 0.20,
            hysteresisThresholdBpm = 0.5,
            sustainedBeatsThreshold = 4,
            phaseErrorThresholdBeats = 0.5,
            quantum = 4.0,
            downstreamSink = sink
        )
        filter.reset(120.0)
    }

    @Test
    fun testSanityBoundsFiltering() {
        // Out-of-bounds BPM samples should be silently discarded
        filter.processRawBpm(45.0)  // Too slow
        filter.processRawBpm(220.0) // Too fast
        filter.processRawBpm(Double.NaN)

        assertEquals(120.0, filter.lastPublishedBpm)
        assertNull(committedBpm.get())
    }

    @Test
    fun testNoiseSuppressionWithMedianAndEma() {
        // Feed micro-fluctuations around 120 BPM (< 0.5 BPM hysteresis threshold)
        val noisySamples = doubleArrayOf(120.0, 120.3, 119.7, 120.2, 119.8, 120.1, 119.9, 120.0)
        for (sample in noisySamples) {
            filter.processRawBpm(sample)
        }

        // Current stabilized BPM should stay very close to 120.0
        assertEquals(120.0, filter.currentStabilizedBpm, 0.3)
        // No outbound tempo change committed because variation stayed within 0.5 BPM
        assertNull(committedBpm.get())
    }

    @Test
    fun testHysteresisAndSustainedBeatsRequirement() {
        assertEquals(120.0, filter.lastPublishedBpm)

        // Step change to 125.0 BPM (delta = 5.0 > 0.5 threshold)
        // Feed 1st, 2nd, and 3rd beats -> should NOT commit yet
        filter.processRawBpm(125.0)
        assertNull(committedBpm.get())

        filter.processRawBpm(125.0)
        assertNull(committedBpm.get())

        filter.processRawBpm(125.0)
        assertNull(committedBpm.get())

        // 4th sustained beat -> SHOULD commit outbound tempo change
        filter.processRawBpm(125.0)
        assertNotNull(committedBpm.get())
        assertTrue(filter.lastPublishedBpm > 120.5)
    }

    @Test
    fun testInterruptedDivergenceResetsHysteresisCount() {
        assertEquals(120.0, filter.lastPublishedBpm)

        // 2 beats at 125.0 BPM (delta = 5.0 >= 0.5 threshold)
        filter.processRawBpm(125.0)
        filter.processRawBpm(125.0)
        assertNull(committedBpm.get())

        // Interrupted by returning to 120.0 BPM for 3 beats (shifts rolling median back to 120.0)
        repeat(3) {
            filter.processRawBpm(120.0)
        }

        // 2 more beats at 125.0 BPM (total 4 beats at 125.0, but interrupted and not consecutive)
        filter.processRawBpm(125.0)
        filter.processRawBpm(125.0)

        // Should NOT have committed because count was reset when tempo returned to 120.0
        assertNull(committedBpm.get())
    }

    @Test
    fun testPhaseRealignmentThresholding() {
        // AbletonLinkEngine NoOp mock initialized at tempo 120 BPM
        AbletonLinkEngine.init(120.0)
        AbletonLinkEngine.setEnabled(true)

        val nowUs = System.nanoTime() / 1000
        val expectedBeat = AbletonLinkEngine.getBeatAtTime(nowUs, 4.0)

        // 1. Minor phase error (< 0.5 beats) -> should NOT align
        val minorBeat = expectedBeat + 0.2
        filter.processBeatOnset(minorBeat, nowUs, 4.0)
        assertNull(alignedBeat.get())

        // 2. Major phase error (>= 0.5 beats, e.g. +0.8 beats) -> SHOULD align
        val majorBeat = expectedBeat + 0.8
        filter.processBeatOnset(majorBeat, nowUs, 4.0)
        assertEquals(majorBeat, alignedBeat.get())
    }

    @Test
    fun testReset() {
        filter.processRawBpm(125.0)
        filter.processRawBpm(125.0)

        filter.reset(130.0)

        assertEquals(130.0, filter.lastPublishedBpm)
        assertEquals(130.0, filter.currentStabilizedBpm)
    }
}
