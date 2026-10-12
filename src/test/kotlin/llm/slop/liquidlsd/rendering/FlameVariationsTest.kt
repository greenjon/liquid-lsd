package llm.slop.liquidlsd.rendering

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.round
import kotlin.math.max
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.cosh
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sinh
import kotlin.math.sqrt
import kotlin.math.tan
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Pointwise checks of the paper's deterministic variations (Draves & Reckase, Appendix A) against a Kotlin mirror of
 * the GLSL formulas, using hand-derived input/output pairs. The mirror cannot prove the shader compiles or matches;
 * [glslCasesMatchTheTable] only guards the table/shader bookkeeping, and the shader needs a visual check in the app.
 */
class FlameVariationsTest {
    private val pi = PI

    /**
     * Mirror of `vari()`; p = parameters in real units, m = (a, b, d, e), t = (c, f). [w] is the weight: only the
     * variations whose spec entry says "non-standard weight" use it (they return the finished output); every other
     * mirror returns the unweighted value, which is the output for w = 1. [u] stands in for every random draw.
     */
    private fun v(name: String, x: Double, y: Double, p: DoubleArray = DoubleArray(4), m: DoubleArray = DoubleArray(4), t: DoubleArray = DoubleArray(2), w: Double = 1.0, u: Double = 0.5): DoubleArray {
        val r = sqrt(x * x + y * y); val th = atan2(x, y); val ph = atan2(y, x)
        return when (name) {
            "linear" -> doubleArrayOf(x, y)
            "sinusoidal" -> doubleArrayOf(sin(x), sin(y))
            "spherical" -> doubleArrayOf(x / (r * r), y / (r * r))
            "swirl" -> { val s = sin(r * r); val c = cos(r * r); doubleArrayOf(x * s - y * c, x * c + y * s) }
            "horseshoe" -> doubleArrayOf((x - y) * (x + y) / r, 2 * x * y / r)
            "polar" -> doubleArrayOf(th / pi, r - 1)
            "handkerchief" -> doubleArrayOf(r * sin(th + r), r * cos(th - r))
            "heart" -> doubleArrayOf(r * sin(th * r), -r * cos(th * r))
            "disc" -> doubleArrayOf(th / pi * sin(pi * r), th / pi * cos(pi * r))
            "spiral" -> doubleArrayOf((cos(th) + sin(r)) / r, (sin(th) - cos(r)) / r)
            "hyperbolic" -> doubleArrayOf(sin(th) / r, r * cos(th))
            "diamond" -> doubleArrayOf(sin(th) * cos(r), cos(th) * sin(r))
            "ex" -> { val a = sin(th + r).pow(3); val b = cos(th - r).pow(3); doubleArrayOf(r * (a + b), r * (a - b)) }
            "bent" -> doubleArrayOf(if (x < 0) 2 * x else x, if (y < 0) y / 2 else y)
            "waves" -> doubleArrayOf(x + m[1] * sin(y / (t[0] * t[0])), y + m[3] * sin(x / (t[1] * t[1])))
            "fisheye" -> doubleArrayOf(2 / (r + 1) * y, 2 / (r + 1) * x)
            "popcorn" -> doubleArrayOf(x + t[0] * sin(tan(3 * y)), y + t[1] * sin(tan(3 * x)))
            "exponential" -> doubleArrayOf(exp(x - 1) * cos(pi * y), exp(x - 1) * sin(pi * y))
            "power" -> doubleArrayOf(r.pow(sin(th)) * cos(th), r.pow(sin(th)) * sin(th))
            "cosine" -> doubleArrayOf(cos(pi * x) * kotlin.math.cosh(y), -sin(pi * x) * kotlin.math.sinh(y))
            "rings" -> { val c2 = t[0] * t[0]; val k = ((r + c2) - 2 * c2 * floor((r + c2) / (2 * c2))) - c2 + r * (1 - c2); doubleArrayOf(k * cos(th), k * sin(th)) }
            "fan" -> {
                val c2 = t[0] * t[0]; val tt = pi * c2
                val md = (th + t[1]) - tt * floor((th + t[1]) / tt)
                val a = if (md > tt / 2) th - tt / 2 else th + tt / 2
                doubleArrayOf(r * cos(a), r * sin(a))
            }
            "blob" -> { val k = r * (p[1] + (p[0] - p[1]) / 2 * (sin(p[2] * th) + 1)); doubleArrayOf(k * sin(th), k * cos(th)) }
            "pdj" -> doubleArrayOf(sin(p[0] * y) - cos(p[1] * x), sin(p[2] * x) - cos(p[3] * y))
            "fan2" -> {
                val p1 = pi * p[0] * p[0]; val p2 = p[1]
                val tt = th + p2 - p1 * (((th + p2) / p1)).let { if (it < 0) kotlin.math.ceil(it) else floor(it) }   // observed: the angle plus p2 wraps modulo p1
                val a = if (tt > p1 / 2) th - p1 / 2 else th + p1 / 2
                doubleArrayOf(r * sin(a), r * cos(a))
            }
            "rings2" -> {
                val q = p[0] * p[0]
                val tt = r - 2 * q * ((r + q) / (2 * q)).let { if (it < 0) kotlin.math.ceil(it) else floor(it) } + r * (1 - q)
                doubleArrayOf(tt * sin(th), tt * cos(th))
            }
            "eyefish" -> doubleArrayOf(2 / (r + 1) * x, 2 / (r + 1) * y)
            "bubble" -> doubleArrayOf(4 / (r * r + 4) * x, 4 / (r * r + 4) * y)
            "cylinder" -> doubleArrayOf(sin(x), y)
            "perspective" -> { val a = p[0] * pi / 2; val k = p[1] / (p[1] - y * sin(a)); doubleArrayOf(k * x, k * y * cos(a)) }
            "curl" -> {
                val t1 = 1 + p[0] * x + p[1] * (x * x - y * y); val t2 = p[0] * y + 2 * p[1] * x * y; val d = t1 * t1 + t2 * t2
                doubleArrayOf((x * t1 + y * t2) / d, (y * t1 - x * t2) / d)
            }
            "rectangles" -> doubleArrayOf((2 * floor(x / p[0]) + 1) * p[0] - x, (2 * floor(y / p[1]) + 1) * p[1] - y)
            "tangent" -> doubleArrayOf(sin(x) / cos(y), tan(y))
            "ngon" -> {
                val p2 = 2 * pi / p[1]
                val t3 = ph - p2 * floor(ph / p2); val t4 = if (t3 > p2 / 2) t3 - p2 else t3
                val k = (p[2] * (1 / cos(t4) - 1) + p[3]) / r.pow(p[0]); doubleArrayOf(k * x, k * y)
            }
            "secant2" -> { val c = cos(r); doubleArrayOf(x, if (c < 0) 1 / c + 1 else 1 / c - 1) }   // weight 1
            "cross" -> { val k = sqrt(1 / ((x * x - y * y).pow(2) + 1e-10)); doubleArrayOf(k * x, k * y) }
            "boarders" -> {
                val xr = round(x); val yr = round(y); val dx = x - xr; val dy = y - yr
                if (u >= 0.75) doubleArrayOf(dx / 2 + xr, dy / 2 + yr)
                else if (abs(dx) >= abs(dy)) { val s = if (dx >= 0) 1.0 else -1.0; doubleArrayOf(dx / 2 + xr + s / 4, dy / 2 + yr + s / 4 * dy / dx) }
                else { val s = if (dy >= 0) 1.0 else -1.0; doubleArrayOf(dx / 2 + xr + s / 4 * dx / dy, dy / 2 + yr + s / 4) }
            }
            "butterfly" -> { val rr = 4 / sqrt(3 * pi) * sqrt(abs(x * y) / (x * x + 4 * y * y + 1e-10)); doubleArrayOf(rr * x, 2 * rr * y) }
            "edisc" -> {
                val t = x * x + y * y + 1; val a = sqrt(t + 2 * x); val b = sqrt(t - 2 * x); val xx = max((a + b) / 2, 1.0)
                val a1 = ln(xx + sqrt(xx - 1)); val a2 = -acos((x / xx).coerceIn(-1.0, 1.0)); val sg = if (y > 0) -sin(a1) else sin(a1)
                doubleArrayOf(cosh(a2) * cos(a1) / 11.57034632, sinh(a2) * sg / 11.57034632)
            }
            "elliptic" -> {
                val t = x * x + y * y + 1; val xx = 0.5 * (sqrt(t + 2 * x) + sqrt(t - 2 * x)); val a = x / xx
                val b = sqrt(max(0.0, 1 - a * a)); val s = sqrt(max(0.0, xx - 1)); val l = ln(xx + s)
                doubleArrayOf(2 / pi * atan2(a, b), 2 / pi * (if (y > 0) l else -l))
            }
            "foci" -> { val d = 1 / (cosh(x) - cos(y)); doubleArrayOf(d * sinh(x), d * sin(y)) }
            "loonie" -> { val q = x * x + y * y; val rr = if (q < w * w) w * sqrt(w * w / q - 1) else w; doubleArrayOf(rr * x, rr * y) }
            "polar2" -> doubleArrayOf(th / pi, ln(r) / pi)
            "scry" -> { val d = 1 / (r * (r * r + 1 / (w + 1e-10))); doubleArrayOf(d * x, d * y) }
            "disc2" -> {
                val tw = p[1]; var sn = sin(tw); var c = cos(tw) - 1
                if (tw > 2 * pi) { val k = 1 + tw - 2 * pi; sn *= k; c *= k } else if (tw < -2 * pi) { val k = 1 + tw + 2 * pi; sn *= k; c *= k }
                val t = pi * p[0] * (x + y); val rr = th / pi
                doubleArrayOf(rr * (sin(t) + c), rr * (cos(t) + sn))
            }
            "flower" -> { val k = (u - p[1]) * cos(p[0] * ph) / r; doubleArrayOf(k * x, k * y) }
            "conic" -> { val k = (u - p[1]) * p[0] / (1 + p[0] * x / r) / r; doubleArrayOf(k * x, k * y) }
            "parabola" -> doubleArrayOf(p[0] * sin(r).pow(2) * u, p[1] * cos(r) * u)
            "bent2" -> doubleArrayOf(if (x < 0) p[0] * x else x, if (y < 0) p[1] * y else y)
            "bipolar" -> {
                val t = x * x + y * y + 1; var ps = 0.5 * atan2(2 * y, x * x + y * y - 1) - pi / 2 * p[0]
                if (ps > pi / 2 + 1e-6) ps = -pi / 2 + (ps + pi / 2) % pi else if (ps < -pi / 2 - 1e-6) ps = pi / 2 - (pi / 2 - ps) % pi
                doubleArrayOf(ln((t + 2 * x) / (t - 2 * x)) / (2 * pi), 2 / pi * ps)
            }
            "cell" -> {
                val s = p[0]; val fi = floor(x / s); val fj = floor(y / s); val dx = x - fi * s; val dy = y - fj * s
                val ii = if (fi >= 0) 2 * fi else -(2 * fi + 1); val jj = if (fj >= 0) 2 * fj else -(2 * fj + 1)
                doubleArrayOf(dx + ii * s, -(dy + jj * s))
            }
            "cpow" -> {
                val pw = p[2]; val k = floor(pw * u); val l = 0.5 * ln(x * x + y * y); val mu = p[0] / pw; val nu = p[1] / pw
                val al = mu * ph + nu * l + 2 * pi * k / pw; val mm = exp(mu * l - nu * ph); doubleArrayOf(mm * cos(al), mm * sin(al))
            }
            "curve" -> {
                val lx = max(p[2] * p[2], 1e-20); val ly = max(p[3] * p[3], 1e-20)
                doubleArrayOf(x + p[0] * exp(-y * y / lx), y + p[1] * exp(-x * x / ly))
            }
            "escher" -> {
                val l = 0.5 * ln(x * x + y * y); val mu = 0.5 * (1 + cos(p[0])); val nu = 0.5 * sin(p[0])
                val al = mu * ph + nu * l; val mm = exp(mu * l - nu * ph); doubleArrayOf(mm * cos(al), mm * sin(al))
            }
            "modulus" -> {
                fun axis(a: Double, m: Double) = if (a > m) -m + (a + m) % (2 * m) else if (a < -m) m - (m - a) % (2 * m) else a
                doubleArrayOf(axis(x, p[0]), axis(y, p[1]))
            }
            "oscope" -> {
                val c = p[2] * cos(2 * pi * p[1] * x)
                val t = (if (p[3] == 0.0) c else c * exp(-abs(x) * p[3])) + p[0]
                doubleArrayOf(x, if (abs(y) <= t) -y else y)
            }
            "popcorn2" -> doubleArrayOf(x + p[0] * sin(tan(p[2] * y)), y + p[1] * sin(tan(p[2] * x)))
            "separation" -> {
                fun axis(a: Double, s: Double, i: Double) = if (a > 0) sqrt(a * a + s * s) - i * a else -(sqrt(a * a + s * s) + i * a)
                doubleArrayOf(axis(x, p[0], p[1]), axis(y, p[2], p[3]))
            }
            "split" -> doubleArrayOf(if (cos(pi * y * p[1]) >= 0) x else -x, if (cos(pi * x * p[0]) >= 0) y else -y)
            "splits" -> doubleArrayOf(if (x >= 0) x + p[0] else x - p[0], if (y >= 0) y + p[1] else y - p[1])
            "stripes" -> { val xr = floor(x + 0.5); val d = x - xr; doubleArrayOf(d * (1 - p[0]) + xr, y + d * d * p[1]) }
            "wedge" -> {
                val al = ph + p[3] * r; val k = floor((p[2] * al + pi) / (2 * pi)); val f = 1 - p[0] * p[2] / (2 * pi); val a2 = al * f + k * p[0]
                doubleArrayOf((r + p[1]) * cos(a2), (r + p[1]) * sin(a2))
            }
            "wedge_julia" -> {
                val pw = p[0]; val j = (abs(pw) * u).toLong().toDouble(); val al = (ph + 2 * pi * j) / pw
                val k = floor((p[2] * al + pi) / (2 * pi)); val f = 1 - p[3] * p[2] / (2 * pi); val a2 = al * f + k * p[3]
                val rr = (x * x + y * y).pow(p[1] / (2 * pw)); doubleArrayOf(rr * cos(a2), rr * sin(a2))
            }
            "wedge_sph" -> {
                val q = 1 / (r + 1e-10); val al = ph + p[3] * q; val k = floor((p[2] * al + pi) / (2 * pi))
                val f = 1 - p[0] * p[2] / (2 * pi); val a2 = al * f + k * p[0]
                doubleArrayOf((q + p[1]) * cos(a2), (q + p[1]) * sin(a2))
            }
            "whorl" -> { val al = if (r < w) ph + p[0] / (w - r) else ph + p[1] / (w - r); doubleArrayOf(w * r * cos(al), w * r * sin(al)) }
            "waves2" -> doubleArrayOf(x + p[0] * sin(y * p[2]), y + p[1] * sin(x * p[3]))
            "auger" -> {
                val sn = sin(p[0] * x); val un = sin(p[0] * y)
                val dy = y + p[1] * (p[3] * sn / 2 + abs(y) * sn); val dx = x + p[1] * (p[3] * un / 2 + abs(x) * un)
                doubleArrayOf(x + p[2] * (dx - x), dy)
            }
            "flux" -> {
                val ap = x + w; val am = x - w
                val rr = w * (2 + p[0]) * ((y * y + ap * ap) / (y * y + am * am)).pow(0.25); val al = 0.5 * (atan2(y, am) - atan2(y, ap))
                doubleArrayOf(rr * cos(al), rr * sin(al))
            }
            "exp" -> doubleArrayOf(exp(x) * cos(y), exp(x) * sin(y))
            "log" -> doubleArrayOf(0.5 * ln(x * x + y * y), ph)
            "sin" -> doubleArrayOf(sin(x) * cosh(y), cos(x) * sinh(y))
            "cos" -> doubleArrayOf(cos(x) * cosh(y), -sin(x) * sinh(y))
            "tan" -> { val d = cos(2 * x) + cosh(2 * y); doubleArrayOf(sin(2 * x) / d, sinh(2 * y) / d) }
            "sec" -> { val d = cos(2 * x) + cosh(2 * y); doubleArrayOf(2 * cos(x) * cosh(y) / d, 2 * sin(x) * sinh(y) / d) }
            "csc" -> { val d = cosh(2 * y) - cos(2 * x); doubleArrayOf(2 * sin(x) * cosh(y) / d, -2 * cos(x) * sinh(y) / d) }
            "cot" -> { val d = cosh(2 * y) - cos(2 * x); doubleArrayOf(sin(2 * x) / d, -sinh(2 * y) / d) }
            "sinh" -> doubleArrayOf(sinh(x) * cos(y), cosh(x) * sin(y))
            "cosh" -> doubleArrayOf(cosh(x) * cos(y), sinh(x) * sin(y))
            "tanh" -> { val d = cos(2 * y) + cosh(2 * x); doubleArrayOf(sinh(2 * x) / d, sin(2 * y) / d) }
            "sech" -> { val d = cos(2 * y) + cosh(2 * x); doubleArrayOf(2 * cosh(x) * cos(y) / d, -2 * sinh(x) * sin(y) / d) }
            "csch" -> { val d = cosh(2 * x) - cos(2 * y); doubleArrayOf(2 * sinh(x) * cos(y) / d, -2 * cosh(x) * sin(y) / d) }
            "coth" -> { val d = cosh(2 * x) - cos(2 * y); doubleArrayOf(sinh(2 * x) / d, sin(2 * y) / d) }   // +sin 2y: the original program, not the textbook
            else -> error("no mirror for $name")
        }
    }

