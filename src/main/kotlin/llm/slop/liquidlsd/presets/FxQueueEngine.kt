package llm.slop.liquidlsd.presets

import kotlinx.serialization.json.Json
import llm.slop.liquidlsd.SessionContext
import llm.slop.liquidlsd.models.FXPlaylistDto
import llm.slop.liquidlsd.rendering.Deck
import llm.slop.liquidlsd.rendering.Mixer
import llm.slop.liquidlsd.ui.FileSystemManager
import llm.slop.liquidlsd.ui.UITheme
import mu.KotlinLogging
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Shared engine backing the live volatile FX queues (Deck A/B and Deck BG).
 * Handles single FX presets (.lsdfx), FX chains (.lsdfxchain), and FX playlists (.lsdfxplay),
 * with shuffle, repeat, and history tracking, plus the same dirty-deck autosave/skip/discard
 * guard [PlayQueueManager] and [BgQueueManager] already apply before an auto-advance overwrites a deck.
 */
abstract class FxQueueEngine(private val queueLabel: String, private val savePrefix: String) {
    private val logger = KotlinLogging.logger {}
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    val queue = CopyOnWriteArrayList<File>()

    @Volatile
    var isRepeatEnabled = false

    @Volatile
    var isShuffleEnabled = false

    @Volatile
    var activeIndex = -1
        private set

    val playedIndices = ConcurrentHashMap.newKeySet<Int>()
    val playbackHistory = CopyOnWriteArrayList<Int>()

    /** Resolves the deck an advance/jump should apply to for the current mixer state. */
    abstract fun getTargetDeck(mixer: Mixer): Deck

    /** Short label ("A", "B", "BG") used in dirty-deck autosave names and log messages. */
    protected abstract fun deckLabel(mixer: Mixer): String

    /** The deck's currently active saved preset name, if any; used as the autosave target name. */
    protected abstract fun activePresetName(mixer: Mixer): String?

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
            val dto = json.decodeFromString<FXPlaylistDto>(content)
            val parent = playlistFile.parentFile
            val fxPresetsRoot = FileSystemManager.getFxPresetsRoot()
            val fxChainsRoot = FileSystemManager.getFxChainsRoot()

