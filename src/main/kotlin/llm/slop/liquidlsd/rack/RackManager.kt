package llm.slop.liquidlsd.rack

import llm.slop.liquidlsd.macro.MacroBank
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
    val patchBay = RackPatchBay()

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
     *
     * Each built-in unit slot uses a fixed, stable id (the `*_UNIT_ID` constants, aliased to
     * [MacroEngine]'s canonical bank ids) and is backed by that same canonical [MacroBank] --
     * [MacroEngine.getBank] -- rather than a rack-owned copy. Those banks are always already
     * registered by the time this runs ([llm.slop.liquidlsd.presets.SessionSerializer] registers
     * all five at session load, independent of whether the Rack workspace is ever opened), so the
     * curated-default setup below only fires the very first time a canonical id is seen with no
     * bank registered at all yet (e.g. a bare `RackManager` in a test).
     */
    fun populateFromSession(mixer: Mixer) {
        units.forEach { MacroEngine.unregisterBank(it.id) }
        units.clear()
        patchBay.clearAll()

        // 1. Deck A: one merged generator+FX unit (§2.7)
        addUnit(DeckRackUnit(mixer.deckA, label = "Deck A", id = DECK_A_UNIT_ID, macroBank = residentBank(DECK_A_UNIT_ID) { bank ->
            setupCuratedBinding(bank.knobs[0], DECK_A_UNIT_ID, "ZOOM", "viewZoom", 0.2f, 3.0f, 0.5f)
            setupCuratedBinding(bank.knobs[1], DECK_A_UNIT_ID, "ROTATE", "viewRotateZ", -3.14f, 3.14f, 0.5f)
        }))

        // 2. Deck B: one merged generator+FX unit (§2.7)
        addUnit(DeckRackUnit(mixer.deckB, label = "Deck B", id = DECK_B_UNIT_ID, macroBank = residentBank(DECK_B_UNIT_ID) { bank ->
            setupCuratedBinding(bank.knobs[0], DECK_B_UNIT_ID, "ZOOM", "viewZoom", 0.2f, 3.0f, 0.5f)
            setupCuratedBinding(bank.knobs[1], DECK_B_UNIT_ID, "ROTATE", "viewRotateZ", -3.14f, 3.14f, 0.5f)
        }))

        // 3. Deck BG: one merged generator+FX unit (§2.7 / Question 2)
        addUnit(DeckRackUnit(mixer.deckBG, label = "Deck BG", id = DECK_BG_UNIT_ID, macroBank = residentBank(DECK_BG_UNIT_ID) { bank ->
            setupCuratedBinding(bank.knobs[0], DECK_BG_UNIT_ID, "ZOOM", "viewZoom", 0.2f, 3.0f, 0.5f)
            setupCuratedBinding(bank.knobs[1], DECK_BG_UNIT_ID, "ROTATE", "viewRotateZ", -3.14f, 3.14f, 0.5f)
        }))

        // 4. Master Mixer & Transition Unit. Taller than the default 2U -- it carries the same
        // 4x2 knob / 2x2 button / preview faceplate as every other unit, plus its own
        // crossfader/mode/alpha/bloom row underneath, so it needs the extra vertical room.
        addUnit(MixerTransitionUnit(mixer, label = "Master Crossfade & Color", id = MASTER_TRANSITION_UNIT_ID, heightU = 4, macroBank = residentBank(MASTER_TRANSITION_UNIT_ID) { bank ->
            setupCuratedBinding(bank.knobs[0], MASTER_TRANSITION_UNIT_ID, "XFADE", "crossfade", -1.0f, 1.0f, 0.0f)
            setupCuratedBinding(bank.knobs[1], MASTER_TRANSITION_UNIT_ID, "BLOOM", "bloom", 0.0f, 1.0f, 0.0f)
            setupCuratedBinding(bank.switches[0], MASTER_TRANSITION_UNIT_ID, "ALPHA", "masterAlpha", 0.0f, 1.0f, 1.0f)
        }))

        // 5. Deck PV: the audition/preview deck. Placed last -- it deliberately never feeds the
        // live composite (see DeckRackUnit doc comment), and RackPipeline's unit chain is a pure
        // monitoring/patch-cable construct for built-in units (it doesn't drive real output), so
        // trailing position is enough to convey "doesn't feed forward" without any pipeline
        // special-casing. Still a full generator+FX unit otherwise -- PV is where a performer
        // dials in a preset's own macro bindings before ever loading it onto a live deck.
        addUnit(DeckRackUnit(mixer.deckPV, label = "Deck PV", id = DECK_PV_UNIT_ID, macroBank = residentBank(DECK_PV_UNIT_ID) { bank ->
            setupCuratedBinding(bank.knobs[0], DECK_PV_UNIT_ID, "ZOOM", "viewZoom", 0.2f, 3.0f, 0.5f)
            setupCuratedBinding(bank.knobs[1], DECK_PV_UNIT_ID, "ROTATE", "viewRotateZ", -3.14f, 3.14f, 0.5f)
        }))

        logger.info { "Populated RackManager with ${units.size} units from active session" }
    }

    private fun residentBank(canonicalId: String, applyDefaultsIfNew: (MacroBank) -> Unit): MacroBank {
        MacroEngine.getBank(canonicalId)?.let { return it }
        val fresh = MacroBank()
        applyDefaultsIfNew(fresh)
        MacroEngine.registerBank(canonicalId, fresh)
        return fresh
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
            patchBay.removeUnitConnections(id)
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
        return pipeline.process(units, renderer, patchBay)
    }

    fun resize(width: Int, height: Int) {
        pipeline.resize(width, height)
    }

    fun dispose() {
        pipeline.dispose()
        patchBay.clearAll()
        units.forEach {
            MacroEngine.unregisterBank(it.id)
            it.dispose()
        }
        units.clear()
    }

    companion object {
        // Fixed, stable ids for the built-in unit slots [populateFromSession] always (re)creates --
        // aliased 1:1 to MacroEngine's canonical bank ids, since a rack unit's macroBank *is* that
        // canonical bank (see [residentBank]).
        const val DECK_A_UNIT_ID = MacroEngine.DECK_A
        const val DECK_B_UNIT_ID = MacroEngine.DECK_B
        const val DECK_BG_UNIT_ID = MacroEngine.DECK_BG
        const val DECK_PV_UNIT_ID = MacroEngine.DECK_PV
        const val MASTER_TRANSITION_UNIT_ID = MacroEngine.TRANS
    }
}
