package llm.slop.liquidlsd.control

import llm.slop.liquidlsd.rendering.Mixer

/** How a [Command] consumes input. The registry uses this for edge detection and input validation. */
enum class CommandKind {
    TRIGGER,    // Fires once on the rising edge of a press
    TOGGLE,     // Same edge semantics as TRIGGER; the handler flips its own state
    MOMENTARY,  // Handler sees both edges (active while held)
    SCALAR,     // Absolute value in 0..1
    RELATIVE    // Signed delta (encoders, arrow keys)
}

/** A normalized input event delivered to a [Command] handler. */
sealed interface CommandInput {
    data class Press(val down: Boolean) : CommandInput
    data class Value(val value: Float) : CommandInput
    /** A signed change; the unit is defined by the command (knob commands take a fraction of full range). */
    data class Delta(val steps: Float) : CommandInput
}

/**
 * Per-dispatch state handed to command handlers. Handlers run on the render thread, so the queue
 * deltas can be plain fields; the caller reads them after dispatching a batch of events.
 */
class CommandContext(
    val mixer: Mixer,
    val onTapTempo: () -> Unit = {},
    /** The Perform-view knobs, when a UI is attached (null in headless tests). */
    var knobSurface: KnobSurface? = null,
    /** Navigation/browse (side buttons, knob 1 cursor), when a UI is attached. */
    var navSurface: NavSurface? = null
) {
    var queueDelta = 0
    var bgQueueDelta = 0
    var transQueueDelta = 0
}

class Command(
    val id: String,
    val kind: CommandKind,
    val category: String,
    val description: String,
    val handler: (CommandInput, CommandContext) -> Unit
)

/**
 * Stable-id registry of everything an input device, keyboard shortcut or OSC message can trigger.
 * Not thread-safe: register at startup and [execute] from the render thread only.
 */
class CommandRegistry {
    private val commands = LinkedHashMap<String, Command>()
    private val aliases = HashMap<String, String>()
    private val wasDown = HashMap<String, Boolean>()

    fun register(command: Command) {
        require(command.id !in commands) { "Duplicate command id: ${command.id}" }
        commands[command.id] = command
    }

    /** Lets a legacy identifier (e.g. an old `Global/...` mapping path) resolve to [commandId]. */
    fun registerAlias(alias: String, commandId: String) {
        require(commandId in commands) { "Alias $alias targets unknown command $commandId" }
        aliases[alias] = commandId
    }

    fun resolveId(idOrAlias: String): String? =
        if (idOrAlias in commands) idOrAlias else aliases[idOrAlias]

    fun get(idOrAlias: String): Command? = resolveId(idOrAlias)?.let { commands[it] }

    fun all(): List<Command> = commands.values.toList()

    /**
     * Runs a command. Returns false if the id is unknown, the input type doesn't fit the command's
     * kind, or an edge-triggered command was suppressed because the button was already down.
     */
    fun execute(idOrAlias: String, input: CommandInput, ctx: CommandContext): Boolean {
        val command = get(idOrAlias) ?: return false
        when (command.kind) {
            CommandKind.TRIGGER, CommandKind.TOGGLE -> {
                if (input !is CommandInput.Press) return false
                val previous = wasDown.put(command.id, input.down) ?: false
                if (!input.down || previous) return false
            }
            CommandKind.MOMENTARY -> if (input !is CommandInput.Press) return false
            CommandKind.SCALAR -> if (input !is CommandInput.Value) return false
            CommandKind.RELATIVE -> if (input !is CommandInput.Delta) return false
        }
        command.handler(input, ctx)
        return true
    }
}
