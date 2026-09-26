package llm.slop.liquidlsd.macro

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class MasterAndSendsMacroDefaultsTest {

    @Test
    fun testDefaultKnobCountForAllCanonicalBanksIsFour() {
        for (canonicalId in MacroEngine.CANONICAL_BANK_IDS) {
            assertEquals(
                4,
                MacroEngine.defaultKnobCountFor(canonicalId),
                "Canonical bank '$canonicalId' should have default knob count of 4"
            )
        }
    }

    @Test
    fun testCanonicalIdForDeckLabelMapsMstAndTrans() {
        assertEquals(MacroEngine.MASTER, MacroEngine.canonicalIdForDeckLabel("Master"))
        assertEquals(MacroEngine.MASTER, MacroEngine.canonicalIdForDeckLabel("MST"))
        assertEquals(MacroEngine.TRANS, MacroEngine.canonicalIdForDeckLabel("TRANS"))
        assertEquals(MacroEngine.TRANS, MacroEngine.canonicalIdForDeckLabel("Transition"))
    }

    @Test
    fun testMasterBankDefaultBindings() {
        val bank = MacroEngine.newBankFor(MacroEngine.MASTER)
        assertEquals(4, bank.knobs.size, "Master bank should have 4 knobs")

        val expected = listOf(
            Triple("ALPHA A", 1.0f, "Mixer/levelA"),
            Triple("ALPHA B", 1.0f, "Mixer/levelB"),
            Triple("ALPHA BG", 0.0f, "Mixer/levelBG"),
            Triple("MASTER", 1.0f, "Mixer/masterLevel")
        )

        for (i in expected.indices) {
            val (label, defaultVal, paramId) = expected[i]
            val knob = bank.knobs[i]
            assertEquals(label, knob.label)
            assertEquals(defaultVal, knob.value)
            assertEquals(1, knob.bindings.size, "Knob '$label' should have 1 default binding")
            val binding = knob.bindings.first()
            assertEquals(paramId, binding.parameterId)
            assertEquals(MacroTargetType.PARAM_BASE_VALUE, binding.targetType)
            assertEquals(0.0f, binding.minVal)
            assertEquals(1.0f, binding.maxVal)
        }
    }

    @Test
    fun testFxSendsBankDefaultBindings() {
        val bank = MacroEngine.newBankFor(MacroEngine.FX_SENDS)
        assertEquals(4, bank.knobs.size, "FX Sends bank should have 4 knobs")

        val expected = listOf(
            Triple("Deck A", 1.0f, "Deck A/FXChain/DryWet"),
            Triple("Deck B", 1.0f, "Deck B/FXChain/DryWet"),
            Triple("Deck BG", 1.0f, "Deck BG/FXChain/DryWet"),
            Triple("Deck PV", 1.0f, "Deck PV/FXChain/DryWet")
        )

        for (i in expected.indices) {
            val (label, defaultVal, paramId) = expected[i]
            val knob = bank.knobs[i]
            assertEquals(label, knob.label)
            assertEquals(defaultVal, knob.value)
            assertEquals(1, knob.bindings.size, "Knob '$label' should have 1 default binding")
            val binding = knob.bindings.first()
            assertEquals(paramId, binding.parameterId)
            assertEquals(MacroTargetType.PARAM_BASE_VALUE, binding.targetType)
            assertEquals(0.0f, binding.minVal)
            assertEquals(1.0f, binding.maxVal)
        }
    }
}
