package lsd.liquid.visuals

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Turns the player's decoded 16-bit PCM (any channel count) into mono 16-bit little-endian chunks of about
 * [chunkMs], the shape the web visuals' `LSD.feed()` takes. Plain Kotlin so it is unit-tested on the JVM.
 */
class PcmMonoChunker(private val chunkMs: Int = 40) {
    private var rate = 44100
    private var channels = 2
    private var out = ByteArray(chunkBytes(rate))
    private var outLen = 0

    private fun chunkBytes(rate: Int) = (rate * chunkMs / 1000) * 2

    fun configure(sampleRateHz: Int, channelCount: Int) {
        rate = sampleRateHz.coerceAtLeast(8000)
        channels = channelCount.coerceAtLeast(1)
        out = ByteArray(chunkBytes(rate))
        outLen = 0
    }

    /** Reads [buffer] without moving its position; calls [emit] (chunk, sample rate) for each full chunk. */
    fun push(buffer: ByteBuffer, emit: (ByteArray, Int) -> Unit) {
        val b = buffer.asReadOnlyBuffer().order(ByteOrder.nativeOrder())
        val frames = b.remaining() / (channels * 2)
        repeat(frames) {
            var sum = 0
            repeat(channels) { sum += b.short.toInt() }
            val s = sum / channels
            out[outLen++] = s.toByte()
            out[outLen++] = (s shr 8).toByte()
            if (outLen == out.size) {
                emit(out.copyOf(), rate)
                outLen = 0
            }
        }
    }
}

/**
 * The audio the web visuals listen to: the app's audio source (a player's audio sink, a microphone, a system
 * audio capture) pushes every buffer of 16-bit PCM in, and while a listener is set (the visuals are on screen
 * and running, see [LsdVisuals]) it is handed on as mono chunks. With no listener the source does no work at all.
 */
object PcmTap {
    @Volatile
    var listener: ((ByteArray, Int) -> Unit)? = null

    private val chunker = PcmMonoChunker()

    /** Audio thread: the source's format changed. */
    fun configure(sampleRateHz: Int, channelCount: Int) = chunker.configure(sampleRateHz, channelCount)

    /** Audio thread: a buffer of 16-bit PCM in native byte order, interleaved. */
    fun onBuffer(buffer: ByteBuffer) {
        val l = listener ?: return
        chunker.push(buffer, l)
    }
}
