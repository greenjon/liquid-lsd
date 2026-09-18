package llm.slop.liquidlsd.macro

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import java.util.UUID

/**
 * Data model for the Macro Controls system.
 *
 * See docs/user_guide/macros_and_rack.md for the user documentation,
 * and docs/developer/preset_management.md (§8) for how the same model is re-scoped
 * per rack-unit instance via [MacroBinding.unitInstanceId].
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

/** Press/release semantics for a Macro Switch (see §3.4 of the proposal). */
@Serializable
enum class SwitchBehavior {
    /** Latch: click toggles between Min and Max. */
    TOGGLE,

    /** Hold: Max while held, Min on release. */
    MOMENTARY,

    /** Impulse: sends a 1-frame pulse (one-shot trigger) then resets to 0. */
    TRIGGER;

    /** Short human-readable label used in UI combos and tooltips. */
    val label: String get() = when (this) {
        TOGGLE    -> "Toggle"
        MOMENTARY -> "Momentary"
        TRIGGER   -> "Trigger"
    }
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
    var enabled: Boolean = true,
    // null = inherit the parent MacroControl's switchBehavior. Only evaluated when the parent is
    // a switch control. Serialized as null when not set so old presets deserialize identically.
    var switchBehaviorOverride: SwitchBehavior? = null
) {
    // Per-binding runtime state for TOGGLE and TRIGGER overrides. @Transient excludes them from
    // serialization (they are frame-rate state, meaningless to persist — same pattern as
    // MacroControl.pendingTriggerReset). Not part of data-class equals/hashCode since they live
    // in the class body rather than the primary constructor.

    /** Current latch state for a TOGGLE override. Flips on each rising press edge. */
    @Transient var bindingLatchState: Boolean = false

    /** Armed by the engine on a rising press edge for a TRIGGER override; consumed one frame later. */
    @Transient var bindingPendingPulse: Boolean = false
}

/**
 * One Macro Knob or Macro Switch. Knobs are read passively via [value]; switches additionally
 * go through the press/release/trigger state machine below (see proposal §3.4). The engine's
 * per-frame evaluation reads [value] for bindings that inherit the control behavior, and
 * [rawPressValue] for bindings that carry a per-binding [MacroBinding.switchBehaviorOverride].
 */
@Serializable
data class MacroControl(
    val id: String = UUID.randomUUID().toString(),
    var label: String = "",
    // Normalized position [0.0..1.0].
    var value: Float = 0.0f,
    val isSwitch: Boolean = false,
    var switchBehavior: SwitchBehavior = SwitchBehavior.TOGGLE,
    // Max MAX_BINDINGS_PER_CONTROL bindings; not enforced by the collection type itself.
    val bindings: MutableList<MacroBinding> = mutableListOf()
) {
    // Transient runtime state for the TRIGGER switch behavior's one-shot reset pulse.
    // Not part of equals/hashCode/copy (it's a class-body property, not a primary-constructor
    // one) and explicitly excluded from serialization since it's not meaningful to persist.
    @Transient
    private var pendingTriggerReset: Boolean = false

    /**
     * Raw press signal: 1f on the frame the button is pressed, stays 1f while held, 0f on
     * release. Independent of [switchBehavior] processing — the existing [value] state machine
     * is left unchanged. [MacroEngine] reads this to drive per-binding [MacroBinding.switchBehaviorOverride]
     * logic (MOMENTARY tracking, TOGGLE/TRIGGER rising-edge detection).
     * Not serialized; frame-rate state.
     */
    @Transient var rawPressValue: Float = 0f

    /**
     * Previous frame's [rawPressValue], maintained by [MacroEngine.tick] to detect rising edges
     * (0→1 transitions) for TOGGLE and TRIGGER per-binding overrides. Not serialized.
     */
    @Transient internal var prevRawPressValue: Float = 0f

    /**
     * Called by whatever input source registers a press (UI click, MIDI note-on, OSC message).
     * See proposal §3.4 for the full state machine this implements.
     */
    fun onPress() {
        rawPressValue = 1f
        value = when (switchBehavior) {
            SwitchBehavior.TOGGLE -> if (value >= 0.5f) 0f else 1f
            SwitchBehavior.MOMENTARY -> 1f
            SwitchBehavior.TRIGGER -> {
                pendingTriggerReset = true
                1f
            }
        }
    }

    /** Called on release. Only MOMENTARY reacts; TOGGLE/TRIGGER are no-ops. */
    fun onRelease() {
        rawPressValue = 0f
        if (switchBehavior == SwitchBehavior.MOMENTARY) value = 0f
    }

    /**
     * Consumes a pending one-shot TRIGGER reset, if armed: resets [value] back to 0 and returns
     * true. Returns false (and does nothing) if no reset is pending. Called once per frame, after
     * binding evaluation, by [MacroEngine.tick] — this is what produces the "1-frame pulse" for
     * TRIGGER without the input source needing to know anything about frame timing.
     */
    internal fun consumeTriggerReset(): Boolean {
        if (pendingTriggerReset) {
            pendingTriggerReset = false
            value = 0f
            return true
        }
        return false
    }

    companion object {
        const val MAX_BINDINGS_PER_CONTROL = 4
    }
}

/**
 * The fixed-shape container for one macro surface: either the global Column 3 bank (session
 * scope, [MacroBinding.unitInstanceId] == null) or a single Rack unit's own local bank (see
 * proposal §6). Both use the same shape: up to 8 knobs, up to 4 switches.
 */
@Serializable
data class MacroBank(
    val knobs: List<MacroControl> = List(8) { MacroControl(label = "KNOB ${it + 1}") },
    val switches: List<MacroControl> = List(4) { MacroControl(isSwitch = true, label = "SW ${it + 1}") }
)

/**
 * Descriptive snapshot of a resolved macro binding and its owning [MacroControl],
 * formatted for UI indicators, badges, and tooltips.
 */
data class MacroBindingInfo(
    val binding: MacroBinding,
    val control: MacroControl,
    val isKnob: Boolean,
    val index: Int,            // 0-based index within knobs or switches list
    val badgeLabel: String,    // e.g. "K1", "SW2"
    val controlName: String    // e.g. "Knob 1" or "KNOB 1 (WARP)"
)
