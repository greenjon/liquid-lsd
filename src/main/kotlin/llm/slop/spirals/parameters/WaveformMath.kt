package llm.slop.spirals.parameters

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

fun calculateAdvancedLFO(phase: Double, morph: Float, hold: Float, slope: Float): Float {
    val s = slope.coerceIn(0.0001f, 0.9999f)
    val triRaw = if (phase < s) {
        (phase / s).toFloat()
    } else {
        ((1.0 - phase) / (1.0 - s)).toFloat()
    }
    var tri = triRaw * 2.0f - 1.0f
    
    val safeHold = hold.coerceIn(0.0f, 0.99f)
    tri = (tri / (1.0f - safeHold)).coerceIn(-1.0f, 1.0f)
    
    val k = 1.5f + (15.0f - 1.5f) * morph
    val maxVal = kotlin.math.log(kotlin.math.cosh(k.toDouble()), Math.E).toFloat() / k
    
    return if (tri >= 0f) {
        val u = 1.0f - tri
        val smoothedU = kotlin.math.log(kotlin.math.cosh((k * u).toDouble()), Math.E).toFloat() / k
        1.0f - (smoothedU / maxVal)
    } else {
        val u = 1.0f + tri
        val smoothedU = kotlin.math.log(kotlin.math.cosh((k * u).toDouble()), Math.E).toFloat() / k
        -1.0f + (smoothedU / maxVal)
    }
}
