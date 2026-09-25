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
import kotlin.test.assertTrue

class FxFocusModeTest {

    @BeforeTest
    fun setUp() {
        for (id in FxMacroSync.FX_BANK_IDS) MacroEngine.unregisterBank(id)
    }

    @AfterTest
    fun tearDown() {
        for (id in FxMacroSync.FX_BANK_IDS) MacroEngine.unregisterBank(id)
    }

    private fun testFilterWithParams(id: String, paramNames: List<String>): ISFFilter {
        val inputs = mutableListOf(ISFInput(NAME = "inputImage", TYPE = "image"))
        for (name in paramNames) {
            inputs.add(
                ISFInput(
                    NAME = name,
                    TYPE = "float",
                    MIN = JsonPrimitive(0.0f),
                    MAX = JsonPrimitive(2.0f),
                    DEFAULT = JsonPrimitive(1.0f)
                )
            )
        }
        val header = ISFHeader(INPUTS = inputs)
        val shader = mockk<Shader>(relaxed = true)
        return ISFFilter(id, id, header, shader)
    }

    @Test
    fun testFocusRetargetingBindsDryWetAndParameters() {
        val chain = FxChain("Master FX")
        val filter = testFilterWithParams("glow", listOf("intensity", "radius", "threshold"))
        filter.parameters["intensity"]?.baseValue = 1.0f // 1.0 in [0, 2] -> 0.5 normalized
        filter.dryWet.baseValue = 0.85f
        chain.slots[0] = filter

        val bankId = MacroEngine.MASTER_FX
        FxMacroSync.syncChain(bankId, "Master", chain)

        // Initial: Group Mode
        assertNull(chain.focusedSlot)
        val bank = MacroEngine.getBank(bankId)!!
        assertEquals("SUPER", bank.knobs[0].label)
        assertEquals("Master/FX/Super", bank.knobs[0].bindings.single().parameterId)

        // Focus Slot 1 (index 0)
        chain.focusSlot(0)
        FxMacroSync.syncChain(bankId, "Master", chain)

        assertTrue(chain.isFocused())
        assertEquals(0, chain.focusedSlot)

        // Knob 0: Focused slot's individual Dry/Wet
        assertEquals("DRY/WET", bank.knobs[0].label)
        val dryWetBinding = bank.knobs[0].bindings.single()
        assertEquals("Master/FX/FX1/DryWet", dryWetBinding.parameterId)
        assertEquals(0.85f, bank.knobs[0].value)

        // Knobs 1-3: Parameters
        assertEquals("INTENSITY", bank.knobs[1].label)
        val p1Binding = bank.knobs[1].bindings.single()
        assertEquals("Master/FX/FX1/intensity", p1Binding.parameterId)
        assertEquals(0f, p1Binding.minVal)
        assertEquals(2f, p1Binding.maxVal)
        assertEquals(0.5f, bank.knobs[1].value) // 1.0 in range 0..2 is 0.5

        assertEquals("RADIUS", bank.knobs[2].label)
        assertEquals("Master/FX/FX1/radius", bank.knobs[2].bindings.single().parameterId)

        assertEquals("THRESHOLD", bank.knobs[3].label)
        assertEquals("Master/FX/FX1/threshold", bank.knobs[3].bindings.single().parameterId)
    }

    @Test
    fun testFocusPaginationWithMoreThanThreeParameters() {
        val chain = FxChain("Deck A FX")
        val filter = testFilterWithParams("multi", listOf("p1", "p2", "p3", "p4", "p5"))
        chain.slots[1] = filter // Slot 2 (index 1)

        val bankId = MacroEngine.DECK_A_FX
        chain.focusSlot(1)
        assertEquals(2, chain.totalParamPages(1))
        assertEquals(0, chain.focusParamPage)

        FxMacroSync.syncChain(bankId, "Deck A", chain)
        val bank = MacroEngine.getBank(bankId)!!

        // Page 0: p1, p2, p3
        assertEquals("P1", bank.knobs[1].label)
        assertEquals("Deck A/FX/FX2/p1", bank.knobs[1].bindings.single().parameterId)
        assertEquals("P2", bank.knobs[2].label)
        assertEquals("P3", bank.knobs[3].label)

        // Step to Page 1
        chain.stepParamPage(1)
        assertEquals(1, chain.focusParamPage)
        FxMacroSync.syncChain(bankId, "Deck A", chain)

        // Page 1: p4, p5, empty
        assertEquals("P4", bank.knobs[1].label)
        assertEquals("Deck A/FX/FX2/p4", bank.knobs[1].bindings.single().parameterId)
        assertEquals("P5", bank.knobs[2].label)
        assertEquals("Deck A/FX/FX2/p5", bank.knobs[2].bindings.single().parameterId)

        // Knob 3 has no parameter on page 1 -> cleared
        assertEquals("—", bank.knobs[3].label)
        assertTrue(bank.knobs[3].bindings.isEmpty())
        assertEquals(0f, bank.knobs[3].value)

        // Step again -> wraps back to Page 0
        chain.stepParamPage(1)
        assertEquals(0, chain.focusParamPage)
        FxMacroSync.syncChain(bankId, "Deck A", chain)
        assertEquals("P1", bank.knobs[1].label)
    }

