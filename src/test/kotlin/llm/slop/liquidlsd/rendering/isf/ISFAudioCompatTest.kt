package llm.slop.liquidlsd.rendering.isf

import llm.slop.liquidlsd.rendering.AudioTexture
import llm.slop.liquidlsd.rendering.FBO
import llm.slop.liquidlsd.rendering.Renderer
import llm.slop.liquidlsd.rendering.Shader
import llm.slop.liquidlsd.rendering.SharedHiddenGl
import llm.slop.liquidlsd.utils.TimeSource
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.GL33.*
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Third-party ISF audio visualizers (the Vidvox ISF-Files "Audio Visualizer" set) depend on: `audio`/`audioFFT`
 * inputs bound under their own names, a pass target allowed to share a built-in uniform's name (`time`), MIN > MAX
 * tolerated, and intermediate passes written unblended. GL parts run on a hidden 3.3 context, skipped without one.
 */
class ISFAudioCompatTest {
    private val vert: String by lazy { Shader::class.java.classLoader.getResourceAsStream("shaders/blit.vert")!!.bufferedReader().readText() }

    /** Simulated time keeps the renderer off ImGui (its frame rate) and away from the wall clock. */
    private fun withGl(body: () -> Unit) = SharedHiddenGl.use {
        TimeSource.setSimulatedTime(1.0)
        try { body() } finally { TimeSource.clearSimulatedTime() }
    }

    private fun load(src: String): ISFVisualSource {
        val header = ISFParser.parseHeader(src) ?: ISFParser.createDefaultHeader("t", ISFParser.detectFormat(src))
        val shader = Shader(vert, ISFParser.buildGLSLFragmentShader(src, header))
        return ISFVisualSource("t", "t", shader, header, ISFVisualSource.createParameters(header), ownsShader = true)
    }

    private fun samplerUnit(shader: Shader, name: String): Int =
        glGetUniformi(shader.programId, glGetUniformLocation(shader.programId, name))

    @Test fun invertedMinMaxIsSwapped() {
        val header = ISFParser.parseHeader("""/*{ "INPUTS": [ { "NAME": "size", "TYPE": "float", "MIN": 1, "MAX": 0.05, "DEFAULT": 0.001 } ] }*/ void main() {}""")!!
        val p = ISFVisualSource.createParameters(header)["size"]!!
        assertEquals(0.05f, p.minClamp)
        assertEquals(1f, p.maxClamp)
        assertEquals(0.05f, p.evaluate())
    }

    @Test fun audioInputsAreBoundByTheirOwnNames() = withGl {
        val renderer = Renderer()
        val source = load("""
            /*{ "INPUTS": [ { "NAME": "fftImage", "TYPE": "audioFFT" }, { "NAME": "waveImage", "TYPE": "audio" } ] }*/
            void main() { gl_FragColor = IMG_NORM_PIXEL(fftImage, vec2(0.5, 0.5)) + IMG_NORM_PIXEL(waveImage, vec2(0.5, 0.5)); }
        """.trimIndent())
        val fbo = FBO(32, 32)
        renderer.render(source, fbo)
        source.shader.bind()
        assertEquals(AudioTexture.UNIT_ISF_FFT, samplerUnit(source.shader, "fftImage"))
        assertEquals(AudioTexture.UNIT_ISF_WAVE, samplerUnit(source.shader, "waveImage"))
        assertEquals(GL_NO_ERROR, glGetError())
        fbo.dispose(); source.dispose(); renderer.dispose()
    }

    @Test fun anInputNamedAudioFFTGetsTheOneRowTexture() = withGl {
        val renderer = Renderer()
        val isf = load("""
            /*{ "INPUTS": [ { "NAME": "audioFFT", "TYPE": "audioFFT" } ] }*/
            void main() { gl_FragColor = IMG_NORM_PIXEL(audioFFT, vec2(0.5, 0.5)); }
        """.trimIndent())
        val toy = load("void mainImage(out vec4 c, in vec2 p) { c = texture(iChannel0, vec2(0.5, 0.25)); }")
        val fbo = FBO(32, 32)
        renderer.render(isf, fbo); isf.shader.bind()
        assertEquals(AudioTexture.UNIT_ISF_FFT, samplerUnit(isf.shader, "audioFFT"))
        renderer.render(toy, fbo); toy.shader.bind()
        assertEquals(AudioTexture.UNIT_STACKED, samplerUnit(toy.shader, "iChannel0"))
        fbo.dispose(); isf.dispose(); toy.dispose(); renderer.dispose()
    }

    /** Radial Spectrogram's shape: a 1x1 float pass called `time` accumulating a value with alpha < 1. */
    @Test fun passTargetNamedTimeAccumulatesUnblended() = withGl {
        val renderer = Renderer()
        val source = load("""
            /*{ "PASSES": [ { "TARGET": "time", "PERSISTENT": true, "FLOAT": true, "WIDTH": "1", "HEIGHT": "1" }, {} ] }*/
            void main() {
                float t = IMG_PIXEL(time, vec2(0.0, 0.0)).r;
                if (PASSINDEX == 0) { gl_FragColor = vec4(t + 0.125); return; }
                gl_FragColor = vec4(t, t, t, 1.0);
            }
        """.trimIndent())
        val fbo = FBO(4, 4, GL_RGBA32F)
        repeat(4) { renderer.render(source, fbo) }
        assertEquals(GL_NO_ERROR, glGetError())
        fbo.bind()
        val px = BufferUtils.createFloatBuffer(4)
        glReadPixels(0, 0, 1, 1, GL_RGBA, GL_FLOAT, px)
        fbo.unbind()
        // four unblended passes of +0.125 -> 0.5 (blended, the value would stay near 0.02)
        assertEquals(0.5f, px.get(0), 1e-4f)
        fbo.dispose(); source.dispose(); renderer.dispose()
    }

    /** Waveform Displace's shape: an effect with an image input and an `audio` input. */
    @Test fun effectAudioInputIsBoundAndIsNotAKnob() = withGl {
        val renderer = Renderer() // owns the AudioTexture the effect binds
        val src = """
            /*{ "INPUTS": [ { "NAME": "inputImage", "TYPE": "image" }, { "NAME": "audio", "TYPE": "audio" },
                            { "NAME": "displaceX", "TYPE": "float", "MIN": 0, "MAX": 1, "DEFAULT": 0.1 } ] }*/
            void main() { gl_FragColor = IMG_THIS_NORM_PIXEL(inputImage) + IMG_NORM_PIXEL(audio, vec2(0.5, 0.5)) * displaceX; }
        """.trimIndent()
        val header = ISFParser.parseHeader(src)!!
        val shader = Shader(vert, ISFParser.buildGLSLFragmentShader(src, header))
        val fx = ISFFilter("t", "t", header, shader)
        assertEquals(setOf("displaceX"), fx.parameters.keys)
        val input = FBO(16, 16); val out = FBO(16, 16)
        out.bind(); fx.render(input.texture, 16, 16); out.unbind()
        shader.bind()
        assertEquals(AudioTexture.UNIT_ISF_WAVE, samplerUnit(shader, "audio"))
        assertEquals(GL_NO_ERROR, glGetError())
        input.dispose(); out.dispose(); fx.dispose(); renderer.dispose()
    }
}
