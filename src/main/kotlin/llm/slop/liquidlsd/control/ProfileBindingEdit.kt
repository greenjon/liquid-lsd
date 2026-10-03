package llm.slop.liquidlsd.control

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
}
