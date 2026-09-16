package llm.slop.liquidlsd.osc

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertIs

class OscCodecTest {

    @Test
    fun testEncodeDecodeSimpleFloatMessage() {
        val original = OscMessage("/1/fader1", listOf(0.75f))
        val bytes = OscCodec.encode(original)
        val decoded = OscCodec.decode(bytes)

        assertIs<OscMessage>(decoded)
        assertEquals("/1/fader1", decoded.address)
        assertEquals(1, decoded.args.size)
        assertEquals(0.75f, decoded.args[0] as Float, absoluteTolerance = 1e-6f)
    }

    @Test
    fun testEncodeDecodeMixedArgumentTypes() {
        val original = OscMessage(
            "/mixer/crossfade",
            listOf(1, 0.5f, "label", true, false, byteArrayOf(1, 2, 3, 4, 5))
        )
        val bytes = OscCodec.encode(original)
        val decoded = OscCodec.decode(bytes)

        assertIs<OscMessage>(decoded)
        assertEquals("/mixer/crossfade", decoded.address)
        assertEquals(6, decoded.args.size)
        assertEquals(1, decoded.args[0])
        assertEquals(0.5f, decoded.args[1] as Float, absoluteTolerance = 1e-6f)
        assertEquals("label", decoded.args[2])
        assertEquals(true, decoded.args[3])
        assertEquals(false, decoded.args[4])
        assertTrue((decoded.args[5] as ByteArray).contentEquals(byteArrayOf(1, 2, 3, 4, 5)))
    }

    @Test
    fun testEncodeDecodeMultiFloatVectorArgs() {
        // TouchOSC XY pad: two floats in a single message
        val original = OscMessage("/2/xy", listOf(0.1f, 0.9f))
        val decoded = OscCodec.decode(OscCodec.encode(original)) as OscMessage

        assertEquals(2, decoded.args.size)
        assertEquals(0.1f, decoded.args[0] as Float, absoluteTolerance = 1e-6f)
        assertEquals(0.9f, decoded.args[1] as Float, absoluteTolerance = 1e-6f)
    }

    @Test
    fun testEncodeDecodeMessageWithNoArgs() {
        val original = OscMessage("/1/push1")
        val decoded = OscCodec.decode(OscCodec.encode(original)) as OscMessage

        assertEquals("/1/push1", decoded.address)
        assertTrue(decoded.args.isEmpty())
    }

    @Test
    fun testStringPaddingAlignsToFourByteBoundary() {
        // Address "/1/f" (4 chars) + null = 5 bytes -> padded to 8.
        // Type tag ",f" (2 chars) + null = 3 bytes -> padded to 4.
        // Float arg = 4 bytes. Total = 8 + 4 + 4 = 16.
        val bytes = OscCodec.encode(OscMessage("/1/f", listOf(1.0f)))
        assertEquals(16, bytes.size)
        assertEquals(0, bytes.size % 4)
    }

    @Test
    fun testBigEndianFloatByteOrder() {
        val bytes = OscCodec.encode(OscMessage("/x", listOf(1.0f)))
        // Address "/x" (2 chars) + null = 3 -> padded to 4. Type tag ",f" + null = 3 -> padded to 4.
        // Float 1.0f big-endian = 0x3F800000
        val floatBytes = bytes.copyOfRange(8, 12)
        assertEquals(0x3F.toByte(), floatBytes[0])
        assertEquals(0x80.toByte(), floatBytes[1])
        assertEquals(0x00.toByte(), floatBytes[2])
        assertEquals(0x00.toByte(), floatBytes[3])
    }

    @Test
    fun testEncodeDecodeBundleWithMultipleMessages() {
        val bundle = OscBundle(
            timetag = OscCodec.IMMEDIATE_TIMETAG,
            elements = listOf(
                OscMessage("/1/fader1", listOf(0.25f)),
                OscMessage("/1/fader2", listOf(0.5f))
            )
        )
        val decoded = OscCodec.decode(OscCodec.encode(bundle))

        assertIs<OscBundle>(decoded)
        assertEquals(OscCodec.IMMEDIATE_TIMETAG, decoded.timetag)
        assertEquals(2, decoded.elements.size)
        val first = decoded.elements[0] as OscMessage
        val second = decoded.elements[1] as OscMessage
        assertEquals("/1/fader1", first.address)
        assertEquals(0.25f, first.args[0] as Float, absoluteTolerance = 1e-6f)
        assertEquals("/1/fader2", second.address)
        assertEquals(0.5f, second.args[0] as Float, absoluteTolerance = 1e-6f)
    }

    @Test
    fun testEncodeDecodeNestedBundle() {
        val inner = OscBundle(elements = listOf(OscMessage("/inner", listOf(42))))
        val outer = OscBundle(timetag = 123456789L, elements = listOf(inner, OscMessage("/outer", listOf("hi"))))

        val decoded = OscCodec.decode(OscCodec.encode(outer)) as OscBundle
        assertEquals(123456789L, decoded.timetag)
        assertEquals(2, decoded.elements.size)
        val decodedInner = decoded.elements[0] as OscBundle
        assertEquals(1, decodedInner.elements.size)
        assertEquals("/inner", (decodedInner.elements[0] as OscMessage).address)
        assertEquals("/outer", (decoded.elements[1] as OscMessage).address)
    }

    @Test
    fun testDecodeBundleWithOversizedElementLengthThrows() {
        // "#bundle\0" (8 bytes, already 4-byte aligned) + 8-byte timetag + a declared
        // element size (9999) that vastly exceeds the zero bytes actually remaining.
        val buffer = java.nio.ByteBuffer.allocate(20).order(java.nio.ByteOrder.BIG_ENDIAN)
        buffer.put("#bundle".toByteArray(Charsets.UTF_8))
        buffer.put(0)
        buffer.putLong(OscCodec.IMMEDIATE_TIMETAG)
        buffer.putInt(9999)

        var threw = false
        try {
            OscCodec.decode(buffer.array())
        } catch (e: Exception) {
            threw = true
        }
        assertTrue(threw)
    }
}
