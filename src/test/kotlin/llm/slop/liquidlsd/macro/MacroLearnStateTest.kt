package llm.slop.liquidlsd.macro

import kotlin.test.*

class MacroLearnStateTest {

    @BeforeTest
    fun setUp() {
        MacroLearnState.cancelLearn()
        MacroLearnState.clearStatus()
        MacroEngine.registerBank(MacroEngine.DECK_A, MacroBank())
        MacroEngine.registerBank(MacroEngine.DECK_A_FX, MacroBank())
    }

    @AfterTest
    fun tearDown() {
        MacroLearnState.cancelLearn()
        MacroLearnState.clearStatus()
        MacroEngine.registerBank(MacroEngine.DECK_A, MacroBank())
        MacroEngine.registerBank(MacroEngine.DECK_A_FX, MacroBank())
    }

    @Test
    fun testStartAndCancelLearn() {
        val bank = MacroEngine.getBank(MacroEngine.DECK_A)!!
        val knob = bank.knobs[0]

        MacroLearnState.startLearn(knob.id)
        assertTrue(MacroLearnState.isLearning())
        assertTrue(MacroLearnState.isControlLearning(knob.id))
        assertEquals(knob.id, MacroLearnState.selectedControlId)

        MacroLearnState.cancelLearn()
        assertFalse(MacroLearnState.isLearning())
        assertFalse(MacroLearnState.isControlLearning(knob.id))
    }

    @Test
    fun testBindTargetBaseValue() {
        val bank = MacroEngine.getBank(MacroEngine.DECK_A)!!
        val knob = bank.knobs[0]
        MacroLearnState.startLearn(knob.id)

        val success = MacroLearnState.bindTarget(
            bank = bank,
            targetType = MacroTargetType.PARAM_BASE_VALUE,
            parameterId = "Deck A/fbZoom",
            minVal = -1f,
            maxVal = 1f
        )

        assertTrue(success)
        assertFalse(MacroLearnState.isLearning())
        assertEquals(1, knob.bindings.size)

        val binding = knob.bindings[0]
        assertEquals("Deck A/fbZoom", binding.parameterId)
        assertEquals(MacroTargetType.PARAM_BASE_VALUE, binding.targetType)
        assertEquals(-1f, binding.minVal)
        assertEquals(1f, binding.maxVal)
        assertTrue(binding.enabled)
    }

    @Test
    fun testBindTargetModulatorProperty() {
        val bank = MacroEngine.getBank(MacroEngine.DECK_A)!!
        val knob = bank.knobs[1]
        MacroLearnState.startLearn(knob.id)

        val success = MacroLearnState.bindTarget(
            bank = bank,
            targetType = MacroTargetType.MODULATOR_PROPERTY,
            parameterId = "Deck A/warp",
            modulatorIndex = 0,
            propertyName = "morph",
            minVal = 0f,
            maxVal = 1f,
            curve = MacroCurveType.S_CURVE
        )

        assertTrue(success)
        assertFalse(MacroLearnState.isLearning())
        assertEquals(1, knob.bindings.size)

        val binding = knob.bindings[0]
        assertEquals("Deck A/warp", binding.parameterId)
        assertEquals(MacroTargetType.MODULATOR_PROPERTY, binding.targetType)
        assertEquals(0, binding.modulatorIndex)
        assertEquals("morph", binding.propertyName)
        assertEquals(MacroCurveType.S_CURVE, binding.curve)
    }

    @Test
    fun testMaxBindingsLimitEnforced() {
        val bank = MacroEngine.getBank(MacroEngine.DECK_A)!!
        val knob = bank.knobs[2]

        for (i in 1..4) {
            MacroLearnState.startLearn(knob.id)
            val success = MacroLearnState.bindTarget(
                bank = bank,
                targetType = MacroTargetType.PARAM_BASE_VALUE,
                parameterId = "Deck A/param_$i"
            )
            assertTrue(success, "Binding $i should succeed")
        }

        assertEquals(4, knob.bindings.size)

        // 5th binding must fail
        MacroLearnState.startLearn(knob.id)
        val fifthSuccess = MacroLearnState.bindTarget(
            bank = bank,
            targetType = MacroTargetType.PARAM_BASE_VALUE,
            parameterId = "Deck A/param_5"
        )
        assertFalse(fifthSuccess, "5th binding must fail because max is 4")
        assertEquals(4, knob.bindings.size)
    }

