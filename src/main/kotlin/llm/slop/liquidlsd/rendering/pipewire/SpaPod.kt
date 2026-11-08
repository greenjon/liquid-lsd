package llm.slop.liquidlsd.rendering.pipewire

import com.sun.jna.Memory
import com.sun.jna.Pointer
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * The few SPA pods a raw video stream needs, written and read by hand: the SPA builder is header-only C, so
 * JNA can't call it. Constants are from PipeWire 1.6's headers (spa/utils/type.h, spa/param/param.h,
 * spa/param/format.h, spa/param/buffers.h, spa/param/video/raw.h); they are part of the stable ABI.
 *
 * Layout: every pod is `{u32 size, u32 type}` then a body padded to 8 bytes. An object's body is
 * `{u32 objectType, u32 paramId}` then properties `{u32 key, u32 flags, pod value}`.
 */
object SpaPod {
    private const val TYPE_ID = 3
    private const val TYPE_INT = 4
    private const val TYPE_RECTANGLE = 10
    private const val TYPE_FRACTION = 11
    private const val TYPE_OBJECT = 15
    private const val TYPE_CHOICE = 19

    private const val CHOICE_NONE = 0
    private const val CHOICE_RANGE = 1
    private const val CHOICE_ENUM = 3
    private const val CHOICE_FLAGS = 4

    private const val OBJECT_FORMAT = 0x40003
    private const val OBJECT_PARAM_BUFFERS = 0x40004

    const val PARAM_ENUM_FORMAT = 3
    const val PARAM_FORMAT = 4
    const val PARAM_BUFFERS = 5

    private const val FORMAT_MEDIA_TYPE = 1
    private const val FORMAT_MEDIA_SUBTYPE = 2
    private const val FORMAT_VIDEO_FORMAT = 0x20001
    private const val FORMAT_VIDEO_SIZE = 0x20003
    private const val FORMAT_VIDEO_FRAMERATE = 0x20004
    private const val FORMAT_VIDEO_MAX_FRAMERATE = 0x20005
    private const val MEDIA_TYPE_VIDEO = 2
    private const val MEDIA_SUBTYPE_RAW = 1

    private const val BUFFERS_BUFFERS = 1
    private const val BUFFERS_BLOCKS = 2
    private const val BUFFERS_SIZE = 3
    private const val BUFFERS_STRIDE = 4
    private const val BUFFERS_DATA_TYPE = 6

    const val VIDEO_FORMAT_RGBX = 7
    const val VIDEO_FORMAT_BGRX = 8
    const val VIDEO_FORMAT_RGBA = 11
    const val VIDEO_FORMAT_BGRA = 12

    private const val DATA_MEM_PTR = 1
    private const val DATA_MEM_FD = 2

    /** A pod held in native memory; keep the reference until PipeWire has copied it (the call returns). */
    class Native(val memory: Memory)

    private class Writer {
        val buf: ByteBuffer = ByteBuffer.allocate(1024).order(ByteOrder.LITTLE_ENDIAN)
        fun int(v: Int) = buf.putInt(v)
        fun pad() { while (buf.position() % 8 != 0) buf.put(0) }
        /** Writes `{size, type}` with a placeholder size, runs [body], then fills the size in. */
        fun pod(type: Int, body: () -> Unit) {
            val at = buf.position(); int(0); int(type)
            val start = buf.position(); body()
            buf.putInt(at, buf.position() - start); pad()
        }
        fun prop(key: Int, value: () -> Unit) { int(key); int(0); value() }
        fun id(v: Int) = pod(TYPE_ID) { int(v) }
        fun intPod(v: Int) = pod(TYPE_INT) { int(v) }
        /** A choice of [childType]: the child header, then the values packed back to back (each [childSize] bytes). */
        fun choice(kind: Int, childType: Int, childSize: Int, values: List<IntArray>) = pod(TYPE_CHOICE) {
            int(kind); int(0); int(childSize); int(childType)
            for (v in values) for (x in v) int(x)
        }
        fun toNative(): Native {
            val n = buf.position(); val m = Memory(n.toLong()); m.write(0, buf.array(), 0, n); return Native(m)
        }
    }

