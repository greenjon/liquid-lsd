package llm.slop.liquidlsd.control

import llm.slop.liquidlsd.midi.MidiEvent
import llm.slop.liquidlsd.midi.ProfileLearner

/** [ProfileLearner] backed by a [ControllerProfileStore]: binds via [ProfileBindingEdit] and saves the user file. */
class ControllerProfileLearner(private val store: ControllerProfileStore = ControllerProfileStore.default) : ProfileLearner {
    override fun learn(profileId: String, event: MidiEvent, commandId: String, modifiers: List<String>): ProfileLearner.Outcome {
        val compiled = store.get(profileId) ?: return ProfileLearner.Outcome.Missing
        return when (val result = ProfileBindingEdit.learn(compiled, event, commandId, modifiers)) {
            is ProfileBindingEdit.Learned.Ignored -> ProfileLearner.Outcome.Ignored(result.reason)
            is ProfileBindingEdit.Learned.Bound -> {
                val problems = store.saveUser(result.profile)
                if (problems.isEmpty()) ProfileLearner.Outcome.Saved(result.key, result.addedInput)
                else ProfileLearner.Outcome.NotSaved(problems)
            }
        }
    }
}
