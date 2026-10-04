package llm.slop.liquidlsd.macro

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import llm.slop.liquidlsd.parameters.ParameterResolver
import llm.slop.liquidlsd.rendering.Mixer
import mu.KotlinLogging
import java.io.File

/**
 * Handles serialization, per-deck snapshot/install, and standalone export/import
 * of [MacroBank] instances.
 *
 * 1. Primary/bundled preset serialization: a deck's own resident [MacroEngine] bank
 *    (see [MacroEngine.CANONICAL_BANK_IDS]) is bundled into [llm.slop.liquidlsd.models.DeckPresetDto]
 *    wholesale on save, and *replaces* the target deck's resident bank wholesale on load --
 *    loading a preset is meant to swap in exactly the knob layout it was saved with, not merge
 *    on top of whatever the deck's previous preset left behind.
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

    private val NON_DECK_ROOTS = setOf("Mixer", "Master", "Global", "Macro")

    /**
     * Rewrites the leading segment of a deck-scoped path ("Deck A/...", or the generic "Deck/..." of
     * generator defaults) to [targetDeckLabel]. Paths rooted outside any deck (`Mixer/...`,
     * `Master/FX/...`) are returned unchanged.
     */
    fun remapDeckPath(path: String, targetDeckLabel: String): String {
        val slashIdx = path.indexOf('/')
        if (slashIdx <= 0 || path.substring(0, slashIdx) in NON_DECK_ROOTS) return path
        return "$targetDeckLabel/" + path.substring(slashIdx + 1)
    }

    /** Deep-copies [bank] for bundling into a preset file so the saved snapshot is immutable. */
    fun snapshotForPreset(bank: MacroBank): MacroBank = MacroBank(
        knobs = bank.knobs.map { it.copy(bindings = it.bindings.map { b -> b.copy() }.toMutableList()) }
    )

    /**
     * Installs [deckBank] (as bundled in a [llm.slop.liquidlsd.models.DeckPresetDto]) into
     * [targetBank] wholesale, replacing every existing label/binding on it -- a full swap, not a
     * merge. A null/empty [deckBank] (e.g. an older preset saved before this field existed, or an
     * empty deck slot) clears [targetBank] to blank rather than leaving stale bindings behind.
     * Remaps each binding's parameterId's leading path segment to [targetDeckLabel] (e.g. a preset
     * saved from "Deck A" loaded onto Deck B gets "Deck A/fbZoom" rewritten to "Deck B/fbZoom") so
     * bundled bindings always target whichever deck slot the preset actually lands on.
     */
    fun installBankForDeck(deckBank: MacroBank?, targetBank: MacroBank, targetDeckLabel: String) {
        fun remapParamId(originalId: String) = remapDeckPath(originalId, targetDeckLabel)

        if (deckBank != null && deckBank.knobs.size > targetBank.knobs.size) {
            logger.warn {
                "Loading a ${deckBank.knobs.size}-knob macro bank onto $targetDeckLabel's " +
                    "${targetBank.knobs.size}-knob bank -- knobs ${targetBank.knobs.size + 1}-${deckBank.knobs.size} will be dropped."
            }
        }

        for (i in targetBank.knobs.indices) {
            val destKnob = targetBank.knobs[i]
            val srcKnob = deckBank?.knobs?.getOrNull(i)
            destKnob.label = srcKnob?.label ?: ""
            destKnob.value = srcKnob?.value ?: 0f
            destKnob.bindings.clear()
            srcKnob?.bindings?.take(MacroControl.MAX_BINDINGS_PER_CONTROL)?.forEach { destKnob.bindings.add(it.copy(parameterId = remapParamId(it.parameterId))) }
        }

        MacroEngine.noteBankReplaced()
        MacroEngine.invalidate()
    }

    /**
     * Convenience wrapper around [installBankForDeck] for the common preset-load case: looks up
     * (auto-registering if missing) the canonical resident bank for [canonicalBankId] and installs
     * [deckBank] into it. Always installs -- including a null [deckBank] -- so loading a preset
     * with no bundled macro bank still clears the deck's previous knob layout rather than leaving
     * it stale.
     */
    fun installPresetBank(canonicalBankId: String, deckBank: MacroBank?, targetDeckLabel: String) {
        val targetBank = MacroEngine.getBank(canonicalBankId) ?: MacroEngine.newBankFor(canonicalBankId).also { MacroEngine.registerBank(canonicalBankId, it) }
        installBankForDeck(deckBank, targetBank, targetDeckLabel)
    }

    /**
     * Imports [file] into the resident bank [target] (id [bankId]) and returns how many bindings were skipped.
     * Deck banks retarget bindings to their own deck ([deckLabel], like a preset load); others (Master, ...) keep
     * them as saved but drop targets the bank can't take (as Learn would).
     */
    fun applyFileToBank(file: File, bankId: String, target: MacroBank, deckLabel: String?, mixer: Mixer): Int {
        val (imported, skipped) = importFromFile(file, mixer, deckLabel)
        if (deckLabel != null) {
            installBankForDeck(imported, target, deckLabel)
            return skipped
        }
        for (i in target.knobs.indices) {
            val dest = target.knobs[i]
            val src = imported.knobs.getOrNull(i)
            dest.label = src?.label ?: ""
            dest.value = src?.value ?: 0f
            dest.bindings.clear()
            src?.bindings?.filter { MacroLearnState.acceptsTarget(bankId, it.parameterId) }
                ?.take(MacroControl.MAX_BINDINGS_PER_CONTROL)?.forEach { dest.bindings.add(it.copy()) }
        }
        MacroEngine.invalidate()
        return skipped
    }

    /** Exports [bank] to a standalone JSON file. */
    fun exportToFile(file: File, bank: MacroBank) {
        file.parentFile?.mkdirs()
        val content = json.encodeToString(bank)
        file.writeText(content)
        logger.info { "Exported MacroBank to ${file.absolutePath}" }
    }

    /**
     * Imports a [MacroBank] from a standalone JSON file.
     * When [mixer] is supplied, bindings referencing missing parameters are safely skipped.
     * With [targetDeckLabel], deck paths are remapped to that deck first, so validation checks the
     * parameters of the deck the bindings will actually land on (not the deck they were saved from).
     */
    fun importFromFile(file: File, mixer: Mixer? = null, targetDeckLabel: String? = null): Pair<MacroBank, Int> {
        val content = file.readText()
        val rawBank = json.decodeFromString<MacroBank>(content)
        if (mixer == null) return rawBank to 0

        var skippedCount = 0

        fun filterValid(bindings: List<MacroBinding>): MutableList<MacroBinding> {
            val valid = mutableListOf<MacroBinding>()
            for (original in bindings) {
                val b = if (targetDeckLabel != null) original.copy(parameterId = remapDeckPath(original.parameterId, targetDeckLabel)) else original
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

        return MacroBank(knobs = sanitizedKnobs) to skippedCount
    }
}
