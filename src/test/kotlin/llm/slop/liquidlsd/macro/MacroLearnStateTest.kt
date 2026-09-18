package llm.slop.liquidlsd.macro

import kotlin.test.*

class MacroLearnStateTest {

    @BeforeTest
    fun setUp() {
        MacroLearnState.cancelLearn()
        MacroLearnState.clearStatus()
        MacroEngine.registerBank(MacroEngine.DECK_A, MacroBank())
    }

    @AfterTest
    fun tearDown() {
        MacroLearnState.cancelLearn()
        MacroLearnState.clearStatus()
        MacroEngine.registerBank(MacroEngine.DECK_A, MacroBank())
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
}
