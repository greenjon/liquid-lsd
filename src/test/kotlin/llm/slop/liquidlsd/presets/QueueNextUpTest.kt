package llm.slop.liquidlsd.presets

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

class QueueNextUpTest {
    private val q = listOf(File("a/Alpha.json"), File("a/Beta.json"), File("a/Gamma.json"))

    @Test
    fun emptyQueue() = assertEquals("Queue empty", QueueNextUp.describe(emptyList(), -1, shuffle = false, repeat = false))

    @Test
    fun nextIsTheItemAfterTheActiveOne() {
        assertEquals("Next: Beta (2/3)", QueueNextUp.describe(q, 0, shuffle = false, repeat = false))
        assertEquals("Next: Alpha (1/3)", QueueNextUp.describe(q, -1, shuffle = false, repeat = false))
    }

    @Test
    fun endOfQueueWrapsOnlyWhenRepeating() {
        assertEquals("End of queue", QueueNextUp.describe(q, 2, shuffle = false, repeat = false))
        assertEquals("Next: Alpha (1/3)", QueueNextUp.describe(q, 2, shuffle = false, repeat = true))
    }

    @Test
    fun shuffleDoesNotNameAnItemBecauseItIsChosenAtTriggerTime() =
        assertEquals("Next: shuffle (3 in queue)", QueueNextUp.describe(q, 0, shuffle = true, repeat = false))

    @Test
    fun aStagedPresetPlaysBeforeTheQueueAdvances() =
        assertEquals("Next: staged on Deck B", QueueNextUp.describe(q, 0, shuffle = false, repeat = false, stagedOn = "B"))
}
