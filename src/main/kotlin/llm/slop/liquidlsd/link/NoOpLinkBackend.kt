package llm.slop.liquidlsd.link

/**
 * Stands in where the native Link library cannot load. [AbletonLinkEngine] will not enable it, so the app
 * never runs its clock from this and the UI can say Link is unavailable.
 */
class NoOpLinkBackend : LinkBackend {
    override val name: String = "Unavailable"

    private var bpm = 120.0

    override fun init(initialBpm: Double): Boolean {
        bpm = initialBpm
        return true
    }

    override fun close() {}

    override fun setEnabled(enabled: Boolean) {}

    override fun isEnabled(): Boolean = false

    override fun getNumPeers(): Int = 0

    override fun clockMicros(): Long = System.nanoTime() / 1000

    override fun getTempo(): Double = bpm

    override fun setTempo(bpm: Double) {
        this.bpm = bpm
    }

    override fun getBeatAtTime(timeUs: Long, quantum: Double): Double = 0.0

    override fun requestBeatAtTime(beat: Double, timeUs: Long, quantum: Double) {}

    override fun forceBeatAtTime(beat: Double, timeUs: Long, quantum: Double) {}
}
