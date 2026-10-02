package llm.slop.liquidlsd.control

/**
 * Holds one [ControllerRuntime] per connected device, created the first time the device sends a
 * message and its name matches a controller profile. Render thread only.
 */
class ControllerManager(
    private val registry: CommandRegistry,
    private val store: ControllerProfileStore = ControllerProfileStore.default
) {
    private val runtimes = HashMap<String, ControllerRuntime?>()

    /** Handles [event] via its device's profile; false if the device has no profile or the profile ignores the input. */
    fun handle(event: llm.slop.liquidlsd.midi.MidiEvent, ctx: CommandContext): Boolean {
        if (event.deviceId.isEmpty()) return false
        if (!runtimes.containsKey(event.deviceId)) {
            runtimes[event.deviceId] = store.matchFor(event.deviceId)?.also {
                ControllerRuntime.warnUnknownCommands(it, registry)
            }?.let { ControllerRuntime(it, registry) }
        }
        return runtimes[event.deviceId]?.handle(event, ctx) ?: false
    }

    /** The runtime for [deviceId], if it has one yet. */
    fun runtimeFor(deviceId: String): ControllerRuntime? = runtimes[deviceId]

    /** Forgets all runtimes (e.g. after profiles are reloaded), so held state and bank knowledge start fresh. */
    fun reset() = runtimes.clear()
}
