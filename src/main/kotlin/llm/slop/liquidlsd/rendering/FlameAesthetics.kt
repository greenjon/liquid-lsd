package llm.slop.liquidlsd.rendering

import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Image heuristics for a rendered flame, used by the curator (and any runtime gate) to rank candidates that already
 * passed [FlameQuality]. Pure maths on an RGBA8 readback. The numbers are proxies for "interesting", not truth: the
 * ranges in [score] are starting guesses to be tuned against the curator's contact sheet.
 */
object FlameAesthetics {
    const val HUE_BINS = 12

    class Metrics(
        /** Share of pixels clearly brighter than the background. */
        val coverage: Float,
        /** Shannon entropy of the luma histogram, 0..1 (1 = uniform over all bins). */
        val entropy: Float,
        /** Mean Sobel magnitude at full resolution (luma 0..1 units). */
        val edgeFine: Float,
        /** Mean Sobel magnitude on a 4x downsample: large-scale structure. */
        val edgeCoarse: Float,
        /** 0..1 spread of the hues present (normalised entropy of the hue histogram). */
        val colourSpread: Float,
        /** Share of the coloured pixels that fall in the single most common hue bin. */
        val dominantShare: Float,
        val dominantHue: Int,
        val saturation: Float,
        val meanLuma: Float,
        /** Share of pixels at (nearly) full brightness. */
        val clipped: Float,
        /** Best of mirror-x, mirror-y and half-turn correlation, 0..1. */
        val symmetry: Float,
        /** Share of lit pixels sitting on a featureless plateau (3x3 neighbourhood almost constant): smeared fill, not filaments. */
        val flat: Float
    ) {
        override fun toString() = "cov=%.2f ent=%.2f edge=%.3f/%.3f hue=%.2f(dom %.2f@%d) sat=%.2f lum=%.2f clip=%.2f sym=%.2f flat=%.2f"
            .format(coverage, entropy, edgeFine, edgeCoarse, colourSpread, dominantShare, dominantHue, saturation, meanLuma, clipped, symmetry, flat)
    }

    private const val BG = 0.04f
    private const val HIST = 64

    /** @param rgba w*h*4 bytes, row-major (row order is irrelevant to every metric) */
    fun analyse(rgba: ByteArray, w: Int, h: Int): Metrics {
        require(rgba.size >= w * h * 4 && w >= 8 && h >= 8) { "image too small or short: ${w}x$h, ${rgba.size} bytes" }
        val n = w * h
        val luma = FloatArray(n)
        val hueHist = FloatArray(HUE_BINS)
        var coloured = 0f; var satSum = 0f; var lumSum = 0.0; var covered = 0; var clip = 0
        val lumHist = FloatArray(HIST)
        for (i in 0 until n) {
            val r = (rgba[i * 4].toInt() and 255) / 255f
            val g = (rgba[i * 4 + 1].toInt() and 255) / 255f
            val b = (rgba[i * 4 + 2].toInt() and 255) / 255f
            val l = 0.2126f * r + 0.7152f * g + 0.0722f * b
            luma[i] = l
            lumSum += l
            lumHist[min(HIST - 1, (l * HIST).toInt())]++
            if (l > 0.98f) clip++
            if (l > BG) {
                covered++
                val mx = max(r, max(g, b)); val mn = min(r, min(g, b))
                val sat = if (mx > 0f) (mx - mn) / mx else 0f
                if (l > 0.1f && sat > 0.15f) {
                    val hue = hue(r, g, b, mx, mn)
                    hueHist[min(HUE_BINS - 1, (hue * HUE_BINS).toInt())] += l   // bright pixels weigh more: dim fringe must not set the palette
                    coloured += l; satSum += sat * l
                }
            }
        }
        val coverage = covered.toFloat() / n
        val entropy = normalisedEntropy(lumHist)
        val hueTotal = hueHist.sum()
        val colourSpread = if (hueTotal > 0f) normalisedEntropy(hueHist) else 0f
        var dom = 0
        for (i in 1 until HUE_BINS) if (hueHist[i] > hueHist[dom]) dom = i
        val dominantShare = if (hueTotal > 0f) hueHist[dom] / hueTotal else 1f

        return Metrics(
            coverage = coverage,
            entropy = entropy,
            edgeFine = meanSobel(luma, w, h),
            edgeCoarse = run { val (d, dw, dh) = downsample(luma, w, h, 4); meanSobel(d, dw, dh) },
            colourSpread = colourSpread,
            dominantShare = dominantShare,
            dominantHue = dom,
            saturation = if (coloured > 0f) satSum / coloured else 0f,
            meanLuma = (lumSum / n).toFloat(),
            clipped = clip.toFloat() / n,
            symmetry = symmetry(luma, w, h),
            flat = flatShare(luma, w, h)
        )
    }

    private fun hue(r: Float, g: Float, b: Float, mx: Float, mn: Float): Float {
        val d = mx - mn
        if (d <= 0f) return 0f
        val h = when (mx) {
            r -> ((g - b) / d) % 6f
            g -> (b - r) / d + 2f
            else -> (r - g) / d + 4f
        }
        return ((h / 6f) % 1f + 1f) % 1f
    }

