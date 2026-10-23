package llm.slop.liquidlsd.macro

import kotlin.test.Test
import kotlin.test.assertEquals

class LegacyMasterLabelTest {

    private fun knob(label: String, target: String) = MacroControl(
        label = label,
        bindings = mutableListOf(MacroBinding(parameterId = target, targetType = MacroTargetType.PARAM_BASE_VALUE))
    )

    @Test
    fun oldDefaultLabelsBoundToTheMatchingLevelAreRenamed() {
        val bank = MacroBank(listOf(
            knob("ALPHA A", "Mixer/levelA"),
            knob("ALPHA B", "Mixer/levelB"),
            knob("ALPHA BG", "Mixer/levelBG"),
            knob("MASTER", "Mixer/masterLevel")
        ))
        MacroEngine.relabelLegacyMasterKnobs(bank)
        assertEquals(listOf("LEVEL A", "LEVEL B", "LEVEL BG", "MASTER"), bank.knobs.map { it.label })
    }

    @Test
    fun editedLabelsAndRebindingsAreLeftAlone() {
        val bank = MacroBank(listOf(
            knob("My Fade", "Mixer/levelA"),
            knob("ALPHA B", "Mixer/levelA"),
            MacroControl(label = "ALPHA BG")
        ))
        MacroEngine.relabelLegacyMasterKnobs(bank)
        assertEquals(listOf("My Fade", "ALPHA B", "ALPHA BG"), bank.knobs.map { it.label })
    }
}
