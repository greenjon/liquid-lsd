package llm.slop.liquidlsd.midi

/**
 * What [MidiMappingManager] needs to bind a controller-profile command to the next control moved.
 * Defined here so midi/ does not reach into control/'s profile storage; the implementation lives in control/.
 */
interface ProfileLearner {
    sealed class Outcome {
        /** The profile no longer exists. */
        object Missing : Outcome()
        /** The event cannot be bound (e.g. a modifier input); keep waiting for another control. */
        data class Ignored(val reason: String) : Outcome()
        data class Saved(val key: String, val addedInput: String?) : Outcome()
        data class NotSaved(val problems: List<String>) : Outcome()
    }

    fun learn(profileId: String, event: MidiEvent, commandId: String, modifiers: List<String>): Outcome

    companion object {
        /** Used until the composition root wires a real learner: every learn reports a missing profile. */
        val NONE: ProfileLearner = object : ProfileLearner {
            override fun learn(profileId: String, event: MidiEvent, commandId: String, modifiers: List<String>) = Outcome.Missing
        }
    }
}
