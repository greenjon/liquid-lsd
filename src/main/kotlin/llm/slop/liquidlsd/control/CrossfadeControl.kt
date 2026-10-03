package llm.slop.liquidlsd.control

/** The slice of the mixer that control commands drive: snapping and auto-triggering the crossfader. */
interface CrossfadeControl {
    var isAutoFading: Boolean
    var targetCrossfade: Float

    /** The crossfader's base value, -1 = Deck A, 1 = Deck B. */
    val crossfadeBase: Float

    /** Sets the crossfader's base value directly. */
    fun setCrossfade(value: Float)

    /** Halts an auto-fade, disarms Auto-VJ and mutes non-MIDI CV on the crossfader. */
    fun onCrossfadeManualTakeover()

    fun muteCrossfadeNonMidiCv()
}
