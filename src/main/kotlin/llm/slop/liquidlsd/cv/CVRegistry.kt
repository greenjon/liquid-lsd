package llm.slop.liquidlsd.cv

import java.util.concurrent.ConcurrentHashMap

/**
 * Central registry for all Control Voltage (CV) signals.
 * Manages registration of CV sources, stores their history, and handles high-precision sync.
 */
object CVRegistry {
    private val startTimeNs = System.nanoTime()

    @Volatile private var currentFrameIndex: Long = 0L

    @Volatile private var anchorBeats: Double = 0.0
    @Volatile private var anchorBpm: Float = 120f
    @Volatile private var anchorTimeNs: Long = System.nanoTime()
    @Volatile private var lastRenderBeats: Double = 0.0
    @Volatile private var lastRenderTimeNs: Long = System.nanoTime()

    private val sources = ConcurrentHashMap<String, CVSource>()
    private val histories = ConcurrentHashMap<String, CvHistoryBuffer>()

    init {
        register(MutableCVSource("bpm", 120f))
        register(BeatSine())

        // LFO: unified generator — evaluates time-based or beat-based waveforms per CvModulator
        register(GenCVSource("lfo"))

        // SEQ: step sequencer — evaluates step pattern with hold/glide per CvModulator
        register(GenCVSource("seq"))

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
        histories[source.id] = CvHistoryBuffer(600)
    }

    fun updateBeatAnchor(beats: Double, bpm: Float, timeNs: Long) {
        anchorBeats = beats
        anchorBpm = bpm
        anchorTimeNs = timeNs
        updatePushedValue("bpm", bpm)
    }

    fun resetBeatAnchor(beats: Double = 0.0, bpm: Float = 120f, timeNs: Long = llm.slop.liquidlsd.utils.TimeSource.getTimeNanos()) {
        anchorBeats = beats
        anchorBpm = bpm
        anchorTimeNs = timeNs
        lastRenderBeats = beats
        lastRenderTimeNs = timeNs
        updatePushedValue("bpm", bpm)
    }

    fun getSynchronizedTotalBeats(): Double {
        val beats = anchorBeats
        val bpm = anchorBpm
        val timeNs = anchorTimeNs
        val now = llm.slop.liquidlsd.utils.TimeSource.getTimeNanos()
        val elapsedSec = kotlin.math.max(0.0, (now - timeNs) / 1_000_000_000.0)
        val beatDelta = elapsedSec * (bpm / 60.0)
        val synchronized = beats + beatDelta

        // Ensure strictly monotonic forward progress on the render thread;
        // prevent backward time jitter (< 0.25 beats) caused by asynchronous audio thread anchor updates.
        val current = lastRenderBeats
        val frameDtSec = kotlin.math.max(0.0, (now - lastRenderTimeNs) / 1_000_000_000.0)
        lastRenderTimeNs = now

        val safeBeats = if (synchronized < current && (current - synchronized) < 0.25) {
            current + frameDtSec * (bpm / 60.0)
        } else {
            synchronized
        }
        lastRenderBeats = safeBeats
        return safeBeats
    }

    /**
     * Returns the elapsed application time in seconds.
     */
    fun getElapsedRealtimeSec(): Double {
        return (llm.slop.liquidlsd.utils.TimeSource.getTimeNanos() - startTimeNs) / 1_000_000_000.0
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
        if (id == "seq" && !llm.slop.liquidlsd.ui.UITheme.sequencerEnabled) {
            return 0f
        }
        if (id.startsWith("midi_cc_")) {
            if (!llm.slop.liquidlsd.ui.UITheme.midiEnabled) return 0f
            val parts = id.substring("midi_cc_".length).split('_')
            if (parts.size >= 2) {
                val channel = parts[0].toIntOrNull() ?: 0
                val cc = parts[1].toIntOrNull() ?: 0
                return llm.slop.liquidlsd.midi.MidiEngine.getCcValue(channel, cc)
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

    @Volatile private var targetFps: Float = 30f

    /**
     * Returns the configured target FPS (e.g. 30 or 60) for frame-synced time conversions.
     */
    fun getTargetFps(): Float = targetFps

    /**
     * Sets the configured target FPS.
     */
    fun setTargetFps(fps: Float) {
        targetFps = fps.coerceAtLeast(1f)
    }

    /**
     * Returns the total elapsed render frames since application launch.
     */
    fun getRenderFrameCount(): Long = currentFrameIndex

    /**
     * Sets or resets the render frame counter (primarily used for unit testing).
     */
    fun setRenderFrameCount(frames: Long) {
        currentFrameIndex = frames
    }

    /**
     * Updates all active CV sources and writes their values to their histories.
     * Must be called once per render frame.
     */
    fun updateAll() {
        currentFrameIndex++
        targetFps = llm.slop.liquidlsd.ui.UITheme.maxFps.coerceAtLeast(1).toFloat()
        val totalBeats = getSynchronizedTotalBeats()
        val elapsedSeconds = getElapsedRealtimeSec()

        for (source in sources.values) {
            if (isAudioOrTriggerSource(source.id)) continue
            source.update(totalBeats, elapsedSeconds)
            histories[source.id]?.add(source.value)
        }
    }

    private fun isAudioOrTriggerSource(id: String): Boolean = when (id) {
        "audio_amp", "audio_bass", "audio_mid", "audio_high", "trigger_onset", "trigger_accent" -> true
        else -> false
    }
}
