package llm.slop.liquidlsd.rendering

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Quality gate for candidate genomes (plan phase 5): judges the cloud of points that a short chaos-game run settled on,
 * so the sampler never hands the morph a blank, exploded or degenerate flame. Pure maths on the readback of
 * [FlameProbe]; the same numbers frame the camera on the attractor.
 */
object FlameQuality {
    enum class Verdict { OK, DIVERGED, COLLAPSED, LINE, EXPLODED, FOG }

    /** [zoom], [centerX], [centerY] are the camera that frames the attractor (valid whatever the verdict, when there are points). */
    class Result(val verdict: Verdict, val zoom: Float, val centerX: Float, val centerY: Float, val extent: Float, val occupancy: Float) {
        val ok get() = verdict == Verdict.OK
        override fun toString() = "$verdict extent=%.2f occupancy=%.2f".format(extent, occupancy)
    }

    // Thresholds; calibrated against FlameSampler.random in FlameProbeTest.
    private const val MIN_VALID = 0.85f           // share of points that survived the run without being reseeded
    private const val MIN_EXTENT = 0.02f          // robust width below which the attractor is a dot
    private const val MAX_EXTENT = 40f            // above this Zoom's lower limit cannot frame it
    private const val MIN_AXIS_RATIO = 0.04f      // sqrt(minor / major eigenvalue) of the covariance: below = a line
    private const val GRID = 24
    private const val MIN_OCCUPANCY = 0.045f      // share of grid cells holding a point: below = a handful of dots
    private const val FOG_OCCUPANCY = 0.92f       // ...and when nearly every cell is hit ...
    private const val FOG_MAX_CV = 0.6f           // ... with this little spread in the counts (std / mean; a uniform cloud is ~0.2) it is fog
    private const val FIT = 0.85f                 // attractor fills this much of the view
    private const val ASPECT = 16f / 9f

    /**
     * @param state `count` points as RGBA floats (x, y, colour, age); age counts iterations since the point was seeded
     * @param iterations how many iterations the run lasted: a point with a lower age was reseeded, i.e. it diverged
     */
    fun analyse(state: FloatArray, count: Int, iterations: Int): Result {
        val xs = FloatArray(count); val ys = FloatArray(count)
        var n = 0
        for (i in 0 until count) {
            val x = state[i * 4]; val y = state[i * 4 + 1]
            if (state[i * 4 + 3] >= iterations - 0.5f && x.isFinite() && y.isFinite() && abs(x) < 1e4f && abs(y) < 1e4f) {
                xs[n] = x; ys[n] = y; n++
            }
        }
        if (n < 16 || n < MIN_VALID * count) return Result(Verdict.DIVERGED, 0.45f, 0f, 0f, Float.POSITIVE_INFINITY, 0f)

        val sx = xs.copyOf(n).also { it.sort() }
        val sy = ys.copyOf(n).also { it.sort() }
        val x0 = sx[(n * 0.02f).toInt()]; val x1 = sx[min(n - 1, (n * 0.98f).toInt())]
        val y0 = sy[(n * 0.02f).toInt()]; val y1 = sy[min(n - 1, (n * 0.98f).toInt())]
        val wx = x1 - x0; val wy = y1 - y0
        val extent = max(wx, wy)
        val cx = (x0 + x1) / 2f; val cy = (y0 + y1) / 2f
        val half = max(wy / 2f, wx / 2f / ASPECT).coerceAtLeast(1e-3f)
        val zoom = (FIT / half).coerceIn(0.01f, 32f)
        fun result(v: Verdict, occupancy: Float = 0f) = Result(v, zoom, cx.coerceIn(-16f, 16f), cy.coerceIn(-16f, 16f), extent, occupancy)

        if (extent < MIN_EXTENT) return result(Verdict.COLLAPSED)
        if (extent > MAX_EXTENT) return result(Verdict.EXPLODED)

        // covariance of the points inside the robust box: a thin diagonal or axis-aligned band is a line
        var mx = 0.0; var my = 0.0; var m = 0
        for (i in 0 until n) if (xs[i] in x0..x1 && ys[i] in y0..y1) { mx += xs[i]; my += ys[i]; m++ }
        mx /= m; my /= m
        var sxx = 0.0; var syy = 0.0; var sxy = 0.0
        for (i in 0 until n) if (xs[i] in x0..x1 && ys[i] in y0..y1) {
            val dx = xs[i] - mx; val dy = ys[i] - my
            sxx += dx * dx; syy += dy * dy; sxy += dx * dy
        }
        val tr = sxx + syy
        val disc = sqrt(max(0.0, (sxx - syy) * (sxx - syy) / 4.0 + sxy * sxy))
        val l1 = tr / 2.0 + disc; val l2 = max(0.0, tr / 2.0 - disc)
        if (l1 <= 0.0 || sqrt(l2 / l1) < MIN_AXIS_RATIO) return result(Verdict.LINE)

        // occupancy and evenness on a coarse grid over the (padded) robust box
        val padX = wx * 0.05f; val padY = wy * 0.05f
        val gx0 = x0 - padX; val gy0 = y0 - padY
        val gw = wx + 2 * padX; val gh = wy + 2 * padY
        val cells = IntArray(GRID * GRID)
        var inside = 0
        for (i in 0 until n) {
            val gx = ((xs[i] - gx0) / gw * GRID).toInt(); val gy = ((ys[i] - gy0) / gh * GRID).toInt()
            if (gx in 0 until GRID && gy in 0 until GRID) { cells[gy * GRID + gx]++; inside++ }
        }
        val occupied = cells.count { it > 0 }
        val occupancy = occupied.toFloat() / cells.size
        if (occupancy < MIN_OCCUPANCY) return result(Verdict.COLLAPSED, occupancy)
        if (occupancy > FOG_OCCUPANCY) {
            val mean = inside.toDouble() / cells.size
            val sd = sqrt(cells.sumOf { (it - mean) * (it - mean) } / cells.size)
            if (sd / mean < FOG_MAX_CV) return result(Verdict.FOG, occupancy)
        }
        return result(Verdict.OK, occupancy)
    }
}
