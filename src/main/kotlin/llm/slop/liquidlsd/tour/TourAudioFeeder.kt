package llm.slop.liquidlsd.tour

import llm.slop.liquidlsd.audio.AudioEngine
import llm.slop.liquidlsd.export.AudioDecoder
import java.io.File
import java.nio.FloatBuffer

/**
 * Feeds a decoded audio file to the analyzer in lockstep with the frame clock instead of the live callback.
 * At frame N every complete 512-sample block whose end is <= N * sampleRate / fps has been processed, in order,
 * so the result depends only on the file and the frame number (causal: no sample past the frame time is seen).
 * Past the end of the file, silence keeps the beat flywheel and envelopes running.
 */
class TourAudioFeeder(file: File, private val originNs: Long, private val fps: Int) {
    private val decoded = AudioDecoder.decodeAudioFile(file)
    private val sampleRate = decoded.sampleRate
    private val block = FloatArray(BLOCK)
    private val buffer: FloatBuffer = FloatBuffer.wrap(block)
    private var cursor = 0L

    fun feed(frame: Long) {
        val target = frame * sampleRate.toLong() / fps
        val samples = decoded.samples
        while (cursor + BLOCK <= target) {
            for (i in 0 until BLOCK) {
                val idx = cursor + i
                block[i] = if (idx < samples.size) samples[idx.toInt()] else 0f
            }
            buffer.position(0)
            buffer.limit(BLOCK)
            val blockStartNs = originNs + (cursor * 1_000_000_000L / sampleRate.toLong())
            AudioEngine.processAudio(buffer, BLOCK, sampleRate, blockStartNs)
            cursor += BLOCK
        }
    }

    companion object {
        const val BLOCK = 512
    }
}
