package llm.slop.liquidlsd.rendering

import llm.slop.liquidlsd.rendering.pipewire.SpaPod
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The hand-written SPA pods: a fixed format reads back, and the header fields sit where PipeWire expects them. */
class SpaPodTest {
    @Test
    fun fixedFormatReadsBack() {
        val pod = SpaPod.videoFormat(SpaPod.PARAM_FORMAT, intArrayOf(SpaPod.VIDEO_FORMAT_BGRX), intArrayOf(640, 360))
        assertEquals(SpaPod.VideoInfo(640, 360, SpaPod.VIDEO_FORMAT_BGRX), SpaPod.parseVideoFormat(pod.memory))
    }

    @Test
    fun choicesReadTheirDefault() {
        val formats = intArrayOf(SpaPod.VIDEO_FORMAT_RGBA, SpaPod.VIDEO_FORMAT_BGRA)
        val pod = SpaPod.videoFormat(SpaPod.PARAM_ENUM_FORMAT, formats, intArrayOf(1920, 1080), intArrayOf(1, 1), intArrayOf(8192, 8192), anyRate = true)
        assertEquals(SpaPod.VideoInfo(1920, 1080, SpaPod.VIDEO_FORMAT_RGBA), SpaPod.parseVideoFormat(pod.memory))
    }

    @Test
    fun headersMatchTheAbi() {
        val m = SpaPod.videoFormat(SpaPod.PARAM_ENUM_FORMAT, intArrayOf(SpaPod.VIDEO_FORMAT_RGBA), intArrayOf(2, 2)).memory
        assertEquals(15, m.getInt(4))          // SPA_TYPE_Object
        assertEquals(0x40003, m.getInt(8))     // SPA_TYPE_OBJECT_Format
        assertEquals(3, m.getInt(12))          // SPA_PARAM_EnumFormat
        assertEquals(0, (m.getInt(0) + 8) % 8) // whole pod padded to 8 bytes
        val b = SpaPod.buffers(16, 8).memory
        assertEquals(0x40004, b.getInt(8))     // SPA_TYPE_OBJECT_ParamBuffers
        assertEquals(5, b.getInt(12))          // SPA_PARAM_Buffers
        assertNull(SpaPod.parseVideoFormat(b))
    }
}
