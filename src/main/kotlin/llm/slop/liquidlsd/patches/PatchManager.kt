package llm.slop.liquidlsd.patches

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import llm.slop.liquidlsd.models.*
import llm.slop.liquidlsd.rendering.Deck
import llm.slop.liquidlsd.rendering.Mixer
import mu.KotlinLogging
import java.io.File
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

object PatchManager {
    private val logger = KotlinLogging.logger {}
    private val patchIoExecutor: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "PatchManager-IO").apply { isDaemon = true }
    }

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    private val PRESETS_ROOT = File("presets").absoluteFile

    @Volatile
    var sessionState = SessionState()

    val deckStatus = Array(3) { AtomicReference(PatchIOStatus()) }
    private val pendingSaves = Array(3) { AtomicReference<CompletableFuture<*>?>(null) }

    val globalPatchQueue = ConcurrentLinkedQueue<GlobalPatchDto>()
    val deckAPatchQueue = ConcurrentLinkedQueue<DeckPatchDto>()
    val deckBPatchQueue = ConcurrentLinkedQueue<DeckPatchDto>()
    val deckCPatchQueue = ConcurrentLinkedQueue<DeckPatchDto>()

    var activePresetA: String? = null
    var activePresetB: String? = null
    var activePresetC: String? = null
    var cachedDtoA: DeckPatchDto? = null
    var cachedDtoB: DeckPatchDto? = null
    var cachedDtoC: DeckPatchDto? = null

    internal data class RestoredQueueState(
        val files: List<File>,
        val activeIndex: Int
    )

    var cachedGlobalDto: GlobalPatchDto? = null
    private var defaultGlobalPatchDto: GlobalPatchDto? = null

    fun initializeDefault(mixer: Mixer) {
        val dto = mixer.toDto("Untitled Project")
        defaultGlobalPatchDto = dto
        if (cachedGlobalDto == null) {
            cachedGlobalDto = dto
        }
    }

    fun isGlobalPatchDirty(mixer: Mixer): Boolean {
        val cached = cachedGlobalDto ?: defaultGlobalPatchDto ?: return false
        val current = mixer.toDto(cached.name)
        return current != cached
    }

    fun resetToDefault(mixer: Mixer) {
        val defaultDto = defaultGlobalPatchDto ?: return
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
     * Builds a canonical "empty" [DeckPatchDto] for the given deck.
     * The DTO uses the deck's current active source name and default parameter
     * values, with [isEmpty] = true and all modulators cleared, so that
     * [Deck.applyDto] leaves the deck in an inert state that the renderer will skip.
     */
    private fun emptyDeckDto(deck: Deck, mixer: Mixer): DeckPatchDto {
        val label = when {
            deck === mixer.deckA -> "Deck A"
            deck === mixer.deckB -> "Deck B"
            deck === mixer.deckC -> "Deck C"
            else -> "Deck"
        }
        // Snapshot the deck as-is but override isEmpty; this preserves the correct
        // visualSourceType so applyDto selects the same source at index 0 and does
        // not accidentally try to load an unrecognised type.
        return deck.toDto(label).copy(isEmpty = true)
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
                logger.info { "Loading global patch from ${file.absolutePath} in background..." }
                val content = file.readText()
                val dto = json.decodeFromString<GlobalPatchDto>(content)
                globalPatchQueue.offer(dto)
                logger.info { "Global patch loaded from file and queued for main thread apply" }
            } catch (e: Exception) {
                logger.error(e) { "Failed to load global patch from ${file.absolutePath}" }
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
        deckStatus[deckIndex].set(PatchIOStatus(PatchIOState.LOADING))
        CompletableFuture.runAsync({
            llm.slop.liquidlsd.audio.AudioEngine.patchIOInFlight.compareAndSet(false, true)
            try {
                logger.info { "Loading deck preset from ${file.absolutePath} in background..." }
                if (!file.exists()) throw java.io.FileNotFoundException(file.absolutePath)
                
                val content = file.readText()
                val dto = json.decodeFromString<DeckPatchDto>(content)
                when {
                    isDeckC -> deckCPatchQueue.offer(dto)
                    isDeckA -> deckAPatchQueue.offer(dto)
                    else -> deckBPatchQueue.offer(dto)
                }
                logger.info { "Deck preset loaded and queued for main thread swap" }
                deckStatus[deckIndex].set(PatchIOStatus(PatchIOState.IDLE))
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
                deckStatus[deckIndex].set(PatchIOStatus(PatchIOState.ERROR, e.message ?: "Unknown error"))
            } finally {
                llm.slop.liquidlsd.audio.AudioEngine.patchIOInFlight.compareAndSet(true, false)
            }
        }, patchIoExecutor)
    }

    fun saveGlobalPatchAsync(file: File, mixer: Mixer, name: String) {
        // Capture states on the main thread to ensure we don't read changing values from other threads
        val dto = mixer.toDto(name)
        cachedGlobalDto = dto
        CompletableFuture.runAsync({
            llm.slop.liquidlsd.audio.AudioEngine.patchIOInFlight.compareAndSet(false, true)
            try {
                logger.info { "Saving global patch to ${file.absolutePath} in background..." }
                val content = json.encodeToString(dto)
                file.parentFile?.mkdirs()
                file.writeText(content)
                logger.info { "Global patch saved to file successfully" }
            } catch (e: Exception) {
                logger.error(e) { "Failed to save global patch to ${file.absolutePath}" }
            } finally {
                llm.slop.liquidlsd.audio.AudioEngine.patchIOInFlight.compareAndSet(true, false)
            }
        }, patchIoExecutor)
    }

    fun saveDeckPresetAsync(file: File, deck: Deck, name: String, tags: List<String> = emptyList(), deckIndex: Int = -1) {
        // Capture deck state on the main thread (Phase 2c: include tags)
        val dto = deck.toDto(name, tags)
        
        if (deckIndex in 0..2) {
            deckStatus[deckIndex].set(PatchIOStatus(PatchIOState.SAVING))
            pendingSaves[deckIndex].getAndSet(null)?.cancel(false)
        }

        val future = CompletableFuture.runAsync({
            llm.slop.liquidlsd.audio.AudioEngine.patchIOInFlight.compareAndSet(false, true)
            try {
                logger.info { "Saving deck preset to ${file.absolutePath} in background..." }
                val content = json.encodeToString(dto)
                file.parentFile?.mkdirs()
                file.writeText(content)
                logger.info { "Deck preset saved to file successfully" }
                if (deckIndex in 0..2) {
                    deckStatus[deckIndex].set(PatchIOStatus(PatchIOState.IDLE))
                }
            } catch (e: Exception) {
                logger.error(e) { "Failed to save deck preset to ${file.absolutePath}" }
                if (deckIndex in 0..2) {
                    deckStatus[deckIndex].set(PatchIOStatus(PatchIOState.ERROR, e.message ?: "Unknown error"))
                }
            } finally {
                llm.slop.liquidlsd.audio.AudioEngine.patchIOInFlight.compareAndSet(true, false)
            }
        }, patchIoExecutor)

        if (deckIndex in 0..2) {
            pendingSaves[deckIndex].set(future)
        }
    }

    fun applyPendingPatches(mixer: Mixer) {
        // Poll global patch queue
        var globalDto = globalPatchQueue.poll()
        while (globalDto != null) {
            try {
                mixer.applyDto(globalDto)
                cachedGlobalDto = globalDto
                logger.info { "Successfully applied global patch: ${globalDto.name}" }
            } catch (e: Exception) {
                logger.error(e) { "Error applying global patch" }
            }
            globalDto = globalPatchQueue.poll()
        }

        // Poll deck A patch queue
        var deckADto = deckAPatchQueue.poll()
        while (deckADto != null) {
            try {
                mixer.deckA.applyDto(deckADto)
                activePresetA = deckADto.name
                cachedDtoA = deckADto
                logger.info { "Successfully applied Deck A preset: ${deckADto.name}" }
            } catch (e: Exception) {
                logger.error(e) { "Error applying Deck A preset" }
            }
            deckADto = deckAPatchQueue.poll()
        }

        // Poll deck B patch queue
        var deckBDto = deckBPatchQueue.poll()
        while (deckBDto != null) {
            try {
                mixer.deckB.applyDto(deckBDto)
                activePresetB = deckBDto.name
                cachedDtoB = deckBDto
                logger.info { "Successfully applied Deck B preset: ${deckBDto.name}" }
            } catch (e: Exception) {
                logger.error(e) { "Error applying Deck B preset" }
            }
            deckBDto = deckBPatchQueue.poll()
        }

        // Poll deck C patch queue
        var deckCDto = deckCPatchQueue.poll()
        while (deckCDto != null) {
            try {
                mixer.deckC.applyDto(deckCDto)
                activePresetC = deckCDto.name
                cachedDtoC = deckCDto
                logger.info { "Successfully applied Deck C preset: ${deckCDto.name}" }
            } catch (e: Exception) {
                logger.error(e) { "Error applying Deck C preset" }
            }
            deckCDto = deckCPatchQueue.poll()
        }
    }

    fun saveSession(mixer: Mixer) {
        try {
            val sessionFile = File("presets/last_session.json")
            val parent = sessionFile.parentFile
            if (parent != null && !parent.exists()) {
                parent.mkdirs()
            }
            
            val deckADto = mixer.deckA.toDto(activePresetA ?: "Deck A")
            val deckBDto = mixer.deckB.toDto(activePresetB ?: "Deck B")
            val deckCDto = mixer.deckC.toDto(activePresetC ?: "Deck C")
            
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
            val sessionFile = File("presets/last_session.json")
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
        val rootPath = PRESETS_ROOT.absolutePath + File.separator
        val filePath = absFile.absolutePath
        return if (filePath.startsWith(rootPath)) {
            filePath.substring(rootPath.length)
        } else {
            filePath
        }
    }

    internal fun resolveSessionPath(path: String): File? {
        // Try as relative to presets root first
        val relativeToRoot = File(PRESETS_ROOT, path)
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
