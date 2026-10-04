package llm.slop.liquidlsd.ui

import io.mockk.every
import io.mockk.mockk
import llm.slop.liquidlsd.SessionContext
import llm.slop.liquidlsd.macro.MacroBinding
import llm.slop.liquidlsd.macro.MacroEngine
import llm.slop.liquidlsd.macro.MacroTargetType
import llm.slop.liquidlsd.parameters.ModulatableParameter
import llm.slop.liquidlsd.rendering.Deck
import llm.slop.liquidlsd.rendering.Mixer
import llm.slop.liquidlsd.rendering.VisualSource
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** Ctrl+Z after a source change must bring back the old source and the macro bank the swap replaced. */
class DeckSourceSwapUndoTest {
    private class Src(override val id: String) : VisualSource {
        override val displayName = id
        override val parameters: Map<String, ModulatableParameter> = emptyMap()
        override val globalAlpha = ModulatableParameter(1f)
        override fun getParameterPaths(prefix: String) = listOf("$prefix/globalAlpha" to globalAlpha)
        override fun clone(): VisualSource = Src(id)
    }

    @BeforeTest
    fun setUp() {
        for (id in MacroEngine.CANONICAL_BANK_IDS) MacroEngine.registerBank(id, MacroEngine.newBankFor(id))
        ToastOverlay.show("", 0L)
    }

    @AfterTest
    fun tearDown() {
        for (id in MacroEngine.CANONICAL_BANK_IDS) MacroEngine.registerBank(id, MacroEngine.newBankFor(id))
    }

    @Test
    fun undoRestoresTheOldSourceAndMacroBank() {
        var current: VisualSource = Src("old_gen")
        val deck = mockk<Deck>(relaxed = true)
        every { deck.source } answers { current }
        every { deck.source = any() } answers { current = firstArg() }
        var empty = false
        every { deck.isEmpty } answers { empty }
        every { deck.isEmpty = any() } answers { empty = firstArg() }
        val mixer = mockk<Mixer>(relaxed = true)
        every { mixer.deckA } returns deck
        val session = mockk<SessionContext>(relaxed = true)
        val state = ParametersState()

        val bank = MacroEngine.getBank(MacroEngine.DECK_A)!!
        bank.knobs[0].label = "MINE"
        bank.knobs[0].value = 0.6f
        bank.knobs[0].bindings.add(MacroBinding(parameterId = "Deck A/zoom", targetType = MacroTargetType.PARAM_BASE_VALUE, maxVal = 0.7f))
        val oldSource = current

        DeckSourcePicker.swapSource(session, state, mixer, deck, "Deck A", Src("new_gen"))

        assertEquals("new_gen", current.id)
        assertTrue(bank.knobs[0].bindings.isEmpty(), "the swap replaced the bank")
        assertTrue(ToastOverlay.active()?.contains("Ctrl+Z") == true, "the toast tells the user how to get it back")

        ParametersUndo.performUndo(state, mixer)

        assertSame(oldSource, current)
        assertEquals("MINE", bank.knobs[0].label)
        assertEquals(0.6f, bank.knobs[0].value)
        assertEquals(listOf("Deck A/zoom"), bank.knobs[0].bindings.map { it.parameterId })
        assertEquals(0.7f, bank.knobs[0].bindings[0].maxVal)
    }
}
