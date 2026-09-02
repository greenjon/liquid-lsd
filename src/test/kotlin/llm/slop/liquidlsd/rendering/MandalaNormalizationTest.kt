package llm.slop.liquidlsd.rendering

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MandalaNormalizationTest {

    @Test
    fun testDefaultArmLengthsNormalization() {
        // Default arm lengths: 0.4, 0.3, 0.2, 0.1 (sum = 1.0)
        val normalized = Mandala.computeNormalizedArmLengths(0.4f, 0.3f, 0.2f, 0.1f)
        assertEquals(0.4f, normalized[0], 1e-5f)
        assertEquals(0.3f, normalized[1], 1e-5f)
        assertEquals(0.2f, normalized[2], 1e-5f)
        assertEquals(0.1f, normalized[3], 1e-5f)
        val sum = normalized.sumOf { abs(it.toDouble()) }.toFloat()
        assertEquals(Mandala.TARGET_RADIUS, sum, 1e-5f)
    }

    @Test
    fun testScaledUpArmLengthsNormalization() {
        // All arms near maximum (1.0, 1.0, 1.0, 1.0 -> sum = 4.0)
        val normalized = Mandala.computeNormalizedArmLengths(1.0f, 1.0f, 1.0f, 1.0f)
        assertEquals(0.25f, normalized[0], 1e-5f)
        assertEquals(0.25f, normalized[1], 1e-5f)
        assertEquals(0.25f, normalized[2], 1e-5f)
        assertEquals(0.25f, normalized[3], 1e-5f)
        val sum = normalized.sumOf { abs(it.toDouble()) }.toFloat()
        assertEquals(Mandala.TARGET_RADIUS, sum, 1e-5f)
    }

    @Test
    fun testSmallArmLengthsNormalization() {
        // Multiple arms set low (sum = 0.2)
        val normalized = Mandala.computeNormalizedArmLengths(0.1f, 0.05f, 0.05f, 0.0f)
        assertEquals(0.5f, normalized[0], 1e-5f)
        assertEquals(0.25f, normalized[1], 1e-5f)
        assertEquals(0.25f, normalized[2], 1e-5f)
        assertEquals(0.0f, normalized[3], 1e-5f)
        val sum = normalized.sumOf { abs(it.toDouble()) }.toFloat()
        assertEquals(Mandala.TARGET_RADIUS, sum, 1e-5f)
    }

    @Test
    fun testRatioPreservation() {
        val l1 = 0.8f
        val l2 = 0.4f
        val l3 = 0.2f
        val l4 = 0.1f
        val normalized = Mandala.computeNormalizedArmLengths(l1, l2, l3, l4)

        // Verify ratio preservation
        assertEquals(l1 / l2, normalized[0] / normalized[1], 1e-5f)
        assertEquals(l2 / l3, normalized[1] / normalized[2], 1e-5f)
        assertEquals(l3 / l4, normalized[2] / normalized[3], 1e-5f)

        val sum = normalized.sumOf { abs(it.toDouble()) }.toFloat()
        assertEquals(Mandala.TARGET_RADIUS, sum, 1e-5f)
    }

    @Test
    fun testBipolarModulatedArmLengths() {
        // Modulators can produce negative values; signs must be preserved while sum of abs equals 1.0
        val normalized = Mandala.computeNormalizedArmLengths(-0.3f, 0.3f, -0.2f, 0.2f)
        assertEquals(-0.3f, normalized[0], 1e-5f)
        assertEquals(0.3f, normalized[1], 1e-5f)
        assertEquals(-0.2f, normalized[2], 1e-5f)
        assertEquals(0.2f, normalized[3], 1e-5f)
        val sum = normalized.sumOf { abs(it.toDouble()) }.toFloat()
        assertEquals(Mandala.TARGET_RADIUS, sum, 1e-5f)
    }

    @Test
    fun testZeroArmLengthsSafety() {
        // Zero arm lengths must safely return 0 without NaN or Infinity
        val normalized = Mandala.computeNormalizedArmLengths(0.0f, 0.0f, 0.0f, 0.0f)
        assertEquals(0.0f, normalized[0])
        assertEquals(0.0f, normalized[1])
        assertEquals(0.0f, normalized[2])
        assertEquals(0.0f, normalized[3])
        for (v in normalized) {
            assertTrue(!v.isNaN() && !v.isInfinite(), "Value should not be NaN or Infinite")
        }
    }

    @Test
    fun testNearZeroArmLengthsSafety() {
        // Near-zero arm lengths (sum <= 1e-5) should safely collapse to 0
        val normalized = Mandala.computeNormalizedArmLengths(1e-6f, 1e-6f, 0.0f, 0.0f)
        assertEquals(0.0f, normalized[0])
        assertEquals(0.0f, normalized[1])
        assertEquals(0.0f, normalized[2])
        assertEquals(0.0f, normalized[3])
    }
}
