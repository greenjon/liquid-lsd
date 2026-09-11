package llm.slop.liquidlsd.audio

import kotlin.test.Test
import kotlin.test.assertEquals

class JavaSoundClientTest {

    @Test
    fun testPcmToFloatConversion() {
        // Prepare a 16-bit signed little-endian input byte array:
        // Index 0, 1: 0 (0x00, 0x00) -> 0.0f
        // Index 2, 3: 32767 (0xFF, 0x7F) -> 32767 / 32768.0 = 0.9999695f
        // Index 4, 5: -32768 (0x00, 0x80) -> -32768 / 32768.0 = -1.0f
        // Index 6, 7: 16384 (0x00, 0x40) -> 16384 / 32768.0 = 0.5f
        // Index 8, 9: -16384 (0x00, 0xC0) -> -16384 / 32768.0 = -0.5f
        
        val byteBuffer = byteArrayOf(
            0x00, 0x00,
            0xFF.toByte(), 0x7F,
            0x00, 0x80.toByte(),
            0x00, 0x40,
            0x00, 0xC0.toByte()
        )
        
        val floatArray = FloatArray(5)
        val samplesProcessed = JavaSoundClient.convertPcmToFloat(byteBuffer, byteBuffer.size, floatArray)
        
        assertEquals(5, samplesProcessed)
        assertEquals(0.0f, floatArray[0])
        assertEquals(32767f / 32768f, floatArray[1])
        assertEquals(-1.0f, floatArray[2])
        assertEquals(0.5f, floatArray[3])
        assertEquals(-0.5f, floatArray[4])
    }

    @Test
    fun testPcmToFloatConversionOutOfBoundsSafety() {
        val byteBuffer = byteArrayOf(
            0x00, 0x00,
            0x00, 0x40
        )
        
        // Test case where the output floatArray is smaller than the available sample count
        val floatArray = FloatArray(1)
        val samplesProcessed = JavaSoundClient.convertPcmToFloat(byteBuffer, byteBuffer.size, floatArray)
        
        assertEquals(1, samplesProcessed)
        assertEquals(0.0f, floatArray[0])
    }

    @Test
    fun testStereoPcmToFloatConversion() {
        // Frame 0: L=0, R=32767
        // Frame 1: L=-32768, R=16384
        val byteBuffer = byteArrayOf(
            // Frame 0: L (0x00, 0x00), R (0xFF, 0x7F)
            0x00, 0x00,
            0xFF.toByte(), 0x7F,
            // Frame 1: L (0x00, 0x80), R (0x00, 0x40)
            0x00, 0x80.toByte(),
            0x00, 0x40
        )

        val leftArray = FloatArray(2)
        val rightArray = FloatArray(2)
        val framesProcessed = JavaSoundClient.convertStereoPcmToFloat(byteBuffer, byteBuffer.size, leftArray, rightArray)

        assertEquals(2, framesProcessed)
        assertEquals(0.0f, leftArray[0])
        assertEquals(32767f / 32768f, rightArray[0])
        assertEquals(-1.0f, leftArray[1])
        assertEquals(0.5f, rightArray[1])
    }
}
