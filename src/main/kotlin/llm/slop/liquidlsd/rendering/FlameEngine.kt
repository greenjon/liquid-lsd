package llm.slop.liquidlsd.rendering

import mu.KotlinLogging
import org.lwjgl.opengl.GL33.*
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sin

private val logger = KotlinLogging.logger {}

/**
 * Tunables for measuring: JVM property, or its upper-case env var (`gradlew run` does not forward -D to the app JVM).
 * Math follows the public fractal-flame paper (Draves & Reckase); no flam3 / Electric Sheep / Apophysis code was consulted.
 */
private fun spikeSetting(prop: String, def: String): String =
    System.getProperty(prop) ?: System.getenv(prop.uppercase().replace('.', '_')) ?: def

/**
 * GL resources and passes for one [FlameSource] instance: a ping-pong RGBA32F state texture (one IFS point per
 * texel), an iterate pass, a GL_POINTS splat pass into a float density buffer, then the source's tone-map shader.
 *  Created lazily on the render thread. */
internal class FlameEngine {
    private val n = spikeSetting("flame.n", "512").toInt()   // state texture side -> n*n points (FLAME_N=1024 -> 1M)
    private val skip = spikeSetting("flame.skip", "")   // "splat" or "iterate": omit that pass to attribute cost
    private val burn = 16f                   // iterations before a point is drawn
    private val density32 = spikeSetting("flame.density", "fp16") != "fp16"

    private var stateA = FBO(n, n, GL_RGBA32F)
    private var stateB = FBO(n, n, GL_RGBA32F)
    private var density: FBO? = null

    private val iterate = Shader(FULLSCREEN_VERT, ITERATE_FRAG)
    private val splat = Shader(SPLAT_VERT, SPLAT_FRAG)
    private val fill = Shader(FULLSCREEN_VERT, "#version 330 core\nout vec4 o;\nvoid main(){o=vec4(0.0);}")
    private val emptyVao = glGenVertexArrays()
    private val palette: Int

    // Timestamp pairs, not GL_TIME_ELAPSED: Main already keeps a TIME_ELAPSED query open around the whole frame
    // and only one may be active at a time.
    private val queries = IntArray(4) { glGenQueries() }
    private val queryUsed = BooleanArray(2)
    private var queryIdx = 0
    private var frames = 0
    private var gpuNsSum = 0L
    private var gpuSamples = 0
    private var frameCounter = 0
    private var lastPasses = 0
    private var lastKernel = 0f
    private val governor = FlameGovernor()

