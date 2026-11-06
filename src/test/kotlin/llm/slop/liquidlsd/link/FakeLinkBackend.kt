package llm.slop.liquidlsd.link

import kotlin.math.floor

/** An in-memory Link session whose clock the test moves by hand. Records what the app wrote to it. */
class FakeLinkBackend(var bpm: Double = 120.0, var peers: Int = 1) : LinkBackend {
    override val name = "Fake"

    var sessionOn = false
    var nowUs = 1_000_000L
    var closed = false
    private var beatAtNow = 0.0

    val tempoWrites = mutableListOf<Double>()
    val forces = mutableListOf<Double>()
    val requests = mutableListOf<Double>()

    /** Runs the session forward at its current tempo. */
    fun advance(seconds: Double) {
        beatAtNow += seconds * bpm / 60.0
        nowUs += (seconds * 1e6).toLong()
    }

    /** Another peer changes the tempo, which writes nothing to [tempoWrites]. */
    fun peerSetsTempo(value: Double) {
        bpm = value
    }

    override fun init(initialBpm: Double) = true
    override fun close() {
        closed = true
    }
    override fun setEnabled(enabled: Boolean) {
        sessionOn = enabled
    }
    override fun isEnabled() = sessionOn
    override fun getNumPeers() = peers
    override fun clockMicros() = nowUs
    override fun getTempo() = bpm
    override fun setTempo(bpm: Double) {
        this.bpm = bpm
        tempoWrites += bpm
    }
    override fun getBeatAtTime(timeUs: Long, quantum: Double) = beatAtNow + (timeUs - nowUs) / 1e6 * bpm / 60.0
    override fun requestBeatAtTime(beat: Double, timeUs: Long, quantum: Double) {
        requests += beat
        if (peers == 0) beatAtNow = beat
    }
    override fun forceBeatAtTime(beat: Double, timeUs: Long, quantum: Double) {
        forces += beat
        beatAtNow = beat
    }

    /** Where the session's beat falls within a beat, -0.5..0.5, relative to [beats]. */
    fun phaseErrorTo(beats: Double): Double {
        val diff = beats - beatAtNow
        return diff - floor(diff + 0.5)
    }
}
