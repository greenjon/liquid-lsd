package llm.slop.liquidlsd.control

import llm.slop.liquidlsd.midi.MidiEvent
import llm.slop.liquidlsd.midi.MidiMessageType

/** Pure edits of a profile's global `bindings` map, used by the binding editor. */
object ProfileBindingEdit {

    /** The binding key for [inputId] held with [modifiers] (sorted, like the compiler's lookup keys). */
    fun key(modifiers: Collection<String>, inputId: String): String =
        (modifiers.sorted() + inputId).joinToString("+")

    /** The input part of a binding key (after the last `+`). */
    fun inputOf(key: String): String = key.substringAfterLast('+')

    fun modifiersOf(key: String): List<String> = key.split('+').dropLast(1)

    fun set(profile: ControllerProfile, key: String, commandId: String): ControllerProfile =
        profile.copy(bindings = profile.bindings + (key to commandId))

    fun remove(profile: ControllerProfile, key: String): ControllerProfile =
        profile.copy(bindings = profile.bindings - key)

    /** Which kind of input a command of [kind] can be driven by. */
    fun fits(kind: CommandKind, input: InputKind): Boolean = when (input) {
        InputKind.ENCODER -> kind == CommandKind.RELATIVE
        InputKind.BUTTON -> kind == CommandKind.TRIGGER || kind == CommandKind.TOGGLE || kind == CommandKind.MOMENTARY
        InputKind.FADER -> kind == CommandKind.SCALAR
        InputKind.MODIFIER, InputKind.BANK_SWITCH -> false
    }

    /**
     * Whether [commandId] suits the input of [key]. Wildcard keys and unknown inputs or commands
     * are not judged (true), since the compiler is the authority on those.
     */
    fun commandFits(compiled: CompiledController, registry: CommandRegistry, key: String, commandId: String): Boolean {
        val input = inputOf(key)
        if ('*' in input) return true
        val inputKind = compiled.inputKinds[input] ?: return true
        val command = registry.get(commandId) ?: return true
        return fits(command.kind, inputKind)
    }

    /** Input ids that can take a binding: everything except modifiers (those are held, not bound). */
    fun bindableInputs(compiled: CompiledController): List<String> =
        compiled.inputKinds.filterValues { it != InputKind.MODIFIER && it != InputKind.BANK_SWITCH }.keys.toList()

    fun modifierInputs(compiled: CompiledController): List<String> =
        compiled.inputKinds.filterValues { it == InputKind.MODIFIER }.keys.toList()

    /** Outcome of [learn]: the edited profile and key, or a reason the event cannot be learned. */
    sealed class Learned {
        data class Bound(val profile: ControllerProfile, val key: String, val addedInput: String?) : Learned()
        data class Ignored(val reason: String) : Learned()
    }

    /**
     * Binds [commandId] to whichever input [event] came from, held with [modifiers]. An event no
     * input of the profile claims (a control the profile does not know yet) becomes a new input,
     * guessed from the message: notes are buttons, 63/65 CCs are relative encoders, other CCs faders.
     * Modifier and bank-switch inputs cannot be bound, so those events are ignored.
     */
    fun learn(compiled: CompiledController, event: MidiEvent, commandId: String, modifiers: Collection<String>): Learned {
        val profile = compiled.profile
        val resolved = compiled.resolve(event)
        if (resolved != null) {
            if (resolved.kind == InputKind.MODIFIER || resolved.kind == InputKind.BANK_SWITCH) {
                return Learned.Ignored("${resolved.inputId} is a ${resolved.kind.name.lowercase().replace('_', ' ')}, not a bindable input")
            }
            val key = key(modifiers, resolved.inputId)
            return Learned.Bound(set(profile, key, commandId), key, null)
        }
        val kind = when {
            event.type == MidiMessageType.NOTE -> InputKind.BUTTON
            event.type == MidiMessageType.CC && (event.rawValue == 63 || event.rawValue == 65) -> InputKind.ENCODER
            else -> InputKind.FADER
        }
        val id = "${event.type.name.lowercase()}-${event.channel + 1}-${event.index}"
        val def = InputDef(
            id = id, kind = kind, channel = event.channel, messageType = event.type, cc = event.index,
            mode = if (kind == InputKind.ENCODER) EncoderMode.RELATIVE_BINARY_OFFSET else EncoderMode.ABSOLUTE
        )
        val key = key(modifiers, id)
        val edited = set(profile.copy(inputs = profile.inputs + def), key, commandId)
        return Learned.Bound(edited, key, id)
    }
}
