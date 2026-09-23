package llm.slop.liquidlsd.macro

import io.mockk.mockk
import kotlinx.serialization.json.JsonPrimitive
import llm.slop.liquidlsd.rendering.FxBank
import llm.slop.liquidlsd.rendering.Shader
import llm.slop.liquidlsd.rendering.isf.ISFFilter
import llm.slop.liquidlsd.rendering.isf.ISFHeader
import llm.slop.liquidlsd.rendering.isf.ISFInput
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FxMacroSyncTest {

    @BeforeTest
    fun setUp() {
        for (id in listOf(MacroEngine.FX_BANK_1, MacroEngine.FX_BANK_2, MacroEngine.MASTER_FX)) {
            MacroEngine.unregisterBank(id)
        }
    }

    @AfterTest
    fun tearDown() {
        for (id in listOf(MacroEngine.FX_BANK_1, MacroEngine.FX_BANK_2, MacroEngine.MASTER_FX)) {
            MacroEngine.unregisterBank(id)
        }
    }

    private fun testFilter(id: String): ISFFilter {
        val header = ISFHeader(INPUTS = listOf(
            ISFInput(NAME = "inputImage", TYPE = "image"),
            ISFInput(NAME = "amount", TYPE = "float", MIN = JsonPrimitive(0.0f), MAX = JsonPrimitive(1.0f), DEFAULT = JsonPrimitive(0.0f))
        ))
        val shader = mockk<Shader>(relaxed = true)
        return ISFFilter(id, id, header, shader)
    }

    @Test
    fun testSyncBindsSuperKnobAndMetaknobsWithIdentityCurve() {
        for (bankLabel in listOf("FX1", "FX2", "MFX")) {
            val bankId = MacroEngine.canonicalIdForDeckLabel(bankLabel)
            val bank = FxBank(bankLabel)
            bank.activeChain.slots[0] = testFilter("fx_a")
            bank.activeChain.setSlotLinked(0, false)
            bank.activeChain.setSlotLinked(1, false)
            bank.activeChain.setSlotLinked(2, false)

            FxMacroSync.sync(bankId, bank)

            val macroBank = MacroEngine.getBank(bankId)!!
            val superBinding = macroBank.knobs[0].bindings.single()
            assertEquals("$bankLabel/C1/Super", superBinding.parameterId)
            assertEquals(MacroTargetType.PARAM_BASE_VALUE, superBinding.targetType)
            assertEquals(0f, superBinding.minVal)
            assertEquals(1f, superBinding.maxVal)
            assertEquals(MacroCurveType.LINEAR, superBinding.curve)
            assertFalse(superBinding.inverted)

            val metaBinding1 = macroBank.knobs[1].bindings.single()
            assertEquals("$bankLabel/C1/FX1/Meta", metaBinding1.parameterId)
            val metaBinding3 = macroBank.knobs[3].bindings.single()
            assertEquals("$bankLabel/C1/FX3/Meta", metaBinding3.parameterId)
        }
    }

    @Test
    fun testSyncUsesActiveChainNumberInPaths() {
        val bank = FxBank("FX1")
        bank.activeChainIndex = 1
        for (i in 0 until 3) bank.activeChain.setSlotLinked(i, false)

        FxMacroSync.sync(MacroEngine.FX_BANK_1, bank)

        val macroBank = MacroEngine.getBank(MacroEngine.FX_BANK_1)!!
        assertEquals("FX1/C2/Super", macroBank.knobs[0].bindings.single().parameterId)
        assertEquals("FX1/C2/FX2/Meta", macroBank.knobs[2].bindings.single().parameterId)
    }

    @Test
    fun testOwnershipRuleLeavesManuallyRetargetedKnobAlone() {
        val bank = FxBank("FX1")
        for (i in 0 until 3) bank.activeChain.setSlotLinked(i, false)
        FxMacroSync.sync(MacroEngine.FX_BANK_1, bank)

        val macroBank = MacroEngine.getBank(MacroEngine.FX_BANK_1)!!
        // User manually retargets Knob 2 away from the FxMacroSync pattern.
        macroBank.knobs[1].bindings.clear()
        macroBank.knobs[1].bindings.add(
            MacroBinding(parameterId = "Deck A/fbZoom", targetType = MacroTargetType.PARAM_BASE_VALUE)
        )
        macroBank.knobs[1].label = "MY WARP"

        // Switch chains -- a real focus-change trigger -- and resync.
        bank.activeChainIndex = 2
        FxMacroSync.sync(MacroEngine.FX_BANK_1, bank)

        assertEquals("Deck A/fbZoom", macroBank.knobs[1].bindings.single().parameterId, "Manually retargeted knob must not be reclaimed")
        assertEquals("MY WARP", macroBank.knobs[1].label)
        // Untouched knobs still follow the new chain.
        assertEquals("FX1/C3/Super", macroBank.knobs[0].bindings.single().parameterId)
    }

    @Test
    fun testForceResyncOverridesManualRetarget() {
        val bank = FxBank("FX1")
        for (i in 0 until 3) bank.activeChain.setSlotLinked(i, false)
        FxMacroSync.sync(MacroEngine.FX_BANK_1, bank)

        val macroBank = MacroEngine.getBank(MacroEngine.FX_BANK_1)!!
        macroBank.knobs[1].bindings.clear()
        macroBank.knobs[1].bindings.add(MacroBinding(parameterId = "Deck A/fbZoom", targetType = MacroTargetType.PARAM_BASE_VALUE))

        FxMacroSync.sync(MacroEngine.FX_BANK_1, bank, forceResync = true)

        assertEquals("FX1/C1/FX1/Meta", macroBank.knobs[1].bindings.single().parameterId)
    }

    @Test
    fun testLinkedSlotIsSkippedAndUnlinkingRestoresBinding() {
        val bank = FxBank("FX1")
        // Default slotSuperKnobLink is all-true; leave slot 1 linked, unlink the others so we can
        // isolate its behavior.
        bank.activeChain.setSlotLinked(0, false)
        bank.activeChain.setSlotLinked(2, false)
        assertTrue(bank.activeChain.slotSuperKnobLink[1])

        FxMacroSync.sync(MacroEngine.FX_BANK_1, bank)

        val macroBank = MacroEngine.getBank(MacroEngine.FX_BANK_1)!!
        assertTrue(macroBank.knobs[2].bindings.isEmpty(), "A linked slot's knob must not get a MacroBinding (would race FxChain's soft-takeover propagation)")

        // Unlinking restores the smart-default binding.
        bank.activeChain.setSlotLinked(1, false)
        FxMacroSync.sync(MacroEngine.FX_BANK_1, bank)
        assertEquals("FX1/C1/FX2/Meta", macroBank.knobs[2].bindings.single().parameterId)
    }

    @Test
    fun testSyncChainMatchesSyncForTheActiveChain() {
        val bank = FxBank("FX2")
        bank.activeChainIndex = 2
        for (i in 0 until 3) bank.activeChain.setSlotLinked(i, false)

        FxMacroSync.syncChain(MacroEngine.FX_BANK_2, bank.label, bank.activeChain, bank.activeChainIndex)

        val macroBank = MacroEngine.getBank(MacroEngine.FX_BANK_2)!!
        assertEquals("FX2/C3/Super", macroBank.knobs[0].bindings.single().parameterId)
    }

    @Test
    fun testSyncDeckFxBindsSuperKnobAndMetaknobs() {
        val chain = llm.slop.liquidlsd.rendering.FxChain("Deck A FX")
        for (i in 0 until 3) chain.setSlotLinked(i, false)

        FxMacroSync.syncDeckFx(MacroEngine.DECK_A_FX, "Deck A", chain)

        val macroBank = MacroEngine.getBank(MacroEngine.DECK_A_FX)!!
        val superBinding = macroBank.knobs[0].bindings.single()
        assertEquals("Deck A/FX/Super", superBinding.parameterId)
        assertEquals(MacroTargetType.PARAM_BASE_VALUE, superBinding.targetType)
        assertEquals(0f, superBinding.minVal)
        assertEquals(1f, superBinding.maxVal)

        val metaBinding1 = macroBank.knobs[1].bindings.single()
        assertEquals("Deck A/FX/FX1/Meta", metaBinding1.parameterId)
        val metaBinding3 = macroBank.knobs[3].bindings.single()
        assertEquals("Deck A/FX/FX3/Meta", metaBinding3.parameterId)
    }

    @Test
    fun testSyncDeckFxLinkedSlotBehavior() {
        val chain = llm.slop.liquidlsd.rendering.FxChain("Deck B FX")
        // Default: slots are linked
        assertTrue(chain.slotSuperKnobLink[0])
        assertTrue(chain.slotSuperKnobLink[1])
        assertTrue(chain.slotSuperKnobLink[2])

        FxMacroSync.syncDeckFx(MacroEngine.DECK_B_FX, "Deck B", chain)

        val macroBank = MacroEngine.getBank(MacroEngine.DECK_B_FX)!!
        assertEquals("Deck B/FX/Super", macroBank.knobs[0].bindings.single().parameterId)
        // Linked slots have no macro bindings (avoid racing soft-takeover)
        assertTrue(macroBank.knobs[1].bindings.isEmpty())
        assertTrue(macroBank.knobs[2].bindings.isEmpty())
        assertTrue(macroBank.knobs[3].bindings.isEmpty())

        // Unlink slot 0
        chain.setSlotLinked(0, false)
        FxMacroSync.syncDeckFx(MacroEngine.DECK_B_FX, "Deck B", chain)
        assertEquals("Deck B/FX/FX1/Meta", macroBank.knobs[1].bindings.single().parameterId)
    }
}
