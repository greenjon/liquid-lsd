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

    data class PendingDeckLoad(
        val dto: DeckPresetDto,
        val isManual: Boolean = true
    )

    val deckAPresetQueue = ConcurrentLinkedQueue<PendingDeckLoad>()
    val deckBPresetQueue = ConcurrentLinkedQueue<PendingDeckLoad>()
    val deckBGPresetQueue = ConcurrentLinkedQueue<PendingDeckLoad>()
    val deckPVPresetQueue = ConcurrentLinkedQueue<PendingDeckLoad>()

    var activePresetA: String? = null
    var activePresetB: String? = null
    var activePresetBG: String? = null
    var activePresetPV: String? = null

    var cachedDtoA: DeckPresetDto? = null
    var cachedDtoB: DeckPresetDto? = null
    var cachedDtoBG: DeckPresetDto? = null
    var cachedDtoPV: DeckPresetDto? = null

    /** File modification time (ms since epoch) of the most recently loaded deck preset. */
    var activePresetMtimeA: Long? = null
    var activePresetMtimeB: Long? = null
    var activePresetMtimeBG: Long? = null
    var activePresetMtimePV: Long? = null

    internal data class RestoredQueueState(
        val files: List<File>,
        val activeIndex: Int,
        val unresolvedPaths: List<String> = emptyList()
    )

    fun isDeckDirty(deck: Deck, mixer: Mixer): Boolean {
        val cached = when {
            deck === mixer.deckA -> cachedDtoA
            deck === mixer.deckB -> cachedDtoB
            deck === mixer.deckBG -> cachedDtoBG
            deck === mixer.deckPV -> cachedDtoPV
            else -> null
        }
        if (cached == null) return false
        val current = deck.toDto(cached.name)
        return current != cached
    }

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

    fun applyPendingPresets(mixer: Mixer) {
        var appliedAny = false
        // Poll deck A preset queue
        var pendingA = deckAPresetQueue.poll()
        while (pendingA != null) {
            appliedAny = true
            try {
                val deckADto = pendingA.dto
                mixer.deckA.applyDto(deckADto)
                activePresetA = deckADto.name
                cachedDtoA = mixer.deckA.toDto(deckADto.name, deckADto.tags).copy(
                    presetNotes = deckADto.presetNotes,
                    paramNotes = deckADto.paramNotes
                )
                NotesManager.syncFromDto("Deck A", deckADto)
                if (pendingA.isManual) {
                    PlayQueueManager.notifyManualDeckLoaded(isDeckA = true, isDeckPV = false, mixer = mixer)
                }
                logger.info { "Successfully applied Deck A preset: ${deckADto.name}" }
            } catch (e: Exception) {
                logger.error(e) { "Error applying Deck A preset" }
            }
            pendingA = deckAPresetQueue.poll()
        }

        // Poll deck B preset queue
        var pendingB = deckBPresetQueue.poll()
        while (pendingB != null) {
            appliedAny = true
            try {
                val deckBDto = pendingB.dto
                mixer.deckB.applyDto(deckBDto)
                activePresetB = deckBDto.name
                cachedDtoB = mixer.deckB.toDto(deckBDto.name, deckBDto.tags).copy(
                    presetNotes = deckBDto.presetNotes,
                    paramNotes = deckBDto.paramNotes
                )
                NotesManager.syncFromDto("Deck B", deckBDto)
                if (pendingB.isManual) {
                    PlayQueueManager.notifyManualDeckLoaded(isDeckA = false, isDeckPV = false, mixer = mixer)
                }
                logger.info { "Successfully applied Deck B preset: ${deckBDto.name}" }
            } catch (e: Exception) {
                logger.error(e) { "Error applying Deck B preset" }
            }
            pendingB = deckBPresetQueue.poll()
        }

        // Poll deck BG preset queue
        var pendingBG = deckBGPresetQueue.poll()
        while (pendingBG != null) {
            appliedAny = true
            try {
                val deckBGDto = pendingBG.dto
                mixer.deckBG.applyDto(deckBGDto)
                activePresetBG = deckBGDto.name
                cachedDtoBG = mixer.deckBG.toDto(deckBGDto.name, deckBGDto.tags).copy(
                    presetNotes = deckBGDto.presetNotes,
                    paramNotes = deckBGDto.paramNotes
                )
                NotesManager.syncFromDto("Deck BG", deckBGDto)
                logger.info { "Successfully applied Deck BG preset: ${deckBGDto.name}" }
            } catch (e: Exception) {
                logger.error(e) { "Error applying Deck BG preset" }
            }
            pendingBG = deckBGPresetQueue.poll()
        }

        // Poll deck PV preset queue
        var pendingPV = deckPVPresetQueue.poll()
        while (pendingPV != null) {
            appliedAny = true
            try {
                val deckPVDto = pendingPV.dto
                mixer.deckPV.applyDto(deckPVDto)
                activePresetPV = deckPVDto.name
                cachedDtoPV = mixer.deckPV.toDto(deckPVDto.name, deckPVDto.tags).copy(
                    presetNotes = deckPVDto.presetNotes,
                    paramNotes = deckPVDto.paramNotes
                )
                NotesManager.syncFromDto("Deck PV", deckPVDto)
                if (pendingPV.isManual) {
                    PlayQueueManager.notifyManualDeckLoaded(isDeckA = false, isDeckPV = true, mixer = mixer)
                }
                logger.info { "Successfully applied Deck PV preset: ${deckPVDto.name}" }
            } catch (e: Exception) {
                logger.error(e) { "Error applying Deck PV preset" }
            }
            pendingPV = deckPVPresetQueue.poll()
        }

        if (appliedAny) {
            llm.slop.liquidlsd.midi.MidiMappingManager.invalidateBindings()
            llm.slop.liquidlsd.parameters.ParameterResolver.clearCache()
            // Notify broadcast engine if connected so full state is pushed immediately
            llm.slop.liquidlsd.broadcast.BroadcastEngine.notifyStateChanged()
        }
    }

    fun startEmpty(mixer: Mixer) = SessionSerializer.startEmpty(mixer)
    internal fun serializeSessionPath(file: File): String = SessionSerializer.serializeSessionPath(file)
    internal fun resolveSessionPath(path: String): File? = SessionSerializer.resolveSessionPath(path)
    internal fun resolveRestoredQueue(paths: List<String>, activeIndex: Int): RestoredQueueState =
        SessionSerializer.resolveRestoredQueue(paths, activeIndex)
}



