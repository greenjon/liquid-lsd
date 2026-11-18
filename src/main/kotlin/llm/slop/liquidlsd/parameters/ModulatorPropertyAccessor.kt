package llm.slop.liquidlsd.parameters

/**
 * Allocation-free accessor and mutator for mutable properties on a [CvModulator].
 *
 * Shared across [llm.slop.liquidlsd.macro.MacroEngine], [llm.slop.liquidlsd.osc.OscMappingManager],
 * and hardware control surfaces to target internal modulator variables (LFO period/speed,
 * depth, waveform shape, envelope attack/decay, etc.) via unified property names.
 */
object ModulatorPropertyAccessor {

    /** Resolves a stable modulator id to the modulator currently on [param], or null if gone. */
    fun findById(param: ModulatableParameter, id: String?): CvModulator? {
        if (id == null) return null
        for (m in param.modulators) if (m.id == id) return m
        return null
    }

    /** Min/Max polarity of [mod] on [param]; without a parameter the source's own polarity is used. */
    private fun unipolar(mod: CvModulator, param: ModulatableParameter?): Boolean =
        param?.let { mod.unipolarOn(it) } ?: mod.sourceUnipolar

    /**
     * Mutates the matching `var` field on [mod]. [param] (the parameter [mod] sits on) decides how Min/Max map
     * onto Base/Amount for the sequencer; pass it whenever it is known.
     * Base (`dcOffset`) and Amount (`depth`) are written unclamped, so a controller can push the swing past
     * the -1..1 track; the parameter clamps its final value.
     * Unrecognized property names are a silent no-op — never throw.
     */
    fun set(mod: CvModulator, propertyName: String, value: Float, param: ModulatableParameter? = null) {
        when (propertyName) {
            "depth" -> mod.depth = value
            "lfoMin" -> {
                val u = unipolar(mod, param)
                val r = mod.withLfoRange(value, mod.getLfoMax(u), u)
                mod.dcOffset = r.dcOffset
                mod.depth = r.depth
            }
            "lfoMax" -> {
                val u = unipolar(mod, param)
                val r = mod.withLfoRange(mod.getLfoMin(u), value, u)
                mod.dcOffset = r.dcOffset
                mod.depth = r.depth
            }
            "lfoMinRollMin" -> mod.lfoMinRollMin = value
            "lfoMinRollMax" -> mod.lfoMinRollMax = value
            "lfoMaxRollMin" -> mod.lfoMaxRollMin = value
            "lfoMaxRollMax" -> mod.lfoMaxRollMax = value
            "subdivision" -> mod.subdivision = value
            "phaseOffset" -> mod.phaseOffset = value
            "slope" -> mod.slope = value
            "morph" -> mod.morph = value
            "hold" -> mod.hold = value
            "dcOffset" -> mod.dcOffset = value
            "dcOffsetMin" -> mod.dcOffsetMin = value
            "dcOffsetMax" -> mod.dcOffsetMax = value
            "depthMin" -> mod.depthMin = value
            "depthMax" -> mod.depthMax = value
            "attackMs" -> mod.attackMs = value
            "decayMs" -> mod.decayMs = value
            "modSubdivision" -> mod.modSubdivision = value
            "modPhaseOffset" -> mod.modPhaseOffset = value
            "modSlope" -> mod.modSlope = value
            "modMorph" -> mod.modMorph = value
            "modHold" -> mod.modHold = value
            "generatorModDepth" -> mod.generatorModDepth = value
            "seqHold" -> mod.seqHold = value
            else -> {}
        }
    }

    /**
     * Reads the current value of the matching property on [mod], or null if unrecognized.
     */
    fun get(mod: CvModulator, propertyName: String, param: ModulatableParameter? = null): Float? {
        return when (propertyName) {
            "depth" -> mod.depth
            "lfoMin" -> mod.getLfoMin(unipolar(mod, param))
            "lfoMax" -> mod.getLfoMax(unipolar(mod, param))
            "lfoMinRollMin" -> mod.lfoMinRollMin
            "lfoMinRollMax" -> mod.lfoMinRollMax
            "lfoMaxRollMin" -> mod.lfoMaxRollMin
            "lfoMaxRollMax" -> mod.lfoMaxRollMax
            "subdivision" -> mod.subdivision
            "phaseOffset" -> mod.phaseOffset
            "slope" -> mod.slope
            "morph" -> mod.morph
            "hold" -> mod.hold
            "dcOffset" -> mod.dcOffset
            "dcOffsetMin" -> mod.dcOffsetMin
            "dcOffsetMax" -> mod.dcOffsetMax
            "depthMin" -> mod.depthMin
            "depthMax" -> mod.depthMax
            "attackMs" -> mod.attackMs
            "decayMs" -> mod.decayMs
            "modSubdivision" -> mod.modSubdivision
            "modPhaseOffset" -> mod.modPhaseOffset
            "modSlope" -> mod.modSlope
            "modMorph" -> mod.modMorph
            "modHold" -> mod.modHold
            "generatorModDepth" -> mod.generatorModDepth
            "seqHold" -> mod.seqHold
            else -> null
        }
    }

    /**
     * Resolves the `<ref>` segment of a persisted `path:mod/<ref>/<prop>` MIDI/OSC target.
     * New paths carry a stable modulator id; a purely numeric segment is a legacy list position.
     */
    fun findByPathRef(param: ModulatableParameter, ref: String): CvModulator? =
        ref.toIntOrNull()?.let { param.modulators.getOrNull(it) } ?: findById(param, ref)

    /** Label for a path `<ref>` segment: legacy positions keep "LFO 1/2"; ids can't be positioned here. */
    fun formatPathRefLabel(ref: String, propertyName: String): String =
        ref.toIntOrNull()?.let { formatPropertyLabel(it, propertyName) }
            ?: formatPropertyLabel(-1, propertyName)

    /**
     * Formats a user-friendly label for UI badges and mapping tables.
     * E.g. (0, "subdivision") -> "LFO 1 Speed", (1, "morph") -> "LFO 2 Morph".
     */
    fun formatPropertyLabel(modulatorIndex: Int, propertyName: String): String {
        val modName = when (modulatorIndex) {
            0 -> "LFO 1"
            1 -> "LFO 2"
            -1 -> "Mod"
            else -> "Mod ${modulatorIndex + 1}"
        }
        val propLabel = when (propertyName) {
            "subdivision" -> "Speed"
            "depth" -> "Amount"
            "phaseOffset" -> "Phase"
            "slope" -> "Asymmetry"
            "morph" -> "Morph"
            "hold" -> "Hold"
            "dcOffset" -> "Base"
            "dcOffsetMin", "dcOffsetMax" -> "Base Roll"
            "depthMin", "depthMax" -> "Amount Roll"
            "lfoMinRollMin", "lfoMinRollMax" -> "Min Roll"
            "lfoMaxRollMin", "lfoMaxRollMax" -> "Max Roll"
            "lfoMin" -> "Min"
            "lfoMax" -> "Max"
            "attackMs" -> "Attack"
            "decayMs" -> "Decay"
            "modSubdivision" -> "LFO 2 Speed"
            "modPhaseOffset" -> "LFO 2 Phase"
            "modSlope" -> "LFO 2 Asymmetry"
            "modMorph" -> "LFO 2 Morph"
            "modHold" -> "LFO 2 Hold"
            "generatorModDepth" -> "LFO 2 Depth"
            "seqHold" -> "Step Hold"
            else -> propertyName
        }
        return if (propertyName.startsWith("mod")) propLabel else "$modName $propLabel"
    }
}
