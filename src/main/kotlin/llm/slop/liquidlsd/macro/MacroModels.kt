package llm.slop.liquidlsd.macro

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import java.util.UUID

/**
 * Data model for the Macro Controls system.
 *
 * See docs/user_guide/macros_and_rack.md for the user documentation,
 * and docs/developer/preset_management.md (§8) for how the same model is re-scoped
 * per-scope instance via [MacroBinding.unitInstanceId].
 *
 * This file is pure data model: no UI, no Learn Mode, no serialization-to-disk, no MIDI/OSC
 * wiring. Those land in later phases (see proposal §7).
 */

/** What kind of field a [MacroBinding] ultimately writes to. */
@Serializable
enum class MacroTargetType {
    /** Target [llm.slop.liquidlsd.parameters.ModulatableParameter.baseValue]. */
    PARAM_BASE_VALUE,

    /** Target a mutable property (depth, subdivision, morph, slope, hold, etc.) on a [llm.slop.liquidlsd.parameters.CvModulator]. */
    MODULATOR_PROPERTY
}

/** Response curve shaping applied to a macro's normalized [0,1] value before range-mapping. */
@Serializable
enum class MacroCurveType {
    LINEAR, EXPONENTIAL, LOGARITHMIC, S_CURVE, STEP
}

/**
 * A single binding from a [MacroControl]'s normalized value to one target field.
 * Up to [MacroControl.MAX_BINDINGS_PER_CONTROL] of these may exist per control.
 */
@Serializable
data class MacroBinding(
    // null = global/session scope (today's decks); non-null = a Rack unit's stable id (see §6 of the proposal).
    val unitInstanceId: String? = null,
    // e.g. "Deck A/fbZoom" (global scope) or "dimensionWarp" (local to unitInstanceId).
    val parameterId: String,
    val targetType: MacroTargetType,
    // Index in the target parameter's modulator stack. Only meaningful for MODULATOR_PROPERTY.
    val modulatorIndex: Int = 0,
    // e.g. "subdivision", "morph", "depth", "attackMs". Only meaningful for MODULATOR_PROPERTY.
    val propertyName: String = "",
    var minVal: Float = 0.0f,
    var maxVal: Float = 1.0f,
    var curve: MacroCurveType = MacroCurveType.LINEAR,
    // Only meaningful when curve == STEP; number of discrete quantized positions.
    var stepCount: Int = 8,
    var inverted: Boolean = false,
    var enabled: Boolean = true
)

/** One Macro Knob, read passively via [value]. */
@Serializable
data class MacroControl(
    val id: String = UUID.randomUUID().toString(),
    var label: String = "",
    // Normalized position [0.0..1.0].
    var value: Float = 0.0f,
    // Max MAX_BINDINGS_PER_CONTROL bindings; not enforced by the collection type itself.
    val bindings: MutableList<MacroBinding> = mutableListOf()
) {
    companion object {
        const val MAX_BINDINGS_PER_CONTROL = 4
    }
}

/**
 * The fixed-shape container for one macro surface: either the global Column 3 bank (session
 * scope, [MacroBinding.unitInstanceId] == null) or a single Rack unit's own local bank (see
 * proposal §6). Both use the same shape: up to 8 knobs.
 */
@Serializable
data class MacroBank(
    val knobs: List<MacroControl> = List(8) { MacroControl(label = "KNOB ${it + 1}") }
)

/**
 * Descriptive snapshot of a resolved macro binding and its owning [MacroControl],
 * formatted for UI indicators, badges, and tooltips.
 */
data class MacroBindingInfo(
    val binding: MacroBinding,
    val control: MacroControl,
    val index: Int,            // 0-based index within the knobs list
    val badgeLabel: String,    // e.g. "K1"
    val controlName: String    // e.g. "Knob 1" or "KNOB 1 (WARP)"
)
