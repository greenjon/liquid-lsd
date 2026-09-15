package llm.slop.liquidlsd.presets

import kotlinx.serialization.json.Json
import llm.slop.liquidlsd.models.TransitionPlaylistDto
import llm.slop.liquidlsd.models.TransitionPresetDto
import llm.slop.liquidlsd.rendering.Mixer
import llm.slop.liquidlsd.ui.FileSystemManager
import mu.KotlinLogging
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Manages the Transition Queue engine (Phase 2).
 * Handles transition presets (.lsdtrans), transition playlists (.lsdtransplay),
 * stock transitions, shuffle mode, repeat, history tracking, and auto-advance hooks.
 */
object TransitionQueueManager {
    private val logger = KotlinLogging.logger {}
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    val queue = CopyOnWriteArrayList<File>()

    @Volatile
    var isAutoAdvanceEnabled = true

    @Volatile
    var isRepeatEnabled = false

    @Volatile
    var isShuffleEnabled = false

    @Volatile
    var activeIndex = -1
        private set

    val playedIndices = ConcurrentHashMap.newKeySet<Int>()
    val playbackHistory = CopyOnWriteArrayList<Int>()

    fun initializeShuffle() {
        playedIndices.clear()
        playbackHistory.clear()
        if (activeIndex in queue.indices) {
            playedIndices.add(activeIndex)
        }
    }

    private fun shiftIndicesAfter(threshold: Int, amount: Int) {
        val newPlayed = ConcurrentHashMap.newKeySet<Int>()
        for (idx in playedIndices) {
            if (idx > threshold) {
                newPlayed.add(idx + amount)
            } else {
                newPlayed.add(idx)
            }
        }
        playedIndices.clear()
        playedIndices.addAll(newPlayed)

        for (i in playbackHistory.indices) {
            val idx = playbackHistory[i]
            if (idx > threshold) {
                playbackHistory[i] = idx + amount
            }
        }
    }

    private fun removeIndexAndShift(removedIdx: Int) {
        playedIndices.remove(removedIdx)
        val newPlayed = ConcurrentHashMap.newKeySet<Int>()
        for (idx in playedIndices) {
            if (idx > removedIdx) {
                newPlayed.add(idx - 1)
            } else if (idx < removedIdx) {
                newPlayed.add(idx)
            }
        }
        playedIndices.clear()
        playedIndices.addAll(newPlayed)

        val newHist = CopyOnWriteArrayList<Int>()
        for (idx in playbackHistory) {
            if (idx == removedIdx) continue
            if (idx > removedIdx) {
                newHist.add(idx - 1)
            } else {
                newHist.add(idx)
            }
        }
        playbackHistory.clear()
        playbackHistory.addAll(newHist)
    }

    private fun moveIndex(from: Int, to: Int) {
        fun mapIdx(idx: Int): Int {
            return when {
                idx == from -> to
                from < to && idx in (from + 1)..to -> idx - 1
                from > to && idx in to..<from -> idx + 1
                else -> idx
            }
        }

        val newPlayed = ConcurrentHashMap.newKeySet<Int>()
        for (idx in playedIndices) {
            newPlayed.add(mapIdx(idx))
        }
        playedIndices.clear()
        playedIndices.addAll(newPlayed)

        val newHist = CopyOnWriteArrayList<Int>()
        for (idx in playbackHistory) {
            newHist.add(mapIdx(idx))
        }
        playbackHistory.clear()
        playbackHistory.addAll(newHist)

        if (activeIndex in queue.indices) {
            if (activeIndex == from) {
                activeIndex = to
            } else if (from < activeIndex && to >= activeIndex) {
                activeIndex--
            } else if (from > activeIndex && to <= activeIndex) {
                activeIndex++
            }
        }
    }

    fun parsePlaylist(playlistFile: File): List<File> {
        if (!playlistFile.exists() || !playlistFile.isFile) return emptyList()
        return try {
            val content = playlistFile.readText()
            val dto = json.decodeFromString<TransitionPlaylistDto>(content)
            val parent = playlistFile.parentFile
            val transRoot = FileSystemManager.getTransitionsRoot()
            
            dto.items.map { item ->
                val candidateParent = parent?.let { File(it, item) }
                val candidateRoot = File(transRoot, item)
                val direct = File(item)
                when {
                    candidateParent != null && candidateParent.exists() -> candidateParent
                    candidateRoot.exists() -> candidateRoot
                    direct.exists() -> direct
                    else -> File(item) // Retain item name/ID as File token
                }
            }
        } catch (e: Exception) {
            logger.error(e) { "Failed to parse transition playlist: ${playlistFile.absolutePath}" }
            emptyList()
        }
    }

