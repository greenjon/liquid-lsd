package llm.slop.spirals.patches

import llm.slop.spirals.rendering.Mixer
import llm.slop.spirals.models.PlaylistDto
import kotlinx.serialization.json.Json
import mu.KotlinLogging
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages the volatile RAM Play Queue (Phase 1).
 */
object PlayQueueManager {
    private val logger = KotlinLogging.logger {}
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

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
            val content = playlistFile.readText()
            val items = if (content.trim().startsWith("{")) {
                val dto = json.decodeFromString<PlaylistDto>(content)
                dto.items
            } else {
                content.lines()
                    .map { it.trim() }
                    .filter { it.isNotEmpty() && !it.startsWith("#") }
            }
            
            items.mapNotNull { itemName ->
                val resolved = resolveQueueItem(itemName)
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
        triggerNext(mixer)
    }

    fun playPlaylistNow(playlistFile: File, mixer: Mixer) {
        clearQueue()
        val files = parsePlaylist(playlistFile)
        if (files.isNotEmpty()) {
            queue.addAll(files)
            logger.info { "Replaced queue with playlist: ${playlistFile.name} (${files.size} items)" }
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

    private fun resolveQueueItem(name: String): File? {
        val f = File(name)
        if (f.exists() && f.isFile) return f

        val roots = listOf("presets/patches")
        for (root in roots) {
            val rf = File(root, name)
            if (rf.exists() && rf.isFile) return rf
        }

        // Try extensions
        val extensions = listOf(".lsd", ".json", ".patch")
        for (ext in extensions) {
            val nameWithExt = if (name.endsWith(ext, ignoreCase = true)) name else "$name$ext"
            val fExt = File(nameWithExt)
            if (fExt.exists() && fExt.isFile) return fExt

            for (root in roots) {
                val rfExt = File(root, nameWithExt)
                if (rfExt.exists() && rfExt.isFile) return rfExt
            }
        }
        return null
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

        // Determine which deck is inactive
        // crossfade -1.0 = Deck A, 1.0 = Deck B
        val targetIsA = mixer.crossfade.value > 0.0f
        val targetDeck = if (targetIsA) mixer.deckA else mixer.deckB
        
        val isDirty = PatchManager.isDeckDirty(targetDeck, mixer)
        if (isDirty) {
            when (llm.slop.spirals.ui.UITheme.autoVjDirtyBehavior) {
                llm.slop.spirals.ui.UITheme.AutoVjDirtyBehavior.SKIP -> {
                    logger.info { "AutoVJ: Skipping next item because target deck is dirty" }
                    return
                }
                llm.slop.spirals.ui.UITheme.AutoVjDirtyBehavior.AUTO_SAVE -> {
                    val activeName = if (targetIsA) PatchManager.activePresetA else PatchManager.activePresetB
                    val saveName = activeName ?: "AutoVJ_${if (targetIsA) "A" else "B"}_${System.currentTimeMillis()}"
                    logger.info { "AutoVJ: Autosaving dirty deck to $saveName" }
                    PatchManager.saveDeckPresetAsync(File("presets/patches/$saveName.lsd"), targetDeck, saveName)
                }
                llm.slop.spirals.ui.UITheme.AutoVjDirtyBehavior.AUTO_DISCARD -> {
                    logger.info { "AutoVJ: Discarding changes on dirty deck" }
                }
            }
        }

        activeIndex = nextIndex
        val file = queue[activeIndex]
        
        logger.info { "Triggering next: ${file.name} to Deck ${if (targetIsA) "A" else "B"}" }
        
        PatchManager.loadDeckPresetAsync(file, targetIsA)
        
        // Start auto-fade to the target deck
        mixer.targetCrossfade = if (targetIsA) -1.0f else 1.0f
        mixer.isAutoFading = true
    }
    
    fun clearQueue() {
        queue.clear()
        activeIndex = -1
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
        playedIndices.clear()
        playbackHistory.clear()
        if (shuffle && activeIdx in queue.indices) {
            playedIndices.add(activeIdx)
        }
    }

    fun triggerPrevious(mixer: Mixer) {
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
        
        // Determine which deck is inactive
        // crossfade -1.0 = Deck A, 1.0 = Deck B
        val targetIsA = mixer.crossfade.value > 0.0f
        val targetDeck = if (targetIsA) mixer.deckA else mixer.deckB
        
        val isDirty = PatchManager.isDeckDirty(targetDeck, mixer)
        if (isDirty) {
            when (llm.slop.spirals.ui.UITheme.autoVjDirtyBehavior) {
                llm.slop.spirals.ui.UITheme.AutoVjDirtyBehavior.SKIP -> {
                    logger.info { "AutoVJ: Skipping prev item because target deck is dirty" }
                    return
                }
                llm.slop.spirals.ui.UITheme.AutoVjDirtyBehavior.AUTO_SAVE -> {
                    val activeName = if (targetIsA) PatchManager.activePresetA else PatchManager.activePresetB
                    val saveName = activeName ?: "AutoVJ_${if (targetIsA) "A" else "B"}_${System.currentTimeMillis()}"
                    logger.info { "AutoVJ: Autosaving dirty deck to $saveName" }
                    PatchManager.saveDeckPresetAsync(File("presets/patches/$saveName.lsd"), targetDeck, saveName)
                }
                llm.slop.spirals.ui.UITheme.AutoVjDirtyBehavior.AUTO_DISCARD -> {
                    logger.info { "AutoVJ: Discarding changes on dirty deck" }
                }
            }
        }

        activeIndex = prevIndex
        val file = queue[activeIndex]
        
        logger.info { "Triggering previous: ${file.name} to Deck ${if (targetIsA) "A" else "B"}" }
        
        PatchManager.loadDeckPresetAsync(file, targetIsA)
        
        // Start auto-fade to the target deck
        mixer.targetCrossfade = if (targetIsA) -1.0f else 1.0f
        mixer.isAutoFading = true
    }
}

