package llm.slop.liquidlsd.rendering

import io.mockk.mockk
import kotlinx.serialization.json.JsonPrimitive
import llm.slop.liquidlsd.rendering.isf.ISFFilter
import llm.slop.liquidlsd.rendering.isf.ISFHeader
import llm.slop.liquidlsd.rendering.isf.ISFInput
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class FxChainSuperKnobTest {

    private fun testFilter(id: String): ISFFilter {
        val header = ISFHeader(INPUTS = listOf(
            ISFInput(NAME = "inputImage", TYPE = "image"),
            ISFInput(NAME = "amount", TYPE = "float", MIN = JsonPrimitive(0.0f), MAX = JsonPrimitive(1.0f), DEFAULT = JsonPrimitive(0.0f))
        ))
        val shader = mockk<Shader>(relaxed = true)
        return ISFFilter(id, id, header, shader)
    }

    @Test
    fun `linked slot without prior takeover ignores super knob until crossed`() {
        val chain = FxChain("Chain 1")
        val fx = testFilter("fx1")
        fx.metaKnob.set(0.5f)
        fx.update() // sync .value with the .set() base value, matching real per-frame usage
        chain.slots[0] = fx
        chain.armSlotTakeover(0)

        // Super knob starts at 0; moving it to 0.2 shouldn't cross or come within tolerance of 0.5 yet.
        chain.superKnob.set(0.2f)
        chain.update()
        assertEquals(0.5f, fx.metaKnob.value, 0.0001f)

        // Moving further so it crosses 0.5 should take over.
        chain.superKnob.set(0.6f)
        chain.update()
        assertEquals(0.6f, fx.metaKnob.value, 0.0001f)
    }

    @Test
    fun `once taken over super knob drives the slot directly`() {
        val chain = FxChain("Chain 1")
        val fx = testFilter("fx1")
        chain.slots[0] = fx
        chain.armSlotTakeover(0)

        chain.superKnob.set(0.0f)
        chain.update() // no-op: super knob hasn't moved from its own starting value yet, so armed-but-not-yet-crossed

        chain.superKnob.set(0.9f)
        chain.update()
        assertEquals(0.9f, fx.metaKnob.value, 0.0001f)

        chain.superKnob.set(0.3f)
        chain.update()
        assertEquals(0.3f, fx.metaKnob.value, 0.0001f)
    }

    @Test
    fun `unlinked slot never follows the super knob`() {
        val chain = FxChain("Chain 1")
        val fx = testFilter("fx1")
        fx.metaKnob.set(0.5f)
        fx.update()
        chain.slots[0] = fx
        chain.setSlotLinked(0, false)

        chain.superKnob.set(1.0f)
        chain.update()
        chain.update()

        assertEquals(0.5f, fx.metaKnob.value, 0.0001f)
    }

    @Test
    fun `relinking arms takeover instead of snapping`() {
        val chain = FxChain("Chain 1")
        val fx = testFilter("fx1")
        fx.metaKnob.set(0.5f)
        fx.update()
        chain.slots[0] = fx
        chain.setSlotLinked(0, false)
        chain.superKnob.set(0.9f)
        chain.update()
        assertEquals(0.5f, fx.metaKnob.value, 0.0001f) // unlinked, unaffected

        chain.setSlotLinked(0, true) // relink while super knob is far away (0.9 vs slot's 0.5)
        chain.update()
        assertEquals(0.5f, fx.metaKnob.value, 0.0001f) // still armed, no super knob movement yet

        chain.superKnob.set(0.4f) // now crosses 0.5 on its way down from 0.9
        chain.update()
        assertEquals(0.4f, fx.metaKnob.value, 0.0001f)
    }

    @Test
    fun `super knob and slot link state round-trip through dto`() {
        val chain = FxChain("Chain 1")
        chain.superKnob.baseValue = 0.42f
        chain.setSlotLinked(1, false)

        val dto = chain.toFxChainDto("chain")
        assertEquals(0.42f, dto.superKnob?.baseValue)
        assertEquals(listOf(true, false, true), dto.slotSuperKnobLink)

        val restored = FxChain("Chain 2")
        restored.applyFxChain(dto)
        assertEquals(0.42f, restored.superKnob.baseValue)
        assertEquals(listOf(true, false, true), restored.slotSuperKnobLink.toList())
    }
}