    private fun assertV(name: String, x: Double, y: Double, ex: Double, ey: Double, p: DoubleArray = DoubleArray(4), m: DoubleArray = DoubleArray(4), t: DoubleArray = DoubleArray(2), w: Double = 1.0, u: Double = 0.5, tol: Double = 1e-9) {
        val o = v(name, x, y, p, m, t, w, u)
        assertEquals(ex, o[0], tol, "$name x at ($x, $y)"); assertEquals(ey, o[1], tol, "$name y at ($x, $y)")
    }

    /** A test pair of the spec, which gives its values to five decimals: tolerance 1e-4. */
    private fun spec(name: String, x: Double, y: Double, ex: Double, ey: Double, p: DoubleArray = DoubleArray(4), w: Double = 1.0, u: Double = 0.5) =
        assertV(name, x, y, ex, ey, p = p, w = w, u = u, tol = 1e-4)

    private fun params(vararg v: Double) = DoubleArray(4).also { v.copyInto(it) }

    @Test fun tableHasNinetyNineIdsWithUniqueNamesAndTheSkippedFourReserved() {
        assertEquals(99, FlameVariations.count())
        assertEquals(99, FlameVariations.NAMES.toSet().size)
        assertEquals("linear", FlameVariations.NAMES[0]); assertEquals("popcorn", FlameVariations.NAMES[17])
        assertEquals("pdj", FlameVariations.NAMES[24]); assertEquals("cross", FlameVariations.NAMES[48])
        assertEquals("disc2", FlameVariations.NAMES[49]); assertEquals("waves2", FlameVariations.NAMES[81])
        assertEquals("exp", FlameVariations.NAMES[82]); assertEquals("coth", FlameVariations.NAMES[95])
        assertEquals("mobius", FlameVariations.NAMES[98])
        for (id in listOf(50, 65, 67, 98)) assertTrue(!FlameVariations.ALL[id].supported, FlameVariations.NAMES[id])
        assertTrue(FlameVariations.ALL.all { it.params.size <= FlameVariations.PARAMS })
        assertTrue(FlameVariations.ALL.all { it.supported || it.params.isEmpty() })
    }