    @Test
    fun testDuplicateBindingRejected() {
        val bank = MacroEngine.getBank(MacroEngine.DECK_A)!!
        val knob = bank.knobs[0]

        MacroLearnState.startLearn(knob.id)
        MacroLearnState.bindTarget(bank, MacroTargetType.PARAM_BASE_VALUE, "Deck A/zoom")

        MacroLearnState.startLearn(knob.id)
        val duplicateSuccess = MacroLearnState.bindTarget(bank, MacroTargetType.PARAM_BASE_VALUE, "Deck A/zoom")

        assertFalse(duplicateSuccess)
        assertEquals(1, knob.bindings.size)
    }

    @Test
    fun testAcceptsTargetScopesDeckSrcAndFx() {
        assertTrue(MacroLearnState.acceptsTarget(MacroEngine.DECK_A, "Deck A/fbZoom"))
        assertFalse(MacroLearnState.acceptsTarget(MacroEngine.DECK_A, "Deck A/FX/slot1/mix"))
        assertFalse(MacroLearnState.acceptsTarget(MacroEngine.DECK_A, "Deck B/fbZoom"))
        assertFalse(MacroLearnState.acceptsTarget(MacroEngine.DECK_B, "Deck BG/fbZoom"))

        assertTrue(MacroLearnState.acceptsTarget(MacroEngine.DECK_A_FX, "Deck A/FX/slot1/mix"))
        assertFalse(MacroLearnState.acceptsTarget(MacroEngine.DECK_A_FX, "Deck A/fbZoom"))
        assertFalse(MacroLearnState.acceptsTarget(MacroEngine.DECK_A_FX, "Deck B/FX/slot1/mix"))

        assertTrue(MacroLearnState.acceptsTarget(MacroEngine.MASTER_FX, "Master/FX/slot1/mix"))
        assertFalse(MacroLearnState.acceptsTarget(MacroEngine.MASTER_FX, "Deck A/FX/slot1/mix"))

        // Master, Transitions and FX Sends aren't section-scoped.
        assertTrue(MacroLearnState.acceptsTarget(MacroEngine.MASTER, "Mixer/levelA"))
        assertTrue(MacroLearnState.acceptsTarget(MacroEngine.FX_SENDS, "Deck B/fxSendLevel"))
    }

    @Test
    fun testOutOfSectionBindRejectedAndLearnStaysArmed() {
        val bank = MacroEngine.getBank(MacroEngine.DECK_A)!!
        val knob = bank.knobs[0]
        MacroLearnState.startLearn(knob.id)

        assertFalse(MacroLearnState.bindTarget(bank, MacroTargetType.PARAM_BASE_VALUE, "Deck A/FX/slot1/mix"))
        assertTrue(knob.bindings.isEmpty())
        assertTrue(MacroLearnState.isControlLearning(knob.id))

        assertTrue(MacroLearnState.bindTarget(bank, MacroTargetType.PARAM_BASE_VALUE, "Deck A/fbZoom"))
        assertEquals(1, knob.bindings.size)
    }

    @Test
    fun testNavigatingAwayFromArmedSectionDisarms() {
        val fxKnob = MacroEngine.getBank(MacroEngine.DECK_A_FX)!!.knobs[0]
        MacroLearnState.startLearn(fxKnob.id)

        MacroLearnState.onNavigateSection("Deck A", "FX")
        assertTrue(MacroLearnState.isLearning(), "Staying in Deck A FX keeps Learn armed")

        MacroLearnState.onNavigateSection("Deck A", "SRC")
        assertFalse(MacroLearnState.isLearning(), "Leaving Deck A FX disarms Learn")
    }
}
