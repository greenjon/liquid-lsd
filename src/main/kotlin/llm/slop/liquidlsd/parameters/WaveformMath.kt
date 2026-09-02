package llm.slop.liquidlsd.parameters

fun calculateWaveform(waveform: Waveform, phase: Double, slope: Float): Float {
    return when (waveform) {
        Waveform.SINE -> kotlin.math.sin(phase * 2.0 * Math.PI).toFloat()
        Waveform.TRIANGLE -> {
            val s = slope.toDouble()
            val raw = if (s <= 0.001) (1.0 - phase).toFloat()
            else if (s >= 0.999) phase.toFloat()
            else if (phase < s) (phase / s).toFloat()
            else ((1.0 - phase) / (1.0 - s)).toFloat()
            raw * 2.0f - 1.0f
        }
        Waveform.SQUARE -> if (phase < slope) 1.0f else -1.0f
        Waveform.RANDOM -> 0f
    }
}

fun calculateAdvancedLFO(
    phase: Double,
    morph: Float,
    hold: Float,
    slope: Float,
    waveform: Waveform = Waveform.SINE
): Float {
    val safeHold = hold.coerceIn(0.0f, 0.999f)
    val divisor = (1.0f - safeHold).coerceAtLeast(0.0001f)

    val shaped = if (waveform == Waveform.SQUARE) {
        val duty = slope.coerceIn(0.001f, 0.999f)
        val vTh = 1.0f - 2.0f * duty
        val shiftedPhase = (phase + (1.0f - duty) * 0.5) % 1.0
        val posPhase = if (shiftedPhase < 0.0) shiftedPhase + 1.0 else shiftedPhase

        val triRaw = if (posPhase < 0.5) {
            (posPhase / 0.5).toFloat()
        } else {
            ((1.0 - posPhase) / 0.5).toFloat()
        }
        val tri = triRaw * 2.0f - 1.0f
        ((tri - vTh) / divisor).coerceIn(-1.0f, 1.0f)
    } else {
        val s = slope.coerceIn(0.0001f, 0.9999f)
        val triRaw = if (phase < s) {
            (phase / s).toFloat()
        } else {
            ((1.0 - phase) / (1.0 - s)).toFloat()
        }
        val tri = triRaw * 2.0f - 1.0f
        (tri / divisor).coerceIn(-1.0f, 1.0f)
    }

    val k = 1.5f + (15.0f - 1.5f) * morph
    val maxVal = kotlin.math.log(kotlin.math.cosh(k.toDouble()), Math.E).toFloat() / k

    return if (shaped >= 0f) {
        val u = 1.0f - shaped
        val smoothedU = kotlin.math.log(kotlin.math.cosh((k * u).toDouble()), Math.E).toFloat() / k
        1.0f - (smoothedU / maxVal)
    } else {
        val u = 1.0f + shaped
        val smoothedU = kotlin.math.log(kotlin.math.cosh((k * u).toDouble()), Math.E).toFloat() / k
        -1.0f + (smoothedU / maxVal)
    }
}
