package llm.slop.liquidlsd.parameters

/**
 * Global "reactivity amount": scales the depth of every recipe modulator (see [CvModulator.recipe]).
 * 0 leaves the authored base values untouched, 1 is the curated recipe, above 1 exaggerates it.
 * An app setting, not part of presets. Hand-built, MIDI and macro modulators ignore it.
 */
object Reactivity {
    const val MAX = 2.0f

    @Volatile var amount: Float = 1.0f
        set(value) { field = value.coerceIn(0f, MAX) }

    /** The depth [mod] contributes right now: its own depth, times [amount] for recipe modulators. */
    fun effectiveDepth(mod: CvModulator): Float = if (mod.recipe) mod.depth * amount else mod.depth
}
