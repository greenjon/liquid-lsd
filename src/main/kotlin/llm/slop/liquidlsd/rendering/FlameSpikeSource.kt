package llm.slop.liquidlsd.rendering

import llm.slop.liquidlsd.parameters.ModulatableParameter
import mu.KotlinLogging
import org.lwjgl.opengl.GL33.*
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private val logger = KotlinLogging.logger {}

/**
 * THROWAWAY SPIKE for the fractal-flame source (see .planning/flame-source-plan.md, phase 0).
 *
 * A chaos-game renderer on OpenGL 3.3 core, no compute:
 *  1. a ping-pong RGBA32F state texture holds one IFS point per texel (x, y, colour index, age);
 *  2. an iterate pass advances every point one step (xform picked by per-point hash);
 *  3. a splat pass draws one GL_POINT per texel with a Gaussian kernel, additively, into a float density buffer;
 *  4. the deck's tone-map shader (resources/default_sources/flame_spike/shader.frag) turns density into colour.
 * The genome is hardcoded here; the real source will build it from parameters. Math follows the public
 * fractal-flame paper (Draves & Reckase); no flam3 / Electric Sheep / Apophysis code was consulted.
 */
class FlameSpikeSource(
    id: String,
    displayName: String,
    shader: Shader,
    parameters: LinkedHashMap<String, ModulatableParameter>,
    ownsShader: Boolean,
    categories: List<String> = emptyList()
) : DynamicVisualSource(
    id = id,
    displayName = displayName,
    shader = shader,
    parameters = parameters,
    ownsShader = ownsShader,
    categories = categories
) {
    private var engine: FlameEngine? = null

    override fun renderTopology(targetFBO: FBO) {
        val e = engine ?: FlameEngine().also { engine = it }
        e.render(this, targetFBO)
    }

    override fun clear() {
        super.clear()
        engine?.resetPoints()
    }

    override fun dispose() {
        engine?.dispose()
        engine = null
        super.dispose()
    }

    override fun clone(): FlameSpikeSource {
        val clonedParams = LinkedHashMap<String, ModulatableParameter>()
        parameters.forEach { (name, param) -> clonedParams[name] = param.clone() }
        return FlameSpikeSource(id, displayName, shader, clonedParams, ownsShader = false, categories = categories).also {
            it.globalAlpha.set(globalAlpha.baseValue)
        }
    }

    // ---- hardcoded genomes -------------------------------------------------------------------------------------

    private class Xf(
        val weight: Float, val color: Float, val speed: Float,
        val rot: Float, val sx: Float, val sy: Float, val shear: Float, val tx: Float, val ty: Float,
        val v: List<Pair<Int, Float>>
    )

    companion object {
        /** Variation ids shared with the GLSL `vari()` below. */
        private val GENOMES: List<List<Xf>> = listOf(
            listOf(
                Xf(1.0f, 0.00f, 0.6f, 0.35f, 0.65f, 0.65f, 0.0f, 0.45f, 0.10f, listOf(1 to 0.7f, 3 to 0.5f)),
                Xf(1.0f, 0.45f, 0.6f, -1.0f, 0.60f, 0.60f, 0.0f, -0.35f, 0.40f, listOf(2 to 0.35f, 0 to 0.65f)),
                Xf(0.8f, 1.00f, 0.6f, 2.10f, 0.55f, 0.50f, 0.2f, 0.00f, -0.55f, listOf(4 to 0.4f, 1 to 0.6f))
            ),
            listOf(
                Xf(1.0f, 0.00f, 0.5f, 0.90f, 0.70f, 0.70f, 0.0f, 0.20f, 0.20f, listOf(6 to 0.8f, 0 to 0.2f)),
                Xf(1.0f, 0.50f, 0.5f, -0.4f, 0.55f, 0.55f, 0.0f, -0.50f, 0.00f, listOf(7 to 0.5f, 3 to 0.5f)),
                Xf(0.6f, 1.00f, 0.5f, 3.00f, 0.60f, 0.60f, 0.0f, 0.00f, -0.40f, listOf(8 to 0.4f, 1 to 0.6f))
            ),
            listOf(
                Xf(1.0f, 0.00f, 0.5f, 0.20f, 0.60f, 0.60f, 0.0f, 0.40f, 0.00f, listOf(9 to 0.5f, 0 to 0.5f)),
                Xf(1.0f, 0.40f, 0.5f, 1.30f, 0.70f, 0.70f, 0.0f, -0.30f, 0.30f, listOf(11 to 0.6f, 1 to 0.4f)),
                Xf(1.0f, 0.80f, 0.5f, -2.2f, 0.55f, 0.55f, 0.0f, 0.00f, -0.50f, listOf(10 to 0.3f, 5 to 0.3f))
            )
        )
    }

    /** Pack the chosen genome, with Spin / Breathe / Warp modifiers applied, into `uXf` (4 vec4 per xform). */
    internal fun buildGenome(out: FloatArray): Int {
        val genome = GENOMES[(parameters["Genome"]?.value ?: 0f).toInt().coerceIn(0, GENOMES.size - 1)]
        val spin = parameters["Spin"]?.value ?: 0f
        val breathe = parameters["Breathe"]?.value ?: 1f
        val warp = parameters["Warp"]?.value ?: 1f
        genome.forEachIndexed { i, x ->
            val rot = x.rot + spin * (if (i % 2 == 0) 1f else -1f) * (1f + 0.3f * i)
            val c = cos(rot); val s = sin(rot)
            val sx = x.sx * breathe; val sy = x.sy * breathe
            val o = i * 16
            out[o] = x.weight; out[o + 1] = x.color; out[o + 2] = x.speed; out[o + 3] = 0f
            out[o + 4] = c * sx; out[o + 5] = c * x.shear - s * sy; out[o + 6] = s * sx; out[o + 7] = s * x.shear + c * sy
            out[o + 8] = x.tx; out[o + 9] = x.ty; out[o + 10] = 0f; out[o + 11] = 0f
            val v0 = x.v[0]; val v1 = x.v[1]
            out[o + 12] = v0.first.toFloat(); out[o + 13] = v0.second * (if (v0.first == 0) 1f else warp)
            out[o + 14] = v1.first.toFloat(); out[o + 15] = v1.second * (if (v1.first == 0) 1f else warp)
        }
        return genome.size
    }
}