    private fun normalisedEntropy(hist: FloatArray): Float {
        val total = hist.sum()
        if (total <= 0f) return 0f
        var e = 0.0
        for (c in hist) if (c > 0f) { val p = c / total; e -= p * ln(p.toDouble()) }
        return (e / ln(hist.size.toDouble())).toFloat()
    }

    private fun meanSobel(l: FloatArray, w: Int, h: Int): Float {
        var sum = 0.0
        for (y in 1 until h - 1) for (x in 1 until w - 1) {
            val i = y * w + x
            val gx = -l[i - w - 1] - 2 * l[i - 1] - l[i + w - 1] + l[i - w + 1] + 2 * l[i + 1] + l[i + w + 1]
            val gy = -l[i - w - 1] - 2 * l[i - w] - l[i - w + 1] + l[i + w - 1] + 2 * l[i + w] + l[i + w + 1]
            sum += sqrt(gx * gx + gy * gy) / 4.0   // /4: a full black-white step reads ~1
        }
        return (sum / ((w - 2) * (h - 2))).toFloat()
    }

    private fun flatShare(l: FloatArray, w: Int, h: Int): Float {
        var lit = 0; var flat = 0
        for (y in 1 until h - 1) for (x in 1 until w - 1) {
            val i = y * w + x
            if (l[i] <= 0.15f) continue
            lit++
            var lo = l[i]; var hi = l[i]
            for (d in intArrayOf(-w - 1, -w, -w + 1, -1, 1, w - 1, w, w + 1)) { val v = l[i + d]; if (v < lo) lo = v; if (v > hi) hi = v }
            if (hi - lo < 0.02f) flat++
        }
        return if (lit == 0) 1f else flat.toFloat() / lit
    }

    private fun downsample(l: FloatArray, w: Int, h: Int, f: Int): Triple<FloatArray, Int, Int> {
        val dw = w / f; val dh = h / f
        val out = FloatArray(dw * dh)
        for (y in 0 until dh) for (x in 0 until dw) {
            var s = 0f
            for (yy in 0 until f) for (xx in 0 until f) s += l[(y * f + yy) * w + x * f + xx]
            out[y * dw + x] = s / (f * f)
        }
        return Triple(out, dw, dh)
    }

    /** Normalised correlation of the image with its mirror images and half-turn; 0 when the image is flat. */
    private fun symmetry(l: FloatArray, w: Int, h: Int): Float {
        val n = w * h
        var mean = 0.0
        for (v in l) mean += v
        mean /= n
        var varSum = 0.0
        for (v in l) varSum += (v - mean) * (v - mean)
        if (varSum < 1e-9) return 0f
        fun corr(map: (Int, Int) -> Int): Float {
            var c = 0.0
            for (y in 0 until h) for (x in 0 until w) c += (l[y * w + x] - mean) * (l[map(x, y)] - mean)
            return (c / varSum).toFloat().coerceIn(0f, 1f)
        }
        return max(corr { x, y -> y * w + (w - 1 - x) }, max(corr { x, y -> (h - 1 - y) * w + x }, corr { x, y -> (h - 1 - y) * w + (w - 1 - x) }))
    }

    /** Linear 0..1 score of [v] in a trapezoid: 0 below [lo] / above [hi], 1 between [okLo] and [okHi]. */
    internal fun band(v: Float, lo: Float, okLo: Float, okHi: Float, hi: Float): Float = when {
        v <= lo || v >= hi -> 0f
        v < okLo -> (v - lo) / (okLo - lo)
        v <= okHi -> 1f
        else -> (hi - v) / (hi - okHi)
    }

    /**
     * 0..1 desirability: the geometric mean of band scores, so one bad axis (static, a washed-out blob, one muddy
     * colour) drags the whole score down. Ranges are first guesses; tune on the contact sheet.
     */
    fun score(m: Metrics): Float {
        val parts = floatArrayOf(
            band(m.coverage, 0.04f, 0.1f, 0.45f, 0.75f),
            band(m.entropy, 0.25f, 0.45f, 0.8f, 0.95f),
            band(m.edgeFine, 0.004f, 0.015f, 0.12f, 0.25f),
            band(m.edgeCoarse, 0.002f, 0.008f, 0.12f, 0.25f),   // busy, detailed flames were kept in the owner's first review (2026-10-12)
            band(m.colourSpread, 0.0f, 0.2f, 0.7f, 0.95f),   // a full rainbow is as muddy as a single colour
            band(m.dominantShare, -1f, -0.5f, 0.7f, 0.97f),
            band(m.meanLuma, 0.02f, 0.06f, 0.3f, 0.6f),
            band(m.flat, -1f, -0.5f, 0.15f, 0.6f),            // flat, low-detail images were rejected most
            band(m.symmetry, -1f, -0.5f, 0.55f, 0.9f),        // so were the strongly symmetric ones
            band(m.clipped, -1f, -0.5f, 0.05f, 0.4f)
        )
        var logSum = 0.0
        for (p in parts) logSum += ln(max(p, 1e-3f).toDouble())
        return kotlin.math.exp(logSum / parts.size).toFloat()
    }
}
