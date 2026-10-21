package llm.slop.liquidlsd.macro

import llm.slop.liquidlsd.parameters.ModulatableParameter

/**
 * Runs an edit of a locked parameter's Value backwards onto whatever drives it, so Value and knob stay linked in
 * both directions. The forward mapping rewrites the base value every frame, so the edit has to land on the knob.
 */
object KnobDrive {
    /**
     * Moves the macro knob or Metaknob that owns [param]'s base value to the position that reproduces [value].
     * Follows a Metaknob that is itself macro-bound up to its macro knob. Other targets of the same knob follow.
     * Returns false when nothing drives [param] (the caller then writes the value itself).
     */
    fun setViaKnob(paramKey: String, param: ModulatableParameter, value: Float, depth: Int = 0): Boolean {
        val info = MacroEngine.findBaseBindingInfo(paramKey)
        if (info != null) {
            val knob = MacroCurve.inverse(value, info.binding).coerceIn(0f, 1f)
            info.control.value = knob
            param.baseValue = MacroCurve.mapToRange(knob, info.binding)
            return true
        }
        val binding = param.metaDrivenBinding
        val knobParam = param.metaDrivenKnob
        if (param.metaDrivenBy == null || binding == null || knobParam == null || depth > 1) return false
        val knob = binding.knobForTarget(value).coerceIn(0f, 1f)
        val metaKey = paramKey.substringBeforeLast('/') + "/Meta"
        if (!setViaKnob(metaKey, knobParam, knob, depth + 1)) knobParam.set(knob)
        param.baseValue = binding.mapKnobToTarget(knob)
        return true
    }
}
