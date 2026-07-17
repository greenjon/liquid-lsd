package llm.slop.spirals.cv

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference

/**
 * Central registry for all Control Voltage (CV) signals.
 * Manages registration of CV sources, stores their history, and handles high-precision sync.
 */
object CVRegistry {
    private val startTimeNs = System.nanoTime()

    /**
     * Immutable snapshot of the beat synchronisation anchor.
     * Written atomically from the audio thread via [updateBeatAnchor];
     * read atomically from the render thread via [getSynchronizedTotalBeats].
     * Using a single AtomicReference<BeatAnchor> instead of three separate
     * @Volatile fields prevents the render thread from observing a torn triplet
     * where, e.g., anchorBeats has been updated but anchorTimeNs has not.
     */
    private data class BeatAnchor(val beats: Double, val bpm: Float, val timeNs: Long)
    private val beatAnchor = AtomicReference(BeatAnchor(0.0, 120f, System.nanoTime()))

    private val sources = ConcurrentHashMap<String, CVSource>()
    private val histories = ConcurrentHashMap<String, CvHistoryBuffer>()

    init {
        register(MutableCVSource("bpm", 120f))
        register(BeatSine())

        // LFO: unified generator — evaluates time-based or beat-based waveforms per CvModulator
        register(GenCVSource("lfo"))

        register(MutableCVSource("audio_amp"))
        register(MutableCVSource("audio_bass"))
        register(MutableCVSource("audio_mid"))
        register(MutableCVSource("audio_high"))

        register(MutableCVSource("trigger_onset"))
        register(MutableCVSource("trigger_accent"))
    }

    /**
     * Checks if a CV source ID is registered.
     */
    fun exists(id: String): Boolean {
        return sources.containsKey(id)
    }

    /**
     * Registers a new CV source and creates its associated history buffer.
     */
    fun register(source: CVSource) {
        sources[source.id] = source
        histories[source.id] = CvHistoryBuffer(200)
    }

    /**
     * Updates the beat synchronization anchor. Called from the audio thread.
     * The three values are published as a single atomic reference swap so the
     * render thread never observes a partially-updated state.
     */
    fun updateBeatAnchor(beats: Double, bpm: Float, timeNs: Long) {
        beatAnchor.set(BeatAnchor(beats, bpm, timeNs))
        updatePushedValue("bpm", bpm)
    }

    /**
     * Calculates the current synchronized total beats with sub-frame interpolation.
     * Reads a single consistent [BeatAnchor] snapshot; safe to call from any thread.
     */
    fun getSynchronizedTotalBeats(): Double {
        val anchor = beatAnchor.get()
        val now = System.nanoTime()
        val elapsedSec = (now - anchor.timeNs) / 1_000_000_000.0
        val beatDelta = elapsedSec * (anchor.bpm / 60.0)
        return anchor.beats + beatDelta
    }

    /**
     * Returns the elapsed application time in seconds.
     */
    fun getElapsedRealtimeSec(): Double {
        return (System.nanoTime() - startTimeNs) / 1_000_000_000.0
    }

    /**
     * Updates an externally pushed mutable signal value.
     */
    fun updatePushedValue(id: String, value: Float) {
        val src = sources[id]
        if (src is MutableCVSource) {
            src.value = value
        }
        when (id) {
            "amp"    -> updatePushedValue("audio_amp",      value)
            "bass"   -> updatePushedValue("audio_bass",     value)
            "mid"    -> updatePushedValue("audio_mid",      value)
            "high"   -> updatePushedValue("audio_high",     value)
            "onset"  -> updatePushedValue("trigger_onset",  value)
            "accent" -> updatePushedValue("trigger_accent", value)
        }
    }

    /**
     * Retrieves the current value of the specified CV signal.
     */
    fun get(id: String): Float {
        if (id.startsWith("midi_cc_")) {
            val parts = id.substring("midi_cc_".length).split('_')
            if (parts.size >= 2) {
                val channel = parts[0].toIntOrNull() ?: 0
                val cc = parts[1].toIntOrNull() ?: 0
                return llm.slop.spirals.midi.MidiEngine.getCcValue(channel, cc)
            }
        }
        return sources[id]?.value ?: 0f
    }

    /**
     * Retrieves the history buffer of the specified CV signal.
     */
    fun getHistory(id: String): CvHistoryBuffer? = histories[id]

    /**
     * Returns all registered CV source IDs, sorted alphabetically.
     */
    fun getSourceIds(): List<String> = sources.keys().toList().sorted()

    /**
     * Updates all active CV sources and writes their values to their histories.
     * Must be called once per render frame.
     */
    fun updateAll() {
        val totalBeats = getSynchronizedTotalBeats()
        val elapsedSeconds = getElapsedRealtimeSec()

        for (source in sources.values) {
            source.update(totalBeats, elapsedSeconds)
            histories[source.id]?.add(source.value)
        }
    }
}
