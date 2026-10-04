package llm.slop.liquidlsd.ui

import llm.slop.liquidlsd.macro.MacroEngine

/** What a Performance row shows in place of its left control lines. See [macroStripModeFor]. */
enum class MacroStripMode {
    /** Normal deck/master/clock controls. */
    NONE,

    /** The selected knob belongs to this row's active bank: the strip replaces the controls. */
    STRIP,

    /** A GLOBAL knob is selected and this Edit row is visiting: the strip draws with a "GLB" tag. */
    GUEST
}

/**
 * Pure visibility rule for the Edit-row macro binding strip.
 *
 * @param isEditView true only in the Edit view; Perform view never shows the strip.
 * @param rowBankId the row's *active* bank (after the SRC/FX or MIX/FX pill is applied).
 * @param selectedBankId the bank of `MacroLearnState.selectedControlId`, null when nothing is selected.
 */
fun macroStripModeFor(isEditView: Boolean, rowBankId: String, selectedBankId: String?): MacroStripMode = when {
    !isEditView || selectedBankId == null -> MacroStripMode.NONE
    selectedBankId == rowBankId -> MacroStripMode.STRIP
    // GLOBAL has no Edit view of its own (its row isn't expandable), so it rides along on any other row.
    selectedBankId == MacroEngine.GLOBAL -> MacroStripMode.GUEST
    else -> MacroStripMode.NONE
}
