package llm.slop.liquidlsd.rendering

import mu.KotlinLogging
import org.lwjgl.opengl.GL33.*
import org.lwjgl.system.MemoryUtil
import java.nio.FloatBuffer

private val logger = KotlinLogging.logger {}

/**
 * Universal 512x2 audio texture for foreign shaders (Shadertoy / ISF / GLSLSandbox).
 * Row 0 (y = 0.25): 512 normalized frequency FFT magnitude bins (0.0 to 1.0)
 * Row 1 (y = 0.75): 512 normalized audio waveform samples (0.0 to 1.0, silence centered at 0.5)
 */
class AudioTexture {
    val textureId: Int
    private val buffer: FloatBuffer = MemoryUtil.memAllocFloat(512 * 2)
    private val rawFft = FloatArray(512)
    private val rawWaveform = FloatArray(512)
    private var isDisposed = false

    init {
        textureId = glGenTextures()
        glBindTexture(GL_TEXTURE_2D, textureId)
        // Zero initialize buffer
        for (i in 0 until 1024) {
            buffer.put(i, 0f)
        }
        glTexImage2D(GL_TEXTURE_2D, 0, GL_R32F, 512, 2, 0, GL_RED, GL_FLOAT, buffer)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE)
        glBindTexture(GL_TEXTURE_2D, 0)

        GLResourceTracker.register(textureId, "AudioTexture")
    }

    fun updateFromAudioEngine(audioEngine: llm.slop.liquidlsd.audio.AudioEngine) {
        if (isDisposed) return

        // 1. Copy audio data from AudioEngine (zero heap allocation)
        audioEngine.copyAudioTextureData(rawFft, rawWaveform)

        // 2. Populate FloatBuffer: Row 0 = FFT, Row 1 = Waveform
        // Row 0: Normalized FFT (0.0 to 1.0)
        for (i in 0 until 512) {
            val mag = rawFft[i]
            buffer.put(i, mag.coerceIn(0f, 1f))
        }

        // Row 1: Waveform (centered at 0.5: range 0.0 to 1.0)
        for (i in 0 until 512) {
            val sample = rawWaveform[i]
            val normSample = (sample * 0.5f + 0.5f).coerceIn(0f, 1f)
            buffer.put(512 + i, normSample)
        }

        // 3. Upload to GPU on Thread 0
        glBindTexture(GL_TEXTURE_2D, textureId)
        glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, 512, 2, GL_RED, GL_FLOAT, buffer)
        glBindTexture(GL_TEXTURE_2D, 0)
    }

    fun bind(unit: Int) {
        if (isDisposed) return
        glActiveTexture(GL_TEXTURE0 + unit)
        glBindTexture(GL_TEXTURE_2D, textureId)
    }

    fun dispose() {
        if (!isDisposed) {
            glDeleteTextures(textureId)
            MemoryUtil.memFree(buffer)
            isDisposed = true
            GLResourceTracker.unregister(textureId)
            logger.debug { "Disposed AudioTexture $textureId" }
        }
    }
}
