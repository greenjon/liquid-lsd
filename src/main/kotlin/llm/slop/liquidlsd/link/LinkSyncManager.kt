package llm.slop.liquidlsd.link

import llm.slop.liquidlsd.audio.AudioEngine
import llm.slop.liquidlsd.audio.ClockSource
import llm.slop.liquidlsd.audio.SignalState
import llm.slop.liquidlsd.cv.CVRegistry
import llm.slop.liquidlsd.utils.TimeSource

/** What the app is doing in the Link session. */
enum class LinkRole {
    OFF,

    /** The session is the clock: [ClockSource.LINK]. */
    FOLLOW,

    /** The app's own clock (MAN or AUDIO) drives the session. */
    LEAD
}

/**
 * Decides each frame whether the session drives the app's clock or the app's clock drives the session.
 * The clock source says who is in charge: LINK follows, anything else with Link on leads.
 */
object LinkSyncManager {
    private val leader = LinkLeader { AbletonLinkEngine.backend }

    val role: LinkRole
        get() = when {
            !AbletonLinkEngine.isEnabled -> LinkRole.OFF
            AudioEngine.clockSource == ClockSource.LINK -> LinkRole.FOLLOW
            else -> LinkRole.LEAD
        }

    val isLinked: Boolean
        get() = AbletonLinkEngine.isEnabled

    val peersCount: Int
        get() = if (isLinked) AbletonLinkEngine.getNumPeers() else 0

    /** Leading, and sure enough of the tempo to be sending it. Leading on the tracker, this waits for a lock. */
    val isPublishing: Boolean
        get() = role == LinkRole.LEAD && leader.isPublishing

    /** The app's tempo, whichever clock is in charge. */
    val activeBpm: Double
        get() = AudioEngine.getEstimatedBpm().toDouble()

    val formattedActiveBpm: String
        get() = String.format(java.util.Locale.US, "%.1f", activeBpm)

    val confidence: Float
        get() = AudioEngine.confidence

    val confidencePercent: Int
        get() = (confidence * 100f).coerceIn(0f, 100f).toInt()

    /** Once per render frame, before the frame reads the beat clock. */
    fun tick() {
        when (role) {
            LinkRole.OFF -> leader.reset()
            LinkRole.FOLLOW -> {
                leader.reset()
                AbletonLinkEngine.updateClockAnchor()
                AudioEngine.showLinkTempo(AbletonLinkEngine.latestTempo.toFloat())
            }
            LinkRole.LEAD -> {
                AbletonLinkEngine.refresh()
                val manual = AudioEngine.clockSource == ClockSource.MANUAL
                val tracking = !manual && AudioEngine.isActive() &&
                    AudioEngine.currentState == SignalState.ACTIVE && AudioEngine.beatDetector.isTempoLocked
                leader.tick(
                    nowSec = TimeSource.getTimeNanos() / 1e9,
                    localBeats = CVRegistry.getSynchronizedTotalBeats(),
                    bpm = (if (manual) AudioEngine.manualBpm else AudioEngine.getEstimatedBpm()).toDouble(),
                    tempoIsSteady = manual,
                    tempoConfident = manual || tracking,
                    phaseConfident = manual || (tracking && AudioEngine.isPhaseLocked),
                    beatsPerBar = CVRegistry.beatsPerBar,
                    quantum = AbletonLinkEngine.quantum
                )
            }
        }
    }

    fun reset() {
        leader.reset()
    }
}
