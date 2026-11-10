package org.liquidlsd.visuals

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class PcmMonoChunkerTest {
    private fun stereo(vararg frames: Pair<Int, Int>): ByteBuffer {
        val b = ByteBuffer.allocate(frames.size * 4).order(ByteOrder.nativeOrder())
        frames.forEach { (l, r) -> b.putShort(l.toShort()); b.putShort(r.toShort()) }
        b.flip()
        return b
    }

    private fun samples(chunk: ByteArray) = ByteBuffer.wrap(chunk).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().let { sb -> ShortArray(sb.remaining()).also { sb.get(it) } }

    @Test
    fun downmixesStereoToMonoLittleEndianInChunksOfTheConfiguredLength() {
        val c = PcmMonoChunker(chunkMs = 5)
        c.configure(8000, 2) // 40 frames per chunk
        val chunks = mutableListOf<ByteArray>()
        val rates = mutableListOf<Int>()
        val input = stereo(*Array(100) { 1000 to 3000 })
        c.push(input) { chunk, rate -> chunks.add(chunk); rates.add(rate) }
        assertEquals(2, chunks.size) // 100 frames = 2 full chunks + 20 waiting
        assertEquals(listOf(8000, 8000), rates)
        assertEquals(80, chunks[0].size)
        assertTrue(samples(chunks[0]).all { it == 2000.toShort() })
        assertEquals(400, input.remaining()) // the caller's buffer is untouched
    }

    @Test
    fun carriesLeftoverFramesIntoTheNextBuffer() {
        val c = PcmMonoChunker(chunkMs = 5)
        c.configure(8000, 2)
        var n = 0
        c.push(stereo(*Array(30) { 0 to 0 })) { _, _ -> n++ }
        assertEquals(0, n)
        c.push(stereo(*Array(30) { 0 to 0 })) { _, _ -> n++ }
        assertEquals(1, n)
    }

    @Test
    fun extremeSamplesDoNotOverflow() {
        val c = PcmMonoChunker(chunkMs = 1)
        c.configure(8000, 2) // 8 frames per chunk
        var out: ShortArray? = null
        c.push(stereo(*Array(8) { 32767 to 32767 })) { chunk, _ -> out = samples(chunk) }
        assertTrue(out!!.all { it == 32767.toShort() })
        c.push(stereo(*Array(8) { -32768 to -32768 })) { chunk, _ -> out = samples(chunk) }
        assertTrue(out!!.all { it == (-32768).toShort() })
    }
}