            dto.items.map { item ->
                val candidateParent = parent?.let { File(it, item) }
                val candidatePresets = File(fxPresetsRoot, item)
                val candidateChains = File(fxChainsRoot, item)
                val direct = File(item)
                when {
                    candidateParent != null && candidateParent.exists() -> candidateParent
                    candidatePresets.exists() -> candidatePresets
                    candidateChains.exists() -> candidateChains
                    direct.exists() -> direct
                    else -> File(item)
                }
            }
        } catch (e: Exception) {
            logger.error(e) { "Failed to parse FX playlist: ${playlistFile.absolutePath}" }
            emptyList()
        }
    }

    fun appendToQueue(file: File) {
        if (file.extension.equals("lsdfxplay", ignoreCase = true)) {
            val files = parsePlaylist(file)
            val insertPos = queue.size
            queue.addAll(files)
            if (isShuffleEnabled) {
                shiftIndicesAfter(insertPos - 1, files.size)
            }
            logger.info { "Appended FX playlist to $queueLabel: ${file.name} (${files.size} items)" }
        } else {
            val insertPos = queue.size
            queue.add(file)
            if (isShuffleEnabled && insertPos <= activeIndex) {
                shiftIndicesAfter(insertPos - 1, 1)
            }
            logger.info { "Appended FX item to $queueLabel: ${file.name}" }
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
        logger.info { "Inserted ${files.size} FX item(s) at index $clampedIndex into $queueLabel" }
    }

    fun insertAt(index: Int, file: File) {
        if (file.extension.equals("lsdfxplay", ignoreCase = true)) {
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
        logger.info { "Removed FX item from $queueLabel at index $index: ${removed.name}" }
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
        logger.info { "Moved $queueLabel item from $fromIndex to $toIndex: ${item.name}" }
    }

    fun clearQueue() {
        queue.clear()
        activeIndex = -1
        playedIndices.clear()
        playbackHistory.clear()
        logger.info { "Cleared $queueLabel" }
    }

    /**
     * Gates an auto-advance against a dirty target deck the same way [PlayQueueManager] and
     * [BgQueueManager] already do, per [UITheme.autoVjDirtyBehavior] (SKIP / AUTO_SAVE / AUTO_DISCARD).
     * @return true if the advance should proceed, false if it was skipped.
     */
    private fun handleDirtyDeck(deck: Deck, mixer: Mixer): Boolean {
        if (!PresetManager.isDeckDirty(deck, mixer)) return true
        val label = deckLabel(mixer)
        return when (UITheme.autoVjDirtyBehavior) {
            UITheme.AutoVjDirtyBehavior.SKIP -> {
                logger.info { "$savePrefix: Skipping $queueLabel advance because Deck $label is dirty" }
                false
            }
            UITheme.AutoVjDirtyBehavior.AUTO_SAVE -> {
                val saveName = activePresetName(mixer) ?: "${savePrefix}_${label}_${System.currentTimeMillis()}"
                logger.info { "$savePrefix: Autosaving dirty Deck $label to $saveName" }
                PresetRepository.saveDeckPresetAsync(File("library/presets/$saveName.lsd"), deck, saveName)
                true
            }
            UITheme.AutoVjDirtyBehavior.AUTO_DISCARD -> {
                logger.info { "$savePrefix: Discarding changes on dirty Deck $label" }
                true
            }
        }
    }

    fun jumpToIndex(index: Int, session: SessionContext, mixer: Mixer) {
        if (index !in queue.indices) return
        val targetDeck = getTargetDeck(mixer)
        if (!handleDirtyDeck(targetDeck, mixer)) return

        if (isShuffleEnabled) {
            if (activeIndex in queue.indices) {
                playbackHistory.add(activeIndex)
            }
            playedIndices.add(index)
        }
        activeIndex = index
        val file = queue[activeIndex]
        logger.info { "Jumping to $queueLabel item at index $activeIndex: ${file.name}" }
        FXItemApplier.apply(session, file, targetDeck)
    }

    fun advanceNext(session: SessionContext, mixer: Mixer) {
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
                    logger.info { "End of shuffle $queueLabel reached (all items played once)." }
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
                    logger.info { "End of $queueLabel reached." }
                    return
                }
            }
        }

        if (nextIndex == -1 || nextIndex !in queue.indices) return

        val targetDeck = getTargetDeck(mixer)
        if (!handleDirtyDeck(targetDeck, mixer)) return

        activeIndex = nextIndex
        val file = queue[activeIndex]
        logger.info { "Advancing $queueLabel to index $activeIndex: ${file.name}" }
        FXItemApplier.apply(session, file, targetDeck)
    }

    fun advancePrevious(session: SessionContext, mixer: Mixer) {
        if (queue.isEmpty()) return

        var prevIndex = -1

        if (isShuffleEnabled) {
            if (playbackHistory.isNotEmpty()) {
                prevIndex = playbackHistory.removeAt(playbackHistory.lastIndex)
            } else {
                logger.info { "No previous item in $queueLabel shuffle history." }
                return
            }
        } else {
            prevIndex = activeIndex - 1
            if (prevIndex < 0) {
                if (isRepeatEnabled) {
                    prevIndex = queue.size - 1
                } else {
                    logger.info { "Beginning of $queueLabel reached." }
                    return
                }
            }
        }

        if (prevIndex == -1 || prevIndex !in queue.indices) return

        val targetDeck = getTargetDeck(mixer)
        if (!handleDirtyDeck(targetDeck, mixer)) return

        activeIndex = prevIndex
        val file = queue[activeIndex]
        logger.info { "Stepping back $queueLabel to index $activeIndex: ${file.name}" }
        FXItemApplier.apply(session, file, targetDeck)
    }
}
