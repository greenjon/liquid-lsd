package llm.slop.liquidlsd.presets

import llm.slop.liquidlsd.rendering.Mixer
import llm.slop.liquidlsd.rendering.Deck
import llm.slop.liquidlsd.ui.UITheme
import mu.KotlinLogging
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages the volatile RAM Play Queue (Phase 1).
 */
object PlayQueueManager {
    private val logger = KotlinLogging.logger {}

    val queue = CopyOnWriteArrayList<File>()
    
    @Volatile
    var isAutoVJEnabled = false

    @Volatile
    var isRepeatEnabled = false

    @Volatile
    var isShuffleEnabled = false

    @Volatile
    var activeIndex = -1
        private set

    @Volatile
    var stagedDeckA = false

    @Volatile
    var stagedDeckB = false

    /**
     * Called when a preset is loaded into a deck manually by the user (not by the automated queue).
     * If loaded onto the standby deck while AutoVJ is active, stages that deck for the next transition ("Jump the Line").
     * If loaded onto the active deck, it plays immediately and clears any previous stage flag.
     * Deck PV loads are silently ignored — PV is a preview-only deck and does not affect Auto-VJ staging.
     */
    fun notifyManualDeckLoaded(isDeckA: Boolean, isDeckPV: Boolean, mixer: Mixer) {
        if (isDeckPV) return
        if (isDeckA) {
            // crossfade > 0.0 means Deck B is dominant, so Deck A is standby
            if (mixer.crossfade.value > 0.0f) {
                stagedDeckA = true
                logger.info { "AutoVJ: Deck A manually staged for next transition" }
            } else {
                stagedDeckA = false
            }
        } else {
            // crossfade <= 0.0 means Deck A is dominant, so Deck B is standby
            if (mixer.crossfade.value <= 0.0f) {
                stagedDeckB = true
                logger.info { "AutoVJ: Deck B manually staged for next transition" }
            } else {
                stagedDeckB = false
            }
        }
    }

    // Track played indices in the current shuffle cycle to ensure each track plays once.
    val playedIndices = ConcurrentHashMap.newKeySet<Int>()

