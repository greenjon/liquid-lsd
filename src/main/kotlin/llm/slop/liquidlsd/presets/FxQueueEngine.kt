package llm.slop.liquidlsd.presets

import llm.slop.liquidlsd.SessionContext
import llm.slop.liquidlsd.rendering.Deck
import llm.slop.liquidlsd.rendering.Mixer
import llm.slop.liquidlsd.ui.FileSystemManager
import llm.slop.liquidlsd.ui.UITheme
import java.io.File

/**
 * [QueueEngine] specialization for the FX queues (Deck A/B and Deck BG): resolves `.lsdfxplay`
 * playlists against the FX preset/chain roots, and adds the dirty-deck autosave/skip/discard
 * guard [PlayQueueManager] and [BgQueueManager] already apply before an auto-advance overwrites
 * a deck's FX slots.
 */
abstract class FxQueueEngine(queueLabel: String, private val savePrefix: String) : QueueEngine(queueLabel) {

    override fun playlistItemExtension(): String = "lsdfxplay"

    override fun playlistRoots(playlistFile: File): List<File> = listOfNotNull(
        playlistFile.parentFile,
        FileSystemManager.getFxPresetsRoot(),
        FileSystemManager.getFxChainsRoot()
    )

    /** Resolves the deck an advance/jump should apply to for the current mixer state. */
    abstract fun getTargetDeck(mixer: Mixer): Deck

    /** Short label ("A", "B", "BG") used in dirty-deck autosave names and log messages. */
    protected abstract fun deckLabel(mixer: Mixer): String

    /** The deck's currently active saved preset name, if any; used as the autosave target name. */
    protected abstract fun activePresetName(mixer: Mixer): String?

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
        val nextIndex = computeNextIndex() ?: return
        val targetDeck = getTargetDeck(mixer)
        if (!handleDirtyDeck(targetDeck, mixer)) return

        activeIndex = nextIndex
        val file = queue[activeIndex]
        logger.info { "Advancing $queueLabel to index $activeIndex: ${file.name}" }
        FXItemApplier.apply(session, file, targetDeck)
    }

    fun advancePrevious(session: SessionContext, mixer: Mixer) {
        val prevIndex = computePrevIndex() ?: return
        val targetDeck = getTargetDeck(mixer)
        if (!handleDirtyDeck(targetDeck, mixer)) return

        activeIndex = prevIndex
        val file = queue[activeIndex]
        logger.info { "Stepping back $queueLabel to index $activeIndex: ${file.name}" }
        FXItemApplier.apply(session, file, targetDeck)
    }
}
