package llm.slop.liquidlsd.audio

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The metre check keeps the comb's six strongest peaks in a preallocated list. A lag left in that list by an earlier
 * estimate must never be compared against or evaluated: with exactly six peaks this pass, the sixth used to be
 * dropped in favour of the stale lag.
 */
class BeatTrackerMetreTest {

    @Test
    fun staleCandidateFromAnEarlierEstimateIsNotKept() {
        val engine = BeatTrackerEngine(sampleRate = 44100f, fftSize = 512)
        val cls = BeatTrackerEngine::class.java
        val scoreTable = cls.getDeclaredField("scoreTable").apply { isAccessible = true }.get(engine) as FloatArray
        val candidates = cls.getDeclaredField("candidateDelays").apply { isAccessible = true }.get(engine) as IntArray
        val settleMetre = cls.getDeclaredMethod(
            "settleMetre",
            Int::class.javaPrimitiveType, Float::class.javaPrimitiveType,
            Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType
        ).apply { isAccessible = true }

        // A strong lag from an earlier estimate, outside this pass's range.
        scoreTable.fill(0f)
        scoreTable[900] = 10f
        candidates.fill(0)
        candidates[5] = 900

        // Exactly six peaks this pass, the sixth weaker than the stale one.
        val peaks = intArrayOf(30, 40, 50, 60, 70, 80)
        peaks.forEachIndexed { i, d -> scoreTable[d] = (6 - i).toFloat() }

        settleMetre.invoke(engine, 30, 86f, 10, 100, 64)

        assertEquals(peaks.toList(), candidates.toList())
    }
}
