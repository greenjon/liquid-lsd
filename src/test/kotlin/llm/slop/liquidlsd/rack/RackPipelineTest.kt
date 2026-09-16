package llm.slop.liquidlsd.rack

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class RackPipelineTest {

    @Test
    fun testNormalledTopDownChaining() {
        val pipeline = RackPipeline(allocateGlBuffers = false)

        val unit0 = GenericRackUnit(id = "u0", label = "Source", onProcess = { _ -> 10 })
        val unit1 = GenericRackUnit(id = "u1", label = "FX Add 5", onProcess = { inTex -> inTex + 5 })
        val unit2 = GenericRackUnit(id = "u2", label = "FX Mult 2", onProcess = { inTex -> inTex * 2 })

        val units = listOf(unit0, unit1, unit2)
        val out = pipeline.process(units, null)

        // (10 + 5) * 2 = 30
        assertEquals(30, out)
        assertEquals(30, pipeline.lastOutputTexture)
    }

    @Test
    fun testBypassPassthroughInChain() {
        val pipeline = RackPipeline(allocateGlBuffers = false)

        val unit0 = GenericRackUnit(id = "u0", label = "Source", onProcess = { _ -> 10 })
        val unit1 = GenericRackUnit(id = "u1", label = "FX Add 5", onProcess = { inTex -> inTex + 5 })
        val unit2 = GenericRackUnit(id = "u2", label = "FX Mult 2", onProcess = { inTex -> inTex * 2 })

        // Bypass unit1
        unit1.isBypassed = true

        val units = listOf(unit0, unit1, unit2)
        val out = pipeline.process(units, null)

        // 10 is passed untouched through unit1, then 10 * 2 = 20
        assertEquals(20, out)
    }

    @Test
    fun testSoloOverride() {
        val pipeline = RackPipeline(allocateGlBuffers = false)

        val unit0 = GenericRackUnit(id = "u0", label = "Source", onProcess = { _ -> 10 })
        val unit1 = GenericRackUnit(id = "u1", label = "FX Add 5", onProcess = { inTex -> inTex + 5 })
        val unit2 = GenericRackUnit(id = "u2", label = "FX Mult 2", onProcess = { inTex -> inTex * 2 })

        // Solo unit1 (output should be unit1's output = 15, overriding unit2's 30)
        unit1.isSoloed = true

        val units = listOf(unit0, unit1, unit2)
        val out = pipeline.process(units, null)

        assertEquals(15, out)
    }

    @Test
    fun testPowerOffSkipsUnit() {
        val pipeline = RackPipeline(allocateGlBuffers = false)

        val unit0 = GenericRackUnit(id = "u0", label = "Source", onProcess = { _ -> 10 })
        val unit1 = GenericRackUnit(id = "u1", label = "FX Add 5", onProcess = { inTex -> inTex + 5 })
        val unit2 = GenericRackUnit(id = "u2", label = "FX Mult 2", onProcess = { inTex -> inTex * 2 })

        unit1.isPowered = false

        val units = listOf(unit0, unit1, unit2)
        val out = pipeline.process(units, null)

        // unit1 skipped: 10 * 2 = 20
        assertEquals(20, out)
    }

    @Test
    fun testEmptyChainReturnsZero() {
        val pipeline = RackPipeline(allocateGlBuffers = false)
        val out = pipeline.process(emptyList(), null)
        assertEquals(0, out)
    }
}
