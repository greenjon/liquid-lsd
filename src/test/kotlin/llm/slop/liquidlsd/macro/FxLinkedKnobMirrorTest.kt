package llm.slop.liquidlsd.macro

import io.mockk.every
import io.mockk.mockk
import kotlinx.serialization.json.JsonPrimitive
import llm.slop.liquidlsd.rendering.FxChain
import llm.slop.liquidlsd.rendering.Mixer
import llm.slop.liquidlsd.rendering.Shader
import llm.slop.liquidlsd.rendering.isf.FxMetaBinding
import llm.slop.liquidlsd.rendering.isf.ISFFilter
import llm.slop.liquidlsd.rendering.isf.ISFHeader
import llm.slop.liquidlsd.rendering.isf.ISFInput
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** A linked slot's knob has no binding but must still move its Metaknob (two-way mirror), in group and focus mode. */
class FxLinkedKnobMirrorTest {

    private lateinit var chain: FxChain
    private lateinit var mixer: Mixer

    @BeforeTest
    fun setUp() {
        for (id in FxMacroSync.FX_BANK_IDS) MacroEngine.unregisterBank(id)
        chain = FxChain("Deck A FX")
        for (i in 0 until FxChain.SLOT_COUNT) {
            chain.slots[i] = filter("fx$i")
            chain.setSlotLinked(i, true)
        }
        mixer = mockk(relaxed = true)
        every { mixer.deckA.fxChain } returns chain
        every { mixer.deckB.fxChain } returns FxChain("Deck B FX")
        every { mixer.deckBG.fxChain } returns FxChain("Deck BG FX")
        every { mixer.deckPV.fxChain } returns FxChain("Deck PV FX")
        every { mixer.masterFxChain } returns FxChain("Master FX")
        FxMacroSync.syncChain(MacroEngine.DECK_A_FX, "Deck A", chain)
    }

    @AfterTest
    fun tearDown() {
        for (id in FxMacroSync.FX_BANK_IDS) MacroEngine.unregisterBank(id)
    }

    private fun filter(id: String): ISFFilter {
        val header = ISFHeader(INPUTS = listOf(
            ISFInput(NAME = "inputImage", TYPE = "image"),
            ISFInput(NAME = "amount", TYPE = "float", MIN = JsonPrimitive(0f), MAX = JsonPrimitive(1f), DEFAULT = JsonPrimitive(0.5f))
        ))
        return ISFFilter(id, id, header, mockk<Shader>(relaxed = true)).also { it.metaBinding = FxMetaBinding("amount", 0f, 1f) }
    }

    private fun frame() {
        MacroEngine.tick(mixer)
        chain.update()
    }

    private val bank get() = MacroEngine.getBank(MacroEngine.DECK_A_FX)!!

    @Test
    fun turningALinkedSlotKnobMovesItsMetaknob() {
        chain.slots[0]!!.metaKnob.baseValue = 0.3f
        frame()
        assertEquals(0.3f, bank.knobs[1].value, "the knob follows the Metaknob")

        bank.knobs[1].value = 0.8f // user turns knob 2
        frame()
        assertEquals(0.8f, chain.slots[0]!!.metaKnob.baseValue, 1e-6f)
        assertEquals(0.8f, bank.knobs[1].value, 1e-6f, "no snap back")
    }

    @Test
    fun turningALinkedKnobReleasesTheSlotUntilTheSuperKnobPicksItUp() {
        // Super Knob catches slot 1 first. (Written directly: the test mixer can't resolve knob 1's Super path.)
        chain.slots[0]!!.metaKnob.baseValue = 0.5f
        chain.superKnob.baseValue = 0.45f; frame()
        chain.superKnob.baseValue = 0.5f; frame()
        assertFalse(chain.isAwaitingPickup(0))

        bank.knobs[1].value = 0.9f; frame() // the slot's own knob takes it away
        assertTrue(chain.isAwaitingPickup(0))

        chain.superKnob.baseValue = 0.6f; frame() // Super moves but has not reached 0.9
        assertEquals(0.9f, chain.slots[0]!!.metaKnob.baseValue, 1e-6f)

        chain.superKnob.baseValue = 0.95f; frame() // crosses 0.9: picked up again
        assertEquals(0.95f, chain.slots[0]!!.metaKnob.baseValue, 1e-6f)
        assertFalse(chain.isAwaitingPickup(0))
    }

    @Test
    fun anyOutsideWriteToATakenOverMetaknobReArmsPickup() {
        chain.slots[1]!!.metaKnob.baseValue = 0.5f
        chain.superKnob.baseValue = 0.45f; frame()
        chain.superKnob.baseValue = 0.5f; frame()
        assertFalse(chain.isAwaitingPickup(1))

        chain.slots[1]!!.metaKnob.baseValue = 0.1f // e.g. an Edit-bay slider or MIDI on .../FX2/Meta
        chain.update()
        assertTrue(chain.isAwaitingPickup(1))
        chain.superKnob.baseValue = 0.55f; frame()
        assertEquals(0.1f, chain.slots[1]!!.metaKnob.baseValue, 1e-6f, "no jump to the Super Knob")
    }