    @Test fun glslCasesMatchTheTable() {
        val src = FlameEngine.COMMON
        val cases = Regex("""case (\d+):""").findAll(src).map { it.groupValues[1].toInt() }.toList()
        // reserved (unsupported) ids have no case and fall through to linear
        assertEquals(FlameVariations.ALL.indices.filter { FlameVariations.ALL[it].supported }, cases)
        // the trailing comment on each case names the variation (first word), keeping ids and names in step
        Regex("""case (\d+):.*?//\s*(\w+)""").findAll(src).forEach {
            assertEquals(FlameVariations.NAMES[it.groupValues[1].toInt()], it.groupValues[2], "case ${it.groupValues[1]}")
        }
    }

    @Test fun normalisedParametersMapMidpointToDefaultAndRoundTrip() {
        val ngon = FlameVariations.NAMES.indexOf("ngon")
        assertEquals(5f, FlameVariations.real(ngon, 1, 0.5f), 1e-6f)
        assertEquals(3f, FlameVariations.real(ngon, 1, 0f), 1e-6f); assertEquals(32f, FlameVariations.real(ngon, 1, 1f), 1e-6f)
        assertEquals(0f, FlameVariations.real(FlameVariations.NAMES.indexOf("linear"), 0, 0.7f))
        FlameVariations.ALL.forEachIndexed { id, vr ->
            vr.params.forEachIndexed { n, prm ->
                assertEquals(prm.default, FlameVariations.real(id, n, 0.5f), 1e-5f, "${vr.name} ${prm.label}")
                assertTrue(prm.min < prm.default && prm.default < prm.max, vr.name)
                for (x in listOf(0.1f, 0.5f, 0.9f)) assertEquals(x, FlameVariations.normalised(id, n, FlameVariations.real(id, n, x)), 1e-5f)
            }
        }
    }

