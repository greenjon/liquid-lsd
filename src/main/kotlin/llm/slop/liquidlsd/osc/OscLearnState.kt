package llm.slop.liquidlsd.osc

/**
 * Transient state machine for interactive "Learn OSC" mode: arms a target parameter,
 * then binds it to whichever OSC address the next inbound message carries.
 * Mirrors [llm.slop.liquidlsd.macro.MacroLearnState]'s click-to-bind UX for the MIDI/OSC surface.
 */
object OscLearnState {
    const val TIMEOUT_MS = 20_000L

    data class LearnSession(
        val parameterPath: String,
        val minVal: Float,
        val maxVal: Float,
        val startTimeMs: Long = System.currentTimeMillis()
    )

    var activeSession: LearnSession? = null
        private set

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
    fun startLearn(parameterPath: String, minVal: Float = 0f, maxVal: Float = 1f) {
        activeSession = LearnSession(parameterPath, minVal, maxVal)
        setStatus("OSC LEARN: Move a control on your OSC surface to bind '$parameterPath'.")
    }

    fun cancelLearn() {
        if (activeSession != null) {
            activeSession = null
            setStatus("OSC Learn cancelled.", 2000L)
        }
    }

    /** Returns true if a Learn session is armed, auto-expiring after [TIMEOUT_MS]. */
    fun isLearning(): Boolean {
        val session = activeSession ?: return false
        if (System.currentTimeMillis() - session.startTimeMs > TIMEOUT_MS) {
            activeSession = null
            setStatus("OSC Learn timed out.", 3000L)
            return false
        }
        return true
    }

    /**
     * Consumes an inbound OSC message to complete the active Learn session, creating
     * (and persisting) a new [OscControlMapping] in [OscMappingManager]. Multi-argument
     * messages bind to the first component (e.g. the X axis of an XY pad).
     */
    fun captureLearnedAddress(message: OscMessage) {
        val session = activeSession ?: return
        val key = if (message.args.size > 1) "${message.address}/0" else message.address
        OscMappingManager.addMapping(
            key,
            OscControlMapping(parameterPath = session.parameterPath, minVal = session.minVal, maxVal = session.maxVal)
        )
        OscMappingManager.saveActiveProfile()
        activeSession = null
        setStatus("Bound '${session.parameterPath}' -> $key", 4000L)
    }
}
