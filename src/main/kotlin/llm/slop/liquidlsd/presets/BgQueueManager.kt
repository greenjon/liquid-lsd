package llm.slop.liquidlsd.presets

import llm.slop.liquidlsd.rendering.Mixer
import llm.slop.liquidlsd.ui.UITheme
import mu.KotlinLogging
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages the volatile Background Queue and single-deck Dip-to-Black transitions.
 */
object BgQueueManager {
    private val logger = KotlinLogging.logger {}

    val queue = CopyOnWriteArrayList<File>()

    @Volatile
    var isAutoBGEnabled = false

    @Volatile
    var isRepeatEnabled = false

    @Volatile
    var isShuffleEnabled = false

    @Volatile
    var activeIndex = -1
        private set

    // Auto-advance hold time (seconds to stay on current preset)
    var holdDurationSec: Float = 30.0f
    
    // Dip-to-black transition duration (seconds for fade-out + fade-in total)
    var transitionDurationSec: Float = 2.0f

    enum class TransitionState {
        IDLE,
        FADING_OUT,
        FADING_IN
    }

    @Volatile
    var transitionState = TransitionState.IDLE
        private set

    private var transitionProgress: Float = 0f // 0.0 to 1.0
    private var pendingFile: File? = null
    private var timeOnCurrentPresetSec: Float = 0f
    private var initialAlpha: Float = 1.0f

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

    fun parsePlaylist(playlistFile: File): List<File> {
        return PlayQueueManager.parsePlaylist(playlistFile)
    }

    fun appendPlaylistToQueue(playlistFile: File) {
        val files = parsePlaylist(playlistFile)
        queue.addAll(files)
        logger.info { "Appended playlist to BG queue: ${playlistFile.name} (${files.size} items)" }
    }

    fun playNow(file: File, mixer: Mixer, withDipToBlack: Boolean = true) {
        clearQueue()
        appendToQueue(file)
        isAutoBGEnabled = true
        playIndex(0, mixer, withDipToBlack)
    }

    fun playPlaylistNow(playlistFile: File, mixer: Mixer, withDipToBlack: Boolean = true) {
        clearQueue()
        val files = parsePlaylist(playlistFile)
        if (files.isNotEmpty()) {
            queue.addAll(files)
            logger.info { "Replaced BG queue with playlist: ${playlistFile.name} (${files.size} items)" }
            isAutoBGEnabled = true
            playIndex(0, mixer, withDipToBlack)
        }
    }

