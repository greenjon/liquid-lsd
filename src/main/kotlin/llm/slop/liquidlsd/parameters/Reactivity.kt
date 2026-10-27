package llm.slop.liquidlsd.parameters

/**
 * Global "reactivity amount": scales the depth and DC offset of every recipe modulator (see [CvModulator.recipe]),
 * so the whole swing scales and a recipe may sit above its parameter's set value (offset = depth).
 * 0 leaves the authored base values untouched, 1 is the curated recipe, above 1 exaggerates it.
 * An app setting, not part of presets. Hand-built, MIDI and macro modulators ignore it.
 */
object Reactivity {
    const val MAX = 2.0f

    @Volatile var amount: Float = 1.0f
        set(value) { field = value.coerceIn(0f, MAX) }

    /** The depth [mod] contributes right now: its own depth, times [amount] for recipe modulators. */
    fun effectiveDepth(mod: CvModulator): Float = if (mod.recipe) mod.depth * amount else mod.depth

    /** The DC offset [mod] contributes right now: its own, times [amount] for recipe modulators. */
    fun effectiveOffset(mod: CvModulator): Float = if (mod.recipe) mod.dcOffset * amount else mod.dcOffset
}
