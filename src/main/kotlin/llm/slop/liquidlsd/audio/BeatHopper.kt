package llm.slop.liquidlsd.audio

import kotlin.math.max
import kotlin.math.sqrt

/**
 * Feeds the beat tracker on a fixed grid of [BeatTrackerEngine.fftSize] samples, whatever the audio buffer size. The
 * tracker counts its input in those units to turn lags into tempo, so one call per buffer read a 128 BPM kick as 200 BPM
 * at 64 frames. The level and the rise in level are measured over the same grid, so a buffer of that size gives
 * exactly what it always did. [bassFlux], [onsetStrength] and [bass] are for the downbeat detector: the rises completed
 * during the last call to [process], and the latest level. No allocation; audio thread only.
 */
class BeatHopper(private val detector: BeatDetector) {
    private var count = 0
    private var ampSum = 0f
    private var bassSum = 0f
    private var midSum = 0f
    private var highSum = 0f
    private var prevBass = 0f
    private var prevMid = 0f
    private var prevHigh = 0f

    var bpm = detector.currentBpm
        private set
    var bassFlux = 0f
        private set
    var onsetStrength = 0f
        private set
    var bass = 0f
        private set

    /** Feeds a block of the full mix and the three bands. Returns the current tempo. */
    fun process(ampIn: FloatArray, bassIn: FloatArray, midIn: FloatArray, highIn: FloatArray, frames: Int, sampleRate: Float): Float {
        val hop = detector.engine.fftSize
        bassFlux = 0f
        onsetStrength = 0f
        for (i in 0 until frames) {
            ampSum += ampIn[i] * ampIn[i]
            bassSum += bassIn[i] * bassIn[i]
            midSum += midIn[i] * midIn[i]
            highSum += highIn[i] * highIn[i]
            if (++count >= hop) {
                val amp = sqrt(ampSum / hop)
                val b = sqrt(bassSum / hop)
                val m = sqrt(midSum / hop)
                val h = sqrt(highSum / hop)
                val rb = max(0f, b - prevBass)
                val onset = rb * 2.0f + max(0f, m - prevMid) * 0.8f + max(0f, h - prevHigh) * 0.3f
                prevBass = b
                prevMid = m
                prevHigh = h
                bpm = detector.processBlock(amp, b, m, h, sampleRate, hop, onset)
                bassFlux += rb
                onsetStrength += onset
                bass = b
                count = 0
                ampSum = 0f
                bassSum = 0f
                midSum = 0f
                highSum = 0f
            }
        }
        return bpm
    }

    fun reset() {
        count = 0
        ampSum = 0f
        bassSum = 0f
        midSum = 0f
        highSum = 0f
        prevBass = 0f
        prevMid = 0f
        prevHigh = 0f
        bassFlux = 0f
        onsetStrength = 0f
        bass = 0f
        bpm = detector.currentBpm
    }
}
