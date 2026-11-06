package llm.slop.liquidlsd.link

import kotlin.math.abs
import kotlin.math.floor

/**
 * Makes a Link session match this app's clock, for when the app is the source of truth.
 *
 * Link has no leader role: any peer may change the tempo, and the last writer wins. So this keeps checking the
 * session against the app and corrects it:
 * - **Tempo**: the app's tempo, reasserted when another peer changes it. A tracked tempo must differ by
 *   [TEMPO_STEP_BPM] for [TEMPO_HOLD_BEATS] before it goes out, so tracker wobble does not warp the peers.
 * - **Small phase error**: a slight tempo offset (at most [MAX_NUDGE]) slides the session into place smoothly.
 * - **Large phase error**: Link's force call, which peers hear as a jump, so only after the error has held for
 *   [FORCE_HOLD_SEC] and not within [FORCE_INTERVAL_SEC] of the last. Alone in a session there is nobody to
 *   disturb, so any error is fixed at once.
 *
 * Nothing is written while the caller is not confident: a wrong tempo here would reach someone's Live set.
 * Render thread only.
 */
class LinkLeader(private val link: () -> LinkBackend) {

    /** True while the last tick had a confident tempo to send. */
    @Volatile var isPublishing = false
        private set

    private var baseBpm = Double.NaN
    private var pendingBeats = 0.0
    private var lastSentBpm = Double.NaN
    private var lastSendSec = Double.NEGATIVE_INFINITY
    private var lastTickSec = Double.NaN
    private var largeErrorSec = 0.0
    private var lastForceSec = Double.NEGATIVE_INFINITY

    fun reset() {
        isPublishing = false
        baseBpm = Double.NaN
        pendingBeats = 0.0
        lastSentBpm = Double.NaN
        lastSendSec = Double.NEGATIVE_INFINITY
        lastTickSec = Double.NaN
        largeErrorSec = 0.0
        lastForceSec = Double.NEGATIVE_INFINITY
    }

    /**
     * @param nowSec any steady clock
     * @param localBeats the app's beat count now
     * @param bpm the app's tempo
     * @param tempoIsSteady the tempo is chosen by hand, so it goes out with no waiting
     * @param tempoConfident the tempo is good enough to send
     * @param phaseConfident [localBeats] is good enough to move the session's beat
     */
    fun tick(
        nowSec: Double,
        localBeats: Double,
        bpm: Double,
        tempoIsSteady: Boolean,
        tempoConfident: Boolean,
        phaseConfident: Boolean,
        beatsPerBar: Int,
        quantum: Double
    ) {
        val dtSec = if (lastTickSec.isNaN()) 0.0 else (nowSec - lastTickSec).coerceAtLeast(0.0)
        lastTickSec = nowSec
        isPublishing = tempoConfident
        if (!tempoConfident) {
            // Whatever comes next may be a different song: start the waits again, and leave the session alone.
            pendingBeats = 0.0
            largeErrorSec = 0.0
            return
        }

        val backend = link()
        updateBase(bpm, tempoIsSteady, dtSec)

        var nudge = 0.0
        if (phaseConfident) {
            val timeUs = backend.clockMicros()
            val sessionBeat = backend.getBeatAtTime(timeUs, quantum)
            // Bars line up when the quantum is whole bars; otherwise only the quantum is shared.
            val wrap = if (quantum >= beatsPerBar && quantum % beatsPerBar == 0.0) beatsPerBar.toDouble() else quantum
            val error = wrapped(localBeats - sessionBeat, wrap)
            when {
                backend.getNumPeers() == 0 -> {
                    if (abs(error) > DEADBAND_BEATS) backend.forceBeatAtTime(sessionBeat + error, timeUs, quantum)
                    largeErrorSec = 0.0
                }
                abs(error) >= LARGE_ERROR_BEATS -> {
                    largeErrorSec += dtSec
                    if (largeErrorSec >= FORCE_HOLD_SEC && nowSec - lastForceSec >= FORCE_INTERVAL_SEC) {
                        backend.forceBeatAtTime(sessionBeat + error, timeUs, quantum)
                        lastForceSec = nowSec
                        largeErrorSec = 0.0
                    }
                }
                else -> {
                    largeErrorSec = 0.0
                    if (abs(error) > DEADBAND_BEATS) {
                        nudge = (error / NUDGE_BEATS).coerceIn(-MAX_NUDGE, MAX_NUDGE)
                    }
                }
            }
        } else {
            largeErrorSec = 0.0
        }

        sendTempo(backend, baseBpm * (1.0 + nudge), nowSec)
    }

    private fun updateBase(bpm: Double, tempoIsSteady: Boolean, dtSec: Double) {
        if (baseBpm.isNaN() || tempoIsSteady) {
            baseBpm = bpm
            pendingBeats = 0.0
        } else if (abs(bpm - baseBpm) >= TEMPO_STEP_BPM) {
            pendingBeats += dtSec * bpm / 60.0
            if (pendingBeats >= TEMPO_HOLD_BEATS) {
                baseBpm = bpm
                pendingBeats = 0.0
            }
        } else {
            pendingBeats = 0.0
        }
    }

    private fun sendTempo(backend: LinkBackend, target: Double, nowSec: Double) {
        val session = backend.getTempo()
        if (abs(target - session) < TEMPO_EPSILON_BPM) return
        // A session tempo we did not send means another peer is writing too. Back off, so two leaders do not spin.
        val otherWriter = lastSentBpm.isNaN() || abs(session - lastSentBpm) >= TEMPO_EPSILON_BPM
        val interval = if (otherWriter) CONTESTED_SEND_INTERVAL_SEC else SEND_INTERVAL_SEC
        if (nowSec - lastSendSec < interval) return
        backend.setTempo(target)
        lastSentBpm = target
        lastSendSec = nowSec
    }

    private fun wrapped(value: Double, span: Double): Double = value - span * floor(value / span + 0.5)

    companion object {
        const val TEMPO_STEP_BPM = 0.5
        const val TEMPO_HOLD_BEATS = 4.0
        const val TEMPO_EPSILON_BPM = 0.01

        /** Within this the session is in place: about 5 ms at 120 BPM. */
        const val DEADBAND_BEATS = 0.01
        const val LARGE_ERROR_BEATS = 0.25

        /** A phase error is worked off over about this many beats. */
        const val NUDGE_BEATS = 8.0
        const val MAX_NUDGE = 0.02

        const val FORCE_HOLD_SEC = 2.0
        const val FORCE_INTERVAL_SEC = 8.0
        const val SEND_INTERVAL_SEC = 0.25
        const val CONTESTED_SEND_INTERVAL_SEC = 1.0
    }
}