    @Test fun simpleVariationsAtKnownPoints() {
        assertV("sinusoidal", pi / 2, 0.0, 1.0, 0.0)
        assertV("spherical", 2.0, 0.0, 0.5, 0.0)                    // (x,y)/r^2
        assertV("swirl", 1.0, 0.0, sin(1.0), cos(1.0))              // r^2 = 1 -> (sin 1, cos 1)
        assertV("horseshoe", 3.0, 0.0, 3.0, 0.0)                    // (9)/3, 0
        assertV("polar", 0.0, 2.0, 0.0, 1.0)                        // theta = atan(0/2) = 0, r - 1
        assertV("eyefish", 1.0, 0.0, 1.0, 0.0)                      // 2/(1+1) * (1,0)
        assertV("fisheye", 1.0, 0.0, 0.0, 1.0)                      // swapped: 2/(r+1) * (y, x)
        assertV("bubble", 2.0, 0.0, 1.0, 0.0)                       // 4/(4+4) * 2
        assertV("cylinder", pi / 2, 5.0, 1.0, 5.0)
        assertV("bent", -1.0, -2.0, -2.0, -1.0)                     // x<0 doubles x, y<0 halves y
        assertV("bent", 1.0, 2.0, 1.0, 2.0)
        assertV("tangent", 0.0, 0.0, 0.0, 0.0)
        assertV("exponential", 1.0, 0.5, 0.0, 1.0)                  // exp(0) * (cos(pi/2), sin(pi/2))
        assertV("cosine", 0.0, 0.0, 1.0, 0.0)
        assertV("heart", 0.0, 1.0, 0.0, -1.0)                       // theta 0, r 1 -> (sin 0, -cos 0)
        assertV("hyperbolic", 0.0, 2.0, 0.0, 2.0)                   // (sin 0 / 2, 2 cos 0)
        assertV("diamond", 0.0, 1.0, 0.0, sin(1.0))                 // (sin 0 cos 1, cos 0 sin 1)
        assertV("secant2", 0.0, 0.0, 0.0, 0.0)                       // the +/-1 shift closes the gap at y = 0
        assertV("secant2", 1.0, 0.0, 1.0, 1 / cos(1.0) - 1)
        assertV("secant2", PI, 0.0, PI, 0.0)                         // cos r < 0 takes the +1 branch
        assertV("cross", 3.0, 1.0, 3.0 / 8.0, 1.0 / 8.0)            // 1/|9-1| * (3, 1)
    }

