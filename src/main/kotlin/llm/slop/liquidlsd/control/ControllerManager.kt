package llm.slop.liquidlsd.control

import llm.slop.liquidlsd.midi.MidiEngine
import llm.slop.liquidlsd.midi.MidiEvent
import llm.slop.liquidlsd.midi.MidiOutputPorts
import mu.KotlinLogging

/**
 * Holds one [ControllerRuntime] per connected device, created the first time the device sends a
 * message and its name matches a controller profile, and one [ControllerFeedback] per connected
 * device whose profile has an `output` section. Render thread only.
 *
 * [connectedDevices] and [openSink] are injectable so tests don't need real MIDI ports.
 */
class ControllerManager(
    private val registry: CommandRegistry,
    private val store: ControllerProfileStore = ControllerProfileStore.default,
    private val connectedDevices: () -> List<String> = { MidiEngine.getConnectedDeviceNames() },
    private val openSink: (String, Int) -> MidiSink? = { name, intervalMs -> MidiOutputPorts.openFor(name, intervalMs) }
) {
    private val logger = KotlinLogging.logger {}
    private val runtimes = HashMap<String, ControllerRuntime?>()
    private val feedbacks = HashMap<String, ControllerFeedback>()
    private val lastOpenAttemptMs = HashMap<String, Long>()
    private var lastScanMs = 0L
    private var hasScanned = false

    /** Handles [event] via its device's profile; false if the device has no profile or the profile ignores the input. */
    fun handle(event: MidiEvent, ctx: CommandContext): Boolean {
        if (event.deviceId.isEmpty()) return false
        if (!runtimes.containsKey(event.deviceId)) {
            runtimes[event.deviceId] = store.matchFor(event.deviceId)?.also {
                ControllerRuntime.warnUnknownCommands(it, registry)
            }?.let { ControllerRuntime(it, registry, trace = TracingSink.enabled(it.profile)) }
        }
        return runtimes[event.deviceId]?.handle(event, ctx) ?: false
    }

    /**
     * Pushes [source]'s lights to every connected controller that has feedback. Every [SCAN_INTERVAL_MS]
     * it also looks for newly connected devices (opening their output port and writing every ring and
     * LED) and forgets devices that went away or whose port died.
     */
    fun updateFeedback(source: KnobLightSource, nowMs: Long = System.currentTimeMillis()) {
        if (!hasScanned || nowMs - lastScanMs >= SCAN_INTERVAL_MS) {
            hasScanned = true
            lastScanMs = nowMs
            scanDevices(nowMs)
        }
        if (feedbacks.isEmpty()) return
        val lights = source.knobLights()
        for ((name, feedback) in feedbacks) feedback.update(lights, runtimes[name]?.activeBank, nowMs)
    }

    private fun scanDevices(nowMs: Long) {
        val connected = connectedDevices().toSet()
        val gone = feedbacks.filter { (name, fb) -> name !in connected || !fb.isHealthy }.keys.toList()
        for (name in gone) {
            feedbacks.remove(name)?.close()
            runtimes.remove(name)   // a replugged device may be on any bank, with nothing held
            lastOpenAttemptMs.remove(name)
        }
        for (name in connected) {
            if (name in feedbacks) continue
            val compiled = store.matchFor(name) ?: continue
            if (compiled.profile.output.knobs == null) continue
            val last = lastOpenAttemptMs[name]
            if (last != null && nowMs - last < RETRY_OPEN_MS) continue
            lastOpenAttemptMs[name] = nowMs
            val sink = openSink(name, compiled.profile.output.minIntervalMs)
            if (sink == null) {
                logger.info { "No MIDI output port found for $name; controller feedback is off for it" }
                continue
            }
            val traced = if (TracingSink.enabled(compiled.profile)) TracingSink(sink, name) else sink
            feedbacks[name] = ControllerFeedback(compiled, traced)
            logger.info { "Controller feedback on for $name (profile ${compiled.profile.id})" }
        }
    }

    /** The runtime for [deviceId], if it has one yet. */
    fun runtimeFor(deviceId: String): ControllerRuntime? = runtimes[deviceId]

    /** The feedback for [deviceId], if it is connected and has an open output. */
    fun feedbackFor(deviceId: String): ControllerFeedback? = feedbacks[deviceId]

    /** Forgets all runtimes and feedback (e.g. after profiles are reloaded), so held state and bank knowledge start fresh. */
    fun reset() {
        runtimes.clear()
        feedbacks.values.forEach { it.close() }
        feedbacks.clear()
        lastOpenAttemptMs.clear()
        hasScanned = false
    }

    companion object {
        const val SCAN_INTERVAL_MS = 1000L
        /** How long to wait before trying to open the same device's output port again. */
        const val RETRY_OPEN_MS = 10_000L
    }
}
