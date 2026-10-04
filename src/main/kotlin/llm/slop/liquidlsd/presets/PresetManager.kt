package llm.slop.liquidlsd.presets

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import llm.slop.liquidlsd.models.*
import llm.slop.liquidlsd.notes.NotesManager
import llm.slop.liquidlsd.rendering.Deck
import llm.slop.liquidlsd.rendering.Mixer
import llm.slop.liquidlsd.rendering.SourceMeta
import mu.KotlinLogging
import java.io.File
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

object PresetManager {
    private val logger = KotlinLogging.logger {}
    internal val presetIoExecutor: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "PresetManager-IO").apply { isDaemon = true }
    }

    internal val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    internal val LIBRARY_ROOT = File("library").absoluteFile
    internal val PRESETS_ROOT = LIBRARY_ROOT
    @Volatile
    var sessionState = SessionState()

    val deckStatus = Array(4) { AtomicReference(PresetIOStatus()) }
    internal val pendingSaves = Array(4) { AtomicReference<CompletableFuture<*>?>(null) }

    // Write only through setActive/clearActive: they also snapshot the macro bank as the dirty baseline.
    var activePresetA: String? = null
        private set
    var activePresetB: String? = null
        private set
    var activePresetBG: String? = null
        private set
    var activePresetPV: String? = null
        private set

    var cachedDtoA: DeckPresetDto? = null
        private set
    var cachedDtoB: DeckPresetDto? = null
        private set
    var cachedDtoBG: DeckPresetDto? = null
        private set
    var cachedDtoPV: DeckPresetDto? = null
        private set

    /** File modification time (ms since epoch) of the most recently loaded deck preset. */
    var activePresetMtimeA: Long? = null
    var activePresetMtimeB: Long? = null
    var activePresetMtimeBG: Long? = null
    var activePresetMtimePV: Long? = null

    /** Name of the preset loaded into [slot], or null if none. */
    fun activePreset(slot: DeckSlot): String? = when (slot) {
        DeckSlot.A -> activePresetA
        DeckSlot.B -> activePresetB
        DeckSlot.BG -> activePresetBG
        DeckSlot.PV -> activePresetPV
    }

    /** The DTO [slot] was last loaded or saved as (the dirty baseline), or null. */
    fun cachedDto(slot: DeckSlot): DeckPresetDto? = when (slot) {
        DeckSlot.A -> cachedDtoA
        DeckSlot.B -> cachedDtoB
        DeckSlot.BG -> cachedDtoBG
        DeckSlot.PV -> cachedDtoPV
    }

    /** The macro bank's labels and bindings as of the last [setActive]; knob values are left out (they move live). */
    internal class BankBaseline(val dto: DeckPresetDto, val signature: List<Pair<String, List<llm.slop.liquidlsd.macro.MacroBinding>>>)

    private val bankBaselines = arrayOfNulls<BankBaseline>(4)

    internal fun bankSignature(slot: DeckSlot): List<Pair<String, List<llm.slop.liquidlsd.macro.MacroBinding>>>? =
        llm.slop.liquidlsd.macro.MacroEngine.getBank(slot.bankId)?.knobs?.map { it.label to it.bindings.toList() }

    /**
     * The bank signature captured when [slot]'s cached DTO was set, or null if the DTO was replaced
     * without going through [setActive] (so a stale signature is never compared).
     */
    internal fun bankBaseline(slot: DeckSlot): List<Pair<String, List<llm.slop.liquidlsd.macro.MacroBinding>>>? =
        bankBaselines[slot.index]?.takeIf { it.dto === cachedDto(slot) }?.signature

    /** Sets [slot]'s active preset name and dirty baseline; the live macro bank is snapshotted as part of the baseline. */
    fun setActive(slot: DeckSlot, name: String?, dto: DeckPresetDto?) {
        when (slot) {
            DeckSlot.A -> { activePresetA = name; cachedDtoA = dto }
            DeckSlot.B -> { activePresetB = name; cachedDtoB = dto }
            DeckSlot.BG -> { activePresetBG = name; cachedDtoBG = dto }
            DeckSlot.PV -> { activePresetPV = name; cachedDtoPV = dto }
        }
        bankBaselines[slot.index] = dto?.let { d -> bankSignature(slot)?.let { BankBaseline(d, it) } }
    }

    fun clearActive(slot: DeckSlot) = setActive(slot, null, null)

    fun mtime(slot: DeckSlot): Long? = when (slot) {
        DeckSlot.A -> activePresetMtimeA
        DeckSlot.B -> activePresetMtimeB
        DeckSlot.BG -> activePresetMtimeBG
        DeckSlot.PV -> activePresetMtimePV
    }

    fun setMtime(slot: DeckSlot, value: Long?) {
        when (slot) {
            DeckSlot.A -> activePresetMtimeA = value
            DeckSlot.B -> activePresetMtimeB = value
            DeckSlot.BG -> activePresetMtimeBG = value
            DeckSlot.PV -> activePresetMtimePV = value
        }
    }

    internal data class RestoredQueueState(
        val files: List<File>,
        val activeIndex: Int,
        val unresolvedPaths: List<String> = emptyList()
    )

    fun isDeckDirty(deck: Deck, mixer: Mixer): Boolean = DeckOps.isDirty(deck, mixer)

    /**
     * Builds a canonical "empty" [DeckPresetDto] for the given deck.
     *
     * Internal (not private) because [DeckLifecycleManager] also needs it when
     * clearing/moving decks.
     */
    internal fun emptyDeckDto(deck: Deck, mixer: Mixer): DeckPresetDto {
        val label = when {
            deck === mixer.deckA -> "Deck A"
            deck === mixer.deckB -> "Deck B"
            deck === mixer.deckBG -> "Deck BG"
            deck === mixer.deckPV -> "Deck PV"
            else -> "Deck"
        }
        return deck.toDto(label).copy(isEmpty = true, visualSourceType = "mandala")
    }

    fun startEmpty(mixer: Mixer) = SessionSerializer.startEmpty(mixer)
    internal fun serializeSessionPath(file: File): String = SessionSerializer.serializeSessionPath(file)
    internal fun resolveSessionPath(path: String): File? = SessionSerializer.resolveSessionPath(path)
    internal fun resolveRestoredQueue(paths: List<String>, activeIndex: Int): RestoredQueueState =
        SessionSerializer.resolveRestoredQueue(paths, activeIndex)
}



