package llm.slop.liquidlsd.ui

import llm.slop.liquidlsd.rendering.FxChain
import llm.slop.liquidlsd.rendering.SuperKnobMode

/**
 * The "Default FX chain linking" preference: whether a chain with no saved link flags starts linked to its Super Knob.
 * A linked slot's own knob still moves its Metaknob (see MacroEngine's linked-knob mirror), so linking costs no knob.
 */
enum class FxLinkDefault(val label: String) {
    LINKED("Linked"),
    UNLINKED("Unlinked")
}

object FxLinkDefaults {
    /** Evaluated lazily so a preference change applies to chains created or loaded afterwards. */
    fun resolve(pref: FxLinkDefault = UITheme.fxLinkDefault): Boolean = pref == FxLinkDefault.LINKED

    /** Labels for the "Super Knob response" preference, in [SuperKnobMode] order. */
    val SUPER_KNOB_MODE_LABELS = SuperKnobMode.entries.map {
        when (it) {
            SuperKnobMode.RELATIVE -> "Relative"
            SuperKnobMode.PICKUP -> "Pickup"
        }
    }.toTypedArray()

    fun install() {
        FxChain.defaultLinked = { resolve() }
        FxChain.superKnobMode = { UITheme.superKnobMode }
    }
}
