package llm.slop.liquidlsd.parameters

/**
 * Allocation-free accessor and mutator for mutable properties on a [CvModulator].
 *
 * Shared across [llm.slop.liquidlsd.macro.MacroEngine], [llm.slop.liquidlsd.osc.OscMappingManager],
 * and hardware control surfaces to target internal modulator variables (LFO period/speed,
 * depth, waveform shape, envelope attack/decay, etc.) via unified property names.
 */
object ModulatorPropertyAccessor {

    /**
     * Mutates the matching `var` field on [mod].
     * Unrecognized property names are a silent no-op — never throw.
     */
    fun set(mod: CvModulator, propertyName: String, value: Float) {
        when (propertyName) {
            "depth" -> mod.depth = value
            "lfoMin" -> {
                val max = mod.getLfoMax()
                mod.dcOffset = (value + max) / 2f
                mod.depth = (max - value) / 2f
            }
            "lfoMax" -> {
                val min = mod.getLfoMin()
                mod.dcOffset = (min + value) / 2f
                mod.depth = (value - min) / 2f
            }
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
    fun get(mod: CvModulator, propertyName: String): Float? {
        return when (propertyName) {
            "depth" -> mod.depth
            "lfoMin" -> mod.getLfoMin()
            "lfoMax" -> mod.getLfoMax()
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
     * Formats a user-friendly label for UI badges and mapping tables.
     * E.g. (0, "subdivision") -> "LFO 1 Speed", (1, "morph") -> "LFO 2 Morph".
     */
    fun formatPropertyLabel(modulatorIndex: Int, propertyName: String): String {
        val modName = when (modulatorIndex) {
            0 -> "LFO 1"
            1 -> "LFO 2"
            else -> "Mod ${modulatorIndex + 1}"
        }
        val propLabel = when (propertyName) {
            "subdivision" -> "Speed"
            "depth" -> "Depth"
            "phaseOffset" -> "Phase"
            "slope" -> "Asymmetry"
            "morph" -> "Morph"
            "hold" -> "Hold"
            "dcOffset" -> "DC Offset"
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
