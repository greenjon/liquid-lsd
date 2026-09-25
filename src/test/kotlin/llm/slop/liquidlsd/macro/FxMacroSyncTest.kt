package llm.slop.liquidlsd.macro

import io.mockk.every
import io.mockk.mockk
import kotlinx.serialization.json.JsonPrimitive
import llm.slop.liquidlsd.rendering.FxChain
import llm.slop.liquidlsd.rendering.Mixer
import llm.slop.liquidlsd.rendering.Shader
import llm.slop.liquidlsd.rendering.isf.ISFFilter
import llm.slop.liquidlsd.rendering.isf.ISFHeader
import llm.slop.liquidlsd.rendering.isf.ISFInput
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class FxMacroSyncTest {

    @BeforeTest
    fun setUp() {
        for (id in FxMacroSync.FX_BANK_IDS) MacroEngine.unregisterBank(id)
    }

    @AfterTest
    fun tearDown() {
        for (id in FxMacroSync.FX_BANK_IDS) MacroEngine.unregisterBank(id)
    }

    private fun testFilter(id: String): ISFFilter {
        val header = ISFHeader(INPUTS = listOf(
            ISFInput(NAME = "inputImage", TYPE = "image"),
            ISFInput(NAME = "amount", TYPE = "float", MIN = JsonPrimitive(0.0f), MAX = JsonPrimitive(1.0f), DEFAULT = JsonPrimitive(0.0f))
        ))
        val shader = mockk<Shader>(relaxed = true)
        return ISFFilter(id, id, header, shader)
    }

    private fun unlinkedChain(label: String) = FxChain(label).also { c ->
        for (i in 0 until FxChain.SLOT_COUNT) c.setSlotLinked(i, false)
    }

    private class Chains {
        val a = FxChain("Deck A FX")
        val b = FxChain("Deck B FX")
        val bg = FxChain("Deck BG FX")
        val pv = FxChain("Deck PV FX")
        val master = FxChain("Master FX")
    }

    private fun mixerWith(chains: Chains): Mixer {
        val mixer = mockk<Mixer>(relaxed = true)
        every { mixer.deckA.fxChain } returns chains.a
        every { mixer.deckB.fxChain } returns chains.b
        every { mixer.deckBG.fxChain } returns chains.bg
        every { mixer.deckPV.fxChain } returns chains.pv
        every { mixer.masterFxChain } returns chains.master
        return mixer
    }

    @Test
    fun testSyncChainBindsSuperKnobAndMetaknobsWithIdentityCurve() {
        val chain = unlinkedChain("Master FX")
        chain.slots[0] = testFilter("fx_a")

        FxMacroSync.syncChain(MacroEngine.MASTER_FX, "Master", chain)

        val macroBank = MacroEngine.getBank(MacroEngine.MASTER_FX)!!
        val superBinding = macroBank.knobs[0].bindings.single()
        assertEquals("Master/FX/Super", superBinding.parameterId)
        assertEquals(MacroTargetType.PARAM_BASE_VALUE, superBinding.targetType)
        assertEquals(0f, superBinding.minVal)
        assertEquals(1f, superBinding.maxVal)
        assertEquals(MacroCurveType.LINEAR, superBinding.curve)
        assertFalse(superBinding.inverted)

        assertEquals("Master/FX/FX1/Meta", macroBank.knobs[1].bindings.single().parameterId)
        assertEquals("META", macroBank.knobs[1].label)
        assertEquals("Master/FX/FX3/Meta", macroBank.knobs[3].bindings.single().parameterId)
    }

    @Test
    fun testSyncForUsesTheRegisteredPathLabelForEveryFxBank() {
        // Regression: Performance rows used to pass "Deck A FX" / "Master", producing paths
        // ("Deck A FX/FX/Super", "Master/C1/Super") that no parameter is registered under.
        val chains = Chains()
        listOf(chains.a, chains.b, chains.bg, chains.pv, chains.master).forEach { c ->
            for (i in 0 until FxChain.SLOT_COUNT) c.setSlotLinked(i, false)
        }
        val mixer = mixerWith(chains)

        FxMacroSync.syncAll(mixer)

        val expected = mapOf(
            MacroEngine.DECK_A_FX to "Deck A", MacroEngine.DECK_B_FX to "Deck B", MacroEngine.DECK_BG_FX to "Deck BG",
            MacroEngine.DECK_PV_FX to "Deck PV", MacroEngine.MASTER_FX to "Master"
        )
        for ((bankId, label) in expected) {
            val macroBank = MacroEngine.getBank(bankId)!!
            assertEquals("$label/FX/Super", macroBank.knobs[0].bindings.single().parameterId, bankId)
            assertEquals("$label/FX/FX2/Meta", macroBank.knobs[2].bindings.single().parameterId, bankId)
        }
    }

    @Test
    fun testChainAndBankIdLookupsAreInverse() {
        val chains = Chains()
        val mixer = mixerWith(chains)
        for (bankId in FxMacroSync.FX_BANK_IDS) {
            val chain = FxMacroSync.chainFor(bankId, mixer)!!
            assertEquals(bankId, FxMacroSync.bankIdFor(chain, mixer))
        }
        assertSame(chains.master, FxMacroSync.chainFor(MacroEngine.MASTER_FX, mixer))
        assertNull(FxMacroSync.chainFor(MacroEngine.DECK_A, mixer))
        assertNull(FxMacroSync.bankIdFor(FxChain("orphan"), mixer))
    }

    @Test
    fun testOwnershipRuleLeavesManuallyRetargetedKnobAlone() {
        val chain = unlinkedChain("Master FX")
        FxMacroSync.syncChain(MacroEngine.MASTER_FX, "Master", chain)

        val macroBank = MacroEngine.getBank(MacroEngine.MASTER_FX)!!
        // User manually retargets Knob 2 away from the FxMacroSync pattern.
        macroBank.knobs[1].bindings.clear()
        macroBank.knobs[1].bindings.add(
            MacroBinding(parameterId = "Deck A/fbZoom", targetType = MacroTargetType.PARAM_BASE_VALUE)
        )
        macroBank.knobs[1].label = "MY WARP"

        // A new effect lands in the chain -- a real resync trigger.
        chain.slots[1] = testFilter("fx_b")
        FxMacroSync.syncChain(MacroEngine.MASTER_FX, "Master", chain)

        assertEquals("Deck A/fbZoom", macroBank.knobs[1].bindings.single().parameterId, "Manually retargeted knob must not be reclaimed")
        assertEquals("MY WARP", macroBank.knobs[1].label)
        // Untouched knobs still follow the chain.
        assertEquals("META", macroBank.knobs[2].label)
    }

    @Test
    fun testForceResyncOverridesManualRetarget() {
        val chain = unlinkedChain("Master FX")
        FxMacroSync.syncChain(MacroEngine.MASTER_FX, "Master", chain)

        val macroBank = MacroEngine.getBank(MacroEngine.MASTER_FX)!!
        macroBank.knobs[1].bindings.clear()
        macroBank.knobs[1].bindings.add(MacroBinding(parameterId = "Deck A/fbZoom", targetType = MacroTargetType.PARAM_BASE_VALUE))

        FxMacroSync.syncChain(MacroEngine.MASTER_FX, "Master", chain, forceResync = true)

        assertEquals("Master/FX/FX1/Meta", macroBank.knobs[1].bindings.single().parameterId)
    }

    @Test
    fun testKnobOwnedByAnotherChainIsNotReclaimed() {
        val chain = unlinkedChain("Deck A FX")
        FxMacroSync.syncChain(MacroEngine.DECK_A_FX, "Deck A", chain)
        val macroBank = MacroEngine.getBank(MacroEngine.DECK_A_FX)!!
        // User points Deck A's knob 1 at Deck B's Super Knob on purpose.
        macroBank.knobs[0].bindings.clear()
        macroBank.knobs[0].bindings.add(MacroBinding(parameterId = "Deck B/FX/Super", targetType = MacroTargetType.PARAM_BASE_VALUE))

        FxMacroSync.syncChain(MacroEngine.DECK_A_FX, "Deck A", chain)

        assertEquals("Deck B/FX/Super", macroBank.knobs[0].bindings.single().parameterId)
    }

    @Test
    fun testLinkedSlotIsSkippedAndUnlinkingRestoresBinding() {
        val chain = FxChain("Deck B FX")
        // Default: slots are linked
        assertTrue(chain.slotSuperKnobLink.all { it })

        FxMacroSync.syncChain(MacroEngine.DECK_B_FX, "Deck B", chain)

        val macroBank = MacroEngine.getBank(MacroEngine.DECK_B_FX)!!
        assertEquals("Deck B/FX/Super", macroBank.knobs[0].bindings.single().parameterId)
        // Linked slots have no macro bindings (would race FxChain's soft-takeover propagation)
        assertTrue(macroBank.knobs[1].bindings.isEmpty())
        assertTrue(macroBank.knobs[2].bindings.isEmpty())
        assertTrue(macroBank.knobs[3].bindings.isEmpty())

        chain.setSlotLinked(0, false)
        FxMacroSync.syncChain(MacroEngine.DECK_B_FX, "Deck B", chain)
        assertEquals("Deck B/FX/FX1/Meta", macroBank.knobs[1].bindings.single().parameterId)
    }
}
