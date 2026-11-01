package llm.slop.liquidlsd.rendering

import mu.KotlinLogging
import org.lwjgl.opengl.GL33.*
import org.lwjgl.system.MemoryUtil
import java.nio.FloatBuffer

private val logger = KotlinLogging.logger {}

/**
 * Audio textures for foreign shaders, refreshed together from one copy of the analysis.
 *
 * [textureId] is the Shadertoy-convention 512x2 texture behind the fixed names `audioFFT` and `iChannel0`:
 * row 0 (y = 0.25) holds 512 FFT magnitude bins (0..1), row 1 (y = 0.75) 512 waveform samples (0..1, silence 0.5).
 *
 * [isfWaveTextureId] and [isfFftTextureId] are 512x1 textures for ISF `audio` and `audioFFT` inputs, which the
 * ISF spec gives one image each with one row per channel; shaders sample them at y = 0, 0.5 or a channel index,
 * which on the stacked texture would read the wrong row or a blend of both. The analysis is mono, so every channel
 * reads the same row. An input's `MAX` (a cap on width) is not applied: shaders read the size with `IMG_SIZE`.
 *
 * Each texture lives on its own fixed unit ([UNIT_STACKED], [UNIT_ISF_WAVE], [UNIT_ISF_FFT]) at the top of the
 * 16 units GL 3.3 guarantees, clear of the pass, import and input-image units that sources and FX count up from 0.
 */
class AudioTexture {
    val textureId: Int
    val isfWaveTextureId: Int
    val isfFftTextureId: Int
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
        isfWaveTextureId = createRow("AudioTexture ISF wave")
        isfFftTextureId = createRow("AudioTexture ISF FFT")
        active = this
    }

    private fun createRow(label: String): Int {
        val id = glGenTextures()
        glBindTexture(GL_TEXTURE_2D, id)
        glTexImage2D(GL_TEXTURE_2D, 0, GL_R32F, 512, 1, 0, GL_RED, GL_FLOAT, buffer)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE)
        glBindTexture(GL_TEXTURE_2D, 0)
        GLResourceTracker.register(id, label)
        return id
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
        // ISF rows: the same values, row 0 of the buffer for FFT, row 1 for the waveform
        glBindTexture(GL_TEXTURE_2D, isfFftTextureId)
        glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, 512, 1, GL_RED, GL_FLOAT, buffer)
        buffer.position(512)
        glBindTexture(GL_TEXTURE_2D, isfWaveTextureId)
        glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, 512, 1, GL_RED, GL_FLOAT, buffer)
        buffer.position(0)
        glBindTexture(GL_TEXTURE_2D, 0)
    }

    /** Binds all three textures to their fixed units. Leaves unit 0 active. */
    fun bindAll() {
        if (isDisposed) return
        glActiveTexture(GL_TEXTURE0 + UNIT_STACKED); glBindTexture(GL_TEXTURE_2D, textureId)
        glActiveTexture(GL_TEXTURE0 + UNIT_ISF_WAVE); glBindTexture(GL_TEXTURE_2D, isfWaveTextureId)
        glActiveTexture(GL_TEXTURE0 + UNIT_ISF_FFT); glBindTexture(GL_TEXTURE_2D, isfFftTextureId)
        glActiveTexture(GL_TEXTURE0)
    }

    fun dispose() {
        if (!isDisposed) {
            glDeleteTextures(textureId)
            glDeleteTextures(isfWaveTextureId)
            glDeleteTextures(isfFftTextureId)
            MemoryUtil.memFree(buffer)
            isDisposed = true
            GLResourceTracker.unregister(textureId)
            GLResourceTracker.unregister(isfWaveTextureId)
            GLResourceTracker.unregister(isfFftTextureId)
            if (active === this) active = null
            logger.debug { "Disposed AudioTexture $textureId" }
        }
    }

    companion object {
        const val UNIT_STACKED = 13
        const val UNIT_ISF_WAVE = 14
        const val UNIT_ISF_FFT = 15

        /** The renderer's instance, so ISF FX (which render without the renderer) can bind the same textures. */
        @Volatile var active: AudioTexture? = null
            private set
    }
}