    // Track order of played indices so "previous" button goes back to previous track in shuffle mode.
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
            } else {
                newPlayed.add(idx)
            }
        }
        playedIndices.clear()
        playedIndices.addAll(newPlayed)

        val newHistory = playbackHistory.filter { it != removedIdx }.map {
            if (it > removedIdx) it - 1 else it
        }
        playbackHistory.clear()
        playbackHistory.addAll(newHistory)
    }

    private fun moveIndex(from: Int, to: Int) {
        fun mapIdx(idx: Int): Int {
            if (idx == from) return to
            if (from < to) {
                if (idx in (from + 1)..to) return idx - 1
            } else {
                if (idx in to until from) return idx + 1
            }
            return idx
        }

        val newPlayed = ConcurrentHashMap.newKeySet<Int>()
        for (idx in playedIndices) {
            newPlayed.add(mapIdx(idx))
        }
        playedIndices.clear()
        playedIndices.addAll(newPlayed)

        for (i in playbackHistory.indices) {
            playbackHistory[i] = mapIdx(playbackHistory[i])
        }
    }

    fun parsePlaylist(playlistFile: File): List<File> {
        return try {
            val items = PlaylistParser.parseFile(playlistFile)
            val roots = listOfNotNull(playlistFile.parentFile, File("library/presets")).distinct()
            
            items.mapNotNull { itemName ->
                val resolved = PlaylistParser.resolveItem(itemName, roots)
                if (resolved == null) {
                    logger.warn { "Queue playlist item not found: $itemName" }
                }
                resolved
            }
        } catch (e: Exception) {
            logger.error(e) { "Failed to parse playlist: ${playlistFile.absolutePath}" }
            emptyList()
        }
    }

    fun appendPlaylistToQueue(playlistFile: File) {
        val files = parsePlaylist(playlistFile)
        queue.addAll(files)
        logger.info { "Appended playlist to queue: ${playlistFile.name} (${files.size} items)" }
    }

    fun playNow(file: File, mixer: Mixer) {
        clearQueue()
        appendToQueue(file)
        isAutoVJEnabled = true
        mixer.muteCrossfadeNonMidiCv()
        triggerNext(mixer)
    }

    fun playIndex(index: Int, mixer: Mixer) {
        if (index !in queue.indices) return

        // Determine which deck is inactive
        // crossfade -1.0 = Deck A, 1.0 = Deck B
        val targetIsA = mixer.crossfade.value > 0.0f
        val targetDeck = if (targetIsA) mixer.deckA else mixer.deckB

        if (!handleDirtyDeck(targetIsA, targetDeck, mixer)) return

        activeIndex = index
        stagedDeckA = false
        stagedDeckB = false
        val file = queue[activeIndex]

        if (isShuffleEnabled) {
            playedIndices.add(index)
            playbackHistory.add(index)
        }

        logger.info { "Playing index $index: ${file.name} to Deck ${if (targetIsA) "A" else "B"}" }
        PresetManager.loadDeckPresetAsync(file, isDeckA = targetIsA, isManual = false)

        // Start auto-fade to the target deck
        mixer.targetCrossfade = if (targetIsA) -1.0f else 1.0f
        mixer.isAutoFading = true
        mixer.muteCrossfadeNonMidiCv()
    }

    fun playPlaylistNow(playlistFile: File, mixer: Mixer) {
        clearQueue()
        val files = parsePlaylist(playlistFile)
        if (files.isNotEmpty()) {
            queue.addAll(files)
            logger.info { "Replaced queue with playlist: ${playlistFile.name} (${files.size} items)" }
            isAutoVJEnabled = true
            mixer.muteCrossfadeNonMidiCv()
            triggerNext(mixer)
        }
    }

    fun insertAfterCurrent(file: File) {
        val insertIndex = if (activeIndex in queue.indices) activeIndex + 1 else 0
        if (insertIndex <= queue.size) {
            shiftIndicesAfter(insertIndex - 1, 1)
            queue.add(insertIndex, file)
            logger.info { "Inserted after current (at $insertIndex): ${file.name}" }
        } else {
            appendToQueue(file)
        }
    }

    fun insertPlaylistAfterCurrent(playlistFile: File) {
        val files = parsePlaylist(playlistFile)
        if (files.isNotEmpty()) {
            val insertIndex = if (activeIndex in queue.indices) activeIndex + 1 else 0
            if (insertIndex <= queue.size) {
                shiftIndicesAfter(insertIndex - 1, files.size)
                queue.addAll(insertIndex, files)
                logger.info { "Inserted playlist after current (at $insertIndex): ${playlistFile.name} (${files.size} items)" }
            } else {
                queue.addAll(files)
                logger.info { "Appended playlist to queue: ${playlistFile.name} (${files.size} items)" }
            }
        }
    }

    fun appendToQueue(file: File) {
        queue.add(file)
        logger.info { "Appended to queue: ${file.name}" }
    }

    fun removeFromQueue(index: Int) {
        if (index in queue.indices) {
            val removed = queue.removeAt(index)
            logger.info { "Removed from queue: ${removed.name}" }
            removeIndexAndShift(index)
            if (index <= activeIndex) {
                activeIndex--
            }
        }
    }

    fun removeFileFromQueue(file: File) {
        val targetPath = file.absolutePath
        var i = 0
        while (i < queue.size) {
            if (queue[i].absolutePath == targetPath) {
                removeFromQueue(i)
            } else {
                i++
            }
        }
    }

    fun moveQueueItem(from: Int, to: Int) {
        if (from in queue.indices && to in queue.indices) {
            val item = queue.removeAt(from)
            queue.add(to, item)
            moveIndex(from, to)
            
            // Adjust activeIndex if it was moved
            if (activeIndex == from) {
                activeIndex = to
            } else if (from < activeIndex && to >= activeIndex) {
                activeIndex--
            } else if (from > activeIndex && to <= activeIndex) {
                activeIndex++
            }
        }
    }

    fun triggerNext(mixer: Mixer) {
        // Determine which deck is inactive
        // crossfade -1.0 = Deck A, 1.0 = Deck B
        val targetIsA = mixer.crossfade.value > 0.0f
        val targetDeck = if (targetIsA) mixer.deckA else mixer.deckB

        val isStaged = if (targetIsA) stagedDeckA else stagedDeckB
        if (isStaged) {
            if (targetIsA) stagedDeckA = false else stagedDeckB = false
            logger.info { "Triggering next to manually staged Deck ${if (targetIsA) "A" else "B"}" }
            // Start auto-fade to the target deck without loading from queue and without advancing queue pointer
            mixer.targetCrossfade = if (targetIsA) -1.0f else 1.0f
            mixer.isAutoFading = true
            mixer.muteCrossfadeNonMidiCv()
            return
        }

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
                    logger.info { "End of shuffle queue reached (all tracks played once)." }
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
                    logger.info { "End of queue reached." }
                    return
                }
            }
        }

        if (nextIndex == -1 || nextIndex !in queue.indices) return

        if (!handleDirtyDeck(targetIsA, targetDeck, mixer)) return

        activeIndex = nextIndex
        val file = queue[activeIndex]
        
        logger.info { "Triggering next: ${file.name} to Deck ${if (targetIsA) "A" else "B"}" }
        
        PresetManager.loadDeckPresetAsync(file, isDeckA = targetIsA, isManual = false)
        
        // Start auto-fade to the target deck
        mixer.targetCrossfade = if (targetIsA) -1.0f else 1.0f
        mixer.isAutoFading = true
        mixer.muteCrossfadeNonMidiCv()
    }
    
    fun clearQueue() {
        queue.clear()
        activeIndex = -1
        stagedDeckA = false
        stagedDeckB = false
        playedIndices.clear()
        playbackHistory.clear()
    }

    fun restoreSessionQueue(files: List<File>, activeIdx: Int, autoVJ: Boolean, repeat: Boolean = false, shuffle: Boolean = false) {
        if (files !== queue) {
            queue.clear()
            queue.addAll(files)
        }
        activeIndex = activeIdx
        isAutoVJEnabled = autoVJ
        isRepeatEnabled = repeat
        isShuffleEnabled = shuffle
        stagedDeckA = false
        stagedDeckB = false
        playedIndices.clear()
        playbackHistory.clear()
        if (shuffle && activeIdx in queue.indices) {
            playedIndices.add(activeIdx)
        }
    }

    fun triggerPrevious(mixer: Mixer) {
        // Determine which deck is inactive
        // crossfade -1.0 = Deck A, 1.0 = Deck B
        val targetIsA = mixer.crossfade.value > 0.0f
        val targetDeck = if (targetIsA) mixer.deckA else mixer.deckB

        val isStaged = if (targetIsA) stagedDeckA else stagedDeckB
        if (isStaged) {
            if (targetIsA) stagedDeckA = false else stagedDeckB = false
            logger.info { "Triggering previous to manually staged Deck ${if (targetIsA) "A" else "B"}" }
            // Start auto-fade to the target deck without loading from queue and without altering queue pointer
            mixer.targetCrossfade = if (targetIsA) -1.0f else 1.0f
            mixer.isAutoFading = true
            mixer.muteCrossfadeNonMidiCv()
            return
        }

        if (queue.isEmpty()) return
        
        var prevIndex = -1

        if (isShuffleEnabled) {
            if (playbackHistory.isNotEmpty()) {
                prevIndex = playbackHistory.removeAt(playbackHistory.size - 1)
                playedIndices.remove(activeIndex)
            } else {
                logger.info { "Start of shuffle history reached." }
                return
            }
        } else {
            prevIndex = activeIndex - 1
            if (prevIndex < 0) {
                if (isRepeatEnabled) {
                    prevIndex = queue.size - 1
                } else {
                    logger.info { "Start of queue reached." }
                    return
                }
            }
        }

        if (prevIndex == -1 || prevIndex !in queue.indices) return

        if (!handleDirtyDeck(targetIsA, targetDeck, mixer)) return

        activeIndex = prevIndex
        val file = queue[activeIndex]
        
        logger.info { "Triggering previous: ${file.name} to Deck ${if (targetIsA) "A" else "B"}" }
        
        PresetManager.loadDeckPresetAsync(file, isDeckA = targetIsA, isManual = false)
        
        // Start auto-fade to the target deck
        mixer.targetCrossfade = if (targetIsA) -1.0f else 1.0f
        mixer.isAutoFading = true
        mixer.muteCrossfadeNonMidiCv()
    }

    /**
     * Handles a dirty target deck according to the configured AutoVJ dirty behavior.
     * @return true if the queue advance should proceed, false if it should be skipped.
     */
    private fun handleDirtyDeck(targetIsA: Boolean, targetDeck: Deck, mixer: Mixer): Boolean {
        if (!PresetManager.isDeckDirty(targetDeck, mixer)) return true
        return when (UITheme.autoVjDirtyBehavior) {
            UITheme.AutoVjDirtyBehavior.SKIP -> {
                logger.info { "AutoVJ: Skipping because target deck is dirty" }
                false
            }
            UITheme.AutoVjDirtyBehavior.AUTO_SAVE -> {
                val activeName = if (targetIsA) PresetManager.activePresetA else PresetManager.activePresetB
                val saveName = activeName ?: "AutoVJ_${if (targetIsA) "A" else "B"}_${System.currentTimeMillis()}"
                logger.info { "AutoVJ: Autosaving dirty deck to $saveName" }
                PresetManager.saveDeckPresetAsync(File("library/presets/$saveName.lsd"), targetDeck, saveName)
                true
            }
            UITheme.AutoVjDirtyBehavior.AUTO_DISCARD -> {
                logger.info { "AutoVJ: Discarding changes on dirty deck" }
                true
            }
        }
    }
}
