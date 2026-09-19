package llm.slop.liquidlsd.presets

import mu.KotlinLogging
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Shared bookkeeping engine backing every live volatile playback queue (Preset A/B, Preset BG,
 * FX A/B, FX BG, Transition): shuffle/repeat state, played/history index tracking, playlist
 * parsing, and queue mutation (append/insert/remove/move/clear). Subclasses only need to supply
 * how a playlist file's items resolve to disk (`playlistItemExtension`/`playlistRoots`) and,
 * for advance/jump, how to turn a selected index into an actual apply action — advance/jump
 * itself isn't shared here since FX (deck + dirty-deck guard) and Transition (no deck concept)
 * need genuinely different signatures, not just a different body.
 */
abstract class QueueEngine(protected val queueLabel: String) {
    protected val logger = KotlinLogging.logger {}

    val queue = CopyOnWriteArrayList<File>()

    @Volatile
    var isRepeatEnabled = false

    @Volatile
    var isShuffleEnabled = false

    @Volatile
    var activeIndex = -1
        protected set

    val playedIndices = ConcurrentHashMap.newKeySet<Int>()
    val playbackHistory = CopyOnWriteArrayList<Int>()

    /** File extension (no dot) identifying a playlist file for this queue, e.g. "lsdfxplay". */
    protected abstract fun playlistItemExtension(): String

    /** Search roots (in priority order) used to resolve a playlist item name to a file. */
    protected abstract fun playlistRoots(playlistFile: File): List<File>

    /**
     * Called when [PlaylistParser.resolveItem] can't find `item` on disk. Return a literal
     * token `File` to keep it in the resolved list anyway (Transition stock-shader IDs aren't
     * files, so they must pass through unresolved), or null (the default) to drop the item
     * with a logged warning.
     */
    protected open fun resolveUnmatchedPlaylistItem(item: String): File? = null

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
            val items = PlaylistParser.parseFile(playlistFile)
            val roots = playlistRoots(playlistFile)
            items.mapNotNull { item ->
                val resolved = PlaylistParser.resolveItem(item, roots) ?: resolveUnmatchedPlaylistItem(item)
                if (resolved == null) {
                    logger.warn { "$queueLabel playlist item not found: $item" }
                }
                resolved
            }
        } catch (e: Exception) {
            logger.error(e) { "Failed to parse $queueLabel playlist: ${playlistFile.absolutePath}" }
            emptyList()
        }
    }

    fun appendToQueue(file: File) {
        if (file.extension.equals(playlistItemExtension(), ignoreCase = true)) {
            val files = parsePlaylist(file)
            val insertPos = queue.size
            queue.addAll(files)
            if (isShuffleEnabled) {
                shiftIndicesAfter(insertPos - 1, files.size)
            }
            logger.info { "Appended playlist to $queueLabel: ${file.name} (${files.size} items)" }
        } else {
            val insertPos = queue.size
            queue.add(file)
            if (isShuffleEnabled && insertPos <= activeIndex) {
                shiftIndicesAfter(insertPos - 1, 1)
            }
            logger.info { "Appended item to $queueLabel: ${file.name}" }
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
        logger.info { "Inserted ${files.size} item(s) at index $clampedIndex into $queueLabel" }
    }

    fun insertAt(index: Int, file: File) {
        if (file.extension.equals(playlistItemExtension(), ignoreCase = true)) {
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
        logger.info { "Removed item from $queueLabel at index $index: ${removed.name}" }
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
     * Selects the next index per shuffle/repeat state, updating [playedIndices]/[playbackHistory]
     * as a side effect exactly like the pre-extraction advance logic did. Returns null if the
     * advance should stop (end of queue, or end of shuffle history with repeat off).
     */
    protected fun computeNextIndex(): Int? {
        if (queue.isEmpty()) return null
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
                    return null
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
                    return null
                }
            }
        }

        if (nextIndex == -1 || nextIndex !in queue.indices) return null
        return nextIndex
    }

    /** Selects the previous index; see [computeNextIndex]. */
    protected fun computePrevIndex(): Int? {
        if (queue.isEmpty()) return null
        var prevIndex = -1

        if (isShuffleEnabled) {
            if (playbackHistory.isNotEmpty()) {
                prevIndex = playbackHistory.removeAt(playbackHistory.lastIndex)
            } else {
                logger.info { "No previous item in $queueLabel shuffle history." }
                return null
            }
        } else {
            prevIndex = activeIndex - 1
            if (prevIndex < 0) {
                if (isRepeatEnabled) {
                    prevIndex = queue.size - 1
                } else {
                    logger.info { "Beginning of $queueLabel reached." }
                    return null
                }
            }
        }

        if (prevIndex == -1 || prevIndex !in queue.indices) return null
        return prevIndex
    }
}
