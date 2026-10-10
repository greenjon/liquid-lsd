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

    init {
        resetPoints()
        palette = glGenTextures()
        val px = FloatArray(256 * 4)
        for (i in 0 until 256) {
            val t = i / 255f
            px[i * 4] = 0.5f + 0.5f * cos((2.0 * PI * (t + 0.00)).toFloat())
            px[i * 4 + 1] = 0.5f + 0.5f * cos((2.0 * PI * (t + 0.33)).toFloat())
            px[i * 4 + 2] = 0.5f + 0.5f * cos((2.0 * PI * (t + 0.67)).toFloat())
            px[i * 4 + 3] = 1f
        }
        glBindTexture(GL_TEXTURE_2D, palette)
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, 256, 1, 0, GL_RGBA, GL_FLOAT, px)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_REPEAT)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE)
        glBindTexture(GL_TEXTURE_2D, 0)
        logger.warn { "Flame: ${n * n} points, density ${if (density32) "RGBA32F" else "RGBA16F"}" }
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

        val rows = ceil(v("Points", 1f).coerceIn(0.05f, 1f) * n).toInt().coerceIn(1, n)
        val hasFinal = !FlameGenome.finalIsIdentity(genome)
        val decay = v("Decay", 0f)
        val passes = v("Passes", 4f).toInt().coerceIn(1, 16)
        lastPasses = passes
        lastKernel = v("Kernel", 3f)
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
            splat.setUniform("uZoom", zoom)
            splat.setUniform("uCos", cos(rot)); splat.setUniform("uSin", sin(rot))
            splat.setUniform("uCenter", v("CenterX", 0f), v("CenterY", 0f))
            splat.setUniform("uAspect", aspect)
            splat.setUniform("uSize", v("Kernel", 3f))
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
        gpuNsSum += glGetQueryObjecti64(queries[prev * 2 + 1], GL_QUERY_RESULT) -
            glGetQueryObjecti64(queries[prev * 2], GL_QUERY_RESULT)
        queryUsed[prev] = false
        gpuSamples++
        if (++frames % 240 == 0 && gpuSamples > 0) {
            logger.warn { "Flame: n=%d (%d pts) passes=%d kernel=%.1f %s skip=%s: gpu %.2f ms/frame (avg of %d)".format(n, n * n, lastPasses, lastKernel, if (density32) "fp32" else "fp16", skip.ifEmpty { "none" }, gpuNsSum / 1e6 / gpuSamples, gpuSamples) }
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

    private companion object {
        const val FULLSCREEN_VERT = """#version 330 core
layout(location = 0) in vec2 aPosition;
void main() { gl_Position = vec4(aPosition, 0.0, 1.0); }
"""

        /** Shared by the iterate and splat shaders: the packed genome (see [FlameGenome.pack]) and the variation set. */
        const val COMMON = """
uniform vec4 uXf[${FlameGenome.MAX_XF + 1}*${FlameGenome.VEC4_PER_XF}];
const float PI = 3.14159265;

vec2 vari(int id, vec2 p) {
    float r2 = dot(p, p) + 1e-9;
    float r = sqrt(r2);
    float th = atan(p.x, p.y);
    switch (id) {
        case 0:  return p;                                                              // linear
        case 1:  return sin(p);                                                         // sinusoidal
        case 2:  return p / r2;                                                         // spherical
        case 3:  { float s = sin(r2), c = cos(r2); return vec2(p.x*s - p.y*c, p.x*c + p.y*s); } // swirl
        case 4:  return vec2((p.x - p.y) * (p.x + p.y), 2.0 * p.x * p.y) / r;           // horseshoe
        case 5:  return vec2(th / PI, r - 1.0);                                         // polar
        case 6:  return r * vec2(sin(th + r), cos(th - r));                             // handkerchief
        case 7:  return r * vec2(sin(th * r), -cos(th * r));                            // heart
        case 8:  return (th / PI) * vec2(sin(PI * r), cos(PI * r));                     // disc
        case 9:  return vec2(cos(th) + sin(r), sin(th) - cos(r)) / r;                   // spiral
        case 10: return vec2(sin(th) / r, r * cos(th));                                 // hyperbolic
        case 11: return vec2(sin(th) * cos(r), cos(th) * sin(r));                       // diamond
    }
    return p;
}

// pre-affine -> weighted variation slots -> post-affine, for packed xform k
vec2 applyXf(int k, vec2 p) {
    int b = k * ${FlameGenome.VEC4_PER_XF};
    vec4 m = uXf[b + 1];
    vec4 t = uXf[b + 2];
    vec2 q = vec2(m.x * p.x + m.y * p.y + t.x, m.z * p.x + m.w * p.y + t.y);
    vec2 acc = vec2(0.0);
    for (int j = 0; j < ${FlameGenome.SLOTS}; j++) {
        vec4 s = uXf[b + 4 + j];   // (idA, wA, idB, wB): B is the morph target's slot, weight 0 outside a morph
        if (s.y != 0.0) acc += s.y * vari(int(s.x + 0.5), q);
        if (s.w != 0.0) acc += s.w * vari(int(s.z + 0.5), q);
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
uint pcg(uint v) {
    uint s = v * 747796405u + 2891336453u;
    uint w = ((s >> ((s >> 28u) + 4u)) ^ s) * 277803737u;
    return (w >> 22u) ^ w;
}
float rnd(inout uint st) { st = pcg(st); return float(st) * (1.0 / 4294967296.0); }

void main() {
    ivec2 c = ivec2(gl_FragCoord.xy);
    vec4 s = texelFetch(uState, c, 0);
    uint st = pcg(uint(c.x + c.y * uN) ^ (uint(uFrame) * 2654435761u));

    // reseed diverged / non-finite points
    if (!(abs(s.x) < 1.0e4 && abs(s.y) < 1.0e4)) {
        s = vec4(rnd(st) * 2.0 - 1.0, rnd(st) * 2.0 - 1.0, rnd(st), 0.0);
    }

    float tot = 0.0;
    for (int i = 0; i < uNumXf; i++) tot += uXf[i * ${FlameGenome.VEC4_PER_XF}].x;
    float r = rnd(st) * tot;
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
uniform vec2 uCenter;
uniform float uAspect;
uniform float uSize;
out float vCol;
""" + COMMON + """
void main() {
    vec4 s = texelFetch(uState, ivec2(gl_VertexID % uN, gl_VertexID / uN), 0);
    vec2 pos = uHasFinal != 0 ? applyXf(${FlameGenome.FINAL_INDEX}, s.xy) : s.xy;   // final xform: drawn position only
    vec2 p = pos - uCenter;
    p = vec2(uCos * p.x - uSin * p.y, uSin * p.x + uCos * p.y) * uZoom;
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