    init {
        resetPoints()
        palette = glGenTextures()
        glBindTexture(GL_TEXTURE_2D, palette)
        uploadPalette(null)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_REPEAT)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE)
        glBindTexture(GL_TEXTURE_2D, 0)
        logger.warn { "Flame: ${n * n} points, density ${if (density32) "RGBA32F" else "RGBA16F"}" }
    }

    /** Replaces the colour palette: 768 bytes of RGB, or null for the procedural default. */
    fun setPalette(rgb: ByteArray?) {
        glBindTexture(GL_TEXTURE_2D, palette)
        uploadPalette(rgb)
        glBindTexture(GL_TEXTURE_2D, 0)
    }

    /** Uploads into the currently bound palette texture. */
    private fun uploadPalette(rgb: ByteArray?) {
        val px = FloatArray(256 * 4)
        for (i in 0 until 256) {
            if (rgb != null && rgb.size >= 768) {
                for (c in 0 until 3) px[i * 4 + c] = (rgb[i * 3 + c].toInt() and 0xFF) / 255f
            } else {
                val t = i / 255f
                px[i * 4] = 0.5f + 0.5f * cos((2.0 * PI * (t + 0.00)).toFloat())
                px[i * 4 + 1] = 0.5f + 0.5f * cos((2.0 * PI * (t + 0.33)).toFloat())
                px[i * 4 + 2] = 0.5f + 0.5f * cos((2.0 * PI * (t + 0.67)).toFloat())
            }
            px[i * 4 + 3] = 1f
        }
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, 256, 1, 0, GL_RGBA, GL_FLOAT, px)
    }

    fun resetPoints() {
        val rnd = java.util.Random(1234)
        val data = FloatArray(n * n * 4)
        for (i in 0 until n * n) {
            data[i * 4] = rnd.nextFloat() * 2f - 1f
            data[i * 4 + 1] = rnd.nextFloat() * 2f - 1f
            data[i * 4 + 2] = rnd.nextFloat()
            data[i * 4 + 3] = 0f
        }
        glBindTexture(GL_TEXTURE_2D, stateA.texture)
        glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, n, n, GL_RGBA, GL_FLOAT, data)
        glBindTexture(GL_TEXTURE_2D, 0)
    }

    fun render(src: FlameSource, target: FBO, genome: FloatArray, numXf: Int) {
        val p = src.parameters
        fun v(name: String, def: Float) = p[name]?.value ?: def

        if (density == null || density!!.width != target.width || density!!.height != target.height) {
            density?.dispose()
            density = FBO(target.width, target.height, if (density32) GL_RGBA32F else GL_RGBA16F)
        }
        val dens = density!!

        collectGpuTime()
        glQueryCounter(queries[queryIdx * 2], GL_TIMESTAMP)

        val tier = llm.slop.liquidlsd.ui.UITheme.flameQuality
        val rows = ceil(tier.points * governor.scale * n).toInt().coerceIn(1, n)
        val hasFinal = !FlameGenome.finalIsIdentity(genome)
        val decay = v("Decay", 0f)
        val passes = tier.passes
        lastPasses = passes
        lastKernel = tier.kernel
        val zoom = v("Zoom", 0.45f)
        val rot = v("Rotation", 0f)
        val aspect = target.width.toFloat() / target.height

        glEnable(GL_PROGRAM_POINT_SIZE)

        // 1. fade (or clear) the persistent density buffer
        dens.bind()
        if (decay <= 0.001f) {
            glDisable(GL_BLEND)
            glClearColor(0f, 0f, 0f, 0f)
            glClear(GL_COLOR_BUFFER_BIT)
        } else {
            glEnable(GL_BLEND)
            glBlendFunc(GL_ZERO, GL_CONSTANT_COLOR)
            glBlendColor(decay, decay, decay, decay)
            fill.bind()
            Geometry.drawFullscreenQuad()
        }

        for (pass in 0 until passes) {
            // 2. iterate: stateA -> stateB
            stateB.bind()
            glViewport(0, 0, n, rows)   // Points: only the first `rows` rows are iterated and drawn
            glDisable(GL_BLEND)
            if (skip != "iterate") {
            iterate.bind()
            glActiveTexture(GL_TEXTURE0)
            glBindTexture(GL_TEXTURE_2D, stateA.texture)
            iterate.setUniform("uState", 0)
            iterate.setUniform("uN", n)
            iterate.setUniform("uFrame", frameCounter++)
            iterate.setUniform("uNumXf", numXf)
            iterate.setUniform4fv("uXf", genome)
            Geometry.drawFullscreenQuad()
            val t = stateA; stateA = stateB; stateB = t
            }

            // 3. splat the new positions additively
            dens.bind()
            glEnable(GL_BLEND)
            glBlendFunc(GL_ONE, GL_ONE)
            splat.bind()
            glBindTexture(GL_TEXTURE_2D, stateA.texture)
            splat.setUniform("uState", 0)
            glActiveTexture(GL_TEXTURE1)
            glBindTexture(GL_TEXTURE_2D, palette)
            splat.setUniform("uPal", 1)
            splat.setUniform("uN", n)
            splat.setUniform("uHasFinal", if (hasFinal) 1 else 0)
            if (hasFinal) splat.setUniform4fv("uXf", genome)
            splat.setUniform("uBurn", burn)
            splat.setUniform("uFrame", frameCounter)
            splat.setUniform("uZoom", zoom)
            splat.setUniform("uCos", cos(rot)); splat.setUniform("uSin", sin(rot))
            splat.setUniform("uFlipY", if (v("FlipY", 0f) >= 0.5f) -1f else 1f)
            splat.setUniform("uCenter", v("CenterX", 0f), v("CenterY", 0f))
            splat.setUniform("uAspect", aspect)
            splat.setUniform("uSize", tier.kernel)
            splat.setUniform("uSharp", v("Sharpness", 3f))
            splat.setUniform("uPalShift", v("Palette", 0f))
            glBindVertexArray(emptyVao)
            if (skip != "splat") glDrawArrays(GL_POINTS, 0, rows * n)
            glBindVertexArray(0)
            glActiveTexture(GL_TEXTURE0)
        }
        glEnd_query()

        // 4. hand back to the renderer: target bound, tone-map shader (already has its param uniforms) bound.
        glDisable(GL_BLEND)
        target.bind()
        src.shader.bind()
        glActiveTexture(GL_TEXTURE0)
        glBindTexture(GL_TEXTURE_2D, dens.texture)
        src.shader.setUniform("uDensity", 0)
        // fewer points (the quality tier or the governor) mean proportionally fewer hits: keep the brightness. The toe
        // is applied before exposure in the shader, so lone hits stay removed.
        src.shader.setUniform("uExposure", v("Exposure", 2f) * n / rows.toFloat() * tier.passExposure)
        Geometry.drawFullscreenQuad()
        glEnable(GL_BLEND)
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA)
    }

    private fun glEnd_query() {
        glQueryCounter(queries[queryIdx * 2 + 1], GL_TIMESTAMP)
        queryUsed[queryIdx] = true
        queryIdx = 1 - queryIdx
    }

    /** Non-blocking: read the query from the previous frame if the GPU has finished it. */
    private fun collectGpuTime() {
        val prev = queryIdx
        if (!queryUsed[prev]) return
        if (glGetQueryObjecti(queries[prev * 2 + 1], GL_QUERY_RESULT_AVAILABLE) == GL_FALSE) return
        val ns = glGetQueryObjecti64(queries[prev * 2 + 1], GL_QUERY_RESULT) - glGetQueryObjecti64(queries[prev * 2], GL_QUERY_RESULT)
        gpuNsSum += ns
        governor.feed(ns / 1e6f, llm.slop.liquidlsd.ui.UITheme.flameBudgetMs)
        queryUsed[prev] = false
        gpuSamples++
        if (++frames % 240 == 0 && gpuSamples > 0) {
            logger.warn { "Flame: n=%d (%d pts) passes=%d kernel=%.1f %s skip=%s: gpu %.2f ms/frame (avg of %d), governor scale %.2f".format(n, n * n, lastPasses, lastKernel, if (density32) "fp32" else "fp16", skip.ifEmpty { "none" }, gpuNsSum / 1e6 / gpuSamples, gpuSamples, governor.scale) }
            gpuNsSum = 0; gpuSamples = 0
        }
    }

    fun dispose() {
        stateA.dispose(); stateB.dispose(); density?.dispose()
        iterate.dispose(); splat.dispose(); fill.dispose()
        glDeleteVertexArrays(emptyVao)
        glDeleteTextures(palette)
        queries.forEach { glDeleteQueries(it) }
    }

    companion object {
        const val FULLSCREEN_VERT = """#version 330 core
layout(location = 0) in vec2 aPosition;
void main() { gl_Position = vec4(aPosition, 0.0, 1.0); }
"""

        /** Shared by the iterate and splat shaders: the packed genome (see [FlameGenome.pack]) and the variation set. */
        const val COMMON = """
uniform vec4 uXf[${FlameGenome.MAX_XF + 1}*${FlameGenome.VEC4_PER_XF}];
const float PI = 3.14159265;

uint gRng;   // per-invocation random state; seed it before calling applyXf
uint pcg(uint v) {
    uint s = v * 747796405u + 2891336453u;
    uint w = ((s >> ((s >> 28u) + 4u)) ^ s) * 277803737u;
    return (w >> 22u) ^ w;
}
float rnd() { gRng = pcg(gRng); return float(gRng) * (1.0 / 4294967296.0); }

// Bad values: a division by zero, a log of a non-positive number and the like must give not-a-number (which the
// iteration loop discards) on every driver, so the variations of ids 49+ go through these instead of / and log.
float nan() { return uintBitsToFloat(0x7fc00000u); }
float sdiv(float a, float b) { return b == 0.0 ? nan() : a / b; }
vec2 sdiv(vec2 a, float b) { return b == 0.0 ? vec2(nan()) : a / b; }
float slog(float a) { return a > 0.0 ? log(a) : nan(); }
float ssqrt(float a) { return a >= 0.0 ? sqrt(a) : nan(); }
float spow(float x, float e) { return e == 0.0 ? 1.0 : (x > 0.0 ? pow(x, e) : (e > 0.0 ? 0.0 : nan())); }
float remT(float a, float b) { return b == 0.0 ? nan() : a - b * trunc(a / b); }   // remainder, sign follows a
float modAxis(float x, float m) {   // modulus: fold x into [-m, m]
    float W = 2.0 * m;
    if (x > m) return -m + remT(x + m, W);
    if (x < -m) return m - remT(m - x, W);
    return x;
}
float sepAxis(float x, float s, float i) { float a = sqrt(x*x + s*s); return x > 0.0 ? a - i*x : -(a + i*x); }   // separation

// Variation `id` of point p, already multiplied by its weight w. Formulas: Draves & Reckase, "The Fractal Flame
// Algorithm", Appendix A (ids are the paper's numbers; keep in step with FlameVariations). P = the variation's
// parameters, M = (a, b, d, e) and T = (c, f) of the pre-affine for the dependent variations. theta = atan(x/y),
// phi = atan(y/x) (atan2 forms).
vec2 vari(int id, vec2 p, float w, vec4 P, vec4 M, vec2 T) {
    float r2 = dot(p, p) + 1e-9;
    float r = sqrt(r2);
    float th = atan(p.x, p.y);
    float ph = atan(p.y, p.x);
    float q = dot(p, p);   // the exact r^2 (r2 carries a guard against zero)
    vec2 v = p;   // ids with no case below (the reserved ones) act as linear
    switch (id) {
        case 0:  v = p; break;                                                                  // linear
        case 1:  v = sin(p); break;                                                             // sinusoidal
        case 2:  v = p / r2; break;                                                             // spherical
        case 3:  { float s = sin(r2), c = cos(r2); v = vec2(p.x*s - p.y*c, p.x*c + p.y*s); break; } // swirl
        case 4:  v = vec2((p.x - p.y) * (p.x + p.y), 2.0 * p.x * p.y) / r; break;               // horseshoe
        case 5:  v = vec2(th / PI, r - 1.0); break;                                             // polar
        case 6:  v = r * vec2(sin(th + r), cos(th - r)); break;                                 // handkerchief
        case 7:  v = r * vec2(sin(th * r), -cos(th * r)); break;                                // heart
        case 8:  v = (th / PI) * vec2(sin(PI * r), cos(PI * r)); break;                         // disc
        case 9:  v = vec2(cos(th) + sin(r), sin(th) - cos(r)) / r; break;                       // spiral
        case 10: v = vec2(sin(th) / r, r * cos(th)); break;                                     // hyperbolic
        case 11: v = vec2(sin(th) * cos(r), cos(th) * sin(r)); break;                           // diamond
        case 12: { float a = sin(th + r), b = cos(th - r); a = a*a*a; b = b*b*b; v = r * vec2(a + b, a - b); break; } // ex
        case 13: { float om = rnd() < 0.5 ? 0.0 : PI; v = sqrt(r) * vec2(cos(th*0.5 + om), sin(th*0.5 + om)); break; } // julia
        case 14: v = vec2(p.x < 0.0 ? 2.0*p.x : p.x, p.y < 0.0 ? 0.5*p.y : p.y); break;        // bent
        case 15: v = vec2(p.x + M.y * sin(p.y / max(T.x*T.x, 1e-4)), p.y + M.w * sin(p.x / max(T.y*T.y, 1e-4))); break; // waves (b, c, e, f)
        case 16: v = 2.0 / (r + 1.0) * vec2(p.y, p.x); break;                                   // fisheye
        case 17: v = vec2(p.x + T.x * sin(tan(3.0*p.y)), p.y + T.y * sin(tan(3.0*p.x))); break; // popcorn (c, f)
        case 18: v = exp(p.x - 1.0) * vec2(cos(PI*p.y), sin(PI*p.y)); break;                    // exponential
        case 19: v = pow(r, sin(th)) * vec2(cos(th), sin(th)); break;                           // power
        case 20: v = vec2(cos(PI*p.x) * cosh(p.y), -sin(PI*p.x) * sinh(p.y)); break;           // cosine
        case 21: { float c2 = max(T.x*T.x, 1e-4); v = (mod(r + c2, 2.0*c2) - c2 + r*(1.0 - c2)) * vec2(cos(th), sin(th)); break; } // rings (c)
        case 22: { float c2 = max(T.x*T.x, 1e-4), t = PI*c2, a = mod(th + T.y, t) > 0.5*t ? th - 0.5*t : th + 0.5*t; v = r * vec2(cos(a), sin(a)); break; } // fan (c, f)
        case 23: v = r * (P.y + (P.x - P.y) * 0.5 * (sin(P.z*th) + 1.0)) * vec2(cos(th), sin(th)); break; // blob: high, low, waves
        case 24: v = vec2(sin(P.x*p.y) - cos(P.y*p.x), sin(P.z*p.x) - cos(P.w*p.y)); break;     // pdj
        case 25: { float p1 = PI*P.x*P.x, p2 = P.y;                                             // fan2
                   float t = th + p2 - p1 * trunc(2.0*th*p2 / p1);
                   float a = t > 0.5*p1 ? th - 0.5*p1 : th + 0.5*p1;
                   v = r * vec2(sin(a), cos(a)); break; }
        case 26: { float q = P.x*P.x;                                                           // rings2
                   float t = r - 2.0*q*trunc((r + q) / (2.0*q)) + r*(1.0 - q);
                   v = t * vec2(sin(th), cos(th)); break; }
        case 27: v = 2.0 / (r + 1.0) * p; break;                                                // eyefish
        case 28: v = 4.0 / (r2 + 4.0) * p; break;                                               // bubble
        case 29: v = vec2(sin(p.x), p.y); break;                                                // cylinder
        case 30: v = P.y / (P.y - p.y*sin(P.x)) * vec2(p.x, p.y*cos(P.x)); break;               // perspective: angle, dist
        case 31: { float a = 2.0*PI*rnd(); v = rnd() * vec2(p.x*cos(a), p.y*sin(a)); break; }   // noise
        case 32: { float p3 = trunc(abs(P.x)*rnd()), t = (ph + 2.0*PI*p3) / P.x;               // juliaN: power, dist
                   v = pow(r, P.y / P.x) * vec2(cos(t), sin(t)); break; }
        case 33: { float lam = rnd() < 0.5 ? -1.0 : 1.0, p3 = trunc(abs(P.x)*rnd()), t = (lam*ph + 2.0*PI*p3) / P.x; // juliaScope
                   v = pow(r, P.y / P.x) * vec2(cos(t), sin(t)); break; }
        case 34: { float a = 2.0*PI*rnd(); v = rnd() * vec2(cos(a), sin(a)); break; }           // blur
        case 35: { float g = rnd() + rnd() + rnd() + rnd() - 2.0, a = 2.0*PI*rnd(); v = g * vec2(cos(a), sin(a)); break; } // gaussian
        case 36: { float p1 = P.x*PI*0.5;                                                       // radialBlur: angle
                   float t1 = w * (rnd() + rnd() + rnd() + rnd() - 2.0), t2 = ph + t1*sin(p1), t3 = t1*cos(p1) - 1.0;
                   v = vec2(r*cos(t2) + t3*p.x, r*sin(t2) + t3*p.y) / w; break; }
        case 37: { float t1 = trunc(rnd()*P.x + 0.5), t2 = P.y + 2.0*PI/P.x * (t1 + rnd()*P.z); // pie: slices, rotation, thickness
                   v = rnd() * vec2(cos(t2), sin(t2)); break; }
        case 38: { float p2 = 2.0*PI/P.y;                                                       // ngon: power, sides, corners, circle
                   float t3 = ph - p2*floor(ph/p2), t4 = t3 > 0.5*p2 ? t3 : t3 - p2;
                   v = (P.z*(1.0/cos(t4) - 1.0) + P.w) / pow(r, P.x) * p; break; }
        case 39: { float t1 = 1.0 + P.x*p.x + P.y*(p.x*p.x - p.y*p.y), t2 = P.x*p.y + 2.0*P.y*p.x*p.y; // curl: c1, c2
                   v = vec2(p.x*t1 + p.y*t2, p.y*t1 - p.x*t2) / (t1*t1 + t2*t2 + 1e-9); break; }
        case 40: v = vec2((2.0*floor(p.x/P.x) + 1.0)*P.x - p.x, (2.0*floor(p.y/P.y) + 1.0)*P.y - p.y); break; // rectangles: x, y
        case 41: { float a = rnd()*PI*w; v = vec2(sin(a), sin(a)*sin(a)/cos(a)); break; }       // arch
        case 42: v = vec2(sin(p.x)/cos(p.y), tan(p.y)); break;                                  // tangent
        case 43: v = vec2(rnd() - 0.5, rnd() - 0.5); break;                                     // square
        case 44: v = w * tan(rnd()*PI*w) / r2 * vec2(cos(p.x), sin(p.y)); break;                // rays
        case 45: { float a = rnd()*r*w; v = p.x * vec2(cos(a) + sin(a), cos(a) - sin(a)); break; } // blade
        case 46: { float c = cos(w*r); v = vec2(p.x, c < 0.0 ? 1.0/c + 1.0 : 1.0/c - 1.0); break; } // secant2
        case 47: { float a = rnd()*r*w, s2 = sin(a)*sin(a);                                         // twintrian
                   float t = s2 > 1e-30 ? log(s2) / log(10.0) + cos(a) : -30.0;
                   v = p.x * vec2(t, t - PI*sin(a)); break; }
        case 48: v = p / sqrt(pow(p.x*p.x - p.y*p.y, 2.0) + 1e-10); break;                                // cross
        case 49: { float tw = P.y, s = sin(tw), c = cos(tw) - 1.0;                              // disc2: rot, twist
                   if (tw > 2.0*PI) { float k = 1.0 + tw - 2.0*PI; s *= k; c *= k; }
                   else if (tw < -2.0*PI) { float k = 1.0 + tw + 2.0*PI; s *= k; c *= k; }
                   float t = PI * P.x * (p.x + p.y), R = th / PI; v = R * vec2(sin(t) + c, cos(t) + s); break; }
        case 51: { float k = (rnd() - P.y) * cos(P.x * ph) / r; v = k * p; break; }             // flower: petals, holes
        case 52: { float e = P.x, k = sdiv((rnd() - P.y) * e, (1.0 + e * p.x / r) * r); v = k * p; break; } // conic: eccentricity, holes
        case 53: { float s = sin(r); v = vec2(P.x * s * s * rnd(), P.y * cos(r) * rnd()); break; } // parabola: height, width
        case 54: v = vec2(p.x < 0.0 ? P.x * p.x : p.x, p.y < 0.0 ? P.y * p.y : p.y); break;     // bent2: x, y
        case 55: { float T = q + 1.0, ps = 0.5 * atan(2.0*p.y, q - 1.0) - 0.5*PI*P.x;           // bipolar: shift (the 1e-6 keeps psi = pi/2 exactly on the axis from flipping to -pi/2)
                   if (ps > 0.5*PI + 1e-6) ps = -0.5*PI + remT(ps + 0.5*PI, PI);
                   else if (ps < -0.5*PI - 1e-6) ps = 0.5*PI - remT(0.5*PI - ps, PI);
                   v = vec2(slog(sdiv(T + 2.0*p.x, T - 2.0*p.x)) / (2.0*PI), (2.0/PI) * ps); break; }
        case 56: { vec2 pr = roundEven(p), d = p - pr;                                           // boarders
                   if (rnd() >= 0.75) v = 0.5 * d + pr;
                   else if (abs(d.x) >= abs(d.y)) { float s = d.x >= 0.0 ? 1.0 : -1.0; v = vec2(0.5*d.x + pr.x + 0.25*s, 0.5*d.y + pr.y + 0.25*s*sdiv(d.y, d.x)); }
                   else { float s = d.y >= 0.0 ? 1.0 : -1.0; v = vec2(0.5*d.x + pr.x + 0.25*s*sdiv(d.x, d.y), 0.5*d.y + pr.y + 0.25*s); }
                   break; }
        case 57: { float R = 1.3029400317 * sqrt(abs(p.x*p.y) / (p.x*p.x + 4.0*p.y*p.y + 1e-10)); v = vec2(R*p.x, 2.0*R*p.y); break; } // butterfly
        case 58: { float s = P.x, fi = floor(sdiv(p.x, s)), fj = floor(sdiv(p.y, s));           // cell: size
                   float dx = p.x - fi*s, dy = p.y - fj*s;
                   float ii = fi >= 0.0 ? 2.0*fi : -(2.0*fi + 1.0), jj = fj >= 0.0 ? 2.0*fj : -(2.0*fj + 1.0);
                   v = vec2(dx + ii*s, -(dy + jj*s)); break; }
        case 59: { float pw = P.z, k = floor(pw * rnd()), L = 0.5 * slog(q);                    // cpow: r, i, power
                   float mu = sdiv(P.x, pw), nu = sdiv(P.y, pw), al = mu*ph + nu*L + sdiv(2.0*PI*k, pw);
                   v = exp(mu*L - nu*ph) * vec2(cos(al), sin(al)); break; }
        case 60: { float lx = max(P.z*P.z, 1e-20), ly = max(P.w*P.w, 1e-20);                    // curve: xamp, yamp, xlength, ylength
                   v = vec2(p.x + P.x * exp(-p.y*p.y / lx), p.y + P.y * exp(-p.x*p.x / ly)); break; }
        case 61: { float T = q + 1.0, a = sqrt(max(T + 2.0*p.x, 0.0)), b = sqrt(max(T - 2.0*p.x, 0.0)), X = max(0.5*(a + b), 1.0); // edisc (X >= 1 and |x/X| <= 1 are clamped: float rounding hits them often)
                   float a1 = log(X + sqrt(X - 1.0)), a2 = -acos(clamp(p.x / X, -1.0, 1.0)), sg = p.y > 0.0 ? -sin(a1) : sin(a1);
                   v = (1.0 / 11.57034632) * vec2(cosh(a2) * cos(a1), sinh(a2) * sg); break; }
        case 62: { float T = q + 1.0, X = 0.5*(sqrt(max(T + 2.0*p.x, 0.0)) + sqrt(max(T - 2.0*p.x, 0.0))); // elliptic
                   float a = p.x / X, B = sqrt(max(0.0, 1.0 - a*a)), S = sqrt(max(0.0, X - 1.0)), l = log(X + S);
                   v = (2.0 / PI) * vec2(atan(a, B), p.y > 0.0 ? l : -l); break; }
        case 63: { float L = 0.5 * slog(q), mu = 0.5 * (1.0 + cos(P.x)), nu = 0.5 * sin(P.x);   // escher: beta
                   float al = mu*ph + nu*L; v = exp(mu*L - nu*ph) * vec2(cos(al), sin(al)); break; }
        case 64: { float D = sdiv(1.0, cosh(p.x) - cos(p.y)); v = D * vec2(sinh(p.x), sin(p.y)); break; } // foci
        case 66: { float v2 = w*w, R = q < v2 ? w * sqrt(sdiv(v2, q) - 1.0) : w; return R * p; } // loonie: v is also the radius, so the output is finished here
        case 68: v = vec2(modAxis(p.x, P.x), modAxis(p.y, P.y)); break;                         // modulus: x, y
        case 69: { float c = P.z * cos(2.0*PI * P.y * p.x);                                     // oscope: separation, frequency, amplitude, damping
                   float t = (P.w == 0.0 ? c : c * exp(-abs(p.x) * P.w)) + P.x;
                   v = vec2(p.x, abs(p.y) <= t ? -p.y : p.y); break; }
        case 70: v = (1.0 / PI) * vec2(th, 0.5 * slog(q)); break;                               // polar2
        case 71: v = vec2(p.x + P.x * sin(tan(P.z * p.y)), p.y + P.y * sin(tan(P.z * p.x))); break; // popcorn2: x, y, c
        case 72: { float rt = sqrt(q); return sdiv(p, rt * (q + sdiv(1.0, w + 1e-10))); }       // scry: v is not a plain output factor
        case 73: v = vec2(sepAxis(p.x, P.x, P.y), sepAxis(p.y, P.z, P.w)); break;               // separation: x, xinside, y, yinside
        case 74: v = vec2(cos(PI*p.y*P.y) >= 0.0 ? p.x : -p.x, cos(PI*p.x*P.x) >= 0.0 ? p.y : -p.y); break; // split: xsize, ysize (each axis flips the other)
        case 75: v = vec2(p.x >= 0.0 ? p.x + P.x : p.x - P.x, p.y >= 0.0 ? p.y + P.y : p.y - P.y); break; // splits: x, y
        case 76: { float xr = floor(p.x + 0.5), d = p.x - xr; v = vec2(d * (1.0 - P.x) + xr, p.y + d*d*P.y); break; } // stripes: space, warp
        case 77: { float al = ph + P.w*r, k = floor((P.z*al + PI) / (2.0*PI)), F = 1.0 - P.x*P.z / (2.0*PI), a2 = al*F + k*P.x; // wedge: angle, hole, count, swirl
                   v = (r + P.y) * vec2(cos(a2), sin(a2)); break; }
        case 78: { float pw = P.x, j = trunc(abs(pw) * rnd()), al = sdiv(ph + 2.0*PI*j, pw);   // wedge_julia: power, dist, count, angle
                   float k = floor((P.z*al + PI) / (2.0*PI)), F = 1.0 - P.w*P.z / (2.0*PI), a2 = al*F + k*P.w;
                   v = spow(q, sdiv(P.y, 2.0*pw)) * vec2(cos(a2), sin(a2)); break; }
        case 79: { float Q = 1.0 / (sqrt(q) + 1e-10), al = ph + P.w*Q;                          // wedge_sph: angle, hole, count, swirl
                   float k = floor((P.z*al + PI) / (2.0*PI)), F = 1.0 - P.x*P.z / (2.0*PI), a2 = al*F + k*P.x;
                   v = (Q + P.y) * vec2(cos(a2), sin(a2)); break; }
        case 80: { float al = ph + sdiv(r < w ? P.x : P.y, w - r); return w*r * vec2(cos(al), sin(al)); } // whorl: inside, outside; v is also the switching radius
        case 81: v = vec2(p.x + P.x * sin(p.y * P.z), p.y + P.y * sin(p.x * P.w)); break;       // waves2: scalex, scaley, freqx, freqy
        case 82: v = exp(p.x) * vec2(cos(p.y), sin(p.y)); break;                                // exp
        case 83: v = vec2(0.5 * slog(q), ph); break;                                            // log
        case 84: v = vec2(sin(p.x) * cosh(p.y), cos(p.x) * sinh(p.y)); break;                   // sin
        case 85: v = vec2(cos(p.x) * cosh(p.y), -sin(p.x) * sinh(p.y)); break;                  // cos
        case 86: { float d = cos(2.0*p.x) + cosh(2.0*p.y); v = sdiv(vec2(sin(2.0*p.x), sinh(2.0*p.y)), d); break; } // tan
        case 87: { float d = cos(2.0*p.x) + cosh(2.0*p.y); v = sdiv(2.0 * vec2(cos(p.x) * cosh(p.y), sin(p.x) * sinh(p.y)), d); break; } // sec
        case 88: { float d = cosh(2.0*p.y) - cos(2.0*p.x); v = sdiv(2.0 * vec2(sin(p.x) * cosh(p.y), -cos(p.x) * sinh(p.y)), d); break; } // csc
        case 89: { float d = cosh(2.0*p.y) - cos(2.0*p.x); v = sdiv(vec2(sin(2.0*p.x), -sinh(2.0*p.y)), d); break; } // cot
        case 90: v = vec2(sinh(p.x) * cos(p.y), cosh(p.x) * sin(p.y)); break;                   // sinh
        case 91: v = vec2(cosh(p.x) * cos(p.y), sinh(p.x) * sin(p.y)); break;                   // cosh
        case 92: { float d = cos(2.0*p.y) + cosh(2.0*p.x); v = sdiv(vec2(sinh(2.0*p.x), sin(2.0*p.y)), d); break; } // tanh
        case 93: { float d = cos(2.0*p.y) + cosh(2.0*p.x); v = sdiv(2.0 * vec2(cosh(p.x) * cos(p.y), -sinh(p.x) * sin(p.y)), d); break; } // sech
        case 94: { float d = cosh(2.0*p.x) - cos(2.0*p.y); v = sdiv(2.0 * vec2(sinh(p.x) * cos(p.y), -cosh(p.x) * sin(p.y)), d); break; } // csch
        case 95: { float d = cosh(2.0*p.x) - cos(2.0*p.y); v = sdiv(vec2(sinh(2.0*p.x), sin(2.0*p.y)), d); break; } // coth: +sin 2y as in the original program, not the textbook -sin 2y
        case 96: { float s = sin(P.x * p.x), u = sin(P.x * p.y);                                // auger: freq, weight, sym, scale
                   float dy = p.y + P.y * (P.w*s*0.5 + abs(p.y)*s), dx = p.x + P.y * (P.w*u*0.5 + abs(p.x)*u);
                   v = vec2(p.x + P.z * (dx - p.x), dy); break; }
        case 97: { float ap = p.x + w, am = p.x - w, y2 = p.y*p.y;                              // flux: spread; v also places the two foci
                   float R = w * (2.0 + P.x) * spow(sdiv(y2 + ap*ap, y2 + am*am), 0.25), al = 0.5 * (atan(p.y, am) - atan(p.y, ap));
                   return R * vec2(cos(al), sin(al)); }
    }
    return w * v;
}

// pre-affine -> weighted variation slots -> post-affine, for packed xform k
vec2 applyXf(int k, vec2 p) {
    int b = k * ${FlameGenome.VEC4_PER_XF};
    vec4 m = uXf[b + 1];
    vec4 t = uXf[b + 2];
    vec2 q = vec2(m.x * p.x + m.y * p.y + t.x, m.z * p.x + m.w * p.y + t.y);
    vec2 acc = vec2(0.0);
    for (int j = 0; j < ${FlameGenome.SLOTS}; j++) {
        int sb = b + 4 + j * ${FlameGenome.VEC4_PER_SLOT};
        vec4 s = uXf[sb];   // (idA, wA, idB, wB): B is the morph target's slot, weight 0 outside a morph
        if (s.y != 0.0) acc += vari(int(s.x + 0.5), q, s.y, uXf[sb + 1], m, t.xy);
        if (s.w != 0.0) acc += vari(int(s.z + 0.5), q, s.w, uXf[sb + 2], m, t.xy);
    }
    vec4 pm = uXf[b + 3];
    return vec2(pm.x * acc.x + pm.y * acc.y + t.z, pm.z * acc.x + pm.w * acc.y + t.w);
}
"""

        const val ITERATE_FRAG = """#version 330 core
out vec4 o;
uniform sampler2D uState;
uniform int uN;
uniform int uFrame;
uniform int uNumXf;
""" + COMMON + """
void main() {
    ivec2 c = ivec2(gl_FragCoord.xy);
    vec4 s = texelFetch(uState, c, 0);
    gRng = pcg(uint(c.x + c.y * uN) ^ (uint(uFrame) * 2654435761u));

    // reseed diverged / non-finite points
    if (!(abs(s.x) < 1.0e4 && abs(s.y) < 1.0e4)) {
        s = vec4(rnd() * 2.0 - 1.0, rnd() * 2.0 - 1.0, rnd(), 0.0);
    }

    float tot = 0.0;
    for (int i = 0; i < uNumXf; i++) tot += uXf[i * ${FlameGenome.VEC4_PER_XF}].x;
    float r = rnd() * tot;
    int k = uNumXf - 1;
    float acc = 0.0;
    for (int i = 0; i < uNumXf; i++) {
        acc += uXf[i * ${FlameGenome.VEC4_PER_XF}].x;
        if (r < acc) { k = i; break; }
    }

    vec4 info = uXf[k * ${FlameGenome.VEC4_PER_XF}];
    float col = mix(s.z, info.y, info.z);
    o = vec4(applyXf(k, s.xy), col, min(s.w + 1.0, 1.0e6));
}
"""

        const val SPLAT_VERT = """#version 330 core
uniform sampler2D uState;
uniform int uN;
uniform int uHasFinal;
uniform float uBurn;
uniform float uZoom;
uniform float uCos;
uniform float uSin;
uniform float uFlipY;
uniform vec2 uCenter;
uniform float uAspect;
uniform float uSize;
uniform int uFrame;
out float vCol;
""" + COMMON + """
void main() {
    gRng = pcg(uint(gl_VertexID) ^ (uint(uFrame) * 2654435761u));
    vec4 s = texelFetch(uState, ivec2(gl_VertexID % uN, gl_VertexID / uN), 0);
    vec2 pos = uHasFinal != 0 ? applyXf(${FlameGenome.FINAL_INDEX}, s.xy) : s.xy;   // final xform: drawn position only
    vec2 p = pos - uCenter;
    p = vec2(uCos * p.x - uSin * p.y, uSin * p.x + uCos * p.y) * uZoom;
    p.y *= uFlipY;   // flames from other programs have y pointing down
    p.x /= uAspect;
    bool ok = s.w >= uBurn && abs(p.x) < 2.0 && abs(p.y) < 2.0;
    gl_Position = ok ? vec4(p, 0.0, 1.0) : vec4(2.0, 2.0, 2.0, 1.0);
    gl_PointSize = uSize;
    vCol = s.z;
}
"""

        const val SPLAT_FRAG = """#version 330 core
uniform sampler2D uPal;
uniform float uPalShift;
uniform float uSharp;
in float vCol;
out vec4 o;

void main() {
    vec2 d = gl_PointCoord * 2.0 - 1.0;
    float r2 = dot(d, d);
    if (r2 > 1.0) discard;
    float w = exp(-r2 * uSharp);
    vec3 rgb = texture(uPal, vec2(vCol + uPalShift, 0.5)).rgb;
    o = vec4(rgb * w, w);
}
"""
    }
}