    fun insertAfterCurrent(file: File) {
        val insertIndex = if (activeIndex in queue.indices) activeIndex + 1 else 0
        if (insertIndex <= queue.size) {
            insertAt(insertIndex, file)
            logger.info { "Inserted after current in BG queue (at $insertIndex): ${file.name}" }
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
                if (activeIndex >= insertIndex) {
                    activeIndex += files.size
                }
                logger.info { "Inserted playlist after current in BG queue (at $insertIndex): ${playlistFile.name} (${files.size} items)" }
            } else {
                queue.addAll(files)
                logger.info { "Appended playlist to BG queue: ${playlistFile.name} (${files.size} items)" }
            }
        }
    }

    fun removeFileFromQueue(file: File) {
        val targetPath = file.absolutePath
        var i = 0
        while (i < queue.size) {
            if (queue[i].absolutePath == targetPath) {
                removeAt(i)
            } else {
                i++
            }
        }
    }

    fun appendToQueue(file: File) {
        queue.add(file)
    }

    fun appendAllToQueue(files: List<File>) {
        queue.addAll(files)
    }

    fun insertAt(index: Int, file: File) {
        val safeIndex = index.coerceIn(0, queue.size)
        queue.add(safeIndex, file)
        if (activeIndex >= safeIndex) {
            activeIndex++
        }
        shiftIndicesAfter(safeIndex - 1, 1)
    }

    fun removeAt(index: Int) {
        if (index in queue.indices) {
            queue.removeAt(index)
            if (activeIndex == index) {
                if (queue.isEmpty()) {
                    activeIndex = -1
                } else if (activeIndex >= queue.size) {
                    activeIndex = queue.size - 1
                }
            } else if (activeIndex > index) {
                activeIndex--
            }
            removeIndexAndShift(index)
        }
    }

    fun move(fromIndex: Int, toIndex: Int) {
        if (fromIndex in queue.indices && toIndex in 0..queue.size && fromIndex != toIndex) {
            val item = queue.removeAt(fromIndex)
            val adjustedTarget = if (toIndex > fromIndex) toIndex - 1 else toIndex
            val safeTarget = adjustedTarget.coerceIn(0, queue.size)
            queue.add(safeTarget, item)

            if (activeIndex == fromIndex) {
                activeIndex = safeTarget
            } else if (fromIndex < activeIndex && safeTarget >= activeIndex) {
                activeIndex--
            } else if (fromIndex > activeIndex && safeTarget <= activeIndex) {
                activeIndex++
            }
        }
    }

    fun clearQueue() {
        queue.clear()
        activeIndex = -1
        playedIndices.clear()
        playbackHistory.clear()
        pendingFile = null
        transitionState = TransitionState.IDLE
    }

    fun startTransitionTo(file: File, mixer: Mixer, withDipToBlack: Boolean = true) {
        if (!withDipToBlack || transitionDurationSec <= 0.05f) {
            transitionState = TransitionState.IDLE
            pendingFile = null
            PresetManager.loadDeckPresetAsync(file, isDeckA = false, isDeckBG = true)
            return
        }
        pendingFile = file
        initialAlpha = mixer.deckBG.source.globalAlpha.baseValue.coerceAtLeast(0.01f)
        transitionProgress = 0f
        transitionState = TransitionState.FADING_OUT
    }

    fun playIndex(index: Int, mixer: Mixer, withDipToBlack: Boolean = true) {
        if (index in queue.indices) {
            if (!handleDirtyDeck(mixer)) return
            activeIndex = index
            timeOnCurrentPresetSec = 0f
            val file = queue[index]
            if (isShuffleEnabled) {
                playedIndices.add(index)
                playbackHistory.add(index)
            }
            startTransitionTo(file, mixer, withDipToBlack)
        }
    }

    fun triggerNext(mixer: Mixer) {
        if (queue.isEmpty()) return

        if (isShuffleEnabled) {
            if (playedIndices.size >= queue.size) {
                if (isRepeatEnabled) {
                    playedIndices.clear()
                } else {
                    return
                }
            }
            val unplayed = queue.indices.filter { it !in playedIndices }
            if (unplayed.isNotEmpty()) {
                val nextIdx = unplayed.random()
                playIndex(nextIdx, mixer)
            }
            return
        }

        val nextIndex = activeIndex + 1
        if (nextIndex < queue.size) {
            playIndex(nextIndex, mixer)
        } else if (isRepeatEnabled && queue.isNotEmpty()) {
            playIndex(0, mixer)
        }
    }

    fun triggerPrevious(mixer: Mixer) {
        if (queue.isEmpty()) return

        if (isShuffleEnabled) {
            if (playbackHistory.size > 1) {
                if (!handleDirtyDeck(mixer)) return
                playbackHistory.removeAt(playbackHistory.size - 1)
                val prevIdx = playbackHistory.last()
                activeIndex = prevIdx
                timeOnCurrentPresetSec = 0f
                startTransitionTo(queue[prevIdx], mixer)
            }
            return
        }

        val prevIndex = activeIndex - 1
        if (prevIndex >= 0) {
            playIndex(prevIndex, mixer)
        } else if (isRepeatEnabled && queue.isNotEmpty()) {
            playIndex(queue.size - 1, mixer)
        }
    }

    /**
     * Handles a dirty target Deck BG according to the configured AutoVJ dirty behavior.
     * @return true if the queue advance should proceed, false if it should be skipped.
     */
    private fun handleDirtyDeck(mixer: Mixer): Boolean {
        if (!PresetManager.isDeckDirty(mixer.deckBG, mixer)) return true
        return when (UITheme.autoVjDirtyBehavior) {
            UITheme.AutoVjDirtyBehavior.SKIP -> {
                logger.info { "AutoBG: Skipping because Deck BG is dirty" }
                false
            }
            UITheme.AutoVjDirtyBehavior.AUTO_SAVE -> {
                val activeName = PresetManager.activePresetBG
                val saveName = activeName ?: "AutoBG_BG_${System.currentTimeMillis()}"
                logger.info { "AutoBG: Autosaving dirty deck to $saveName" }
                PresetManager.saveDeckPresetAsync(File("library/presets/$saveName.lsd"), mixer.deckBG, saveName)
                true
            }
            UITheme.AutoVjDirtyBehavior.AUTO_DISCARD -> {
                logger.info { "AutoBG: Discarding changes on dirty Deck BG" }
                true
            }
        }
    }

    fun update(mixer: Mixer, deltaTimeSec: Float) {
        val halfDuration = (transitionDurationSec * 0.5f).coerceAtLeast(0.05f)

        when (transitionState) {
            TransitionState.FADING_OUT -> {
                transitionProgress += deltaTimeSec / halfDuration
                if (transitionProgress >= 1f) {
                    transitionProgress = 1f
                    mixer.deckBG.source.globalAlpha.baseValue = 0f
                    val file = pendingFile
                    if (file != null) {
                        PresetManager.loadDeckPresetAsync(file, isDeckA = false, isDeckBG = true)
                    }
                    pendingFile = null
                    transitionProgress = 0f
                    transitionState = TransitionState.FADING_IN
                } else {
                    mixer.deckBG.source.globalAlpha.baseValue = initialAlpha * (1f - transitionProgress)
                }
            }
            TransitionState.FADING_IN -> {
                transitionProgress += deltaTimeSec / halfDuration
                if (transitionProgress >= 1f) {
                    transitionProgress = 1f
                    mixer.deckBG.source.globalAlpha.baseValue = initialAlpha
                    transitionState = TransitionState.IDLE
                } else {
                    mixer.deckBG.source.globalAlpha.baseValue = initialAlpha * transitionProgress
                }
            }
            TransitionState.IDLE -> {
                if (isAutoBGEnabled && queue.isNotEmpty() && activeIndex >= 0) {
                    timeOnCurrentPresetSec += deltaTimeSec
                    if (timeOnCurrentPresetSec >= holdDurationSec) {
                        timeOnCurrentPresetSec = 0f
                        triggerNext(mixer)
                    }
                }
            }
        }
    }

    fun restoreSessionQueue(
        files: List<File>,
        savedActiveIndex: Int,
        autoBG: Boolean,
        repeat: Boolean,
        shuffle: Boolean
    ) {
        queue.clear()
        queue.addAll(files)
        activeIndex = if (savedActiveIndex in files.indices) savedActiveIndex else if (files.isNotEmpty()) 0 else -1
        isAutoBGEnabled = autoBG
        isRepeatEnabled = repeat
        isShuffleEnabled = shuffle
        if (shuffle) {
            initializeShuffle()
        }
    }
}