    fun appendToQueue(file: File) {
        if (file.extension.equals("lsdtransplay", ignoreCase = true)) {
            val files = parsePlaylist(file)
            val insertPos = queue.size
            queue.addAll(files)
            if (isShuffleEnabled) {
                shiftIndicesAfter(insertPos - 1, files.size)
            }
            logger.info { "Appended transition playlist: ${file.name} (${files.size} items)" }
        } else {
            val insertPos = queue.size
            queue.add(file)
            if (isShuffleEnabled && insertPos <= activeIndex) {
                shiftIndicesAfter(insertPos - 1, 1)
            }
            logger.info { "Appended transition to queue: ${file.name}" }
        }
    }

    fun insertAt(index: Int, files: List<File>) {
        if (files.isEmpty()) return
        val clampedIndex = index.coerceIn(0, queue.size)
        queue.addAll(clampedIndex, files)
        if (activeIndex >= clampedIndex) {
            activeIndex += files.size
        }
        if (isShuffleEnabled) {
            shiftIndicesAfter(clampedIndex - 1, files.size)
        }
        logger.info { "Inserted ${files.size} transition(s) at index $clampedIndex" }
    }

    fun insertAt(index: Int, file: File) {
        if (file.extension.equals("lsdtransplay", ignoreCase = true)) {
            val files = parsePlaylist(file)
            insertAt(index, files)
        } else {
            insertAt(index, listOf(file))
        }
    }

    fun removeFromQueue(index: Int) {
        if (index !in queue.indices) return
        val removed = queue.removeAt(index)
        if (isShuffleEnabled) {
            removeIndexAndShift(index)
        }
        if (queue.isEmpty()) {
            activeIndex = -1
        } else if (activeIndex == index) {
            if (activeIndex >= queue.size) {
                activeIndex = queue.size - 1
            }
        } else if (activeIndex > index) {
            activeIndex--
        }
        logger.info { "Removed transition from queue at index $index: ${removed.name}" }
    }

    fun moveItem(fromIndex: Int, toIndex: Int) {
        if (fromIndex !in queue.indices || toIndex !in queue.indices || fromIndex == toIndex) return
        val item = queue.removeAt(fromIndex)
        queue.add(toIndex, item)
        if (isShuffleEnabled) {
            moveIndex(fromIndex, toIndex)
        } else {
            if (activeIndex == fromIndex) {
                activeIndex = toIndex
            } else if (fromIndex < activeIndex && toIndex >= activeIndex) {
                activeIndex--
            } else if (fromIndex > activeIndex && toIndex <= activeIndex) {
                activeIndex++
            }
        }
        logger.info { "Moved transition item from $fromIndex to $toIndex: ${item.name}" }
    }

    fun clearQueue() {
        queue.clear()
        activeIndex = -1
        playedIndices.clear()
        playbackHistory.clear()
        logger.info { "Cleared transition queue" }
    }

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
        if (queue.isEmpty()) return

        var nextIndex = -1

        if (isShuffleEnabled) {
            val unplayed = queue.indices.filter { it !in playedIndices }
            if (unplayed.isEmpty()) {
                if (isRepeatEnabled) {
                    playedIndices.clear()
                    if (activeIndex in queue.indices) {
                        playedIndices.add(activeIndex)
                    }
                    val freshUnplayed = queue.indices.filter { it !in playedIndices }
                    if (freshUnplayed.isNotEmpty()) {
                        nextIndex = freshUnplayed.random()
                    } else if (queue.isNotEmpty()) {
                        nextIndex = 0
                    }
                } else {
                    logger.info { "End of shuffle transition queue reached (all items played once)." }
                    return
                }
            } else {
                nextIndex = unplayed.random()
            }

            if (nextIndex != -1) {
                if (activeIndex in queue.indices) {
                    playbackHistory.add(activeIndex)
                }
                playedIndices.add(nextIndex)
            }
        } else {
            nextIndex = activeIndex + 1
            if (nextIndex >= queue.size) {
                if (isRepeatEnabled) {
                    nextIndex = 0
                } else {
                    logger.info { "End of transition queue reached." }
                    return
                }
            }
        }

        if (nextIndex == -1 || nextIndex !in queue.indices) return

        activeIndex = nextIndex
        val file = queue[activeIndex]
        logger.info { "Advancing transition queue to index $activeIndex: ${file.name}" }
        applyTransitionItem(file, mixer)
    }

    fun advancePrevious(mixer: Mixer) {
        if (queue.isEmpty()) return

        var prevIndex = -1

        if (isShuffleEnabled) {
            if (playbackHistory.isNotEmpty()) {
                prevIndex = playbackHistory.removeAt(playbackHistory.lastIndex)
            } else {
                logger.info { "No previous item in transition shuffle history." }
                return
            }
        } else {
            prevIndex = activeIndex - 1
            if (prevIndex < 0) {
                if (isRepeatEnabled) {
                    prevIndex = queue.lastIndex
                } else {
                    logger.info { "Beginning of transition queue reached." }
                    return
                }
            }
        }

        if (prevIndex !in queue.indices) return

        activeIndex = prevIndex
        val file = queue[activeIndex]
        logger.info { "Advancing transition queue back to index $activeIndex: ${file.name}" }
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