    /**
     * Raw video formats: [formats] (first is preferred) and a fixed size or a range. A producer offers a variable
     * frame rate (0/1) up to [maxFps]; a reader ([anyRate]) accepts any frame rate.
     */
    fun videoFormat(paramId: Int, formats: IntArray, size: IntArray, sizeMin: IntArray? = null, sizeMax: IntArray? = null, maxFps: Int = 60, anyRate: Boolean = false): Native {
        val w = Writer()
        w.pod(TYPE_OBJECT) {
            w.int(OBJECT_FORMAT); w.int(paramId)
            w.prop(FORMAT_MEDIA_TYPE) { w.id(MEDIA_TYPE_VIDEO) }
            w.prop(FORMAT_MEDIA_SUBTYPE) { w.id(MEDIA_SUBTYPE_RAW) }
            w.prop(FORMAT_VIDEO_FORMAT) {
                if (formats.size == 1) w.id(formats[0])
                else w.choice(CHOICE_ENUM, TYPE_ID, 4, listOf(intArrayOf(formats[0])) + formats.map { intArrayOf(it) })
            }
            w.prop(FORMAT_VIDEO_SIZE) {
                if (sizeMin == null || sizeMax == null) w.pod(TYPE_RECTANGLE) { w.int(size[0]); w.int(size[1]) }
                else w.choice(CHOICE_RANGE, TYPE_RECTANGLE, 8, listOf(size, sizeMin, sizeMax))
            }
            if (anyRate) {
                w.prop(FORMAT_VIDEO_FRAMERATE) {
                    w.choice(CHOICE_RANGE, TYPE_FRACTION, 8, listOf(intArrayOf(30, 1), intArrayOf(0, 1), intArrayOf(1000, 1)))
                }
            } else {
                w.prop(FORMAT_VIDEO_FRAMERATE) { w.pod(TYPE_FRACTION) { w.int(0); w.int(1) } }
                w.prop(FORMAT_VIDEO_MAX_FRAMERATE) {
                    w.choice(CHOICE_RANGE, TYPE_FRACTION, 8, listOf(intArrayOf(maxFps, 1), intArrayOf(1, 1), intArrayOf(1000, 1)))
                }
            }
        }
        return w.toNative()
    }

    /** Buffer requirements once a format is fixed: one block of [size] bytes, [stride] per row, in plain or memfd memory. */
    fun buffers(size: Int, stride: Int, count: Int = 4): Native {
        val w = Writer()
        w.pod(TYPE_OBJECT) {
            w.int(OBJECT_PARAM_BUFFERS); w.int(PARAM_BUFFERS)
            w.prop(BUFFERS_BUFFERS) { w.choice(CHOICE_RANGE, TYPE_INT, 4, listOf(intArrayOf(count), intArrayOf(2), intArrayOf(16))) }
            w.prop(BUFFERS_BLOCKS) { w.intPod(1) }
            w.prop(BUFFERS_SIZE) { w.intPod(size) }
            w.prop(BUFFERS_STRIDE) { w.intPod(stride) }
            w.prop(BUFFERS_DATA_TYPE) {
                w.choice(CHOICE_FLAGS, TYPE_INT, 4, listOf(intArrayOf((1 shl DATA_MEM_PTR) or (1 shl DATA_MEM_FD))))
            }
        }
        return w.toNative()
    }

    /** What a negotiated Format pod says: width, height and SPA video format, or null if it isn't raw video. */
    data class VideoInfo(val width: Int, val height: Int, val format: Int)

    fun parseVideoFormat(pod: Pointer): VideoInfo? {
        val size = pod.getInt(0)
        if (pod.getInt(4) != TYPE_OBJECT || size < 8) return null
        if (pod.getInt(8) != OBJECT_FORMAT) return null
        var w = 0; var h = 0; var fmt = 0
        var off = 16L
        val end = 8L + size
        while (off + 16 <= end) {
            val key = pod.getInt(off)
            val vSize = pod.getInt(off + 8); val vType = pod.getInt(off + 12)
            val body = off + 16
            // A choice's first value is the current one: skip the choice header to it.
            val (type, at) = if (vType == TYPE_CHOICE) pod.getInt(body + 12) to body + 16 else vType to body
            when (key) {
                FORMAT_VIDEO_SIZE -> if (type == TYPE_RECTANGLE) { w = pod.getInt(at); h = pod.getInt(at + 4) }
                FORMAT_VIDEO_FORMAT -> if (type == TYPE_ID) fmt = pod.getInt(at)
                FORMAT_MEDIA_TYPE -> if (type == TYPE_ID && pod.getInt(at) != MEDIA_TYPE_VIDEO) return null
            }
            off = body + ((vSize + 7) and 7.inv())
        }
        return if (w > 0 && h > 0 && fmt != 0) VideoInfo(w, h, fmt) else null
    }
}
