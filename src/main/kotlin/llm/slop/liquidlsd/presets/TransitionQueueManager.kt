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

    fun applyTransitionItem(file: File, mixer: Mixer) {
        if (file.extension.equals("lsdtrans", ignoreCase = true) && file.exists()) {
            try {
                val dto = json.decodeFromString<TransitionPresetDto>(file.readText())
                mixer.applyTransitionPreset(dto)
                logger.info { "Applied transition preset ${dto.name} from ${file.name}" }
            } catch (e: Exception) {
                logger.error(e) { "Failed to load transition preset ${file.absolutePath}, falling back to stock transition" }
                val fallbackId = file.nameWithoutExtension.ifBlank { "linear_crossfade" }
                mixer.setTransition(fallbackId)
            }
        } else {
            val id = if (file.extension.equals("fs", ignoreCase = true) || file.extension.equals("isf", ignoreCase = true)) {
                file.nameWithoutExtension
            } else {
                file.nameWithoutExtension.ifBlank { file.name }
            }
            mixer.setTransition(id)
            logger.info { "Applied transition shader ID: $id" }
        }
    }

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
