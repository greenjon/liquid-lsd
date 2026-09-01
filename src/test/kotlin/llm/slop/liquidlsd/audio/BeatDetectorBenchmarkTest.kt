package llm.slop.liquidlsd.audio

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

class BeatDetectorBenchmarkTest {

    private fun simulateDrumTrack(
        detector: BeatDetector,
        targetBpm: Float,
        durationSeconds: Float = 15f,
        sampleRate: Float = 44100f,
        nframes: Int = 512,
        hasBreakdown: Boolean = false
    ): BenchmarkResult {
        val totalBlocks = ((durationSeconds * sampleRate) / nframes).toInt()
        val secPerBlock = nframes / sampleRate
        val secPerBeat = 60.0f / targetBpm

        var convergedBlock = -1
        var lockCount = 0
        var totalPostConvergenceBlocks = 0

        var prevKickHit = false
        var prevSnareHit = false
        var lastEstBpm = targetBpm

        for (block in 0 until totalBlocks) {
            val timeSec = block * secPerBlock
            val beatPosition = (timeSec / secPerBeat)
            val measureBeat = beatPosition % 4.0f
            
            val inBreakdown = hasBreakdown && (beatPosition >= 8f && beatPosition < 12f)

            var bass = 0.01f
            var mid = 0.01f
            var high = 0.01f
            var onset = 0.001f

            if (!inBreakdown) {
                val isKickHit = (measureBeat % 2.0f) < 0.15f
                val isSnareHit = (abs(measureBeat - 1.0f) < 0.15f) || (abs(measureBeat - 3.0f) < 0.15f)
                val subBeat8th = (timeSec / (secPerBeat / 2.0f)) % 1.0f
                val is8thHit = subBeat8th < 0.15f

                if (isKickHit && !prevKickHit) {
                    bass = 0.9f
                    onset += 1.0f
                }
                if (isSnareHit && !prevSnareHit) {
                    mid = 0.8f
                    onset += 0.7f
                }
                if (is8thHit) {
                    high = 0.3f
                }

                prevKickHit = isKickHit
                prevSnareHit = isSnareHit
            }

            val estBpm = detector.processBlock(onset, bass, mid, high, sampleRate, nframes, onset)
            lastEstBpm = estBpm

            // Evaluate convergence (< 1.5 BPM error)
            val error = abs(estBpm - targetBpm)
            if (error < 1.5f) {
                if (convergedBlock < 0) {
                    convergedBlock = block
                }
                lockCount++
            }
            if (convergedBlock >= 0) {
                totalPostConvergenceBlocks++
            }
        }

        val convergenceTimeSec = if (convergedBlock >= 0) convergedBlock * secPerBlock else durationSeconds
        val lockRatio = if (totalBlocks > 0) lockCount.toFloat() / totalBlocks.toFloat() else 0f

        return BenchmarkResult(
            targetBpm = targetBpm,
            convergenceTimeSec = convergenceTimeSec,
            lockRatio = lockRatio,
            finalBpmEstimate = lastEstBpm
        )
    }

    data class BenchmarkResult(
        val targetBpm: Float,
        val convergenceTimeSec: Float,
        val lockRatio: Float,
        val finalBpmEstimate: Float
    )

    @Test
    fun testBenchmark120BpmFourOnTheFloor() {
        val detector = BeatDetector()
        detector.applyPreset(BeatDetectionSettings.highAccuracy())

        val result = simulateDrumTrack(detector, 120.0f, durationSeconds = 12f)
        
        assertTrue(
            result.convergenceTimeSec <= 2.5f,
            "120 BPM convergence should occur within 2.5 seconds (actual: ${result.convergenceTimeSec}s)"
        )
        assertTrue(
            abs(result.finalBpmEstimate - 120.0f) <= 1.5f,
            "Final estimate should be within 1.5 BPM of 120.0 (actual: ${result.finalBpmEstimate})"
        )
    }

    @Test
    fun testBenchmark128BpmHouseTrack() {
        val detector = BeatDetector()
        detector.applyPreset(BeatDetectionSettings.highAccuracy())

        val result = simulateDrumTrack(detector, 128.0f, durationSeconds = 12f)
        
        assertTrue(
            result.convergenceTimeSec <= 2.5f,
            "128 BPM convergence should occur within 2.5 seconds (actual: ${result.convergenceTimeSec}s)"
        )
        assertTrue(
            abs(result.finalBpmEstimate - 128.0f) <= 1.5f,
            "Final estimate should be within 1.5 BPM of 128.0 (actual: ${result.finalBpmEstimate})"
        )
    }

    @Test
    fun testBenchmark140BpmDubstepTrack() {
        val detector = BeatDetector()
        detector.applyPreset(BeatDetectionSettings.highAccuracy())

        val result = simulateDrumTrack(detector, 140.0f, durationSeconds = 12f)
        
        assertTrue(
            result.convergenceTimeSec <= 2.5f,
            "140 BPM convergence should occur within 2.5 seconds (actual: ${result.convergenceTimeSec}s)"
        )
        assertTrue(
            abs(result.finalBpmEstimate - 140.0f) <= 1.5f,
            "Final estimate should be within 1.5 BPM of 140.0 (actual: ${result.finalBpmEstimate})"
        )
    }

    @Test
    fun testBenchmark100BpmSlowGroove() {
        val detector = BeatDetector()
        detector.applyPreset(BeatDetectionSettings.highAccuracy())

        val result = simulateDrumTrack(detector, 100.0f, durationSeconds = 12f)
        
        assertTrue(
            result.convergenceTimeSec <= 3.0f,
            "100 BPM convergence should occur within 3.0 seconds (actual: ${result.convergenceTimeSec}s)"
        )
        assertTrue(
            abs(result.finalBpmEstimate - 100.0f) <= 1.5f,
            "Final estimate should be within 1.5 BPM of 100.0 (actual: ${result.finalBpmEstimate})"
        )
    }

    @Test
    fun testPllFlywheelBreakdownStability() {
        val detector = BeatDetector()
        detector.applyPreset(BeatDetectionSettings.highAccuracy())

        // Run track with a 4-beat breakdown (silence/fill) at 120 BPM
        val result120 = simulateDrumTrack(detector, 120.0f, durationSeconds = 15f, hasBreakdown = true)
        assertTrue(
            abs(result120.finalBpmEstimate - 120.0f) <= 2.0f,
            "BPM estimate should remain steady during/after drum breakdown at 120 BPM (actual: ${result120.finalBpmEstimate})"
        )

        // Reset and run track with an extended breakdown at 128 BPM
        val detector128 = BeatDetector()
        detector128.applyPreset(BeatDetectionSettings.highAccuracy())
        val result128 = simulateDrumTrack(detector128, 128.0f, durationSeconds = 15f, hasBreakdown = true)
        assertTrue(
            abs(result128.finalBpmEstimate - 128.0f) <= 2.0f,
            "BPM estimate should remain steady during/after drum breakdown at 128 BPM (actual: ${result128.finalBpmEstimate})"
        )

        // Reset and run track with an extended breakdown at 140 BPM
        val detector140 = BeatDetector()
        detector140.applyPreset(BeatDetectionSettings.highAccuracy())
        val result140 = simulateDrumTrack(detector140, 140.0f, durationSeconds = 15f, hasBreakdown = true)
        assertTrue(
            abs(result140.finalBpmEstimate - 140.0f) <= 2.0f,
            "BPM estimate should remain steady during/after drum breakdown at 140 BPM (actual: ${result140.finalBpmEstimate})"
        )
    }
}