    @Test
    fun focusedLinkedSlotKeepsKnobOne() {
        FxMacroSync.focusSlot(MacroEngine.DECK_A_FX, mixer, 2)
        chain.slots[2]!!.metaKnob.baseValue = 0.2f
        frame()
        assertEquals(0.2f, bank.knobs[0].value, 1e-6f)

        bank.knobs[0].value = 0.7f
        frame()
        assertEquals(0.7f, chain.slots[2]!!.metaKnob.baseValue, 1e-6f)
    }

    @Test
    fun resyncIsNotATurn() {
        chain.slots[0]!!.metaKnob.baseValue = 0.4f
        frame()
        bank.knobs[1].value = 0.9f // stale value left on the knob by a resync, not a turn
        FxMacroSync.syncChain(MacroEngine.DECK_A_FX, "Deck A", chain)
        frame()
        assertEquals(0.4f, chain.slots[0]!!.metaKnob.baseValue, 1e-6f)
        assertEquals(0.4f, bank.knobs[1].value, 1e-6f)
    }
}

/** RELATIVE Super Knob: linked slots move at once, keep their spread, and still reach both ends. */
class FxSuperKnobRelativeTest {

    @BeforeTest
    fun setUp() { FxChain.superKnobMode = { llm.slop.liquidlsd.rendering.SuperKnobMode.RELATIVE } }

    @AfterTest
    fun tearDown() { FxChain.superKnobMode = { llm.slop.liquidlsd.rendering.SuperKnobMode.PICKUP } }

    private fun filter(id: String): ISFFilter {
        val header = ISFHeader(INPUTS = listOf(
            ISFInput(NAME = "inputImage", TYPE = "image"),
            ISFInput(NAME = "amount", TYPE = "float", MIN = JsonPrimitive(0f), MAX = JsonPrimitive(1f), DEFAULT = JsonPrimitive(0.5f))
        ))
        return ISFFilter(id, id, header, mockk<Shader>(relaxed = true)).also { it.metaBinding = FxMetaBinding("amount", 0f, 1f) }
    }

    private fun chainWith(vararg metas: Float): FxChain {
        val chain = FxChain("t")
        metas.forEachIndexed { i, m ->
            chain.slots[i] = filter("fx$i").also { it.metaKnob.baseValue = m }
            chain.setSlotLinked(i, true)
        }
        chain.superKnob.baseValue = 0.5f
        chain.update() // anchors every slot at (0.5, its own value)
        return chain
    }

    private fun FxChain.meta(i: Int) = slots[i]!!.metaKnob.baseValue

    @Test
    fun slotsMoveAtOnceAndKeepTheirOrder() {
        val chain = chainWith(0.2f, 0.5f, 0.9f)
        chain.superKnob.baseValue = 0.6f
        chain.update()
        assertTrue(chain.meta(0) > 0.2f && chain.meta(1) > 0.5f && chain.meta(2) > 0.9f, "no pickup wait")
        assertTrue(chain.meta(0) < chain.meta(1) && chain.meta(1) < chain.meta(2))
        assertFalse(chain.isAwaitingPickup(0))
    }

    @Test
    fun bothEndsAreReachedAndTheSpreadComesBack() {
        val chain = chainWith(0.2f, 0.9f)
        chain.superKnob.baseValue = 1f; chain.update()
        assertEquals(1f, chain.meta(0), 1e-6f); assertEquals(1f, chain.meta(1), 1e-6f)
        chain.superKnob.baseValue = 0f; chain.update()
        assertEquals(0f, chain.meta(0), 1e-6f); assertEquals(0f, chain.meta(1), 1e-6f)
        chain.superKnob.baseValue = 0.5f; chain.update()
        assertEquals(0.2f, chain.meta(0), 1e-6f); assertEquals(0.9f, chain.meta(1), 1e-6f)
    }

    @Test
    fun aHandSetMetaknobReAnchorsWithoutAJump() {
        val chain = chainWith(0.2f)
        chain.slots[0]!!.metaKnob.baseValue = 0.7f // its own knob, a slider, MIDI...
        chain.update()
        assertEquals(0.7f, chain.meta(0), 1e-6f)
        chain.superKnob.baseValue = 0.55f; chain.update()
        assertTrue(chain.meta(0) > 0.7f && chain.meta(0) < 0.8f, "small move from the new anchor, was ${chain.meta(0)}")
    }

    @Test
    fun relativeLineMath() {
        assertEquals(0.25f, FxChain.relativeValue(0.25f, 0.5f, 0.5f), 1e-6f)
        assertEquals(0.4f, FxChain.relativeValue(0.5f, 0.5f, 0.4f), 1e-6f)
        assertEquals(0.7f, FxChain.relativeValue(0.75f, 0.5f, 0.4f), 1e-6f)
        assertEquals(0.3f, FxChain.relativeValue(0f, 0f, 0.3f), 1e-6f)
        assertEquals(0.3f, FxChain.relativeValue(1f, 1f, 0.3f), 1e-6f)
    }
}
