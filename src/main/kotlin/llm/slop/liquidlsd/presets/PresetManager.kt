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

    val deckStatus = Array(4) { AtomicReference(PresetIOStatus()) }
    private val pendingSaves = Array(4) { AtomicReference<CompletableFuture<*>?>(null) }

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

    fun clearDeckActivePreset(deck: Deck, mixer: Mixer) {
        when {
            deck === mixer.deckA -> { cachedDtoA = null; activePresetA = null }
            deck === mixer.deckB -> { cachedDtoB = null; activePresetB = null }
            deck === mixer.deckBG -> { cachedDtoBG = null; activePresetBG = null }
            deck === mixer.deckPV -> { cachedDtoPV = null; activePresetPV = null }
        }
    }

    fun copyDeck(mixer: Mixer, from: Deck, to: Deck) {
        if (from.isEmpty) {
            to.applyDto(emptyDeckDto(to, mixer))
            when {
                to === mixer.deckA -> { cachedDtoA = null; activePresetA = null }
                to === mixer.deckB -> { cachedDtoB = null; activePresetB = null }
                to === mixer.deckBG -> { cachedDtoBG = null; activePresetBG = null }
                to === mixer.deckPV -> { cachedDtoPV = null; activePresetPV = null }
            }
            return
        }
        val fromDto = when {
            from === mixer.deckA -> cachedDtoA?.let { from.toDto(it.name) } ?: from.toDto("Deck A")
            from === mixer.deckB -> cachedDtoB?.let { from.toDto(it.name) } ?: from.toDto("Deck B")
            from === mixer.deckBG -> cachedDtoBG?.let { from.toDto(it.name) } ?: from.toDto("Deck BG")
            from === mixer.deckPV -> cachedDtoPV?.let { from.toDto(it.name) } ?: from.toDto("Deck PV")
            else -> return
        }
        
        to.applyDto(fromDto)
        
        when {
            to === mixer.deckA -> { cachedDtoA = fromDto; activePresetA = fromDto.name }
            to === mixer.deckB -> { cachedDtoB = fromDto; activePresetB = fromDto.name }
            to === mixer.deckBG -> { cachedDtoBG = fromDto; activePresetBG = fromDto.name }
            to === mixer.deckPV -> { cachedDtoPV = fromDto; activePresetPV = fromDto.name }
        }
    }

    fun moveDeck(mixer: Mixer, from: Deck, to: Deck) {
        copyDeck(mixer, from, to)
        from.applyDto(emptyDeckDto(from, mixer))
        when {
            from === mixer.deckA -> { cachedDtoA = null; activePresetA = null }
            from === mixer.deckB -> { cachedDtoB = null; activePresetB = null }
            from === mixer.deckBG -> { cachedDtoBG = null; activePresetBG = null }
            from === mixer.deckPV -> { cachedDtoPV = null; activePresetPV = null }
        }
    }

    /**
     * Builds a canonical "empty" [DeckPresetDto] for the given deck.
     */
    private fun emptyDeckDto(deck: Deck, mixer: Mixer): DeckPresetDto {
        val label = when {
            deck === mixer.deckA -> "Deck A"
            deck === mixer.deckB -> "Deck B"
            deck === mixer.deckBG -> "Deck BG"
            deck === mixer.deckPV -> "Deck PV"
            else -> "Deck"
        }
        return deck.toDto(label).copy(isEmpty = true, visualSourceType = "mandala")
    }

    fun swapDecks(mixer: Mixer, deck1: Deck, deck2: Deck) {
        val dto1 = when {
            deck1 === mixer.deckA -> cachedDtoA?.let { deck1.toDto(it.name) } ?: deck1.toDto("Deck A")
            deck1 === mixer.deckB -> cachedDtoB?.let { deck1.toDto(it.name) } ?: deck1.toDto("Deck B")
            deck1 === mixer.deckBG -> cachedDtoBG?.let { deck1.toDto(it.name) } ?: deck1.toDto("Deck BG")
            deck1 === mixer.deckPV -> cachedDtoPV?.let { deck1.toDto(it.name) } ?: deck1.toDto("Deck PV")
            else -> return
        }
        val dto2 = when {
            deck2 === mixer.deckA -> cachedDtoA?.let { deck2.toDto(it.name) } ?: deck2.toDto("Deck A")
            deck2 === mixer.deckB -> cachedDtoB?.let { deck2.toDto(it.name) } ?: deck2.toDto("Deck B")
            deck2 === mixer.deckBG -> cachedDtoBG?.let { deck2.toDto(it.name) } ?: deck2.toDto("Deck BG")
            deck2 === mixer.deckPV -> cachedDtoPV?.let { deck2.toDto(it.name) } ?: deck2.toDto("Deck PV")
            else -> return
        }

        deck1.applyDto(dto2)
        deck2.applyDto(dto1)

        val oldDto1 = dto1
        val oldDto2 = dto2

        when {
            deck1 === mixer.deckA -> { cachedDtoA = oldDto2; activePresetA = oldDto2.name }
            deck1 === mixer.deckB -> { cachedDtoB = oldDto2; activePresetB = oldDto2.name }
            deck1 === mixer.deckBG -> { cachedDtoBG = oldDto2; activePresetBG = oldDto2.name }
            deck1 === mixer.deckPV -> { cachedDtoPV = oldDto2; activePresetPV = oldDto2.name }
        }
        when {
            deck2 === mixer.deckA -> { cachedDtoA = oldDto1; activePresetA = oldDto1.name }
            deck2 === mixer.deckB -> { cachedDtoB = oldDto1; activePresetB = oldDto1.name }
            deck2 === mixer.deckBG -> { cachedDtoBG = oldDto1; activePresetBG = oldDto1.name }
            deck2 === mixer.deckPV -> { cachedDtoPV = oldDto1; activePresetPV = oldDto1.name }
        }
    }

    /**
     * Sanitizes an incoming [DeckPresetDto] against the active visual source and feedback schemas.
     * Fills in defaults for any missing parameters and removes obsolete/legacy keys.
     * Returns a pair of the sanitized DTO and a boolean indicating whether any modifications occurred.
     */
    fun sanitizePresetDto(dto: DeckPresetDto): Pair<DeckPresetDto, Boolean> {
        if (dto.isEmpty) return Pair(dto, false)
        var modified = false

        val metaFile = File(LIBRARY_ROOT, "sources/${dto.visualSourceType}/meta.json")
        val sanitizedParams = LinkedHashMap<String, ParameterDto>()

        if (metaFile.exists()) {
            try {
                val meta = json.decodeFromString<SourceMeta>(metaFile.readText())
                val expectedNames = meta.parameters.map { it.name }.toSet()

                for (pMeta in meta.parameters) {
                    val existing = dto.parameters[pMeta.name]
                    if (existing != null) {
                        sanitizedParams[pMeta.name] = existing
                    } else {
                        modified = true
                        sanitizedParams[pMeta.name] = ParameterDto(
                            baseValue = pMeta.default,
                            baseMin = pMeta.defaultMin ?: pMeta.default,
                            baseMax = pMeta.defaultMax ?: pMeta.default,
                            randomizeBase = false,
                            modulators = emptyList()
                        )
                    }
                }

                if (dto.parameters.keys != expectedNames) {
                    modified = true
                }
            } catch (e: Exception) {
                logger.warn(e) { "Could not parse meta.json for source '${dto.visualSourceType}' during sanitization" }
                sanitizedParams.putAll(dto.parameters)
            }
        } else {
            sanitizedParams.putAll(dto.parameters)
        }

        // Canonical feedback parameters
        val canonicalFeedbackDefaults = mapOf(
            "fbDecay" to ParameterDto(0.0f, 0.0f, 0.0f, false, emptyList()),
            "fbGain" to ParameterDto(1.0f, 1.0f, 1.0f, false, emptyList()),
            "fbZoom" to ParameterDto(0.0f, 0.0f, 0.0f, false, emptyList()),
            "fbRotate" to ParameterDto(0.0f, 0.0f, 0.0f, false, emptyList()),
            "fbHueShift" to ParameterDto(0.0f, 0.0f, 0.0f, false, emptyList()),
            "fbBlur" to ParameterDto(0.0f, 0.0f, 0.0f, false, emptyList()),
            "fbChroma" to ParameterDto(0.0f, 0.0f, 0.0f, false, emptyList()),
            "fbMode" to ParameterDto(0.0f, 0.0f, 0.0f, false, emptyList()),
            "fbKaleido" to ParameterDto(1.0f, 1.0f, 1.0f, false, emptyList())
        )

        val sanitizedFeedback = LinkedHashMap<String, ParameterDto>()
        for ((key, defaultDto) in canonicalFeedbackDefaults) {
            val existing = dto.feedbackParameters[key]
            if (existing != null) {
                sanitizedFeedback[key] = existing
            } else {
                modified = true
                sanitizedFeedback[key] = defaultDto
            }
        }

        if (dto.feedbackParameters.keys != canonicalFeedbackDefaults.keys) {
            modified = true
        }

        val sanitizedGlobalAlpha = dto.globalAlpha ?: run {
            modified = true
            ParameterDto(1.0f, 1.0f, 1.0f, false, emptyList())
        }

        val sanitizedDto = if (modified) {
            dto.copy(
                parameters = sanitizedParams,
                feedbackParameters = sanitizedFeedback,
                globalAlpha = sanitizedGlobalAlpha
            )
        } else {
            dto
        }

        return Pair(sanitizedDto, modified)
    }

    fun loadDeckPresetAsync(
        file: File,
        isDeckA: Boolean = false,
        isDeckBG: Boolean = false,
        isDeckPV: Boolean = false,
        isManual: Boolean = true
    ) {
        val deckIndex = when {
            isDeckA -> 0
            isDeckBG -> 2
            isDeckPV -> 3
            else -> 1 // Deck B
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
                val namedDto = rawDto.copy(name = file.nameWithoutExtension)
                val (sanitizedDto, wasMigrated) = sanitizePresetDto(namedDto)

                if (wasMigrated && file.canWrite()) {
                    try {
                        file.writeText(json.encodeToString(sanitizedDto))
                        logger.info { "Auto-healed and migrated preset '${file.name}' to latest schema" }
                    } catch (e: Exception) {
                        logger.warn(e) { "Could not auto-save migrated preset '${file.name}'" }
                    }
                }

                val pending = PendingDeckLoad(sanitizedDto, isManual)
                when {
                    isDeckA -> deckAPresetQueue.offer(pending)
                    isDeckBG -> deckBGPresetQueue.offer(pending)
                    isDeckPV -> deckPVPresetQueue.offer(pending)
                    else -> deckBPresetQueue.offer(pending)
                }
                when {
                    isDeckA -> activePresetMtimeA = fileMtime
                    isDeckBG -> activePresetMtimeBG = fileMtime
                    isDeckPV -> activePresetMtimePV = fileMtime
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
        val deckLabel = when (deckIndex) {
            0 -> "Deck A"
            1 -> "Deck B"
            2 -> "Deck BG"
            3 -> "Deck PV"
            else -> "Deck"
        }
        val dto = NotesManager.syncToDto(deckLabel, deck.toDto(name, tags))

        if (deckIndex in 0..3) {
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
                when (deckIndex) {
                    0 -> activePresetMtimeA = file.lastModified().takeIf { it > 0L }
                    1 -> activePresetMtimeB = file.lastModified().takeIf { it > 0L }
                    2 -> activePresetMtimeBG = file.lastModified().takeIf { it > 0L }
                    3 -> activePresetMtimePV = file.lastModified().takeIf { it > 0L }
                }
                logger.info { "Deck preset saved to file successfully" }
                if (deckIndex in 0..3) {
                    deckStatus[deckIndex].set(PresetIOStatus(PresetIOState.IDLE))
                }
            } catch (e: Exception) {
                logger.error(e) { "Failed to save deck preset to ${file.absolutePath}" }
                if (deckIndex in 0..3) {
                    deckStatus[deckIndex].set(PresetIOStatus(PresetIOState.ERROR, e.message ?: "Unknown error"))
                }
            } finally {
                llm.slop.liquidlsd.audio.AudioEngine.presetIOInFlight.compareAndSet(true, false)
            }
        }, presetIoExecutor)

        if (deckIndex in 0..3) {
            pendingSaves[deckIndex].set(future)
        }
    }

    fun saveFxPresetAsync(file: File, name: String, slotDto: FXSlotDto, tags: List<String> = emptyList()) {
        CompletableFuture.runAsync({
            try {
                logger.info { "Saving FX preset to ${file.absolutePath}..." }
                val dto = FXPresetDto(name = name, tags = tags, slot = slotDto)
                file.parentFile?.mkdirs()
                file.writeText(json.encodeToString(dto))
                logger.info { "FX preset saved successfully to ${file.name}" }
            } catch (e: Exception) {
                logger.error(e) { "Failed to save FX preset to ${file.absolutePath}" }
            }
        }, presetIoExecutor)
    }

    fun loadFxPresetAsync(file: File): CompletableFuture<FXPresetDto> {
        return CompletableFuture.supplyAsync({
            if (!file.exists()) throw java.io.FileNotFoundException(file.absolutePath)
            val content = file.readText()
            json.decodeFromString<FXPresetDto>(content)
        }, presetIoExecutor)
    }

    fun saveFxChainAsync(file: File, name: String, chainDto: FXChainDto, tags: List<String> = emptyList()) {
        CompletableFuture.runAsync({
            try {
                logger.info { "Saving FX chain to ${file.absolutePath}..." }
                val dto = chainDto.copy(name = name, tags = tags)
                file.parentFile?.mkdirs()
                file.writeText(json.encodeToString(dto))
                logger.info { "FX chain saved successfully to ${file.name}" }
            } catch (e: Exception) {
                logger.error(e) { "Failed to save FX chain to ${file.absolutePath}" }
            }
        }, presetIoExecutor)
    }

    fun loadFxChainAsync(file: File): CompletableFuture<FXChainDto> {
        return CompletableFuture.supplyAsync({
            if (!file.exists()) throw java.io.FileNotFoundException(file.absolutePath)
            val content = file.readText()
            json.decodeFromString<FXChainDto>(content)
        }, presetIoExecutor)
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

    fun saveSession(mixer: Mixer) {
        try {
            val sessionFile = File(LIBRARY_ROOT, "last_session.json")
            val parent = sessionFile.parentFile
            if (parent != null && !parent.exists()) {
                parent.mkdirs()
            }
            
            val deckADto = if (mixer.deckA.isEmpty) emptyDeckDto(mixer.deckA, mixer) else mixer.deckA.toDto(activePresetA ?: "Deck A")
            val deckBDto = if (mixer.deckB.isEmpty) emptyDeckDto(mixer.deckB, mixer) else mixer.deckB.toDto(activePresetB ?: "Deck B")
            val deckBGDto = if (mixer.deckBG.isEmpty) emptyDeckDto(mixer.deckBG, mixer) else mixer.deckBG.toDto(activePresetBG ?: "Deck BG")
            val deckPVDto = if (mixer.deckPV.isEmpty) emptyDeckDto(mixer.deckPV, mixer) else mixer.deckPV.toDto(activePresetPV ?: "Deck PV")
            
            val transSlot = mixer.transitionFilter?.takeIf { it.id.isNotEmpty() }?.let { trans ->
                FXSlotDto(
                    filterId = trans.id,
                    enabled = trans.enabled,
                    dryWet = trans.dryWet.toDto(),
                    parameters = trans.parameters.mapValues { it.value.toDto() }
                )
            }

            val masterFxSlotDtos = (0 until Deck.FX_SLOT_COUNT).map { mixer.toMasterFxSlotDto(it) }

            val mixerDto = MixerDto(
                crossfade = mixer.crossfade.toDto(),
                masterAlpha = mixer.masterAlpha.toDto(),
                blendMode = mixer.mode.baseValue,
                bloom = mixer.bloom.toDto(),
                xfadeSpeed = mixer.xfadeSpeed.toDto(),
                queueNext = mixer.queueNext.toDto(),
                queuePrev = mixer.queuePrev.toDto(),
                bgQueueNext = mixer.bgQueueNext.toDto(),
                bgQueuePrev = mixer.bgQueuePrev.toDto(),
                tapTempo = mixer.tapTempo.toDto(),
                levelA = mixer.levelA,
                levelB = mixer.levelB,
                levelBG = mixer.levelBG,
                levelPV = mixer.levelPV,
                masterLevel = mixer.masterLevel,
                transitionSlot = transSlot,
                masterFxSlots = masterFxSlotDtos
            )

            val session = SessionStateDto(
                deckA = deckADto,
                deckB = deckBDto,
                deckBG = deckBGDto,
                deckPV = deckPVDto,
                mixer = mixerDto,
                queue = PlayQueueManager.queue.map { serializeSessionPath(it) },
                activeIndex = PlayQueueManager.activeIndex,
                isAutoVJEnabled = PlayQueueManager.isAutoVJEnabled,
                isRepeatEnabled = PlayQueueManager.isRepeatEnabled,
                isShuffleEnabled = PlayQueueManager.isShuffleEnabled,
                bgQueue = BgQueueManager.queue.map { serializeSessionPath(it) },
                bgActiveIndex = BgQueueManager.activeIndex,
                isAutoBGEnabled = BgQueueManager.isAutoBGEnabled,
                isBgRepeatEnabled = BgQueueManager.isRepeatEnabled,
                isBgShuffleEnabled = BgQueueManager.isShuffleEnabled
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
                logger.info { "No previous session file found. Initializing default state." }
                startEmpty(mixer)
                loadInitialPreset(mixer)
                return
            }
            val content = sessionFile.readText()
            val session = json.decodeFromString<SessionStateDto>(content)
            
            val mDto = session.mixer

            mixer.crossfade.applyDto(mDto.crossfade)
            mixer.masterAlpha.applyDto(mDto.masterAlpha)
            mixer.mode.set(mDto.blendMode)
            mixer.levelA = mDto.levelA
            mixer.levelB = mDto.levelB
            mixer.levelBG = mDto.levelBG
            mixer.levelPV = mDto.levelPV
            mixer.masterLevel = mDto.masterLevel

            mixer.setTransition(null)
            mDto.transitionSlot?.let { transDto ->
                mixer.setTransition(transDto.filterId)
                mixer.transitionFilter?.let { trans ->
                    trans.enabled = transDto.enabled
                    trans.dryWet.applyDto(transDto.dryWet)
                    for ((key, paramDto) in transDto.parameters) {
                        trans.parameters[key]?.applyDto(paramDto)
                    }
                }
            }

            for (i in 0 until Deck.FX_SLOT_COUNT) {
                mixer.clearMasterFxSlot(i)
                val slotDto = mDto.masterFxSlots.getOrNull(i)
                if (slotDto != null && slotDto.filterId.isNotBlank()) {
                    mixer.applyMasterFxSlot(i, slotDto)
                }
            }
            
            mixer.deckA.applyDto(session.deckA)
            mixer.deckB.applyDto(session.deckB)
            
            val bgDto = session.deckBG ?: emptyDeckDto(mixer.deckBG, mixer)
            mixer.deckBG.applyDto(bgDto)

            val pvDto = session.deckPV ?: emptyDeckDto(mixer.deckPV, mixer)
            mixer.deckPV.applyDto(pvDto)
            
            mDto.bloom?.let { mixer.bloom.applyDto(it) }
            mDto.xfadeSpeed?.let { mixer.xfadeSpeed.applyDto(it) }
            mDto.queueNext?.let { mixer.queueNext.applyDto(it) }
            mDto.queuePrev?.let { mixer.queuePrev.applyDto(it) }
            mDto.bgQueueNext?.let { mixer.bgQueueNext.applyDto(it) }
            mDto.bgQueuePrev?.let { mixer.bgQueuePrev.applyDto(it) }
            mDto.tapTempo?.let { mixer.tapTempo.applyDto(it) }
            mixer.queueNext.baseValue = 0f
            mixer.queuePrev.baseValue = 0f
            mixer.bgQueueNext.baseValue = 0f
            mixer.bgQueuePrev.baseValue = 0f
            mixer.tapTempo.baseValue = 0f
            mixer.syncQueueTriggerPrevValues()
            
            activePresetA = if (session.deckA.isEmpty) null else session.deckA.name
            cachedDtoA = if (session.deckA.isEmpty) null else mixer.deckA.toDto(session.deckA.name, session.deckA.tags).copy(
                presetNotes = session.deckA.presetNotes,
                paramNotes = session.deckA.paramNotes
            )
            
            activePresetB = if (session.deckB.isEmpty) null else session.deckB.name
            cachedDtoB = if (session.deckB.isEmpty) null else mixer.deckB.toDto(session.deckB.name, session.deckB.tags).copy(
                presetNotes = session.deckB.presetNotes,
                paramNotes = session.deckB.paramNotes
            )

            activePresetBG = if (bgDto.isEmpty) null else bgDto.name
            cachedDtoBG = if (bgDto.isEmpty) null else mixer.deckBG.toDto(bgDto.name, bgDto.tags).copy(
                presetNotes = bgDto.presetNotes,
                paramNotes = bgDto.paramNotes
            )

            activePresetPV = if (pvDto.isEmpty) null else pvDto.name
            cachedDtoPV = if (pvDto.isEmpty) null else mixer.deckPV.toDto(pvDto.name, pvDto.tags).copy(
                presetNotes = pvDto.presetNotes,
                paramNotes = pvDto.paramNotes
            )
            
            val allUnresolved = mutableListOf<String>()

            mDto.transitionSlot?.let { transDto ->
                if (transDto.filterId.isNotBlank() && !llm.slop.liquidlsd.rendering.isf.ISFTransitionRegistry.hasTransition(transDto.filterId)) {
                    allUnresolved.add("Transition filter not found: ${transDto.filterId}")
                }
            }
            mDto.masterFxSlots.forEachIndexed { i, fxDto ->
                if (fxDto != null && fxDto.filterId.isNotBlank() && llm.slop.liquidlsd.rendering.isf.ISFFilterRegistry.availableFilters.none { it.id == fxDto.filterId }) {
                    allUnresolved.add("Master FX${i + 1} filter not found: ${fxDto.filterId}")
                }
            }

            val deckDtos = listOf("A" to session.deckA, "B" to session.deckB, "BG" to bgDto, "PV" to pvDto)
            for ((deckName, dDto) in deckDtos) {
                if (!dDto.isEmpty && dDto.visualSourceType.isNotBlank() && dDto.visualSourceType != "mandala" && dDto.visualSourceType != "blank") {
                    val found = llm.slop.liquidlsd.rendering.VisualSourceRegistry.availableSources.any { it.id == dDto.visualSourceType }
                    if (!found) {
                        allUnresolved.add("Deck $deckName visual source not found: ${dDto.visualSourceType}")
                    }
                }
                listOf("FX1" to dDto.fxSlot1, "FX2" to dDto.fxSlot2, "FX3" to dDto.fxSlot3, "FX4" to dDto.fxSlot4).forEach { (label, fx) ->
                    if (fx != null && fx.filterId.isNotBlank() && llm.slop.liquidlsd.rendering.isf.ISFFilterRegistry.availableFilters.none { it.id == fx.filterId }) {
                        allUnresolved.add("Deck $deckName $label filter not found: ${fx.filterId}")
                    }
                }
            }

            val restoredQueue = resolveRestoredQueue(session.queue, session.activeIndex)
            allUnresolved.addAll(restoredQueue.unresolvedPaths)
            PlayQueueManager.restoreSessionQueue(
                restoredQueue.files,
                restoredQueue.activeIndex,
                session.isAutoVJEnabled,
                session.isRepeatEnabled,
                session.isShuffleEnabled
            )

            val restoredBgQueue = resolveRestoredQueue(session.bgQueue, session.bgActiveIndex)
            allUnresolved.addAll(restoredBgQueue.unresolvedPaths)
            BgQueueManager.restoreSessionQueue(
                restoredBgQueue.files,
                restoredBgQueue.activeIndex,
                session.isAutoBGEnabled,
                session.isBgRepeatEnabled,
                session.isBgShuffleEnabled
            )

            sessionState = sessionState.copy(unresolvedItems = allUnresolved.distinct())
            llm.slop.liquidlsd.midi.MidiMappingManager.invalidateBindings()
            llm.slop.liquidlsd.parameters.ParameterResolver.clearCache()
            logger.info { "Successfully loaded session state from ${sessionFile.name} (unresolved items: ${allUnresolved.size})" }
        } catch (e: Exception) {
            logger.error(e) { "Failed to load session state, falling back to empty" }
            startEmpty(mixer)
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
        val relativeToRoot = File(LIBRARY_ROOT, path)
        if (relativeToRoot.exists()) return relativeToRoot

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
            return RestoredQueueState(existingFiles.map { it.second }, -1, unresolved)
        }

        val rebasedActiveIndex = existingFiles.indexOfFirst { it.first >= savedActiveIndex }
            .takeIf { it >= 0 }
            ?: existingFiles.lastIndex

        return RestoredQueueState(existingFiles.map { it.second }, rebasedActiveIndex, unresolved)
    }

    fun startEmpty(mixer: Mixer) {
        mixer.deckA.reset()
        mixer.deckB.reset()
        mixer.deckBG.reset()
        mixer.deckPV.reset()
        activePresetA = null
        cachedDtoA = null
        activePresetB = null
        cachedDtoB = null
        activePresetBG = null
        cachedDtoBG = null
        activePresetPV = null
        cachedDtoPV = null
        PlayQueueManager.clearQueue()
        BgQueueManager.clearQueue()
        llm.slop.liquidlsd.midi.MidiMappingManager.invalidateBindings()
        llm.slop.liquidlsd.parameters.ParameterResolver.clearCache()
        logger.info { "Started application empty" }
    }

    private fun loadInitialPreset(mixer: Mixer) {
        try {
            val presets = llm.slop.liquidlsd.ui.FileSystemManager.scanAllPresets()
            if (presets.isEmpty()) return
            val candidate = presets.firstOrNull { it.name.equals("3d mandala", ignoreCase = true) }
                ?: presets.first()
            val file = File(candidate.path)
            if (!file.exists()) return
            val rawDto = json.decodeFromString<DeckPresetDto>(file.readText())
            val namedDto = rawDto.copy(name = file.nameWithoutExtension)
            val (sanitizedDto, _) = sanitizePresetDto(namedDto)
            mixer.deckA.applyDto(sanitizedDto)
            activePresetA = sanitizedDto.name
            cachedDtoA = mixer.deckA.toDto(sanitizedDto.name, sanitizedDto.tags).copy(
                presetNotes = sanitizedDto.presetNotes,
                paramNotes = sanitizedDto.paramNotes
            )
            logger.info { "First launch: loaded initial preset '${sanitizedDto.name}' onto Deck A" }
        } catch (e: Exception) {
            logger.warn(e) { "Failed to load initial preset on first launch" }
        }
    }
}