/** GL resources and passes for one [FlameSpikeSource] instance. Created lazily on the render thread. */
private class FlameEngine {
    private val n = 512                      // state texture side -> n*n points
    private val burn = 16f                   // iterations before a point is drawn
    private val density32 = System.getProperty("flame.density", "fp32") != "fp16"

    private var stateA = FBO(n, n, GL_RGBA32F)
    private var stateB = FBO(n, n, GL_RGBA32F)
    private var density: FBO? = null

    private val iterate = Shader(FULLSCREEN_VERT, ITERATE_FRAG)
    private val splat = Shader(SPLAT_VERT, SPLAT_FRAG)
    private val fill = Shader(FULLSCREEN_VERT, "#version 330 core\nout vec4 o;\nvoid main(){o=vec4(0.0);}")
    private val emptyVao = glGenVertexArrays()
    private val palette: Int
    private val genome = FloatArray(16 * 4)

    // Timestamp pairs, not GL_TIME_ELAPSED: Main already keeps a TIME_ELAPSED query open around the whole frame
    // and only one may be active at a time.
    private val queries = IntArray(4) { glGenQueries() }
    private val queryUsed = BooleanArray(2)
    private var queryIdx = 0
    private var frames = 0
    private var gpuNsSum = 0L
    private var gpuSamples = 0
    private var frameCounter = 0

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
        logger.info { "Flame spike: ${n * n} points, density ${if (density32) "RGBA32F" else "RGBA16F"}" }
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

