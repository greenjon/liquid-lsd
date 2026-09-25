package llm.slop.liquidlsd.rendering

import io.mockk.mockk
import kotlinx.serialization.json.JsonPrimitive
import llm.slop.liquidlsd.rendering.isf.ISFFilter
import llm.slop.liquidlsd.rendering.isf.ISFHeader
import llm.slop.liquidlsd.rendering.isf.ISFInput
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FxChainFadeTest {

    private fun testFilter(id: String): ISFFilter {
        val header = ISFHeader(INPUTS = listOf(
            ISFInput(NAME = "inputImage", TYPE = "image"),
            ISFInput(NAME = "amount", TYPE = "float", MIN = JsonPrimitive(0.0f), MAX = JsonPrimitive(1.0f), DEFAULT = JsonPrimitive(0.0f))
        ))
        val shader = mockk<Shader>(relaxed = true)
        return ISFFilter(id, id, header, shader)
    }

    @Test
    fun testImmediateSlotChangeWhenFadeSecIsZero() {
        val chain = FxChain("Test Chain")
        var applied = false
        chain.scheduleSlotChange(0, 0f) {
            applied = true
            chain.slots[0] = testFilter("instant_fx")
        }

        assertTrue(applied)
        assertEquals("instant_fx", chain.slots[0]?.id)
        assertFalse(chain.isFading)
    }

    @Test
    fun testSlotDipStateMachineAdvancesAndAppliesAtBottom() {
        val chain = FxChain("Test Chain")
        chain.slots[0] = testFilter("old_fx")
        chain.slots[0]?.enabled = true

        var changeApplied = false
        // 0.2s total dip = 0.1s fade-out, 0.1s fade-in
        chain.scheduleSlotChange(0, 0.2f) {
            changeApplied = true
            chain.slots[0] = testFilter("new_fx")
        }

        assertTrue(chain.isFading)
        assertFalse(changeApplied, "Change should not be applied before fade-out completes")

        // Advance 0.05s (halfway through fade-out)
        chain.advanceFade(0.05f)
        assertFalse(changeApplied)
        assertTrue(chain.effectiveSlotWet(0) < 1f && chain.effectiveSlotWet(0) > 0f)

        // Advance another 0.05s (reaches bottom)
        chain.advanceFade(0.05f)
        assertTrue(changeApplied, "Change should apply when slot gain reaches 0")
        assertEquals("new_fx", chain.slots[0]?.id)

        // Advance 0.1s (fade-in completes)
        chain.advanceFade(0.1f)
        assertFalse(chain.isFading)
        assertEquals(1f, chain.effectiveSlotWet(0))
    }

    @Test
    fun testChainDipStateMachineAdvancesAndAppliesAtBottom() {
        val chain = FxChain("Test Chain")
        chain.slots[0] = testFilter("fx_1")
        chain.slots[0]?.dryWet?.baseValue = 1f

        var chainChangeApplied = false
        // 0.2s total dip
        chain.scheduleChainChange(0.2f) {
            chainChangeApplied = true
            chain.slots[1] = testFilter("fx_2")
        }

        assertTrue(chain.isFading)
        assertFalse(chainChangeApplied)

        // Advance 0.1s to bottom of chain dip
        chain.advanceFade(0.1f)
        assertTrue(chainChangeApplied)
        assertEquals("fx_2", chain.slots[1]?.id)

        // Advance 0.1s to recover chain gain
        chain.advanceFade(0.1f)
        assertFalse(chain.isFading)
        assertEquals(1f, chain.effectiveChainWet())
    }

    @Test
    fun testSwapSlotWithMovesFilterAndSuperKnobLink() {
        val chainA = FxChain("Chain A")
        val chainB = FxChain("Chain B")

        val filterA = testFilter("filterA")
        val filterB = testFilter("filterB")

        chainA.slots[0] = filterA
        chainA.setSlotLinked(0, true)

        chainB.slots[1] = filterB
        chainB.setSlotLinked(1, false)

        chainA.swapSlotWith(0, chainB, 1)

        assertEquals("filterB", chainA.slots[0]?.id)
        assertFalse(chainA.slotSuperKnobLink[0])

        assertEquals("filterA", chainB.slots[1]?.id)
        assertTrue(chainB.slotSuperKnobLink[1])
    }

    @Test
    fun testEffectiveSlotWetSkipsDisabledSlot() {
        val chain = FxChain("Test Chain")
        val filter = testFilter("fx")
        filter.enabled = false
        chain.slots[0] = filter

        assertEquals(0f, chain.effectiveSlotWet(0))

        filter.enabled = true
        filter.dryWet.baseValue = 0.75f
        filter.dryWet.evaluate()
        assertEquals(0.75f, chain.effectiveSlotWet(0))
    }
}
