package llm.slop.liquidlsd.presets

import java.io.File

/**
 * One-line "what plays next" description of a play queue, for the Edit view where the Library (and its queue
 * columns) is not on screen. Pure: mirrors the next-index rules of `PlayQueueManager.triggerNext` /
 * `BgQueueManager.triggerNext` without advancing anything.
 */
object QueueNextUp {
    /**
     * @param stagedOn "A"/"B" when a manually loaded preset is already waiting on the standby deck (Auto-VJ's
     *   "jump the line"), which plays before the queue advances; null otherwise (and always for the BG queue).
     */
    fun describe(
        queue: List<File>,
        activeIndex: Int,
        shuffle: Boolean,
        repeat: Boolean,
        stagedOn: String? = null
    ): String {
        if (stagedOn != null) return "Next: staged on Deck $stagedOn"
        if (queue.isEmpty()) return "Queue empty"
        if (shuffle) return "Next: shuffle (${queue.size} in queue)"
        val next = activeIndex + 1
        val index = when {
            next in queue.indices -> next
            repeat -> 0
            else -> return "End of queue"
        }
        return "Next: ${queue[index].nameWithoutExtension} (${index + 1}/${queue.size})"
    }
}
