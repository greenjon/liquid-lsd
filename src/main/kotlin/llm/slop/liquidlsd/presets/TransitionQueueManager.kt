package llm.slop.liquidlsd.presets

import kotlinx.serialization.json.Json
import llm.slop.liquidlsd.models.TransitionPresetDto
import llm.slop.liquidlsd.rendering.Mixer
import llm.slop.liquidlsd.ui.FileSystemManager
import java.io.File

/**
 * [QueueEngine] specialization for the Transition Queue (Phase 2): transition presets
 * (.lsdtrans), transition playlists (.lsdtransplay), and stock transitions (a shader ID with
 * no backing file — kept as a literal token rather than dropped, unlike FX/Preset queue items).
 */
object TransitionQueueManager : QueueEngine(queueLabel = "transition queue") {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    @Volatile
    var isAutoAdvanceEnabled = true

    override fun playlistItemExtension(): String = "lsdtransplay"

    override fun playlistRoots(playlistFile: File): List<File> = listOfNotNull(
        playlistFile.parentFile,
        FileSystemManager.getTransitionsRoot()
    )

    override fun resolveUnmatchedPlaylistItem(item: String): File = File(item)

    /** Queues [file] on [TransitionOps]; applied on the GL thread. [mixer] is unused, kept for the queue-engine call shape. */
    @Suppress("UNUSED_PARAMETER")
    fun applyTransitionItem(file: File, mixer: Mixer) = TransitionOps.applyItem(file)

    fun jumpToIndex(index: Int, mixer: Mixer) {
        if (index !in queue.indices) return
        if (isShuffleEnabled) {
            if (activeIndex in queue.indices) {
                playbackHistory.add(activeIndex)
            }
            playedIndices.add(index)
        }
        activeIndex = index
        val file = queue[activeIndex]
        applyTransitionItem(file, mixer)
    }

    fun advanceNext(mixer: Mixer) {
        val nextIndex = computeNextIndex() ?: return
        activeIndex = nextIndex
        val file = queue[activeIndex]
        logger.info { "Advancing $queueLabel to index $activeIndex: ${file.name}" }
        applyTransitionItem(file, mixer)
    }

    fun advancePrevious(mixer: Mixer) {
        val prevIndex = computePrevIndex() ?: return
        activeIndex = prevIndex
        val file = queue[activeIndex]
        logger.info { "Advancing $queueLabel back to index $activeIndex: ${file.name}" }
        applyTransitionItem(file, mixer)
    }

    fun advanceOnAutoFade(mixer: Mixer) {
        if (isAutoAdvanceEnabled && queue.isNotEmpty()) {
            advanceNext(mixer)
        }
    }

    fun restoreSessionQueue(
        files: List<File>,
        activeIdx: Int,
        autoAdvance: Boolean = true,
        repeat: Boolean = false,
        shuffle: Boolean = false
    ) {
        if (files !== queue) {
            queue.clear()
            queue.addAll(files)
        }
        activeIndex = activeIdx
        isAutoAdvanceEnabled = autoAdvance
        isRepeatEnabled = repeat
        isShuffleEnabled = shuffle
        playedIndices.clear()
        playbackHistory.clear()
        if (shuffle && activeIdx in queue.indices) {
            playedIndices.add(activeIdx)
        }
    }
}
