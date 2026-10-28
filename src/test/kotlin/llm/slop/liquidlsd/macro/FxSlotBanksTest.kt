package llm.slop.liquidlsd.macro

import io.mockk.mockk
import kotlinx.serialization.json.JsonPrimitive
import llm.slop.liquidlsd.rendering.FxChain
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Chain view's per-slot FX banks ([FxMacroSync.slotBankId]) and per-slot parameter pages ([FxChain.slotParamPage]). */
class FxSlotBanksTest {

    private val allIds get() = FxMacroSync.FX_BANK_IDS + FxMacroSync.SLOT_BANK_IDS

    @BeforeTest
    fun setUp() {
        for (id in allIds) MacroEngine.unregisterBank(id)
    }

    @AfterTest
    fun tearDown() {
        for (id in allIds) MacroEngine.unregisterBank(id)
    }

    private fun filter(id: String, paramNames: List<String>): ISFFilter {
        val inputs = mutableListOf(ISFInput(NAME = "inputImage", TYPE = "image"))
        for (name in paramNames) {
            inputs.add(ISFInput(NAME = name, TYPE = "float", MIN = JsonPrimitive(0.0f), MAX = JsonPrimitive(2.0f), DEFAULT = JsonPrimitive(1.0f)))
        }
        return ISFFilter(id, id, ISFHeader(INPUTS = inputs), mockk<Shader>(relaxed = true))
            .also { it.metaBinding = FxMetaBinding.DRY_WET_SAFETY_NET }
    }

    private fun unlinkedChain(): FxChain = FxChain("Deck A FX").also { c ->
        for (i in 0 until FxChain.SLOT_COUNT) c.setSlotLinked(i, false)
    }

    @Test
    fun slotBankIdsRoundTrip() {
        assertEquals("deckA_fx_s2", FxMacroSync.slotBankId(MacroEngine.DECK_A_FX, 1))
        assertEquals(MacroEngine.MASTER_FX to 2, FxMacroSync.slotBankTarget("masterFx_s3"))
        assertNull(FxMacroSync.slotBankTarget(MacroEngine.DECK_A_FX))
        assertNull(FxMacroSync.slotBankTarget("deckA_fx_s4"))
        assertEquals(15, FxMacroSync.SLOT_BANK_IDS.size)
        assertTrue(FxMacroSync.isFxBank("deckBG_fx_s1"))
        assertFalse(MacroLearnState.acceptsTarget("deckA_fx_s1", "Deck A/FX/FX1/p1"))
    }

    @Test
    fun everySlotBankGetsItsSlotWhateverTheChainMode() {
        val chain = unlinkedChain()
        chain.slots[0] = filter("one", listOf("a1", "a2", "a3"))
        chain.slots[2] = filter("three", listOf("c1", "c2"))

        FxMacroSync.syncChain(MacroEngine.DECK_A_FX, "Deck A", chain)

        // Chain bank stays in Group mode.
        assertEquals("Deck A/FX/Super", MacroEngine.getBank(MacroEngine.DECK_A_FX)!!.knobs[0].bindings.single().parameterId)

        val s1 = MacroEngine.getBank("deckA_fx_s1")!!
        assertEquals("Deck A/FX/FX1/Meta", s1.knobs[0].bindings.single().parameterId)
        assertEquals(listOf("Deck A/FX/FX1/a1", "Deck A/FX/FX1/a2", "Deck A/FX/FX1/a3"), s1.knobs.drop(1).map { it.bindings.single().parameterId })

        // Empty slot: Metaknob binding to a missing path, parameter knobs cleared.
        val s2 = MacroEngine.getBank("deckA_fx_s2")!!
        assertTrue(s2.knobs.drop(1).all { it.bindings.isEmpty() && it.label == "—" })

        val s3 = MacroEngine.getBank("deckA_fx_s3")!!
        assertEquals("Deck A/FX/FX3/c2", s3.knobs[2].bindings.single().parameterId)
        assertTrue(s3.knobs[3].bindings.isEmpty())
    }

    @Test
    fun pagesArePerSlotAndSharedWithFocusMode() {
        val chain = unlinkedChain()
        chain.slots[0] = filter("one", listOf("a1", "a2", "a3", "a4"))
        chain.slots[1] = filter("two", listOf("b1", "b2", "b3", "b4", "b5", "b6", "b7"))

        chain.stepParamPage(1, 1)
        chain.stepParamPage(1, 1)
        assertEquals(listOf(0, 2, 0), chain.slotParamPage.toList())

        FxMacroSync.syncChain(MacroEngine.DECK_A_FX, "Deck A", chain)
        assertEquals("Deck A/FX/FX2/b7", MacroEngine.getBank("deckA_fx_s2")!!.knobs[1].bindings.single().parameterId)
        assertEquals("Deck A/FX/FX1/a1", MacroEngine.getBank("deckA_fx_s1")!!.knobs[1].bindings.single().parameterId)

        // Focusing slot 2 shows the page its Chain-view row is on; leaving and re-focusing keeps it.
        chain.focusSlot(1)
        assertEquals(2, chain.focusParamPage)
        FxMacroSync.syncChain(MacroEngine.DECK_A_FX, "Deck A", chain)
        assertEquals("Deck A/FX/FX2/b7", MacroEngine.getBank(MacroEngine.DECK_A_FX)!!.knobs[1].bindings.single().parameterId)
        chain.focusSlot(null)
        assertEquals(0, chain.focusParamPage)
        chain.focusSlot(1)
        assertEquals(2, chain.focusParamPage)
    }

    @Test
    fun slotBankPageStepTargetsItsOwnSlot() {
        val chain = unlinkedChain()
        chain.slots[0] = filter("one", listOf("a1", "a2", "a3", "a4"))
        chain.slots[2] = filter("three", listOf("c1", "c2", "c3", "c4"))
        chain.focusSlot(0)

        chain.stepParamPage(1, 2)
        assertEquals(listOf(0, 0, 1), chain.slotParamPage.toList())
        assertEquals(0, chain.focusParamPage)
    }

    @Test
    fun pageFollowsTheEffectOnSwapAndResetsOnNewEffect() {
        val chain = unlinkedChain()
        chain.slots[0] = filter("one", listOf("a1", "a2", "a3", "a4"))
        chain.slots[1] = filter("two", listOf("b1"))
        chain.stepParamPage(1, 0)

        chain.swapSlotWith(0, chain, 1)
        assertEquals(listOf(0, 1, 0), chain.slotParamPage.toList())

        chain.clearFxSlot(1)
        assertEquals(0, chain.slotParamPage[1])
    }

    @Test
    fun pageClampsWhenTheEffectShrinks() {
        val chain = unlinkedChain()
        chain.slots[0] = filter("one", listOf("a1", "a2", "a3", "a4"))
        chain.stepParamPage(1, 0)
        chain.slots[0] = filter("small", listOf("a1"))

        FxMacroSync.syncChain(MacroEngine.DECK_A_FX, "Deck A", chain)
        assertEquals(0, chain.slotParamPage[0])
    }
}
