package llm.slop.liquidlsd.cv

/**
 * A ring buffer to store the last N samples of a CV signal.
 * Optimized for zero-allocation access in the draw loop.
 * 
 * THREAD SAFETY WARNING: This class is designed for single-writer, single-reader scenarios.
 * Writing to [add] and reading via [getAt] or [copyTo] concurrently from different threads
 * is technically a data race on the `index` and `buffer` fields. For visualization usage (e.g.
 * oscilloscopes), this is acceptable as the consequence is at most a single-sample transient visual artifact.
 * For critical data processing, external synchronization is required.
 */
class CvHistoryBuffer(val size: Int) {
    private val buffer = FloatArray(size)
    
    @Volatile
    private var index = 0

    fun add(value: Float) {
        buffer[index] = value
        index = (index + 1) % size
    }

    /**
     * Gets a sample at a specific chronological index (0 = oldest, size-1 = newest).
     */
    fun getAt(i: Int): Float {
        return buffer[(index + i) % size]
    }

    /**
     * Copies the samples in chronological order into the target array.
     * When target is smaller than buffer size, copies the most recent samples.
     */
    fun copyTo(target: FloatArray) {
        val count = size.coerceAtMost(target.size)
        val startOffset = size - count
        val currentIndex = index // Read once
        for (i in 0 until count) {
            target[i] = buffer[(currentIndex + startOffset + i) % size]
        }
    }

    /**
     * Returns the samples in chronological order.
     * Warning: This allocates a new array.
     */
    /**
     * Samples a value from the most recent [historySpanCount] entries in the buffer.
     * [fraction] ranges from 0.0 (oldest sample in the window) to 1.0 (newest sample / NOW).
     */
    fun sampleWindow(historySpanCount: Int, fraction: Float): Float {
        val span = historySpanCount.coerceIn(1, size)
        val floatIndex = (size - span) + fraction.coerceIn(0f, 1f) * (span - 1)
        val baseIdx = floatIndex.toInt().coerceIn(0, size - 1)
        val nextIdx = (baseIdx + 1).coerceIn(0, size - 1)
        val frac = floatIndex - baseIdx
        val v1 = getAt(baseIdx)
        val v2 = getAt(nextIdx)
        return v1 + (v2 - v1) * frac
    }
}

