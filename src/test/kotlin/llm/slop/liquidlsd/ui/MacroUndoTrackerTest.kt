package llm.slop.liquidlsd.ui

import io.mockk.mockk
import llm.slop.liquidlsd.macro.MacroBinding
import llm.slop.liquidlsd.macro.MacroEngine
import llm.slop.liquidlsd.macro.MacroTargetType
import llm.slop.liquidlsd.rendering.Mixer
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class MacroUndoTrackerTest {
    private val mixer = mockk<Mixer>(relaxed = true)
    private lateinit var state: ParametersState
    private val knob get() = MacroEngine.getBank(MacroEngine.DECK_A)!!.knobs[0]

    @BeforeTest
    fun setUp() {
        for (id in MacroEngine.CANONICAL_BANK_IDS) MacroEngine.registerBank(id, MacroEngine.newBankFor(id))
        state = ParametersState()
        MacroUndoTracker.reset()
        MacroUndoTracker.update(state, mixer, false) // baseline
    }

    @AfterTest
    fun tearDown() {
        MacroUndoTracker.reset()
        for (id in MacroEngine.CANONICAL_BANK_IDS) MacroEngine.registerBank(id, MacroEngine.newBankFor(id))
    }

    private fun addTarget() {
        knob.bindings.add(MacroBinding(parameterId = "Deck A/zoom", targetType = MacroTargetType.PARAM_BASE_VALUE))
    }

    @Test
    fun aDragIsOneUndoStepAndUndoRestoresTheRange() {
        addTarget()
        MacroUndoTracker.update(state, mixer, false)            // click that added the target (one step)
        MacroUndoTracker.update(state, mixer, false)            // quiet frame ends the gesture
        for (v in listOf(0.9f, 0.8f, 0.7f)) {                   // a drag on Max: mouse held, changes every frame
            knob.bindings[0].maxVal = v
            MacroUndoTracker.update(state, mixer, true)
        }
        MacroUndoTracker.update(state, mixer, false)

        ParametersUndo.performUndo(state, mixer)                // undoes the whole drag
        assertEquals(1f, knob.bindings[0].maxVal)
        assertEquals(1, knob.bindings.size)
        ParametersUndo.performUndo(state, mixer)                // undoes the added target
        assertEquals(0, knob.bindings.size)
    }

    @Test
    fun renameIsUndoable() {
        val original = knob.label
        knob.label = "WOBBLE"
        MacroUndoTracker.update(state, mixer, false)
        ParametersUndo.performUndo(state, mixer)
        assertEquals(original, knob.label)
    }

    @Test
    fun knobValueChangesAreNotEdits() {
        knob.value = 0.8f
        MacroUndoTracker.update(state, mixer, false)
        assertEquals(null, state.popUndoState())
    }

    @Test
    fun undoItselfAndWholesaleLoadsDoNotCreateNewSteps() {
        addTarget()
        MacroUndoTracker.update(state, mixer, false)
        ParametersUndo.performUndo(state, mixer)
        MacroUndoTracker.update(state, mixer, false)             // the restore must not look like an edit
        assertEquals(null, state.popUndoState())

        addTarget()
        MacroEngine.noteBankReplaced()                           // e.g. a preset load installed this bank
        MacroUndoTracker.update(state, mixer, false)
        assertEquals(null, state.popUndoState())
    }
}
