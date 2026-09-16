package llm.slop.liquidlsd.rack

import llm.slop.liquidlsd.macro.MacroBinding
import llm.slop.liquidlsd.macro.MacroControl
import llm.slop.liquidlsd.macro.MacroEngine
import llm.slop.liquidlsd.macro.MacroTargetType
import llm.slop.liquidlsd.rendering.Mixer
import llm.slop.liquidlsd.rendering.Renderer
import mu.KotlinLogging

/**
 * Central manager governing the Modular Video Rack bay state, unit lifecycle,
 * reordering, per-unit macro scoping, and bridging with the core rendering engine.
 */
class RackManager(
    val allocateGlBuffers: Boolean = true
) {
    private val logger = KotlinLogging.logger {}

    val units = mutableListOf<RackUnit>()
    val pipeline = RackPipeline(allocateGlBuffers = allocateGlBuffers)

    var isMasterBypassed: Boolean = false
    var isMasterFolded: Boolean = false

    init {
        MacroEngine.unitParameterResolver = { unitId, paramId ->
            findUnit(unitId)?.findParameter(paramId)
        }
    }

    fun findUnit(id: String): RackUnit? = units.find { it.id == id }

    /**
     * Initializes or synchronizes the rack units from the current active [Mixer] session.
     */
    fun populateFromSession(mixer: Mixer) {
        // Unregister existing unit banks
        units.forEach { MacroEngine.unregisterBank(it.id) }
        units.clear()

        // 1. Deck A Generator Unit
        val deckAGen = DeckGeneratorUnit(mixer.deckA, isDeckA = true, label = "Deck A Synth")
        setupCuratedBinding(deckAGen.macroBank.knobs[0], deckAGen.id, "ZOOM", "viewZoom", 0.2f, 3.0f, 0.5f)
        setupCuratedBinding(deckAGen.macroBank.knobs[1], deckAGen.id, "ROTATE", "viewRotateZ", -3.14f, 3.14f, 0.5f)
        addUnit(deckAGen)

        // 2. Deck A Feedback Unit
        val deckAFb = FeedbackProcessorUnit(mixer.deckA, isDeckA = true, label = "Deck A Feedback Loop")
        setupCuratedBinding(deckAFb.macroBank.knobs[0], deckAFb.id, "GAIN", "fbGain", 0.0f, 2.0f, 0.5f)
        setupCuratedBinding(deckAFb.macroBank.knobs[1], deckAFb.id, "DECAY", "fbDecay", 0.0f, 1.0f, 0.0f)
        setupCuratedBinding(deckAFb.macroBank.knobs[2], deckAFb.id, "ZOOM", "fbZoom", -0.5f, 0.5f, 0.5f)
        setupCuratedBinding(deckAFb.macroBank.knobs[3], deckAFb.id, "HUE", "fbHueShift", -1.0f, 1.0f, 0.5f)
        addUnit(deckAFb)

        // 3. Deck A active ISF FX slots
        for (i in mixer.deckA.fxSlots.indices) {
            val fx = mixer.deckA.fxSlots[i]
            if (fx != null) {
                val isfUnit = ISFProcessorUnit(fx, slotIndex = i, label = "Deck A FX ${i + 1}: ${fx.displayName}")
                setupCuratedBinding(isfUnit.macroBank.knobs[0], isfUnit.id, "DRY/WET", "dryWet", 0.0f, 1.0f, fx.dryWet.baseValue)
                addUnit(isfUnit)
            }
        }

        // 4. Deck B Generator Unit
        val deckBGen = DeckGeneratorUnit(mixer.deckB, isDeckA = false, label = "Deck B Synth")
        setupCuratedBinding(deckBGen.macroBank.knobs[0], deckBGen.id, "ZOOM", "viewZoom", 0.2f, 3.0f, 0.5f)
        setupCuratedBinding(deckBGen.macroBank.knobs[1], deckBGen.id, "ROTATE", "viewRotateZ", -3.14f, 3.14f, 0.5f)
        addUnit(deckBGen)

        // 5. Deck B Feedback Unit
        val deckBFb = FeedbackProcessorUnit(mixer.deckB, isDeckA = false, label = "Deck B Feedback Loop")
        setupCuratedBinding(deckBFb.macroBank.knobs[0], deckBFb.id, "GAIN", "fbGain", 0.0f, 2.0f, 0.5f)
        setupCuratedBinding(deckBFb.macroBank.knobs[1], deckBFb.id, "DECAY", "fbDecay", 0.0f, 1.0f, 0.0f)
        setupCuratedBinding(deckBFb.macroBank.knobs[2], deckBFb.id, "ZOOM", "fbZoom", -0.5f, 0.5f, 0.5f)
        setupCuratedBinding(deckBFb.macroBank.knobs[3], deckBFb.id, "HUE", "fbHueShift", -1.0f, 1.0f, 0.5f)
        addUnit(deckBFb)

        // 6. Deck B active ISF FX slots
        for (i in mixer.deckB.fxSlots.indices) {
            val fx = mixer.deckB.fxSlots[i]
            if (fx != null) {
                val isfUnit = ISFProcessorUnit(fx, slotIndex = i, label = "Deck B FX ${i + 1}: ${fx.displayName}")
                setupCuratedBinding(isfUnit.macroBank.knobs[0], isfUnit.id, "DRY/WET", "dryWet", 0.0f, 1.0f, fx.dryWet.baseValue)
                addUnit(isfUnit)
            }
        }

        // 7. Master Mixer & Transition Unit
        val transUnit = MixerTransitionUnit(mixer, label = "Master Crossfade & Color")
        setupCuratedBinding(transUnit.macroBank.knobs[0], transUnit.id, "XFADE", "crossfade", -1.0f, 1.0f, 0.0f)
        setupCuratedBinding(transUnit.macroBank.knobs[1], transUnit.id, "BLOOM", "bloom", 0.0f, 1.0f, 0.0f)
        setupCuratedBinding(transUnit.macroBank.switches[0], transUnit.id, "ALPHA", "masterAlpha", 0.0f, 1.0f, 1.0f)
        addUnit(transUnit)

        logger.info { "Populated RackManager with ${units.size} units from active session" }
    }

    private fun setupCuratedBinding(
        control: MacroControl,
        unitId: String,
        label: String,
        paramId: String,
        min: Float,
        max: Float,
        defaultVal: Float
    ) {
        control.label = label
        control.value = defaultVal
        control.bindings.clear()
        control.bindings.add(
            MacroBinding(
                unitInstanceId = unitId,
                parameterId = paramId,
                targetType = MacroTargetType.PARAM_BASE_VALUE,
                minVal = min,
                maxVal = max
            )
        )
    }

    fun addUnit(unit: RackUnit, atIndex: Int = -1) {
        if (atIndex in 0..units.size) {
            units.add(atIndex, unit)
        } else {
            units.add(unit)
        }
        MacroEngine.registerBank(unit.id, unit.macroBank)
        logger.info { "Added rack unit '${unit.label}' (id=${unit.id}) at index ${units.indexOf(unit)}" }
    }

    fun removeUnit(id: String): Boolean {
        val idx = units.indexOfFirst { it.id == id }
        return if (idx >= 0) {
            val removed = units.removeAt(idx)
            MacroEngine.unregisterBank(id)
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
        units.forEach {
            MacroEngine.unregisterBank(it.id)
            it.dispose()
        }
        units.clear()
    }
}
