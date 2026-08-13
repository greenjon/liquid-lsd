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
    private val patchIoExecutor: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "PresetManager-IO").apply { isDaemon = true }
    }

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    private val LIBRARY_ROOT = File("library").absoluteFile
    private val PRESETS_ROOT = LIBRARY_ROOT

    init {
        val legacyRoot = File("presets").absoluteFile
        val libraryRoot = LIBRARY_ROOT
        if (legacyRoot.exists() && !libraryRoot.exists()) {
            try {
                legacyRoot.renameTo(libraryRoot)
                val legacyPatches = File(libraryRoot, "patches")
                val newPresets = File(libraryRoot, "presets")
                if (legacyPatches.exists() && !newPresets.exists()) {
                    legacyPatches.renameTo(newPresets)
                }
                logger.info { "Migrated legacy presets/ folder to library/" }
            } catch (e: Exception) {
                logger.error(e) { "Failed to auto-migrate legacy presets/ directory to library/" }
            }
        }
    }

    @Volatile
    var sessionState = SessionState()

    val deckStatus = Array(3) { AtomicReference(PatchIOStatus()) }
    private val pendingSaves = Array(3) { AtomicReference<CompletableFuture<*>?>(null) }

    val globalPresetQueue = ConcurrentLinkedQueue<GlobalPresetDto>()
    val deckAPresetQueue = ConcurrentLinkedQueue<DeckPresetDto>()
    val deckBPresetQueue = ConcurrentLinkedQueue<DeckPresetDto>()
    val deckCPresetQueue = ConcurrentLinkedQueue<DeckPresetDto>()

    val globalPatchQueue get() = globalPresetQueue
    val deckAPatchQueue get() = deckAPresetQueue
    val deckBPatchQueue get() = deckBPresetQueue
    val deckCPatchQueue get() = deckCPresetQueue

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

    var cachedGlobalDto: GlobalPresetDto? = null
    private var defaultGlobalPresetDto: GlobalPresetDto? = null

    fun initializeDefault(mixer: Mixer) {
        val dto = mixer.toDto("Untitled Project")
        defaultGlobalPresetDto = dto
        if (cachedGlobalDto == null) {
            cachedGlobalDto = dto
        }
    }

    fun isGlobalPresetDirty(mixer: Mixer): Boolean {
        val cached = cachedGlobalDto ?: defaultGlobalPresetDto ?: return false
        val current = mixer.toDto(cached.name)
        return current != cached
    }

    fun isGlobalPatchDirty(mixer: Mixer): Boolean = isGlobalPresetDirty(mixer)

    fun resetToDefault(mixer: Mixer) {
        val defaultDto = defaultGlobalPresetDto ?: return
        mixer.applyDto(defaultDto)
        cachedGlobalDto = defaultDto
    }

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

    fun loadGlobalPatchAsync(file: File) {
        CompletableFuture.runAsync({
            llm.slop.liquidlsd.audio.AudioEngine.patchIOInFlight.compareAndSet(false, true)
            try {
                logger.info { "Loading global preset from ${file.absolutePath} in background..." }
                val content = file.readText()
                val dto = json.decodeFromString<GlobalPresetDto>(content)
                globalPresetQueue.offer(dto)
                logger.info { "Global preset loaded from file and queued for main thread apply" }
            } catch (e: Exception) {
                logger.error(e) { "Failed to load global preset from ${file.absolutePath}" }
            } finally {
                llm.slop.liquidlsd.audio.AudioEngine.patchIOInFlight.compareAndSet(true, false)
            }
        }, patchIoExecutor)
    }

    fun loadDeckPresetAsync(file: File, isDeckA: Boolean, isDeckC: Boolean = false) {
        val deckIndex = when {
            isDeckC -> 2
            isDeckA -> 0
            else -> 1
        }
        deckStatus[deckIndex].set(PresetIOStatus(PresetIOState.LOADING))
        val fileMtime = file.lastModified().takeIf { it > 0L }
        CompletableFuture.runAsync({
            llm.slop.liquidlsd.audio.AudioEngine.patchIOInFlight.compareAndSet(false, true)
            try {
                logger.info { "Loading deck preset from ${file.absolutePath} in background..." }
                if (!file.exists()) throw java.io.FileNotFoundException(file.absolutePath)
                
                val content = file.readText()
                val rawDto = json.decodeFromString<DeckPresetDto>(content)
                val dto = rawDto.copy(name = file.nameWithoutExtension)
                when {
                    isDeckC -> deckCPresetQueue.offer(dto)
                    isDeckA -> deckAPresetQueue.offer(dto)
                    else -> deckBPresetQueue.offer(dto)
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
                // Extension fallback: if .lsd fails, try .json, and vice versa.
                val altFile = when {
                    file.name.endsWith(".lsd") -> File(file.absolutePath.substringBeforeLast(".lsd") + ".json")
                    file.name.endsWith(".json") -> File(file.absolutePath.substringBeforeLast(".json") + ".lsd")
                    else -> null
                }
                
                if (altFile != null && altFile.exists()) {
                    logger.info { "File not found or failed, trying alternative: ${altFile.name}" }
                    // clear flag before recursive call
                    llm.slop.liquidlsd.audio.AudioEngine.patchIOInFlight.compareAndSet(true, false)
                    loadDeckPresetAsync(altFile, isDeckA, isDeckC)
                    return@runAsync
                }

                logger.error(e) { "Failed to load deck preset from ${file.absolutePath}" }
                deckStatus[deckIndex].set(PresetIOStatus(PresetIOState.ERROR, e.message ?: "Unknown error"))
            } finally {
                llm.slop.liquidlsd.audio.AudioEngine.patchIOInFlight.compareAndSet(true, false)
            }
        }, patchIoExecutor)
    }

    fun saveGlobalPresetAsync(file: File, mixer: Mixer, name: String) {
        // Capture states on the main thread to ensure we don't read changing values from other threads
        val dto = mixer.toDto(name)
        cachedGlobalDto = dto
        CompletableFuture.runAsync({
            llm.slop.liquidlsd.audio.AudioEngine.patchIOInFlight.compareAndSet(false, true)
            try {
                logger.info { "Saving global preset to ${file.absolutePath} in background..." }
                val content = json.encodeToString(dto)
                file.parentFile?.mkdirs()
                file.writeText(content)
                logger.info { "Global preset saved to file successfully" }
            } catch (e: Exception) {
                logger.error(e) { "Failed to save global preset to ${file.absolutePath}" }
            } finally {
                llm.slop.liquidlsd.audio.AudioEngine.patchIOInFlight.compareAndSet(true, false)
            }
        }, patchIoExecutor)
    }

    fun saveGlobalPatchAsync(file: File, mixer: Mixer, name: String) = saveGlobalPresetAsync(file, mixer, name)

    fun saveDeckPresetAsync(file: File, deck: Deck, name: String, tags: List<String> = emptyList(), deckIndex: Int = -1) {
        // Capture deck state on the main thread (Phase 2c: include tags)
        val deckLabel = when (deckIndex) { 0 -> "Deck A"; 2 -> "Deck C"; else -> "Deck B" }
        val dto = NotesManager.syncToDto(deckLabel, deck.toDto(name, tags))

        if (deckIndex in 0..2) {
            deckStatus[deckIndex].set(PresetIOStatus(PresetIOState.SAVING))
            pendingSaves[deckIndex].getAndSet(null)?.cancel(false)
        }

        val future = CompletableFuture.runAsync({
            llm.slop.liquidlsd.audio.AudioEngine.patchIOInFlight.compareAndSet(false, true)
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
                llm.slop.liquidlsd.audio.AudioEngine.patchIOInFlight.compareAndSet(true, false)
            }
        }, patchIoExecutor)

        if (deckIndex in 0..2) {
            pendingSaves[deckIndex].set(future)
        }
    }

    fun saveDeckPatchAsync(file: File, deck: Deck, name: String, tags: List<String> = emptyList(), deckIndex: Int = -1) =
        saveDeckPresetAsync(file, deck, name, tags, deckIndex)

    fun applyPendingPresets(mixer: Mixer) {
        // Poll global preset queue
        var globalDto = globalPresetQueue.poll()
        while (globalDto != null) {
            try {
                mixer.applyDto(globalDto)
                cachedGlobalDto = globalDto
                logger.info { "Successfully applied global preset: ${globalDto.name}" }
            } catch (e: Exception) {
                logger.error(e) { "Error applying global preset" }
            }
            globalDto = globalPresetQueue.poll()
        }

        // Poll deck A preset queue
        var deckADto = deckAPresetQueue.poll()
        while (deckADto != null) {
            try {
                mixer.deckA.applyDto(deckADto)
                activePresetA = deckADto.name
                cachedDtoA = deckADto
                NotesManager.syncFromDto("Deck A", deckADto)
                logger.info { "Successfully applied Deck A preset: ${deckADto.name}" }
            } catch (e: Exception) {
                logger.error(e) { "Error applying Deck A preset" }
            }
            deckADto = deckAPresetQueue.poll()
        }

        // Poll deck B preset queue
        var deckBDto = deckBPresetQueue.poll()
        while (deckBDto != null) {
            try {
                mixer.deckB.applyDto(deckBDto)
                activePresetB = deckBDto.name
                cachedDtoB = deckBDto
                NotesManager.syncFromDto("Deck B", deckBDto)
                logger.info { "Successfully applied Deck B preset: ${deckBDto.name}" }
            } catch (e: Exception) {
                logger.error(e) { "Error applying Deck B preset" }
            }
            deckBDto = deckBPresetQueue.poll()
        }

        // Poll deck C preset queue
        var deckCDto = deckCPresetQueue.poll()
        while (deckCDto != null) {
            try {
                mixer.deckC.applyDto(deckCDto)
                activePresetC = deckCDto.name
                cachedDtoC = deckCDto
                NotesManager.syncFromDto("Deck C", deckCDto)
                logger.info { "Successfully applied Deck C preset: ${deckCDto.name}" }
            } catch (e: Exception) {
                logger.error(e) { "Error applying Deck C preset" }
            }
            deckCDto = deckCPresetQueue.poll()
        }
    }

    fun applyPendingPatches(mixer: Mixer) = applyPendingPresets(mixer)

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
            var sessionFile = File(LIBRARY_ROOT, "last_session.json")
            if (!sessionFile.exists()) {
                val legacyFile = File("presets/last_session.json")
                if (legacyFile.exists()) sessionFile = legacyFile
            }
            if (!sessionFile.exists()) {
                logger.info { "No previous session file found." }
                return
            }
            val content = sessionFile.readText()
            val session = json.decodeFromString<SessionStateDto>(content)
            
            val crossfadeDto = if (session.version <= 1) llm.slop.liquidlsd.models.mapMonopolarToBipolar(session.crossfade) else session.crossfade
            mixer.crossfade.applyDto(crossfadeDto)
            mixer.masterAlpha.applyDto(session.masterAlpha)
            mixer.mode.set(session.blendMode)
            
            mixer.deckA.applyDto(session.deckA)
            mixer.deckB.applyDto(session.deckB)
            mixer.deckC.applyDto(session.deckC)
            
            session.bloom?.let { mixer.bloom.applyDto(it) }
            session.xfadeSpeed?.let { 
                if (session.version <= 3) {
                    val oldVal = it.baseValue
                    val convertedVal = (2.0f / (3.0f * oldVal)).coerceIn(0.1f, 30.0f)
                    mixer.xfadeSpeed.applyDto(it.copy(baseValue = convertedVal))
                } else {
                    mixer.xfadeSpeed.applyDto(it)
                }
            }
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

        // Try as relative to legacy presets root
        val relativeToLegacy = File("presets", path)
        if (relativeToLegacy.exists()) return relativeToLegacy

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

typealias PatchManager = PresetManager