    @Test fun dependentVariationsUseTheAffineCoefficients() {
        // popcorn: (x + c sin(tan 3y), y + f sin(tan 3x)); with x = y = 0 only the offsets survive as 0
        assertV("popcorn", 0.0, 0.0, 0.0, 0.0, t = doubleArrayOf(0.7, -0.3))
        assertV("popcorn", 0.1, 0.0, 0.1, -0.3 * sin(tan(0.3)), t = doubleArrayOf(0.7, -0.3))
        // waves: (x + b sin(y / c^2), y + e sin(x / f^2)); m = (a, b, d, e)
        assertV("waves", 0.0, 0.5, 0.2 * sin(0.5 / 0.25), 0.5, m = doubleArrayOf(1.0, 0.2, 0.0, 0.9), t = doubleArrayOf(0.5, 1.0))
        // rings: ((r + c^2) mod 2c^2 - c^2 + r (1 - c^2)) (cos, sin) ; c = 1 -> c^2 = 1: (r+1) mod 2 - 1 + 0
        assertV("rings", 0.0, 0.5, 0.5, 0.0, t = doubleArrayOf(1.0, 0.0))
    }

    @Test fun parametricVariationsAtKnownPoints() {
        assertV("pdj", 0.0, 0.0, -1.0, -1.0, p = doubleArrayOf(1.0, 1.0, 1.0, 1.0))        // (0-1, 0-1)
        assertV("curl", 1.0, 0.0, 0.5, 0.0, p = doubleArrayOf(1.0, 0.0, 0.0, 0.0))         // t1 = 2, t2 = 0 -> (2,0)/4
        assertV("rectangles", 0.3, 0.3, 0.2, 0.2, p = doubleArrayOf(0.5, 0.5, 0.0, 0.0))   // (2*0+1)*0.5 - 0.3
        assertV("rectangles", 0.7, 0.3, 0.8, 0.2, p = doubleArrayOf(0.5, 0.5, 0.0, 0.0))   // floor(1.4) = 1 -> 3*0.5 - 0.7
        assertV("perspective", 1.0, 0.0, 1.0, 0.0, p = doubleArrayOf(0.4, 2.0, 0.0, 0.0)) // y = 0: dist/dist * (x, 0)
        assertV("blob", 0.0, 1.0, 0.0, 1.0, p = doubleArrayOf(1.0, 1.0, 5.0, 0.0))        // high = low = 1: the output points along the input
        // black-box pairs (flam3 observed with the probe kit; see docs/developer/clean_room_provenance.md): the exact formula values at observed points
        assertV("blob", 4.0, 0.0, 4.0, 0.0, p = doubleArrayOf(1.0, 0.2, 5.0, 0.0))
        assertV("blob", -3.46, 2.0, -3.2763507367, 1.8938443565, p = doubleArrayOf(1.0, 0.2, 5.0, 0.0))
        assertV("perspective", 0.0, 4.0, 0.0, -3.8042260652, p = doubleArrayOf(0.6, 2.0, 0.0, 0.0))
        assertV("perspective", 1.6, 1.6, 4.5353221655, 2.6657954832, p = doubleArrayOf(0.6, 2.0, 0.0, 0.0))
        assertV("fan2", 1.0, 2.0, 0.1585126678, 2.2304424974, p = doubleArrayOf(0.5, 0.3, 0.0, 0.0))
        assertV("fan2", -2.0, 1.0, -0.2273256645, 2.2244826460, p = doubleArrayOf(0.8, 0.2, 0.0, 0.0))
        assertV("fan2", 2.0, 1.6, 2.4600525568, 0.7128403873, p = doubleArrayOf(0.5, 0.0, 0.0, 0.0))
        assertV("ngon", 0.25, 0.0, 4.0, 0.0, p = doubleArrayOf(2.0, 5.0, 1.0, 1.0))
        assertV("ngon", 0.0, 0.5, 0.0, 2.1029244484, p = doubleArrayOf(2.0, 5.0, 1.0, 1.0))
    }