    fun render(src: FlameSpikeSource, target: FBO) {
        val p = src.parameters
        fun v(name: String, def: Float) = p[name]?.value ?: def

        if (density == null || density!!.width != target.width || density!!.height != target.height) {
            density?.dispose()
            density = FBO(target.width, target.height, if (density32) GL_RGBA32F else GL_RGBA16F)
        }
        val dens = density!!

        collectGpuTime()
        glQueryCounter(queries[queryIdx * 2], GL_TIMESTAMP)

        val numXf = src.buildGenome(genome)
        val decay = v("Decay", 0f)
        val passes = v("Passes", 4f).toInt().coerceIn(1, 16)
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
            glDisable(GL_BLEND)
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
            splat.setUniform("uBurn", burn)
            splat.setUniform("uZoom", zoom)
            splat.setUniform("uCos", cos(rot)); splat.setUniform("uSin", sin(rot))
            splat.setUniform("uCenter", v("CenterX", 0f), v("CenterY", 0f))
            splat.setUniform("uAspect", aspect)
            splat.setUniform("uSize", v("Kernel", 3f))
            splat.setUniform("uSharp", v("Sharpness", 3f))
            splat.setUniform("uPalShift", v("Palette", 0f))
            glBindVertexArray(emptyVao)
            glDrawArrays(GL_POINTS, 0, n * n)
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
            logger.info { "Flame spike: gpu %.2f ms/frame avg over %d frames".format(gpuNsSum / 1e6 / gpuSamples, gpuSamples) }
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

        const val ITERATE_FRAG = """#version 330 core
out vec4 o;
uniform sampler2D uState;
uniform int uN;
uniform int uFrame;
uniform int uNumXf;
uniform vec4 uXf[16];   // per xform: (weight,color,speed,_) (a,b,c,d) (tx,ty,_,_) (id0,w0,id1,w1)
const float PI = 3.14159265;

uint pcg(uint v) {
    uint s = v * 747796405u + 2891336453u;
    uint w = ((s >> ((s >> 28u) + 4u)) ^ s) * 277803737u;
    return (w >> 22u) ^ w;
}
float rnd(inout uint st) { st = pcg(st); return float(st) * (1.0 / 4294967296.0); }

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

void main() {
    ivec2 c = ivec2(gl_FragCoord.xy);
    vec4 s = texelFetch(uState, c, 0);
    uint st = pcg(uint(c.x + c.y * uN) ^ (uint(uFrame) * 2654435761u));

    // reseed diverged / non-finite points
    if (!(abs(s.x) < 1.0e4 && abs(s.y) < 1.0e4)) {
        s = vec4(rnd(st) * 2.0 - 1.0, rnd(st) * 2.0 - 1.0, rnd(st), 0.0);
    }

    float tot = 0.0;
    for (int i = 0; i < uNumXf; i++) tot += uXf[i * 4].x;
    float r = rnd(st) * tot;
    int k = uNumXf - 1;
    float acc = 0.0;
    for (int i = 0; i < uNumXf; i++) {
        acc += uXf[i * 4].x;
        if (r < acc) { k = i; break; }
    }

    vec4 info = uXf[k * 4];
    vec4 m = uXf[k * 4 + 1];
    vec4 t = uXf[k * 4 + 2];
    vec4 vs = uXf[k * 4 + 3];
    vec2 q = vec2(m.x * s.x + m.y * s.y + t.x, m.z * s.x + m.w * s.y + t.y);
    vec2 res = vs.y * vari(int(vs.x + 0.5), q) + vs.w * vari(int(vs.z + 0.5), q);
    float col = mix(s.z, info.y, info.z);
    o = vec4(res, col, min(s.w + 1.0, 1.0e6));
}
"""

        const val SPLAT_VERT = """#version 330 core
uniform sampler2D uState;
uniform int uN;
uniform float uBurn;
uniform float uZoom;
uniform float uCos;
uniform float uSin;
uniform vec2 uCenter;
uniform float uAspect;
uniform float uSize;
out float vCol;

void main() {
    vec4 s = texelFetch(uState, ivec2(gl_VertexID % uN, gl_VertexID / uN), 0);
    vec2 p = s.xy - uCenter;
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
