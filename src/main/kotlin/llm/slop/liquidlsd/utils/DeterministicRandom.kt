package llm.slop.liquidlsd.utils

import java.util.UUID
import kotlin.random.Random

/**
 * Single source of randomness for anything that affects pixels (randomizers, shuffle queues, modulator ids that seed RANDOM LFOs).
 * Live runs delegate to [Random.Default] and real UUIDs; tour renders call [seed] so every run draws the same sequence.
 * Callers must draw on one thread (the render/UI thread) for the sequence to be reproducible.
 */
object DeterministicRandom {
    @Volatile private var seeded: Random? = null
    private var idCounter = 0L
    private var idSeed = 0L

    /** A [Random] that follows the current mode; safe to hold in default parameter values. */
    val rng: Random = object : Random() {
        override fun nextBits(bitCount: Int): Int = (seeded ?: Random.Default).nextBits(bitCount)
    }

    fun seed(seed: Long) {
        seeded = Random(seed)
        idSeed = seed
        idCounter = 0L
    }

    val isSeeded: Boolean get() = seeded != null

    /** Random UUID string live; a reproducible one (seed + counter) when seeded. */
    fun uuid(): String {
        if (seeded == null) return UUID.randomUUID().toString()
        val n = ++idCounter
        return UUID(idSeed * -7046029254386353131L xor 0x5DEECE66DL, n).toString()
    }
}