    // ---- ids 82-95: trigonometric and hyperbolic (spec test pairs) ----

    @Test fun trigonometricAndHyperbolicVariations() {
        spec("exp", 0.0, 0.0, 1.0, 0.0); spec("exp", 0.0, pi / 2, 0.0, 1.0); spec("exp", 0.0, pi, -1.0, 0.0); spec("exp", 1.0, pi / 3, 1.35914, 2.35410)
        spec("log", 1.0, 0.0, 0.0, 0.0); spec("log", Math.E, 0.0, 1.0, 0.0); spec("log", 0.0, 1.0, 0.0, 1.57080); spec("log", -1.0, 0.0, 0.0, 3.14159)
        spec("sin", 0.0, 0.0, 0.0, 0.0); spec("sin", pi / 2, 0.0, 1.0, 0.0); spec("sin", 0.0, 1.0, 0.0, 1.17520); spec("sin", 1.0, 0.5, 0.94886, 0.28155)
        spec("cos", 0.0, 0.0, 1.0, 0.0); spec("cos", pi, 0.0, -1.0, 0.0); spec("cos", 0.0, 1.0, 1.54308, 0.0); spec("cos", 1.0, 0.5, 0.60926, -0.43849)
        spec("tan", 0.0, 0.0, 0.0, 0.0); spec("tan", pi / 4, 0.0, 1.0, 0.0); spec("tan", 0.0, 0.5, 0.0, 0.46212); spec("tan", 1.0, 0.5, 0.80688, 1.04283)
        spec("sec", 0.0, 0.0, 1.0, 0.0); spec("sec", pi, 0.0, -1.0, 0.0); spec("sec", 0.0, 1.0, 0.64805, 0.0); spec("sec", 1.0, 0.5, 1.08127, 0.77819)
        spec("csc", pi / 2, 0.0, 1.0, 0.0); spec("csc", -pi / 2, 0.0, -1.0, 0.0); spec("csc", 1.0, 0.5, 0.96861, -0.28741)
        spec("cot", pi / 4, 0.0, 1.0, 0.0); spec("cot", pi / 2, 0.0, 0.0, 0.0); spec("cot", 1.0, 0.5, 0.46411, -0.59983)
        spec("sinh", 0.0, 0.0, 0.0, 0.0); spec("sinh", 0.0, pi / 2, 0.0, 1.0); spec("sinh", 1.0, 0.0, 1.17520, 0.0); spec("sinh", 1.0, 0.5, 1.03134, 0.73979)
        spec("cosh", 0.0, 0.0, 1.0, 0.0); spec("cosh", 0.0, pi, -1.0, 0.0); spec("cosh", 1.0, 0.0, 1.54308, 0.0); spec("cosh", 1.0, 0.5, 1.35418, 0.56342)
        spec("tanh", 0.0, 0.0, 0.0, 0.0); spec("tanh", 0.0, pi / 4, 0.0, 1.0); spec("tanh", 0.5, 0.5, 0.56408, 0.40390)
        spec("sech", 0.0, 0.0, 1.0, 0.0); spec("sech", 0.0, pi, -1.0, 0.0); spec("sech", 0.5, 0.5, 0.94998, -0.23983)
        spec("csch", 0.0, pi / 2, 0.0, -1.0); spec("csch", 0.0, -pi / 2, 0.0, 1.0); spec("csch", 0.5, 0.5, 0.91207, -1.07823)
        spec("coth", 0.0, pi / 4, 0.0, 1.0); spec("coth", 0.0, pi / 2, 0.0, 0.0); spec("coth", 0.5, 0.5, 1.17195, 0.83914)   // +sin 2y: not the textbook coth
    }

    // ---- batch 2: parameterless ----

    @Test fun boardersInteriorAndBorderBranches() {
        spec("boarders", 0.3, 0.2, 0.15, 0.1, u = 0.9)             // U >= 0.75: interior
        spec("boarders", 0.3, 0.1, 0.4, 0.13333, u = 0.1)          // border, |dx| >= |dy|
        spec("boarders", 0.1, 0.3, 0.13333, 0.4, u = 0.1)          // border, |dy| > |dx|
        spec("boarders", 0.5, 0.5, 0.25, 0.25, u = 0.9)            // 0.5 rounds half-even to 0
        spec("boarders", 1.5, 0.0, 1.75, 0.0, u = 0.9)             // 1.5 -> 2 (ties to even), dx = -0.5: -0.25 + 2
    }

    @Test fun butterflyElliptic() {
        spec("butterfly", 0.0, 0.0, 0.0, 0.0); spec("butterfly", 1.0, 0.0, 0.0, 0.0); spec("butterfly", 2.0, 1.0, 1.30294, 1.30294); spec("butterfly", -1.0, -1.0, -0.58269, -1.16538)
        spec("edisc", 0.0, 0.0, 0.21686, 0.0); spec("edisc", 1.0, 1.0, 0.07944, 0.06866); spec("edisc", 1.0, -1.0, 0.07944, -0.06866)
        spec("elliptic", 0.0, 0.0, 0.0, 0.0); spec("elliptic", 0.0, 1.0, 0.0, 0.45941); spec("elliptic", 2.0, 1.0, 0.67701, 0.78342)
        spec("elliptic", 2.0, -1.0, 0.67701, -0.78342); spec("elliptic", 2.0, 0.0, 1.0, -0.69940)   // y = 0 takes the negative branch
    }