    @Test
    fun testExitFocusModeRestoresGroupKnobs() {
        val chain = FxChain("Deck B FX")
        chain.slots[0] = testFilterWithParams("fx1", listOf("speed"))
        for (i in 0 until FxChain.SLOT_COUNT) chain.setSlotLinked(i, false)

        val bankId = MacroEngine.DECK_B_FX
        val mixer = mockk<Mixer>(relaxed = true)
        every { mixer.deckB.fxChain } returns chain

        // Focus slot 0
        FxMacroSync.focusSlot(bankId, mixer, 0)
        val bank = MacroEngine.getBank(bankId)!!
        assertEquals("DRY/WET", bank.knobs[0].label)
        assertEquals("SPEED", bank.knobs[1].label)

        // Exit focus
        FxMacroSync.focusSlot(bankId, mixer, null)
        assertFalse(chain.isFocused())
        assertEquals("SUPER", bank.knobs[0].label)
        assertEquals("Deck B/FX/Super", bank.knobs[0].bindings.single().parameterId)
        assertEquals("META", bank.knobs[1].label)
        assertEquals("Deck B/FX/FX1/Meta", bank.knobs[1].bindings.single().parameterId)
        assertEquals("META", bank.knobs[2].label)
        assertEquals("Deck B/FX/FX2/Meta", bank.knobs[2].bindings.single().parameterId)
    }

    @Test
    fun testMacroEngineLinkedKnobBypassDuringFocus() {
        val chain = FxChain("Deck A FX")
        chain.slots[0] = testFilterWithParams("fx", listOf("p1", "p2", "p3"))
        chain.superKnob.baseValue = 0.9f
        chain.slotSuperKnobLink[0] = true // Slot 1 linked to Super Knob

        val bankId = MacroEngine.DECK_A_FX
        chain.focusSlot(0)
        FxMacroSync.syncChain(bankId, "Deck A", chain)

        val bank = MacroEngine.getBank(bankId)!!
        bank.knobs[1].value = 0.25f // User turns parameter knob to 0.25

        val mixer = mockk<Mixer>(relaxed = true)
        every { mixer.deckA.fxChain } returns chain
        every { mixer.deckB.fxChain } returns FxChain("Deck B FX")
        every { mixer.deckBG.fxChain } returns FxChain("Deck BG FX")
        every { mixer.deckPV.fxChain } returns FxChain("Deck PV FX")
        every { mixer.masterFxChain } returns FxChain("Master FX")

        MacroEngine.tick(mixer)

        // Parameter knob must NOT be overwritten by superKnob (0.9f)
        assertEquals(0.25f, bank.knobs[1].value)
    }

    @Test
    fun testCustomUserBindingPreservedAcrossFocus() {
        val chain = FxChain("Deck A FX")
        chain.slots[0] = testFilterWithParams("fx", listOf("p1"))
        for (i in 0 until FxChain.SLOT_COUNT) chain.setSlotLinked(i, false)

        val bankId = MacroEngine.DECK_A_FX
        FxMacroSync.syncChain(bankId, "Deck A", chain)

        val bank = MacroEngine.getBank(bankId)!!
        // Manually bind Knob 3 (index 2) to custom parameter outside Deck A/FX/...
        bank.knobs[2].label = "ZOOM"
        bank.knobs[2].bindings.clear()
        bank.knobs[2].bindings.add(
            MacroBinding(
                parameterId = "Deck A/Geometry/Zoom",
                targetType = MacroTargetType.PARAM_BASE_VALUE,
                minVal = 0.5f,
                maxVal = 2.0f
            )
        )

        // Focus Slot 0
        chain.focusSlot(0)
        FxMacroSync.syncChain(bankId, "Deck A", chain)

        // Knob 3 custom binding should be preserved!
        assertEquals("ZOOM", bank.knobs[2].label)
        assertEquals("Deck A/Geometry/Zoom", bank.knobs[2].bindings.single().parameterId)

        // Exit Focus
        chain.focusSlot(null)
        FxMacroSync.syncChain(bankId, "Deck A", chain)

        assertEquals("ZOOM", bank.knobs[2].label)
        assertEquals("Deck A/Geometry/Zoom", bank.knobs[2].bindings.single().parameterId)
    }

    @Test
    fun testSlotSwapDuringFocusFollowsEffect() {
        val chain = FxChain("Master FX")
        chain.slots[0] = testFilterWithParams("f0", listOf("p0"))
        chain.slots[1] = testFilterWithParams("f1", listOf("p1"))

        chain.focusSlot(0)
        assertEquals(0, chain.focusedSlot)

        // Swap slot 0 with slot 1
        chain.swapSlotWith(0, chain, 1)

        // Focus should have moved with the effect to slot 1
        assertEquals(1, chain.focusedSlot)
    }
}
