package llm.slop.liquidlsd.ui

import llm.slop.liquidlsd.control.KnobLight
import llm.slop.liquidlsd.parameters.MeterType

/**
 * The Clock row's one hardware knob. The ring shows the tempo across the beat tracker's search range, the LED pulses
 * on the beat (brighter on the downbeat of the bar), pushing it taps tempo and turning it does nothing -- the BPM is
 * deliberately not a knob (see [PerformanceClockControls]).
 */
internal class ClockKnobFeed(
    /** Total beats since the clock started, fractional ([llm.slop.liquidlsd.cv.CVRegistry.getSynchronizedTotalBeats]). */
    val totalBeats: () -> Double,
    val bpm: () -> Float,
    val bpmFloor: () -> Float,
    val bpmCeiling: () -> Float,
    val tap: () -> Unit
) {
    fun light(): KnobLight {
        val bpm = bpm()
        val floor = bpmFloor()
        val range = bpmCeiling() - floor
        val beats = totalBeats()
        val phase = beats - Math.floor(beats)
        val bar = Math.floorMod(Math.floor(beats).toLong(), BEATS_PER_BAR.toLong())
        // The flash lasts a fixed time, not a fixed share of the beat, so it stays a tick at 60 BPM.
        val flashing = phase < (FLASH_SECONDS * bpm / 60f).coerceAtMost(MAX_FLASH_BEATS)
        val c = when {
            flashing && bar == 0L -> DOWNBEAT
            flashing -> BEAT
            else -> REST
        }
        return KnobLight(
            value = if (range > 0f) ((bpm - floor) / range).coerceIn(0f, 1f) else 0f,
            r = c[0], g = c[1], b = c[2],
            meterType = MeterType.MONOPOLAR,
            // Devices that can't show a second colour (the stock firmware's hue wheel) pulse the ring's brightness instead.
            ringBrightness = if (flashing) 1f else REST_RING_BRIGHTNESS
        )
    }

    companion object {
        const val BEATS_PER_BAR = 4
        const val FLASH_SECONDS = 0.09f
        const val MAX_FLASH_BEATS = 0.5f
        const val REST_RING_BRIGHTNESS = 0.4f

        // Shared (never mutated) so a frame allocates nothing.
        private val REST = scaled(PerformanceColors.LED_GLOBAL, 0.55f)
        private val BEAT = TangoPalette.PLUM.light
        private val DOWNBEAT = TangoPalette.ORANGE.normal

        private fun scaled(rgb: FloatArray, k: Float) = floatArrayOf(rgb[0] * k, rgb[1] * k, rgb[2] * k)
    }
}
