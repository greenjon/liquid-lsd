package llm.slop.liquidlsd.link

/**
 * Fallback no-op Ableton Link backend when native or network drivers are unavailable or disabled.
 */
class NoOpLinkBackend : LinkBackend {
    override val name: String = "Disabled / No-Op"

    private var active = false
    private var bpm = 120.0
    private var startStopSync = false
    private var playing = false
    private var startTimeUs = System.nanoTime() / 1000

    override fun init(initialBpm: Double): Boolean {
        bpm = initialBpm
        active = true
        startTimeUs = System.nanoTime() / 1000
        return true
    }

    override fun close() {
        active = false
    }

    override fun setEnabled(enabled: Boolean) {
        active = enabled
    }

    override fun isEnabled(): Boolean = active

    override fun getNumPeers(): Int = 0

    override fun getTempo(): Double = bpm

    override fun setTempo(bpm: Double) {
        this.bpm = bpm.coerceIn(20.0, 300.0)
    }

    override fun getBeatAtTime(timeUs: Long, quantum: Double): Double {
        val elapsedSec = (timeUs - startTimeUs) / 1_000_000.0
        return (elapsedSec * (bpm / 60.0)).coerceAtLeast(0.0)
    }

    override fun getPhaseAtTime(timeUs: Long, quantum: Double): Double {
        val beat = getBeatAtTime(timeUs, quantum)
        val q = quantum.coerceAtLeast(1.0)
        return beat % q
    }

    override fun requestBeatAtTime(beat: Double, quantum: Double) {
        val nowUs = System.nanoTime() / 1000
        val elapsedBeats = beat.coerceAtLeast(0.0)
        val elapsedSec = elapsedBeats / (bpm / 60.0)
        startTimeUs = nowUs - (elapsedSec * 1_000_000.0).toLong()
    }

    override fun setStartStopSyncEnabled(enabled: Boolean) {
        startStopSync = enabled
    }

    override fun isStartStopSyncEnabled(): Boolean = startStopSync

    override fun isPlaying(): Boolean = playing

    override fun setIsPlaying(isPlaying: Boolean) {
        playing = isPlaying
    }
}
