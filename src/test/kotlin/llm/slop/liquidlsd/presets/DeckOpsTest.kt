package llm.slop.liquidlsd.presets

import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import llm.slop.liquidlsd.macro.MacroBank
import llm.slop.liquidlsd.macro.MacroBinding
import llm.slop.liquidlsd.macro.MacroControl
import llm.slop.liquidlsd.macro.MacroEngine
import llm.slop.liquidlsd.macro.MacroTargetType
import llm.slop.liquidlsd.models.DeckPresetDto
import llm.slop.liquidlsd.models.ParameterDto
import llm.slop.liquidlsd.models.applyDto
import llm.slop.liquidlsd.models.toDto
import llm.slop.liquidlsd.parameters.ModulatableParameter
import llm.slop.liquidlsd.rendering.Deck
import llm.slop.liquidlsd.rendering.Mixer
import llm.slop.liquidlsd.rendering.VisualSource
import llm.slop.liquidlsd.ui.ToastOverlay
import llm.slop.liquidlsd.ui.UITheme
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DeckOpsTest {
    private class Src(override val id: String) : VisualSource {
        override val displayName = id
        override val parameters: Map<String, ModulatableParameter> = mapOf("zoom" to ModulatableParameter(0.5f))
        override val globalAlpha = ModulatableParameter(1f)
        override fun getParameterPaths(prefix: String) = listOf("$prefix/globalAlpha" to globalAlpha)
        override fun clone(): VisualSource = Src(id)
    }

    /** A mock [Deck] whose source, empty flag and one tweakable value round-trip through toDto/applyDto. */
    private class FakeDeck(initial: VisualSource?) {
        var current: VisualSource = initial ?: Src("mandala")
        var empty = initial == null
        var tweak = 1f
        val deck = mockk<Deck>(relaxed = true)

        init {
            every { deck.source } answers { current }
            every { deck.source = any() } answers { current = firstArg() }
            every { deck.isEmpty } answers { empty }
            every { deck.isEmpty = any() } answers { empty = firstArg() }
            every { deck.toDto(any(), any()) } answers {
                dto(secondArg(), current.id, tweak, empty) // firstArg() is the extension receiver
            }
            every { deck.applyDto(any()) } answers {
                val dto = secondArg<DeckPresetDto>()
                current = Src(dto.visualSourceType)
                empty = dto.isEmpty
                tweak = dto.globalAlpha?.baseValue ?: 1f
            }
        }
    }

    private lateinit var a: FakeDeck
    private lateinit var b: FakeDeck
    private lateinit var mixer: Mixer
    private val undo = mutableListOf<() -> Unit>()
    private var prompted: (() -> Unit)? = null
    private var promptCancel: (() -> Unit)? = null

    companion object {
        fun dto(name: String, sourceId: String = "gen", tweak: Float = 1f, empty: Boolean = false, bank: MacroBank? = null) = DeckPresetDto(
            name = name,
            visualSourceType = sourceId,
            parameters = emptyMap(),
            feedbackParameters = emptyMap(),
            globalAlpha = ParameterDto(tweak, 0f, 1f, false, emptyList()),
            isEmpty = empty,
            macroBank = bank
        )

        fun bankWith(label: String, paramId: String) = MacroBank(
            knobs = MacroEngine.newBankFor(MacroEngine.DECK_A).knobs.mapIndexed { i, k ->
                if (i == 0) MacroControl(label = label, value = 0.4f, bindings = mutableListOf(
                    MacroBinding(parameterId = paramId, targetType = MacroTargetType.PARAM_BASE_VALUE)
                )) else k
            }
        )
    }

    @BeforeTest
    fun setUp() {
        mockkStatic("llm.slop.liquidlsd.models.PresetModelsKt")
        for (id in MacroEngine.CANONICAL_BANK_IDS) MacroEngine.registerBank(id, MacroEngine.newBankFor(id))
        for (slot in DeckSlot.values()) PresetManager.clearActive(slot)
        ToastOverlay.show("", 0L)
        UITheme.manualLoadDirtyBehavior = UITheme.ManualLoadDirtyBehavior.PROMPT
        UITheme.autoVjDirtyBehavior = UITheme.AutoVjDirtyBehavior.AUTO_DISCARD

        a = FakeDeck(Src("old_gen"))
        b = FakeDeck(Src("other_gen"))
        mixer = mockk(relaxed = true)
        every { mixer.deckA } returns a.deck
        every { mixer.deckB } returns b.deck
        every { mixer.deckBG } returns mockk(relaxed = true)
        every { mixer.deckPV } returns mockk(relaxed = true)

        undo.clear()
        prompted = null
        DeckOps.mixerProvider = { mixer }
        DeckOps.prompt = { _, proceed, cancel -> prompted = proceed; promptCancel = cancel }
        DeckOps.undoSink = { undo.add(it) }
        DeckOps.postApply = null
    }

    @AfterTest
    fun tearDown() {
        DeckOps.drainOnGlThread(mixer)
        DeckOps.mixerProvider = { null }
        DeckOps.prompt = null
        DeckOps.undoSink = null
        DeckOps.postApply = null
        UITheme.manualLoadDirtyBehavior = UITheme.ManualLoadDirtyBehavior.PROMPT
        UITheme.autoVjDirtyBehavior = UITheme.AutoVjDirtyBehavior.AUTO_DISCARD
        for (slot in DeckSlot.values()) PresetManager.clearActive(slot)
        for (id in MacroEngine.CANONICAL_BANK_IDS) MacroEngine.registerBank(id, MacroEngine.newBankFor(id))
    }

    private fun bank(slot: DeckSlot) = MacroEngine.getBank(slot.bankId)!!

    /** Loads [name] into A and makes the result the clean baseline. */
    private fun loadClean(name: String, bank: MacroBank? = bankWith("MINE", "Deck A/zoom")) {
        DeckOps.postLoaded(DeckSlot.A, dto(name, bank = bank))
        DeckOps.drainOnGlThread(mixer)
        undo.clear()
        ToastOverlay.show("", 0L)
    }

    @Test
    fun nothingIsAppliedBeforeTheDrain() {
        DeckOps.request(DeckSlot.A, DeckChange.Source(Src("new_gen")))
        assertEquals(1, DeckOps.pendingCount)
        assertEquals("old_gen", a.current.id)

        DeckOps.drainOnGlThread(mixer)
        assertEquals(0, DeckOps.pendingCount)
        assertEquals("new_gen", a.current.id)
    }

    @Test
    fun presetLoadCapturesUndoAndUndoRestoresDeckBankAndName() {
        loadClean("old_preset", bankWith("MINE", "Deck A/zoom"))
        assertEquals("MINE", bank(DeckSlot.A).knobs[0].label)

        DeckOps.postLoaded(DeckSlot.A, dto("new_preset", "new_gen", bank = bankWith("NEW", "Deck A/other")))
        DeckOps.drainOnGlThread(mixer)

        assertEquals(1, undo.size, "a manual preset load is undoable")
        assertEquals("new_preset", PresetManager.activePreset(DeckSlot.A))
        assertEquals("NEW", bank(DeckSlot.A).knobs[0].label)
        assertEquals("new_gen", a.current.id)
        assertTrue(ToastOverlay.active()?.contains("Ctrl+Z") == true, "replacing bound knobs toasts how to get them back")

        undo.single().invoke()

        assertEquals("gen", a.current.id)
        assertEquals("old_preset", PresetManager.activePreset(DeckSlot.A))
        assertEquals("MINE", bank(DeckSlot.A).knobs[0].label)
        assertEquals(listOf("Deck A/zoom"), bank(DeckSlot.A).knobs[0].bindings.map { it.parameterId })
        assertFalse(DeckOps.isDirty(a.deck, mixer), "the restored bank is the new baseline")
    }

    @Test
    fun sourceChangeUndoPutsBackTheSameSourceInstance() {
        val old = a.current
        DeckOps.request(DeckSlot.A, DeckChange.Source(Src("new_gen")))
        DeckOps.drainOnGlThread(mixer)
        assertEquals(1, undo.size)

        undo.single().invoke()

        assertTrue(old === a.current)
    }

    @Test
    fun queueOriginPushesNoUndoAndNoCtrlZHint() {
        loadClean("old_preset")
        DeckOps.postLoaded(DeckSlot.A, dto("queued", bank = bankWith("NEW", "Deck A/other")), LoadOrigin.QUEUE)
        DeckOps.drainOnGlThread(mixer)

        assertTrue(undo.isEmpty())
        assertEquals("queued", PresetManager.activePreset(DeckSlot.A))
        assertFalse(ToastOverlay.active()?.contains("Ctrl+Z") == true)
    }

    @Test
    fun sourceSwapSetsADirtyBaseline() {
        DeckOps.request(DeckSlot.A, DeckChange.Source(Src("new_gen")))
        DeckOps.drainOnGlThread(mixer)

        assertNull(PresetManager.activePreset(DeckSlot.A))
        assertNotNull(PresetManager.cachedDto(DeckSlot.A))
        assertFalse(DeckOps.isDirty(a.deck, mixer))

        a.tweak = 0.3f
        assertTrue(DeckOps.isDirty(a.deck, mixer), "tweaking a fresh generator shows as unsaved")
    }

    @Test
    fun repickingTheSameSourceDoesNothing() {
        DeckOps.request(DeckSlot.A, DeckChange.Source(Src("old_gen")))
        assertEquals(0, DeckOps.pendingCount)

        DeckOps.request(DeckSlot.A, DeckChange.Source(Src("old_gen"), force = true))
        assertEquals(1, DeckOps.pendingCount, "force re-applies")
    }

    @Test
    fun pickingTheDefaultSourceOnAnEmptyDeckStillApplies() {
        val empty = FakeDeck(null)
        every { mixer.deckA } returns empty.deck
        DeckOps.request(DeckSlot.A, DeckChange.Source(Src("mandala")))
        assertEquals(1, DeckOps.pendingCount)
    }

    @Test
    fun copyCarriesTheKnobBankRemappedToTheTargetDeck() {
        bank(DeckSlot.A).knobs[0].label = "MINE"
        bank(DeckSlot.A).knobs[0].bindings.add(MacroBinding(parameterId = "Deck A/zoom", targetType = MacroTargetType.PARAM_BASE_VALUE))
        PresetManager.setActive(DeckSlot.A, "a_preset", a.deck.toDto("a_preset"))

        DeckOps.request(DeckSlot.B, DeckChange.CopyFrom(DeckSlot.A))
        DeckOps.drainOnGlThread(mixer)

        assertEquals("old_gen", b.current.id)
        assertEquals("a_preset", PresetManager.activePreset(DeckSlot.B))
        assertEquals("MINE", bank(DeckSlot.B).knobs[0].label)
        assertEquals(listOf("Deck B/zoom"), bank(DeckSlot.B).knobs[0].bindings.map { it.parameterId })
        assertEquals(listOf("Deck A/zoom"), bank(DeckSlot.A).knobs[0].bindings.map { it.parameterId }, "copy leaves the source alone")
    }

    @Test
    fun moveAndSwapCarryTheBankToo() {
        bank(DeckSlot.A).knobs[0].label = "FROM_A"
        bank(DeckSlot.A).knobs[0].bindings.add(MacroBinding(parameterId = "Deck A/zoom", targetType = MacroTargetType.PARAM_BASE_VALUE))
        bank(DeckSlot.B).knobs[0].label = "FROM_B"
        bank(DeckSlot.B).knobs[0].bindings.add(MacroBinding(parameterId = "Deck B/zoom", targetType = MacroTargetType.PARAM_BASE_VALUE))

        DeckOps.request(DeckSlot.B, DeckChange.SwapWith(DeckSlot.A))
        DeckOps.drainOnGlThread(mixer)

        assertEquals("FROM_A", bank(DeckSlot.B).knobs[0].label)
        assertEquals("Deck B/zoom", bank(DeckSlot.B).knobs[0].bindings[0].parameterId)
        assertEquals("FROM_B", bank(DeckSlot.A).knobs[0].label)
        assertEquals("Deck A/zoom", bank(DeckSlot.A).knobs[0].bindings[0].parameterId)
        assertEquals("old_gen", b.current.id)
        assertEquals("other_gen", a.current.id)

        DeckOps.request(DeckSlot.A, DeckChange.MoveFrom(DeckSlot.B))
        DeckOps.drainOnGlThread(mixer)

        assertEquals("FROM_A", bank(DeckSlot.A).knobs[0].label)
        assertEquals("", bank(DeckSlot.B).knobs[0].label, "the emptied deck's bank is cleared")
        assertTrue(b.empty)
    }

    @Test
    fun presetWithNoBankGetsTheGeneratorDefaultKnobs() {
        DeckOps.postLoaded(DeckSlot.A, dto("legacy", bank = null))
        DeckOps.drainOnGlThread(mixer)

        assertEquals(listOf("Deck A/zoom"), bank(DeckSlot.A).knobs[0].bindings.map { it.parameterId })
    }

    @Test
    fun bankEditMakesTheDeckDirtyButKnobValuesDoNot() {
        loadClean("p")
        assertFalse(DeckOps.isDirty(a.deck, mixer))

        bank(DeckSlot.A).knobs[0].value = 0.9f
        assertFalse(DeckOps.isDirty(a.deck, mixer), "turning a knob is performing, not editing")

        bank(DeckSlot.A).knobs[0].label = "RENAMED"
        assertTrue(DeckOps.isDirty(a.deck, mixer))

        bank(DeckSlot.A).knobs[0].label = "MINE"
        assertFalse(DeckOps.isDirty(a.deck, mixer))
        bank(DeckSlot.A).knobs[1].bindings.add(MacroBinding(parameterId = "Deck A/x", targetType = MacroTargetType.PARAM_BASE_VALUE))
        assertTrue(DeckOps.isDirty(a.deck, mixer))
    }

    @Test
    fun onResultReportsAppliedAfterTheDrain() {
        var result: Boolean? = null
        DeckOps.request(DeckSlot.A, DeckChange.Eject) { result = it }
        assertNull(result)
        DeckOps.drainOnGlThread(mixer)
        assertEquals(true, result)
    }

    @Test
    fun onResultReportsFalseWhenThePromptIsCancelled() {
        loadClean("p")
        a.tweak = 0.2f // dirty
        var result: Boolean? = null
        DeckOps.request(DeckSlot.A, DeckChange.Eject) { result = it }
        assertNull(result)
        promptCancel!!.invoke()
        assertEquals(false, result)
        assertEquals(0, DeckOps.pendingCount)
    }

    @Test
    fun onResultReportsFalseWhenAQueueLoadIsSkipped() {
        loadClean("p")
        a.tweak = 0.2f
        UITheme.autoVjDirtyBehavior = UITheme.AutoVjDirtyBehavior.SKIP
        var result: Boolean? = null
        DeckOps.request(DeckSlot.A, DeckChange.Eject, LoadOrigin.QUEUE) { result = it }
        assertEquals(false, result)
    }

    @Test
    fun manualLoadsUseTheManualPreferenceAndQueueLoadsTheAutoVjOne() {
        loadClean("p")
        a.tweak = 0.2f // dirty

        // MANUAL + PROMPT: held until the user agrees.
        DeckOps.request(DeckSlot.A, DeckChange.Eject)
        assertEquals(0, DeckOps.pendingCount)
        prompted!!.invoke()
        assertEquals(1, DeckOps.pendingCount)
        DeckOps.drainOnGlThread(mixer)

        // MANUAL + DISCARD ignores the AutoVJ preference.
        a.tweak = 0.2f
        loadClean("p2")
        a.tweak = 0.2f
        UITheme.manualLoadDirtyBehavior = UITheme.ManualLoadDirtyBehavior.DISCARD
        UITheme.autoVjDirtyBehavior = UITheme.AutoVjDirtyBehavior.SKIP
        prompted = null
        DeckOps.request(DeckSlot.A, DeckChange.Eject)
        assertEquals(1, DeckOps.pendingCount)
        assertNull(prompted)
        DeckOps.drainOnGlThread(mixer)

        // QUEUE + SKIP leaves a dirty deck alone, without prompting.
        loadClean("p3")
        a.tweak = 0.2f
        assertTrue(DeckOps.wouldSkipQueueLoad(DeckSlot.A, mixer))
        assertFalse(DeckOps.request(DeckSlot.A, DeckChange.Eject, LoadOrigin.QUEUE), "a skipped queue load reports false")
        assertEquals(0, DeckOps.pendingCount)
        assertNull(prompted)

        // QUEUE + AUTO_DISCARD proceeds even though the manual preference is PROMPT.
        UITheme.manualLoadDirtyBehavior = UITheme.ManualLoadDirtyBehavior.PROMPT
        UITheme.autoVjDirtyBehavior = UITheme.AutoVjDirtyBehavior.AUTO_DISCARD
        assertFalse(DeckOps.wouldSkipQueueLoad(DeckSlot.A, mixer))
        assertTrue(DeckOps.request(DeckSlot.A, DeckChange.Eject, LoadOrigin.QUEUE))
        assertEquals(1, DeckOps.pendingCount)
        assertNull(prompted)
    }

    @Test
    fun cleanDecksNeverPromptAndMissingPromptHookProceeds() {
        loadClean("p")
        DeckOps.request(DeckSlot.A, DeckChange.Source(Src("new_gen")))
        assertNull(prompted)
        assertEquals(1, DeckOps.pendingCount)
        DeckOps.drainOnGlThread(mixer)

        a.tweak = 0.1f
        DeckOps.prompt = null
        DeckOps.request(DeckSlot.A, DeckChange.Eject)
        assertEquals(1, DeckOps.pendingCount, "tests and headless runs have no prompt wired")
    }

    @Test
    fun postApplyHookSeesEachAppliedChange() {
        val seen = mutableListOf<Pair<DeckSlot, DeckChange>>()
        DeckOps.postApply = { slot, change -> seen.add(slot to change) }
        DeckOps.request(DeckSlot.A, DeckChange.Source(Src("new_gen")))
        DeckOps.drainOnGlThread(mixer)

        assertEquals(DeckSlot.A, seen.single().first)
        assertTrue(seen.single().second is DeckChange.Source)
    }
}
