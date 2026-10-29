package llm.slop.liquidlsd.macro

import llm.slop.liquidlsd.parameters.BaseDriver
import llm.slop.liquidlsd.parameters.ModulatableParameter

/**
 * Runs an edit of a locked parameter's Value backwards onto whatever drives it, so Value and knob stay linked in
 * both directions. The forward mapping rewrites the base value every frame, so the edit has to land on the knob.
 */
object KnobDrive {
    /**
     * Moves the macro knob or Metaknob that owns [param]'s base value to the position that reproduces [value].
     * Follows a Metaknob that is itself macro-bound up to its macro knob. Other targets of the same knob follow.
     * A Metaknob link wins over a macro binding on the same parameter, as in [MacroEngine.tick], which skips the macro.
     * Returns false when nothing drives [param] (the caller then writes the value itself).
     */
    fun setViaKnob(paramKey: String, param: ModulatableParameter, value: Float, depth: Int = 0): Boolean {
        val binding = param.metaDrivenBinding
        val knobParam = param.metaDrivenKnob
        if (param.metaDrivenBy != null && binding != null && knobParam != null) {
            if (depth > 1) return false
            val knob = binding.knobForTarget(value).coerceIn(0f, 1f)
            val metaKey = paramKey.substringBeforeLast('/') + "/Meta"
            if (!setViaKnob(metaKey, knobParam, knob, depth + 1)) knobParam.set(knob)
            param.baseValue = binding.mapKnobToTarget(knob)
            return true
        }
        val info = MacroEngine.findBaseBindingInfo(paramKey) ?: return false
        val knob = MacroCurve.inverse(value, info.binding).coerceIn(0f, 1f)
        info.control.value = knob
        param.baseValue = MacroCurve.mapToRange(knob, info.binding)
        return true
    }

    /** The [BaseDriver] for a parameter whose base value [control] drives through [binding]: moves the knob there. */
    fun driver(control: MacroControl, binding: MacroBinding) = BaseDriver { param, value ->
        control.value = MacroCurve.inverse(value, binding).coerceIn(0f, 1f)
        param.baseValue = MacroCurve.mapToRange(control.value, binding)
    }
}