    @Test fun fociLoonieScryPolar2() {
        spec("foci", 0.0, pi / 2, 0.0, 1.0); spec("foci", 1.0, 0.0, 2.16395, 0.0); spec("foci", -1.0, 1.0, -1.17195, 0.83914)
        spec("loonie", 2.0, 0.0, 2.0, 0.0); spec("loonie", 0.6, 0.0, 0.8, 0.0)
        spec("loonie", 1.0, 0.0, 3.46410, 0.0, w = 2.0); spec("loonie", 3.0, 0.0, 6.0, 0.0, w = 2.0)
        spec("scry", 1.0, 0.0, 0.5, 0.0); spec("scry", 0.0, 2.0, 0.0, 0.2); spec("scry", 1.0, 0.0, 0.66667, 0.0, w = 2.0)
        spec("polar2", 0.0, 1.0, 0.0, 0.0); spec("polar2", 1.0, 0.0, 0.5, 0.0); spec("polar2", 0.0, Math.exp(pi), 0.0, 1.0); spec("polar2", -1.0, -1.0, -0.75, 0.11032)
    }

    // ---- batch 3: parametric (spec test pairs; `params` lists the parameters in table order) ----

    @Test fun disc2() {
        spec("disc2", 1.0, 0.0, 0.0, 0.5, params(0.0, 0.0)); spec("disc2", 0.0, 1.0, 0.0, 0.0, params(0.0, 0.0))
        spec("disc2", 0.5, 0.5, 0.0, -0.25, params(1.0, 0.0)); spec("disc2", 1.0, 0.0, -1.0, 0.5, params(0.0, pi))
        spec("disc2", 1.0, 0.0, -4.14159, 0.5, params(0.0, 3 * pi))   // beyond 2 pi the output is scaled by 1 + twist - 2 pi
    }

    @Test fun flowerConicParabolaWithAFixedDraw() {
        // flower: radius |U - holes| |cos(petals phi)|, direction kept for petals = 0
        for (uu in listOf(0.0, 0.25, 0.5, 1.0)) {
            val o = v("flower", 3.0, 4.0, params(0.0, 0.0), u = uu)
            assertEquals(uu, sqrt(o[0] * o[0] + o[1] * o[1]), 1e-9); assertEquals(0.6 * uu, o[0], 1e-9); assertEquals(0.8 * uu, o[1], 1e-9)
            val h = v("flower", 3.0, 4.0, params(2.0, 0.3), u = uu)
            assertTrue(sqrt(h[0] * h[0] + h[1] * h[1]) <= 0.7 + 1e-9)   // max(|1 - holes|, |holes|)
        }
        spec("conic", 1.0, 0.0, 0.5, 0.0, params(1.0, 0.0), u = 1.0)   // U e / (1 + e cos phi) at phi = 0
        spec("parabola", pi / 2, 0.0, 0.7, 0.0, params(1.0, 1.0), u = 0.7)   // sin^2(pi/2) U, cos(pi/2) U ~ 6e-17
    }

    @Test fun bent2Bipolar() {
        val b = params(2.0, 0.5)
        spec("bent2", 1.0, 2.0, 1.0, 2.0, b); spec("bent2", -1.0, -2.0, -2.0, -1.0, b); spec("bent2", -3.0, 4.0, -6.0, 4.0, b)
        val d = params(0.0)
        spec("bipolar", 0.0, 0.0, 0.0, 1.0, d); spec("bipolar", 0.0, 1.0, 0.0, 0.5, d); spec("bipolar", 0.5, 0.0, 0.34970, 1.0, d)
        spec("bipolar", 1.0, 1.0, 0.25615, 0.35242, d); spec("bipolar", 0.0, 1.0, 0.0, 0.0, params(0.5))
    }

    @Test fun cellCpowCurve() {
        spec("cell", 0.5, 0.5, 0.5, -0.5, params(1.0)); spec("cell", 1.5, 0.5, 2.5, -0.5, params(1.0)); spec("cell", -0.5, 0.5, 1.5, -0.5, params(1.0))
        spec("cell", 3.0, -1.0, 5.0, -3.0, params(2.0)); spec("cell", -0.2, 0.7, 0.8, -1.2, params(0.5))
        val one = params(1.0, 0.0, 1.0)   // r = 1, i = 0, power = 1: one branch, the identity
        spec("cpow", 1.0, 2.0, 1.0, 2.0, one); spec("cpow", -3.0, 4.0, -3.0, 4.0, one)
        spec("curve", 1.0, 2.0, 1.0, 2.0, params(0.0, 0.0, 1.0, 1.0))
        val c = params(1.0, 2.0, 1.0, 1.0)
        spec("curve", 0.0, 0.0, 1.0, 2.0, c); spec("curve", 1.0, 0.0, 2.0, 0.73576, c)
        spec("curve", 0.5, 1.0, 2.05760, 0.63212, params(2.0, -1.0, 2.0, 0.5))
    }

