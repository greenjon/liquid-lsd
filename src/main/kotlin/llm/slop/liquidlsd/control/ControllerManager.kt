package llm.slop.liquidlsd.control

import llm.slop.liquidlsd.midi.MidiEngine
import llm.slop.liquidlsd.midi.MidiEvent
import llm.slop.liquidlsd.midi.MidiOutputPorts
import mu.KotlinLogging
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Holds one [ControllerRuntime] per connected device, created the first time the device sends a
 * message and its name matches a controller profile, and one [ControllerFeedback] per connected
 * device whose profile has an `output` section. Render thread only, except [shutdown], which may also run
 * from the `controller-native-leave` shutdown hook: the feedback map is guarded by [feedbackLock], and once
 * [shutdown] has run no more lights are queued, so nothing can land after a device's leave-native SysEx.
 *
 * [connectedDevices] and [openSink] are injectable so tests don't need real MIDI ports.
 */
class ControllerManager(
    private val registry: CommandRegistry,
    private val store: ControllerProfileStore = ControllerProfileStore.default,
    private val connectedDevices: () -> List<String> = { MidiEngine.getConnectedDeviceNames() },
    private val openSink: (String, Int) -> MidiSink? = { name, intervalMs -> MidiOutputPorts.openFor(name, intervalMs) },
    private val installShutdownHooks: Boolean = true
) {
    private val logger = KotlinLogging.logger {}
    private val runtimes = HashMap<String, ControllerRuntime?>()
    /** Guarded by [feedbackLock]. */
    private val feedbacks = HashMap<String, ControllerFeedback>()
    private val feedbackLock = Any()
    /** Set by the first [shutdown]; the manager stays inert after it. */
    private val isShutDown = AtomicBoolean(false)
    private val lastOpenAttemptMs = HashMap<String, Long>()
    private var lastScanMs = 0L
    private var hasScanned = false
    private var shutdownHookInstalled = false
    private val lightBuffer = arrayOfNulls<KnobLight>(KnobCommands.KNOB_COUNT)

    /** Handles [event] via its device's profile; false if the device has no profile or the profile ignores the input. */
    /** True while a controller with a profile and an output (lights) is connected, as of the last [updateFeedback]. */
    @Volatile
    var anyConnected: Boolean = false
        private set

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
    fun updateFeedback(
        source: KnobLightSource,
        nowMs: Long = System.currentTimeMillis(),
        activePageId: String? = null
    ) {
        synchronized(feedbackLock) { updateFeedbackLocked(source, nowMs, activePageId) }
    }

    private fun updateFeedbackLocked(source: KnobLightSource, nowMs: Long, activePageId: String?) {
        if (isShutDown.get()) { anyConnected = false; return }
        if (!hasScanned || nowMs - lastScanMs >= SCAN_INTERVAL_MS) {
            hasScanned = true
            lastScanMs = nowMs
            scanDevices(nowMs)
        }
        anyConnected = feedbacks.isNotEmpty()
        if (feedbacks.isEmpty()) return
        source.fillKnobLights(lightBuffer)
        for ((name, feedback) in feedbacks) {
            val runtime = runtimes[name]
            if (activePageId != null) {
                feedback.syncActiveBank(activePageId, runtime)
            }
            feedback.update(lightBuffer, runtime?.activeBank)
        }
    }

    private fun scanDevices(nowMs: Long) {
        val connected = connectedDevices().toSet()
        val gone = feedbacks.filter { (name, fb) -> name !in connected || !fb.isHealthy }.keys.toList()
        for (name in gone) {
            feedbacks.remove(name)?.let { it.leaveNativeMode(); it.close() }
            runtimes.remove(name)?.clearHeldState()   // a replugged device may be on any bank, with nothing held
            lastOpenAttemptMs.remove(name)
        }
        if (gone.isNotEmpty()) registry.clearHeldState()   // a button held on the lost device will never send its release
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
            val feedback = ControllerFeedback(compiled, traced)
            feedbacks[name] = feedback
            if (compiled.profile.output.native != null) {
                feedback.enterNativeMode()
                installShutdownHook()
            }
            logger.info { "Controller feedback on for $name (profile ${compiled.profile.id})" }
        }
    }

    /** The runtime for [deviceId], if it has one yet. */
    fun runtimeFor(deviceId: String): ControllerRuntime? = runtimes[deviceId]

    /** The feedback for [deviceId], if it is connected and has an open output. */
    fun feedbackFor(deviceId: String): ControllerFeedback? = synchronized(feedbackLock) { feedbacks[deviceId] }

    /** Forgets all runtimes and feedback (e.g. after profiles are reloaded), so held state and bank knowledge start fresh. */
    fun reset() {
        runtimes.values.forEach { it?.clearHeldState() }
        runtimes.clear()
        registry.clearHeldState()
        synchronized(feedbackLock) { releaseAll() }
        lastOpenAttemptMs.clear()
        hasScanned = false
    }

    /** Hands every device back to its stock behaviour and lets go of the feedback ports; returns at once. Lock held. */
    private fun releaseAll(): List<ControllerFeedback> {
        val open = feedbacks.values.toList()
        feedbacks.clear()
        open.forEach { it.leaveNativeMode(); it.close() }
        return open
    }

    /**
     * For app exit: hands every device back to its stock behaviour, waits (up to [EXIT_FLUSH_MS]) for that to
     * reach the hardware, and stops all further feedback. Main calls it before the MIDI ports close; the
     * shutdown hook calls it too (Ctrl-C/SIGTERM), and whichever comes second does nothing. Any thread.
     */
    fun shutdown() {
        if (!isShutDown.compareAndSet(false, true)) return
        val open = synchronized(feedbackLock) { releaseAll() }
        anyConnected = false
        val deadline = System.currentTimeMillis() + EXIT_FLUSH_MS
        for (fb in open) fb.awaitClosed((deadline - System.currentTimeMillis()).coerceAtLeast(1))
    }

    /** A native-mode device stays in that mode until replugged, so leave it when the app quits. */
    private fun installShutdownHook() {
        if (shutdownHookInstalled || !installShutdownHooks) return
        shutdownHookInstalled = true
        Runtime.getRuntime().addShutdownHook(Thread({ shutdown() }, "controller-native-leave"))
    }

    companion object {
        const val SCAN_INTERVAL_MS = 1000L
        /** How long to wait before trying to open the same device's output port again. */
        const val RETRY_OPEN_MS = 10_000L
        /** How long [shutdown] waits for every device's last SysEx to be sent. */
        const val EXIT_FLUSH_MS = 1000L
    }
}
