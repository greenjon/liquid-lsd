package llm.slop.liquidlsd.presets

import llm.slop.liquidlsd.SessionContext
import llm.slop.liquidlsd.rendering.Deck
import llm.slop.liquidlsd.rendering.Mixer
import llm.slop.liquidlsd.ui.FileSystemManager
import java.io.File

/**
 * [QueueEngine] specialization for the FX queues (Deck A/B and Deck BG): resolves `.lsdfxplay`
 * playlists against the FX preset/chain roots. FX changes don't touch the deck preset, so
 * there is no dirty-deck guard here.
 */
abstract class FxQueueEngine(queueLabel: String) : QueueEngine(queueLabel) {

    override fun playlistItemExtension(): String = "lsdfxplay"

    override fun playlistRoots(playlistFile: File): List<File> = listOfNotNull(
        playlistFile.parentFile,
        FileSystemManager.getFxPresetsRoot(),
        FileSystemManager.getFxChainsRoot()
    )

    /** Resolves the deck an advance/jump should apply to for the current mixer state. */
    abstract fun getTargetDeck(mixer: Mixer): Deck

    fun jumpToIndex(index: Int, session: SessionContext, mixer: Mixer, explicitTargetDeck: Deck? = null) {
        if (index !in queue.indices) return
        val targetDeck = explicitTargetDeck ?: getTargetDeck(mixer)

        if (isShuffleEnabled) {
            if (activeIndex in queue.indices) {
                playbackHistory.add(activeIndex)
            }
            playedIndices.add(index)
        }
        activeIndex = index
        val file = queue[activeIndex]
        logger.info { "Jumping to $queueLabel item at index $activeIndex: ${file.name}" }
        FxOps.applyItem(session, file, targetDeck.fxChain)
    }

    fun advanceNext(session: SessionContext, mixer: Mixer, explicitTargetDeck: Deck? = null) {
        val nextIndex = computeNextIndex() ?: return
        val targetDeck = explicitTargetDeck ?: getTargetDeck(mixer)

        activeIndex = nextIndex
        val file = queue[activeIndex]
        logger.info { "Advancing $queueLabel to index $activeIndex: ${file.name}" }
        FxOps.applyItem(session, file, targetDeck.fxChain)
    }

    fun advancePrevious(session: SessionContext, mixer: Mixer, explicitTargetDeck: Deck? = null) {
        val prevIndex = computePrevIndex() ?: return
        val targetDeck = explicitTargetDeck ?: getTargetDeck(mixer)

        activeIndex = prevIndex
        val file = queue[activeIndex]
        logger.info { "Stepping back $queueLabel to index $activeIndex: ${file.name}" }
        FxOps.applyItem(session, file, targetDeck.fxChain)
    }
}