    @Test fun escherModulusOscilloscope() {
        for ((px, py) in listOf(1.0 to 0.0, 0.0 to 2.0, -3.0 to 4.0)) spec("escher", px, py, px, py, params(0.0))
        spec("escher", 1.0, 0.0, 1.0, 0.0, params(pi / 2)); spec("escher", 0.0, 1.0, 0.32240, 0.32240, params(pi / 2))
        spec("escher", Math.E, 0.0, 1.44689, 0.79044, params(pi / 2)); spec("escher", 0.0, 1.0, 1.0, 0.0, params(pi))
        val m = params(1.0, 1.0)
        spec("modulus", 0.5, 0.5, 0.5, 0.5, m); spec("modulus", 1.5, 0.0, -0.5, 0.0, m); spec("modulus", -1.5, 0.0, 0.5, 0.0, m)
        spec("modulus", 1.2, -5.0, 0.2, -1.0, params(0.5, 2.0))
        val o = params(1.0, pi, 1.0, 0.0)   // separation, frequency, amplitude, damping
        spec("oscope", 0.0, 0.5, 0.0, -0.5, o); spec("oscope", 0.0, 2.5, 0.0, 2.5, o)
        spec("oscope", 0.3, 0.1, 0.3, -0.1, params(0.0, 0.5, 2.0, 1.0))   // t = 0.87088
    }

    @Test fun popcorn2SeparationSplitSplitsStripes() {
        spec("popcorn2", 0.7, -0.2, 0.7, -0.2, params(0.0, 0.0, 0.0))
        val pc = params(0.5, 0.5, 1.0)
        spec("popcorn2", 0.0, 0.0, 0.0, 0.0, pc); spec("popcorn2", pi / 4, 0.0, 0.78540, 0.42074, pc)
        spec("popcorn2", 0.2, 0.3, 0.51600, 0.40258, params(0.5, 0.25, 2.0))
        val none = params(0.0, 0.0, 0.0, 0.0)
        spec("separation", 1.0, 1.0, 1.0, 1.0, none); spec("separation", -1.0, -2.0, -1.0, -2.0, none)
        spec("separation", 0.0, 0.0, -1.0, -1.0, params(1.0, 0.0, 1.0, 0.0)); spec("separation", 1.0, 1.0, 1.41421, 1.41421, params(1.0, 0.0, 1.0, 0.0))
        spec("separation", 2.0, -2.0, 1.23607, -1.0, params(1.0, 0.5, 0.0, 0.5))
        spec("split", 1.5, -2.5, 1.5, -2.5, params(0.0, 0.0)); spec("split", 1.0, 2.0, 1.0, -2.0, params(1.0, 1.0))
        val sp = params(1.0, 2.0)
        spec("splits", 0.0, 0.0, 1.0, 2.0, sp); spec("splits", 3.0, 4.0, 4.0, 6.0, sp); spec("splits", -3.0, -4.0, -4.0, -6.0, sp)
        spec("stripes", 0.7, -1.3, 0.7, -1.3, params(0.0, 0.0)); spec("stripes", 0.2, 1.0, 0.16, 1.04, params(0.2, 1.0)); spec("stripes", 0.3, 2.0, 0.0, 2.0, params(1.0, 0.0))
    }

    @Test fun wedgeFamily() {
        val d = params(0.0, 0.0, 1.0, 0.0)   // angle, hole, count, swirl
        spec("wedge", 1.0, 0.0, 1.0, 0.0, d); spec("wedge", 0.0, 2.0, 0.0, 2.0, d)
        spec("wedge", 2.0, 0.0, 2.5, 0.0, params(0.0, 0.5, 1.0, 0.0)); spec("wedge", 1.0, 0.0, 0.54030, 0.84147, params(0.0, 0.0, 1.0, 1.0))
        spec("wedge", 0.0, 2.0, -1.41421, 1.41421, params(pi / 2, 0.0, 2.0, 0.0))
        // wedge_julia with power 1 has one branch and, at dist 1, count 2, angle pi/2, equals that wedge
        spec("wedge_julia", 0.0, 2.0, -1.41421, 1.41421, params(1.0, 1.0, 2.0, pi / 2))
        spec("wedge_sph", 2.0, 0.0, 0.5, 0.0, d); spec("wedge_sph", 0.0, 1.0, 0.0, 1.0, d); spec("wedge_sph", 2.0, 0.0, 1.0, 0.0, params(0.0, 0.5, 1.0, 0.0))
    }

    @Test fun whorlWaves2AugerFlux() {
        // Spec 80 lists (1,0) -> (1,0) for the defaults, but there r = v and outside = 0, which its own edge-case text
        // says is 0/0 = not-a-number; the equations and that text are followed, the pair is not.
        assertTrue(v("whorl", 1.0, 0.0, params(0.0, 0.0), w = 1.0).all { it.isNaN() })
        spec("whorl", 0.0, 2.0, 0.0, 2.0, params(0.0, 0.0), w = 1.0)
        spec("whorl", 0.5, 0.0, -0.20807, 0.45465, params(1.0, 2.0), w = 1.0); spec("whorl", 2.0, 0.0, -0.83229, -1.81859, params(1.0, 2.0), w = 1.0)
        spec("waves2", 0.4, -0.9, 0.4, -0.9, params(0.0, 0.0, 0.0, 0.0)); spec("waves2", 0.0, 0.5, 1.0, 0.5, params(1.0, 2.0, pi, pi / 2))
        val a = params(1.0, 0.5, 0.0, 1.0)   // freq, weight, sym, scale
        spec("auger", 0.0, 0.0, 0.0, 0.0, a); spec("auger", pi / 2, 0.0, 1.57080, 0.25, a)
        spec("auger", 0.3, 0.4, 0.3, 0.65409, params(2.0, 0.5, 0.0, 1.0)); spec("auger", 1.0, 2.0, 1.68197, 3.05184, params(1.0, 0.5, 1.0, 1.0))
        val f = params(0.0)
        spec("flux", 0.0, 1.0, 1.41421, 1.41421, f, w = 1.0); spec("flux", 0.0, 0.0, 0.0, 2.0, f, w = 1.0); spec("flux", 1.0, 1.0, 3.14461, 5.08808, f, w = 2.0)
    }
}
