package llm.slop.liquidlsd.rendering.isf

import llm.slop.liquidlsd.rendering.AudioTexture
import llm.slop.liquidlsd.rendering.Shader

/**
 * A shader's ISF `audio` (waveform) and `audioFFT` inputs, pointed at [AudioTexture]'s per-type textures.
 * The input names are the shader's own (`fftImage`, `waveImage`, `audioWave`, ...), resolved once from the header;
 * [apply] only sets sampler uniforms to fixed units, so it allocates nothing.
 */
class ISFAudioInputs(header: ISFHeader) {
    private val names: Array<String>
    private val units: IntArray

    init {
        val audio = header.INPUTS.filter { isAudioType(it.TYPE) }
        names = audio.map { it.NAME }.toTypedArray()
        units = audio.map {
            if (it.TYPE.equals("audioFFT", ignoreCase = true)) AudioTexture.UNIT_ISF_FFT else AudioTexture.UNIT_ISF_WAVE
        }.toIntArray()
    }

    val isEmpty: Boolean get() = names.isEmpty()

    /** Sets each input's sampler to its texture unit. The textures themselves are bound by [AudioTexture.bindAll]. */
    fun apply(shader: Shader) {
        for (i in names.indices) shader.setUniform(names[i], units[i])
    }

    companion object {
        fun isAudioType(type: String): Boolean = type.equals("audio", ignoreCase = true) || type.equals("audioFFT", ignoreCase = true)
    }
}
