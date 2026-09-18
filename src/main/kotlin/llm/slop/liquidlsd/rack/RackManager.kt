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

    /**
     * Last known per-unit [MacroBank] for each of the built-in unit slots (see the `*_UNIT_ID`
     * constants below), keyed by [RackUnit.id]. Seeded from disk by
     * [llm.slop.liquidlsd.presets.SessionSerializer.loadSession] before the first
     * [populateFromSession] call (units are constructed lazily on first draw -- see
     * [llm.slop.liquidlsd.rack.ui.RackPanel]), and refreshed from the live units on every
     * subsequent [populateFromSession] call (e.g. a "RE-SYNC SESSION" click) so a rebuild never
     * silently discards macro curation the user did this session.
     * [llm.slop.liquidlsd.presets.SessionSerializer.saveSession] reads this back out (falling back
     * to it when [units] is empty, e.g. the Rack workspace was never opened this run) to persist
     * curation across restarts.
     */
    var persistedUnitMacroBanks: Map<String, MacroBank> = emptyMap()

    init {
        MacroEngine.unitParameterResolver = { unitId, paramId ->
            findUnit(unitId)?.findParameter(paramId)
        }
    }

    fun findUnit(id: String): RackUnit? = units.find { it.id == id }

    /**
     * Initializes or synchronizes the rack units from the current active [Mixer] session.
     *
     * Each built-in unit slot uses a fixed, stable id (the `*_UNIT_ID` constants) rather than a
     * randomly generated one specifically so [persistedUnitMacroBanks] entries loaded from a
     * previous session -- or captured live from the outgoing units right below -- can be matched
     * back up to the correct rebuilt unit. When a slot has no persisted bank (its very first
     * appearance, e.g. a brand new session or a format that predates this field), it falls back to
     * the original hardcoded default curated bindings.
     */
    fun populateFromSession(mixer: Mixer) {
        // Snapshot the outgoing units' live macro banks (e.g. curation done since the last
        // populate) before wiping them, so a RE-SYNC SESSION click doesn't discard it. Live state
        // takes priority over whatever was last loaded/persisted for the same unit id.
        if (units.isNotEmpty()) {
            persistedUnitMacroBanks = persistedUnitMacroBanks + units.associate { it.id to it.macroBank }
        }

        // Unregister existing unit banks and clear patch cables
        units.forEach { MacroEngine.unregisterBank(it.id) }
        units.clear()
        patchBay.clearAll()

        // 1. Deck A: one merged generator+FX unit (§2.7)
        val deckA = DeckRackUnit(mixer.deckA, label = "Deck A", id = DECK_A_UNIT_ID, macroBank = restoredBankFor(DECK_A_UNIT_ID))
        if (!hasPersistedBank(DECK_A_UNIT_ID)) {
            setupCuratedBinding(deckA.macroBank.knobs[0], deckA.id, "ZOOM", "viewZoom", 0.2f, 3.0f, 0.5f)
            setupCuratedBinding(deckA.macroBank.knobs[1], deckA.id, "ROTATE", "viewRotateZ", -3.14f, 3.14f, 0.5f)
        }
        addUnit(deckA)

        // 2. Deck B: one merged generator+FX unit (§2.7)
        val deckB = DeckRackUnit(mixer.deckB, label = "Deck B", id = DECK_B_UNIT_ID, macroBank = restoredBankFor(DECK_B_UNIT_ID))
        if (!hasPersistedBank(DECK_B_UNIT_ID)) {
            setupCuratedBinding(deckB.macroBank.knobs[0], deckB.id, "ZOOM", "viewZoom", 0.2f, 3.0f, 0.5f)
            setupCuratedBinding(deckB.macroBank.knobs[1], deckB.id, "ROTATE", "viewRotateZ", -3.14f, 3.14f, 0.5f)
        }
        addUnit(deckB)

        // 3. Deck BG: one merged generator+FX unit (§2.7 / Question 2) -- Deck PV is intentionally excluded
        val deckBG = DeckRackUnit(mixer.deckBG, label = "Deck BG", id = DECK_BG_UNIT_ID, macroBank = restoredBankFor(DECK_BG_UNIT_ID))
        if (!hasPersistedBank(DECK_BG_UNIT_ID)) {
            setupCuratedBinding(deckBG.macroBank.knobs[0], deckBG.id, "ZOOM", "viewZoom", 0.2f, 3.0f, 0.5f)
            setupCuratedBinding(deckBG.macroBank.knobs[1], deckBG.id, "ROTATE", "viewRotateZ", -3.14f, 3.14f, 0.5f)
        }
        addUnit(deckBG)

        // 4. Master Mixer & Transition Unit
        val transUnit = MixerTransitionUnit(mixer, label = "Master Crossfade & Color", id = MASTER_TRANSITION_UNIT_ID, macroBank = restoredBankFor(MASTER_TRANSITION_UNIT_ID))
        if (!hasPersistedBank(MASTER_TRANSITION_UNIT_ID)) {
            setupCuratedBinding(transUnit.macroBank.knobs[0], transUnit.id, "XFADE", "crossfade", -1.0f, 1.0f, 0.0f)
            setupCuratedBinding(transUnit.macroBank.knobs[1], transUnit.id, "BLOOM", "bloom", 0.0f, 1.0f, 0.0f)
            setupCuratedBinding(transUnit.macroBank.switches[0], transUnit.id, "ALPHA", "masterAlpha", 0.0f, 1.0f, 1.0f)
        }
        addUnit(transUnit)

        // 5. Deck PV: the audition/preview deck. Placed last -- it deliberately never feeds the
        // live composite (see DeckRackUnit doc comment), and RackPipeline's unit chain is a pure
        // monitoring/patch-cable construct for built-in units (it doesn't drive real output), so
        // trailing position is enough to convey "doesn't feed forward" without any pipeline
        // special-casing. Still a full generator+FX unit otherwise -- PV is where a performer
        // dials in a preset's own macro bindings before ever loading it onto a live deck.
        val deckPV = DeckRackUnit(mixer.deckPV, label = "Deck PV", id = DECK_PV_UNIT_ID, macroBank = restoredBankFor(DECK_PV_UNIT_ID))
        if (!hasPersistedBank(DECK_PV_UNIT_ID)) {
            setupCuratedBinding(deckPV.macroBank.knobs[0], deckPV.id, "ZOOM", "viewZoom", 0.2f, 3.0f, 0.5f)
            setupCuratedBinding(deckPV.macroBank.knobs[1], deckPV.id, "ROTATE", "viewRotateZ", -3.14f, 3.14f, 0.5f)
        }
        addUnit(deckPV)

        logger.info { "Populated RackManager with ${units.size} units from active session" }
    }

    private fun hasPersistedBank(unitId: String): Boolean = persistedUnitMacroBanks.containsKey(unitId)

    private fun restoredBankFor(unitId: String): MacroBank = persistedUnitMacroBanks[unitId] ?: MacroBank()

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
        // Fixed, stable ids for the built-in unit slots [populateFromSession] always (re)creates.
        // Deliberately not random -- see [persistedUnitMacroBanks]/[populateFromSession] doc
        // comments for why a stable id is required to round-trip per-unit macro curation.
        const val DECK_A_UNIT_ID = "deckA"
        const val DECK_B_UNIT_ID = "deckB"
        const val DECK_BG_UNIT_ID = "deckBG"
        const val DECK_PV_UNIT_ID = "deckPV"
        const val MASTER_TRANSITION_UNIT_ID = "masterTransition"
    }
}
