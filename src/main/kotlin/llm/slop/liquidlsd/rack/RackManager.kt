package llm.slop.liquidlsd.rack

import llm.slop.liquidlsd.rendering.Mixer
import llm.slop.liquidlsd.rendering.Renderer
import mu.KotlinLogging

/**
 * Central manager governing the Modular Video Rack bay state, unit lifecycle,
 * reordering, and bridging with the core rendering engine.
 */
class RackManager(
    val allocateGlBuffers: Boolean = true
) {
    private val logger = KotlinLogging.logger {}

    val units = mutableListOf<RackUnit>()
    val pipeline = RackPipeline(allocateGlBuffers = allocateGlBuffers)

    var isMasterBypassed: Boolean = false
    var isMasterFolded: Boolean = false

    /**
     * Initializes or synchronizes the rack units from the current active [Mixer] session.
     */
    fun populateFromSession(mixer: Mixer) {
        units.clear()

        // 1. Deck A Generator Unit
        units.add(DeckGeneratorUnit(mixer.deckA, isDeckA = true, label = "Deck A Synth"))

        // 2. Deck A Feedback Unit
        units.add(FeedbackProcessorUnit(mixer.deckA, isDeckA = true, label = "Deck A Feedback Loop"))

        // 3. Deck A active ISF FX slots
        for (i in mixer.deckA.fxSlots.indices) {
            val fx = mixer.deckA.fxSlots[i]
            if (fx != null) {
                units.add(ISFProcessorUnit(fx, slotIndex = i, label = "Deck A FX ${i + 1}: ${fx.displayName}"))
            }
        }

        // 4. Deck B Generator Unit
        units.add(DeckGeneratorUnit(mixer.deckB, isDeckA = false, label = "Deck B Synth"))

        // 5. Deck B Feedback Unit
        units.add(FeedbackProcessorUnit(mixer.deckB, isDeckA = false, label = "Deck B Feedback Loop"))

        // 6. Deck B active ISF FX slots
        for (i in mixer.deckB.fxSlots.indices) {
            val fx = mixer.deckB.fxSlots[i]
            if (fx != null) {
                units.add(ISFProcessorUnit(fx, slotIndex = i, label = "Deck B FX ${i + 1}: ${fx.displayName}"))
            }
        }

        // 7. Master Mixer & Transition Unit
        units.add(MixerTransitionUnit(mixer, label = "Master Crossfade & Color"))

        logger.info { "Populated RackManager with ${units.size} units from active session" }
    }

    fun addUnit(unit: RackUnit, atIndex: Int = -1) {
        if (atIndex in 0..units.size) {
            units.add(atIndex, unit)
        } else {
            units.add(unit)
        }
        logger.info { "Added rack unit '${unit.label}' (id=${unit.id}) at index ${units.indexOf(unit)}" }
    }

    fun removeUnit(id: String): Boolean {
        val idx = units.indexOfFirst { it.id == id }
        return if (idx >= 0) {
            val removed = units.removeAt(idx)
            removed.dispose()
            logger.info { "Removed rack unit '${removed.label}' (id=$id)" }
            true
        } else {
            false
        }
    }

    fun moveUnit(fromIndex: Int, toIndex: Int): Boolean {
        if (fromIndex !in units.indices || toIndex !in units.indices || fromIndex == toIndex) {
            return false
        }
        val unit = units.removeAt(fromIndex)
        units.add(toIndex, unit)
        return true
    }

    fun moveUp(index: Int): Boolean {
        if (index > 0 && index in units.indices) {
            return moveUnit(index, index - 1)
        }
        return false
    }

    fun moveDown(index: Int): Boolean {
        if (index >= 0 && index < units.size - 1) {
            return moveUnit(index, index + 1)
        }
        return false
    }

    fun toggleFoldAll() {
        isMasterFolded = !isMasterFolded
        units.forEach { it.isCollapsed = isMasterFolded }
    }

    fun toggleMasterBypass() {
        isMasterBypassed = !isMasterBypassed
        units.forEach { it.isBypassed = isMasterBypassed }
    }

    fun clearAllSolo() {
        units.forEach { it.isSoloed = false }
    }

    fun update() {
        for (i in units.indices) {
            units[i].update()
        }
    }

    fun process(renderer: Renderer?): Int {
        return pipeline.process(units, renderer)
    }

    fun resize(width: Int, height: Int) {
        pipeline.resize(width, height)
    }

    fun dispose() {
        pipeline.dispose()
        units.forEach { it.dispose() }
        units.clear()
    }
}
