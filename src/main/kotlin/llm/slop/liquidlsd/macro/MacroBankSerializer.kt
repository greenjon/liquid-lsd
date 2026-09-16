package llm.slop.liquidlsd.macro

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import llm.slop.liquidlsd.parameters.ParameterResolver
import llm.slop.liquidlsd.rendering.Mixer
import mu.KotlinLogging
import java.io.File

/**
 * Handles serialization, deck-scoped filtering/restoration, and standalone export/import
 * of [MacroBank] instances.
 *
 * Implements proposal §5.2:
 * 1. Primary/bundled preset serialization: deck-specific bindings are bundled in [llm.slop.liquidlsd.models.DeckPresetDto]
 *    and the full global bank is bundled in [llm.slop.liquidlsd.models.SessionStateDto].
 * 2. Standalone export/import (.knobpreset.json): reads and writes isolated MacroBank JSON fragments
 *    with graceful skipping for missing parameters.
 */
object MacroBankSerializer {
    private val logger = KotlinLogging.logger {}

    val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /**
     * Extracts a deck-scoped [MacroBank] snapshot containing only bindings that target
     * parameters under [deckLabel] (e.g. "Deck A/fbZoom").
     * Returns null if no bindings target this deck.
     */
    fun filterMacroBankForDeck(globalBank: MacroBank, deckLabel: String): MacroBank? {
        val prefix = "$deckLabel/"
        var hasAnyBindings = false

        val filteredKnobs = globalBank.knobs.map { knob ->
            val matching = knob.bindings.filter { it.parameterId.startsWith(prefix) }
            if (matching.isNotEmpty()) hasAnyBindings = true
            knob.copy(bindings = matching.map { it.copy() }.toMutableList())
        }

        val filteredSwitches = globalBank.switches.map { switch ->
            val matching = switch.bindings.filter { it.parameterId.startsWith(prefix) }
            if (matching.isNotEmpty()) hasAnyBindings = true
            switch.copy(bindings = matching.map { it.copy() }.toMutableList())
        }

        return if (hasAnyBindings) MacroBank(knobs = filteredKnobs, switches = filteredSwitches) else null
    }

    /**
     * Merges a deck-scoped [MacroBank] into the active global bank, remapping parameter prefixes
     * to [targetDeckLabel] (e.g. if loaded onto "Deck B", "Deck A/fbZoom" becomes "Deck B/fbZoom").
     */
    fun restoreMacroBankForDeck(
        deckBank: MacroBank?,
        targetDeckLabel: String,
        sourceDeckLabel: String? = null
    ) {
        if (deckBank == null) return
        val globalBank = MacroEngine.globalBank()

        fun remapParamId(originalId: String): String {
            return if (sourceDeckLabel != null && originalId.startsWith("$sourceDeckLabel/")) {
                "$targetDeckLabel/" + originalId.removePrefix("$sourceDeckLabel/")
            } else if (!originalId.startsWith("$targetDeckLabel/")) {
                val slashIdx = originalId.indexOf('/')
                if (slashIdx > 0) "$targetDeckLabel/" + originalId.substring(slashIdx + 1)
                else originalId
            } else {
                originalId
            }
        }

        // Merge knob bindings
        deckBank.knobs.forEachIndexed { i, srcKnob ->
            val destKnob = globalBank.knobs.getOrNull(i) ?: return@forEachIndexed
            if (destKnob.label.isEmpty() || destKnob.label.startsWith("KNOB ")) {
                if (srcKnob.label.isNotEmpty() && !srcKnob.label.startsWith("KNOB ")) {
                    destKnob.label = srcKnob.label
                }
            }
            for (binding in srcKnob.bindings) {
                val remappedId = remapParamId(binding.parameterId)
                val exists = destKnob.bindings.any {
                    it.parameterId == remappedId &&
                    it.targetType == binding.targetType &&
                    it.modulatorIndex == binding.modulatorIndex &&
                    it.propertyName == binding.propertyName
                }
                if (!exists && destKnob.bindings.size < MacroControl.MAX_BINDINGS_PER_CONTROL) {
                    destKnob.bindings.add(binding.copy(parameterId = remappedId))
                }
            }
        }

        // Merge switch bindings
        deckBank.switches.forEachIndexed { i, srcSwitch ->
            val destSwitch = globalBank.switches.getOrNull(i) ?: return@forEachIndexed
            if (destSwitch.label.isEmpty() || destSwitch.label.startsWith("SW ")) {
                if (srcSwitch.label.isNotEmpty() && !srcSwitch.label.startsWith("SW ")) {
                    destSwitch.label = srcSwitch.label
                }
            }
            destSwitch.switchBehavior = srcSwitch.switchBehavior
            for (binding in srcSwitch.bindings) {
                val remappedId = remapParamId(binding.parameterId)
                val exists = destSwitch.bindings.any {
                    it.parameterId == remappedId &&
                    it.targetType == binding.targetType &&
                    it.modulatorIndex == binding.modulatorIndex &&
                    it.propertyName == binding.propertyName
                }
                if (!exists && destSwitch.bindings.size < MacroControl.MAX_BINDINGS_PER_CONTROL) {
                    destSwitch.bindings.add(binding.copy(parameterId = remappedId))
                }
            }
        }

        MacroEngine.invalidate()
    }

    /** Exports [bank] to a standalone JSON file. */
    fun exportToFile(file: File, bank: MacroBank = MacroEngine.globalBank()) {
        file.parentFile?.mkdirs()
        val content = json.encodeToString(bank)
        file.writeText(content)
        logger.info { "Exported MacroBank to ${file.absolutePath}" }
    }

    /**
     * Imports a [MacroBank] from a standalone JSON file.
     * When [mixer] is supplied, bindings referencing missing parameters are safely skipped.
     */
    fun importFromFile(file: File, mixer: Mixer? = null): Pair<MacroBank, Int> {
        val content = file.readText()
        val rawBank = json.decodeFromString<MacroBank>(content)
        if (mixer == null) return rawBank to 0

        var skippedCount = 0

        fun filterValid(bindings: List<MacroBinding>): MutableList<MacroBinding> {
            val valid = mutableListOf<MacroBinding>()
            for (b in bindings) {
                val param = ParameterResolver.findParameterByPath(mixer, b.parameterId)
                if (param != null) {
                    valid.add(b)
                } else {
                    skippedCount++
                    logger.warn { "Skipping macro binding to missing parameter: ${b.parameterId}" }
                }
            }
            return valid
        }

        val sanitizedKnobs = rawBank.knobs.map { knob ->
            knob.copy(bindings = filterValid(knob.bindings))
        }
        val sanitizedSwitches = rawBank.switches.map { switch ->
            switch.copy(bindings = filterValid(switch.bindings))
        }

        return MacroBank(knobs = sanitizedKnobs, switches = sanitizedSwitches) to skippedCount
    }
}
