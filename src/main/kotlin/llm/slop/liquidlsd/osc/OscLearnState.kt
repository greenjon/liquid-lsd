package llm.slop.liquidlsd.osc

/**
 * Transient state machine for interactive "Learn OSC" mode: arms a target parameter,
 * then binds it to whichever OSC address the next inbound message carries.
 * Mirrors [llm.slop.liquidlsd.macro.MacroLearnState]'s click-to-bind UX for the MIDI/OSC surface.
 */
object OscLearnState {
    const val TIMEOUT_MS = 20_000L

    /** Minimum value change required from a candidate address before it's accepted as the binding. */
    private const val MOVEMENT_THRESHOLD = 0.05f

    data class LearnSession(
        val parameterPath: String,
        val minVal: Float,
        val maxVal: Float,
        val displayLabel: String = parameterPath,
        val startTimeMs: Long = System.currentTimeMillis()
    )

    private data class PendingCandidate(val address: String, val baselineValue: Float)

    var activeSession: LearnSession? = null
        private set

    /**
     * The first-seen (address, value) pair while armed, held until a later packet from the
     * same address moves far enough to distinguish real input from a controller's connect-time
     * sync burst (TouchOSC page changes, accelerometer noise, initial state dumps, etc.).
     */
    private var pendingCandidate: PendingCandidate? = null

    var statusBanner: String? = null
        private set
    private var statusBannerExpiryMs: Long = 0L

    /** Sets a temporary status message displayed in the UI banner. */
    fun setStatus(message: String, durationMs: Long = 4000L) {
        statusBanner = message
        statusBannerExpiryMs = System.currentTimeMillis() + durationMs
    }

    /** Returns the current active status message if not expired. */
    fun getActiveStatus(): String? {
        val banner = statusBanner ?: return null
        if (System.currentTimeMillis() > statusBannerExpiryMs) {
            statusBanner = null
            return null
        }
        return banner
    }

    fun clearStatus() {
        statusBanner = null
        statusBannerExpiryMs = 0L
    }

    /** Arms Learn Mode: the next inbound OSC message will be bound to [parameterPath]. */
    fun startLearn(parameterPath: String, minVal: Float = 0f, maxVal: Float = 1f, displayLabel: String = parameterPath) {
        activeSession = LearnSession(parameterPath, minVal, maxVal, displayLabel)
        pendingCandidate = null
        setStatus("OSC LEARN: Move a control on your OSC surface to bind '$displayLabel'.")
    }

    fun cancelLearn() {
        if (activeSession != null) {
            activeSession = null
            pendingCandidate = null
            setStatus("OSC Learn cancelled.", 2000L)
        }
    }

    /** Returns true if a Learn session is armed, auto-expiring after [TIMEOUT_MS]. */
    fun isLearning(): Boolean {
        val session = activeSession ?: return false
        if (System.currentTimeMillis() - session.startTimeMs > TIMEOUT_MS) {
            activeSession = null
            pendingCandidate = null
            setStatus("OSC Learn timed out.", 3000L)
            return false
        }
        return true
    }

    /** Returns true if a Learn session is currently armed specifically for [parameterPath]. */
    fun isTargetLearning(parameterPath: String): Boolean = isLearning() && activeSession?.parameterPath == parameterPath

    /**
     * Consumes an inbound OSC message towards completing the active Learn session. Multi-argument
     * messages bind to the first component (e.g. the X axis of an XY pad). A message carrying a
     * numeric value only completes the binding once a later packet from the same address moves by
     * at least [MOVEMENT_THRESHOLD] from the first-seen value -- see [pendingCandidate]. Messages
     * with no numeric arg (bangs, strings) bind immediately since there's nothing to threshold.
     */
    fun captureLearnedAddress(message: OscMessage) {
        val session = activeSession ?: return
        val key = if (message.args.size > 1) "${message.address}/0" else message.address
        val value = message.args.getOrNull(0)?.let(::asFloat)

        if (value == null) {
            bind(session, key)
            return
        }

        val candidate = pendingCandidate
        if (candidate == null || candidate.address != key) {
            pendingCandidate = PendingCandidate(key, value)
            return
        }

        if (kotlin.math.abs(value - candidate.baselineValue) >= MOVEMENT_THRESHOLD) {
            bind(session, key)
        }
    }

    private fun bind(session: LearnSession, key: String) {
        OscMappingManager.addMapping(
            key,
            OscControlMapping(parameterPath = session.parameterPath, minVal = session.minVal, maxVal = session.maxVal)
        )
        OscMappingManager.saveActiveProfile()
        val label = session.displayLabel
        activeSession = null
        pendingCandidate = null
        setStatus("Bound '$label' -> $key", 4000L)
    }

    private fun asFloat(arg: Any): Float? = when (arg) {
        is Float -> arg
        is Int -> arg.toFloat()
        else -> null
    }
}
