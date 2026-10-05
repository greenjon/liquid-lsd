package llm.slop.liquidlsd.ui

import llm.slop.liquidlsd.control.ControllerProfileStore
import llm.slop.liquidlsd.midi.MidiEngine
import llm.slop.liquidlsd.rendering.FxChain

/** The "Default FX chain linking" preference: whether a chain with no saved link flags starts linked to its Super Knob. */
enum class FxLinkDefault(val label: String) {
    AUTO("Auto (unlinked with a knob controller)"),
    LINKED("Linked"),
    UNLINKED("Unlinked")
}

object FxLinkDefaults {
    /** Evaluated lazily so AUTO sees the controllers connected at the time a chain is created or loaded. */
    fun resolve(
        pref: FxLinkDefault = UITheme.fxLinkDefault,
        hasKnobController: () -> Boolean = ::knobControllerConnected
    ): Boolean = when (pref) {
        FxLinkDefault.LINKED -> true
        FxLinkDefault.UNLINKED -> false
        FxLinkDefault.AUTO -> !hasKnobController()
    }

    /** True when a connected device matches a profile with per-knob feedback (e.g. the 16-knob Twister). */
    private fun knobControllerConnected(): Boolean = try {
        MidiEngine.getConnectedDeviceNames().any { ControllerProfileStore.default.matchFor(it)?.profile?.output?.knobs != null }
    } catch (e: Exception) { false }

    fun install() { FxChain.defaultLinked = { resolve() } }
}
