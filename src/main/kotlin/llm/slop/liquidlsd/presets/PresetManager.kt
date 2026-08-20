package llm.slop.liquidlsd.presets

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import llm.slop.liquidlsd.models.*
import llm.slop.liquidlsd.notes.NotesManager
import llm.slop.liquidlsd.rendering.Deck
import llm.slop.liquidlsd.rendering.Mixer
import mu.KotlinLogging
import java.io.File
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

object PresetManager {
    private val logger = KotlinLogging.logger {}
    private val presetIoExecutor: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "PresetManager-IO").apply { isDaemon = true }
    }

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    private val LIBRARY_ROOT = File("library").absoluteFile
    private val PRESETS_ROOT = LIBRARY_ROOT
    @Volatile
    var sessionState = SessionState()

    val deckStatus = Array(3) { AtomicReference(PresetIOStatus()) }
    private val pendingSaves = Array(3) { AtomicReference<CompletableFuture<*>?>(null) }

    data class PendingDeckLoad(
        val dto: DeckPresetDto,
        val isManual: Boolean = true
    )

    val deckAPresetQueue = ConcurrentLinkedQueue<PendingDeckLoad>()
    val deckBPresetQueue = ConcurrentLinkedQueue<PendingDeckLoad>()
    val deckCPresetQueue = ConcurrentLinkedQueue<PendingDeckLoad>()


    var activePresetA: String? = null
    var activePresetB: String? = null
    var activePresetC: String? = null
    var cachedDtoA: DeckPresetDto? = null
    var cachedDtoB: DeckPresetDto? = null
    var cachedDtoC: DeckPresetDto? = null

    /** File modification time (ms since epoch) of the most recently loaded deck preset, for Idea E tooltip. */
    var activePresetMtimeA: Long? = null
    var activePresetMtimeB: Long? = null
    var activePresetMtimeC: Long? = null

    internal data class RestoredQueueState(
        val files: List<File>,
        val activeIndex: Int
    )

    fun isDeckDirty(deck: Deck, mixer: Mixer): Boolean {
        val cached = when {
            deck === mixer.deckA -> cachedDtoA
            deck === mixer.deckB -> cachedDtoB
            deck === mixer.deckC -> cachedDtoC
            else -> null
        }
        if (cached == null) return false
        val current = deck.toDto(cached.name)
        return current != cached
    }

    fun copyDeck(mixer: Mixer, from: Deck, to: Deck) {
        if (from.isEmpty) {
            to.applyDto(emptyDeckDto(to, mixer))
            when {
                to === mixer.deckA -> { cachedDtoA = null; activePresetA = null }
                to === mixer.deckB -> { cachedDtoB = null; activePresetB = null }
                to === mixer.deckC -> { cachedDtoC = null; activePresetC = null }
            }
            return
        }
        val fromDto = when {
            from === mixer.deckA -> cachedDtoA?.let { from.toDto(it.name) } ?: from.toDto("Deck A")
            from === mixer.deckB -> cachedDtoB?.let { from.toDto(it.name) } ?: from.toDto("Deck B")
            from === mixer.deckC -> cachedDtoC?.let { from.toDto(it.name) } ?: from.toDto("Deck C")
            else -> return
        }
        
        to.applyDto(fromDto)
        
        when {
            to === mixer.deckA -> { cachedDtoA = fromDto; activePresetA = fromDto.name }
            to === mixer.deckB -> { cachedDtoB = fromDto; activePresetB = fromDto.name }
            to === mixer.deckC -> { cachedDtoC = fromDto; activePresetC = fromDto.name }
        }
    }

    fun moveDeck(mixer: Mixer, from: Deck, to: Deck) {
        copyDeck(mixer, from, to)
        // Apply an explicit empty DTO rather than calling from.reset() so that all
        // state — isEmpty, lastSourceSelectBase, modulators — is set atomically via
        // applyDto and cannot drift on subsequent update() calls.
        from.applyDto(emptyDeckDto(from, mixer))
        when {
            from === mixer.deckA -> { cachedDtoA = null; activePresetA = null }
            from === mixer.deckB -> { cachedDtoB = null; activePresetB = null }
            from === mixer.deckC -> { cachedDtoC = null; activePresetC = null }
        }
    }

    /**
     * Builds a canonical "empty" [DeckPresetDto] for the given deck.
     * The DTO uses the deck's current active source name and default parameter
     * values, with [isEmpty] = true and all modulators cleared, so that
     * [Deck.applyDto] leaves the deck in an inert state that the renderer will skip.
     */
    private fun emptyDeckDto(deck: Deck, mixer: Mixer): DeckPresetDto {
        val label = when {
            deck === mixer.deckA -> "Deck A"
            deck === mixer.deckB -> "Deck B"
            deck === mixer.deckC -> "Deck C"
            else -> "Deck"
        }
        return deck.toDto(label).copy(isEmpty = true, visualSourceType = "mandala")
    }

    fun swapDecks(mixer: Mixer, deck1: Deck, deck2: Deck) {
        val dto1 = when {
            deck1 === mixer.deckA -> cachedDtoA?.let { deck1.toDto(it.name) } ?: deck1.toDto("Deck A")
            deck1 === mixer.deckB -> cachedDtoB?.let { deck1.toDto(it.name) } ?: deck1.toDto("Deck B")
            deck1 === mixer.deckC -> cachedDtoC?.let { deck1.toDto(it.name) } ?: deck1.toDto("Deck C")
            else -> return
        }
        val dto2 = when {
            deck2 === mixer.deckA -> cachedDtoA?.let { deck2.toDto(it.name) } ?: deck2.toDto("Deck A")
            deck2 === mixer.deckB -> cachedDtoB?.let { deck2.toDto(it.name) } ?: deck2.toDto("Deck B")
            deck2 === mixer.deckC -> cachedDtoC?.let { deck2.toDto(it.name) } ?: deck2.toDto("Deck C")
            else -> return
        }

        deck1.applyDto(dto2)
        deck2.applyDto(dto1)

        // Update caches
        val oldDto1 = dto1
        val oldDto2 = dto2

        when {
            deck1 === mixer.deckA -> { cachedDtoA = oldDto2; activePresetA = oldDto2.name }
            deck1 === mixer.deckB -> { cachedDtoB = oldDto2; activePresetB = oldDto2.name }
            deck1 === mixer.deckC -> { cachedDtoC = oldDto2; activePresetC = oldDto2.name }
        }
        when {
            deck2 === mixer.deckA -> { cachedDtoA = oldDto1; activePresetA = oldDto1.name }
            deck2 === mixer.deckB -> { cachedDtoB = oldDto1; activePresetB = oldDto1.name }
            deck2 === mixer.deckC -> { cachedDtoC = oldDto1; activePresetC = oldDto1.name }
        }
    }

    fun loadDeckPresetAsync(file: File, isDeckA: Boolean, isDeckC: Boolean = false, isManual: Boolean = true) {
        val deckIndex = when {
            isDeckC -> 2
            isDeckA -> 0
            else -> 1
        }
        deckStatus[deckIndex].set(PresetIOStatus(PresetIOState.LOADING))
        val fileMtime = file.lastModified().takeIf { it > 0L }
        CompletableFuture.runAsync({
            llm.slop.liquidlsd.audio.AudioEngine.presetIOInFlight.compareAndSet(false, true)
            try {
                logger.info { "Loading deck preset from ${file.absolutePath} in background..." }
                if (!file.exists()) throw java.io.FileNotFoundException(file.absolutePath)
                
                val content = file.readText()
                val rawDto = json.decodeFromString<DeckPresetDto>(content)
                val dto = rawDto.copy(name = file.nameWithoutExtension)
                val pending = PendingDeckLoad(dto, isManual)
                when {
                    isDeckC -> deckCPresetQueue.offer(pending)
                    isDeckA -> deckAPresetQueue.offer(pending)
                    else -> deckBPresetQueue.offer(pending)
                }
                // Mtime is captured before the background thread runs so it reflects the file on disk
                when {
                    isDeckC -> activePresetMtimeC = fileMtime
                    isDeckA -> activePresetMtimeA = fileMtime
                    else    -> activePresetMtimeB = fileMtime
                }
                logger.info { "Deck preset loaded and queued for main thread swap" }
                deckStatus[deckIndex].set(PresetIOStatus(PresetIOState.IDLE))
            } catch (e: Exception) {
                logger.error(e) { "Failed to load deck preset from ${file.absolutePath}" }
                deckStatus[deckIndex].set(PresetIOStatus(PresetIOState.ERROR, e.message ?: "Unknown error"))
            } finally {
                llm.slop.liquidlsd.audio.AudioEngine.presetIOInFlight.compareAndSet(true, false)
            }
        }, presetIoExecutor)
    }


    fun saveDeckPresetAsync(file: File, deck: Deck, name: String, tags: List<String> = emptyList(), deckIndex: Int = -1) {
        // Capture deck state on the main thread (Phase 2c: include tags)
        val deckLabel = when (deckIndex) { 0 -> "Deck A"; 2 -> "Deck C"; else -> "Deck B" }
        val dto = NotesManager.syncToDto(deckLabel, deck.toDto(name, tags))

        if (deckIndex in 0..2) {
            deckStatus[deckIndex].set(PresetIOStatus(PresetIOState.SAVING))
            pendingSaves[deckIndex].getAndSet(null)?.cancel(false)
        }

        val future = CompletableFuture.runAsync({
            llm.slop.liquidlsd.audio.AudioEngine.presetIOInFlight.compareAndSet(false, true)
            try {
                logger.info { "Saving deck preset to ${file.absolutePath} in background..." }
                val content = json.encodeToString(dto)
                file.parentFile?.mkdirs()
                file.writeText(content)
                // Update mtime to reflect the newly written file
                when (deckIndex) {
                    0 -> activePresetMtimeA = file.lastModified().takeIf { it > 0L }
                    1 -> activePresetMtimeB = file.lastModified().takeIf { it > 0L }
                    2 -> activePresetMtimeC = file.lastModified().takeIf { it > 0L }
                }
                logger.info { "Deck preset saved to file successfully" }
                if (deckIndex in 0..2) {
                    deckStatus[deckIndex].set(PresetIOStatus(PresetIOState.IDLE))
                }
            } catch (e: Exception) {
                logger.error(e) { "Failed to save deck preset to ${file.absolutePath}" }
                if (deckIndex in 0..2) {
                    deckStatus[deckIndex].set(PresetIOStatus(PresetIOState.ERROR, e.message ?: "Unknown error"))
                }
            } finally {
                llm.slop.liquidlsd.audio.AudioEngine.presetIOInFlight.compareAndSet(true, false)
            }
        }, presetIoExecutor)

        if (deckIndex in 0..2) {
            pendingSaves[deckIndex].set(future)
        }
    }


    fun applyPendingPresets(mixer: Mixer) {
        // Poll deck A preset queue
        var pendingA = deckAPresetQueue.poll()
        while (pendingA != null) {
            try {
                val deckADto = pendingA.dto
                mixer.deckA.applyDto(deckADto)
                activePresetA = deckADto.name
                cachedDtoA = deckADto
                NotesManager.syncFromDto("Deck A", deckADto)
                if (pendingA.isManual) {
                    PlayQueueManager.notifyManualDeckLoaded(isDeckA = true, isDeckC = false, mixer = mixer)
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
            try {
                val deckBDto = pendingB.dto
                mixer.deckB.applyDto(deckBDto)
                activePresetB = deckBDto.name
                cachedDtoB = deckBDto
                NotesManager.syncFromDto("Deck B", deckBDto)
                if (pendingB.isManual) {
                    PlayQueueManager.notifyManualDeckLoaded(isDeckA = false, isDeckC = false, mixer = mixer)
                }
                logger.info { "Successfully applied Deck B preset: ${deckBDto.name}" }
            } catch (e: Exception) {
                logger.error(e) { "Error applying Deck B preset" }
            }
            pendingB = deckBPresetQueue.poll()
        }

        // Poll deck C preset queue
        var pendingC = deckCPresetQueue.poll()
        while (pendingC != null) {
            try {
                val deckCDto = pendingC.dto
                mixer.deckC.applyDto(deckCDto)
                activePresetC = deckCDto.name
                cachedDtoC = deckCDto
                NotesManager.syncFromDto("Deck C", deckCDto)
                if (pendingC.isManual) {
                    PlayQueueManager.notifyManualDeckLoaded(isDeckA = false, isDeckC = true, mixer = mixer)
                }
                logger.info { "Successfully applied Deck C preset: ${deckCDto.name}" }
            } catch (e: Exception) {
                logger.error(e) { "Error applying Deck C preset" }
            }
            pendingC = deckCPresetQueue.poll()
        }
    }


    fun saveSession(mixer: Mixer) {
        try {
            val sessionFile = File(LIBRARY_ROOT, "last_session.json")
            val parent = sessionFile.parentFile
            if (parent != null && !parent.exists()) {
                parent.mkdirs()
            }
            
            val deckADto = if (mixer.deckA.isEmpty) emptyDeckDto(mixer.deckA, mixer) else mixer.deckA.toDto(activePresetA ?: "Deck A")
            val deckBDto = if (mixer.deckB.isEmpty) emptyDeckDto(mixer.deckB, mixer) else mixer.deckB.toDto(activePresetB ?: "Deck B")
            val deckCDto = if (mixer.deckC.isEmpty) emptyDeckDto(mixer.deckC, mixer) else mixer.deckC.toDto(activePresetC ?: "Deck C")
            
            val session = SessionStateDto(
                deckA = deckADto,
                deckB = deckBDto,
                deckC = deckCDto,
                crossfade = mixer.crossfade.toDto(),
                masterAlpha = mixer.masterAlpha.toDto(),
                blendMode = mixer.mode.baseValue,
                queue = PlayQueueManager.queue.map { serializeSessionPath(it) },
                activeIndex = PlayQueueManager.activeIndex,
                isAutoVJEnabled = PlayQueueManager.isAutoVJEnabled,
                bloom = mixer.bloom.toDto(),
                xfadeSpeed = mixer.xfadeSpeed.toDto(),
                queueNext = mixer.queueNext.toDto(),
                queuePrev = mixer.queuePrev.toDto(),
                isRepeatEnabled = PlayQueueManager.isRepeatEnabled,
                isShuffleEnabled = PlayQueueManager.isShuffleEnabled
            )
            
            val content = json.encodeToString(session)
            sessionFile.writeText(content)
            logger.info { "Successfully saved session state to ${sessionFile.name}" }
        } catch (e: Exception) {
            logger.error(e) { "Failed to save session state" }
        }
    }

    fun loadSession(mixer: Mixer) {
        try {
            val sessionFile = File(LIBRARY_ROOT, "last_session.json")
            if (!sessionFile.exists()) {
                logger.info { "No previous session file found." }
                return
            }
            val content = sessionFile.readText()
            val session = json.decodeFromString<SessionStateDto>(content)
            
            mixer.crossfade.applyDto(session.crossfade)
            mixer.masterAlpha.applyDto(session.masterAlpha)
            mixer.mode.set(session.blendMode)
            
            mixer.deckA.applyDto(session.deckA)
            mixer.deckB.applyDto(session.deckB)
            mixer.deckC.applyDto(session.deckC)
            
            session.bloom?.let { mixer.bloom.applyDto(it) }
            session.xfadeSpeed?.let { mixer.xfadeSpeed.applyDto(it) }
            session.queueNext?.let { mixer.queueNext.applyDto(it) }
            session.queuePrev?.let { mixer.queuePrev.applyDto(it) }
            mixer.queueNext.baseValue = 0f
            mixer.queuePrev.baseValue = 0f
            mixer.syncQueueTriggerPrevValues()
            
            activePresetA = if (session.deckA.isEmpty) null else session.deckA.name
            cachedDtoA = if (session.deckA.isEmpty) null else session.deckA
            
            activePresetB = if (session.deckB.isEmpty) null else session.deckB.name
            cachedDtoB = if (session.deckB.isEmpty) null else session.deckB
            
            activePresetC = if (session.deckC.isEmpty) null else session.deckC.name
            cachedDtoC = if (session.deckC.isEmpty) null else session.deckC
            
            val restoredQueue = resolveRestoredQueue(session.queue, session.activeIndex)
            PlayQueueManager.restoreSessionQueue(
                restoredQueue.files,
                restoredQueue.activeIndex,
                session.isAutoVJEnabled,
                session.isRepeatEnabled,
                session.isShuffleEnabled
            )
            logger.info { "Successfully loaded session state from ${sessionFile.name}" }
        } catch (e: Exception) {
            logger.error(e) { "Failed to load session state" }
        }
    }

    internal fun serializeSessionPath(file: File): String {
        val absFile = file.absoluteFile
        val rootPath = LIBRARY_ROOT.absolutePath + File.separator
        val filePath = absFile.absolutePath
        return if (filePath.startsWith(rootPath)) {
            filePath.substring(rootPath.length)
        } else {
            filePath
        }
    }

    internal fun resolveSessionPath(path: String): File? {
        // Try as relative to library root first
        val relativeToRoot = File(LIBRARY_ROOT, path)
        if (relativeToRoot.exists()) return relativeToRoot

        // Try as absolute path (or relative to CWD)
        val directFile = File(path)
        if (directFile.exists()) return directFile

        return null
    }

    internal fun resolveRestoredQueue(queuePaths: List<String>, savedActiveIndex: Int): RestoredQueueState {
        val unresolved = mutableListOf<String>()
        val existingFiles = queuePaths.mapIndexedNotNull { originalIndex, path ->
            val file = resolveSessionPath(path)
            if (file != null) {
                originalIndex to file
            } else {
                unresolved.add(path)
                null
            }
        }
        
        sessionState = sessionState.copy(unresolvedItems = unresolved)

        if (existingFiles.isEmpty() || savedActiveIndex < 0) {
            return RestoredQueueState(existingFiles.map { it.second }, -1)
        }

        val rebasedActiveIndex = existingFiles.indexOfFirst { it.first >= savedActiveIndex }
            .takeIf { it >= 0 }
            ?: existingFiles.lastIndex

        return RestoredQueueState(existingFiles.map { it.second }, rebasedActiveIndex)
    }

    fun startEmpty(mixer: Mixer) {
        mixer.deckA.reset()
        mixer.deckB.reset()
        mixer.deckC.reset()
        activePresetA = null
        cachedDtoA = null
        activePresetB = null
        cachedDtoB = null
        activePresetC = null
        cachedDtoC = null
        PlayQueueManager.clearQueue()
        logger.info { "Started application empty" }
    }
}



