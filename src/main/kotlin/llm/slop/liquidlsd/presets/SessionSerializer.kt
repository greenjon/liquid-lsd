package llm.slop.liquidlsd.presets

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import llm.slop.liquidlsd.models.*
import llm.slop.liquidlsd.rendering.Deck
import llm.slop.liquidlsd.rendering.Mixer
import mu.KotlinLogging
import java.io.File

object SessionSerializer {
    private val logger = KotlinLogging.logger {}

    /** Persists the active session, including the canonical per-deck/mixer/FX-bank macro banks. */
    fun saveSession(mixer: Mixer) {
        try {
            val sessionFile = File(PresetManager.LIBRARY_ROOT, "last_session.json")
            val parent = sessionFile.parentFile
            if (parent != null && !parent.exists()) {
                parent.mkdirs()
            }
            
            val deckADto = if (mixer.deckA.isEmpty) PresetManager.emptyDeckDto(mixer.deckA, mixer) else mixer.deckA.toDto(PresetManager.activePresetA ?: "Deck A")
            val deckBDto = if (mixer.deckB.isEmpty) PresetManager.emptyDeckDto(mixer.deckB, mixer) else mixer.deckB.toDto(PresetManager.activePresetB ?: "Deck B")
            val deckBGDto = if (mixer.deckBG.isEmpty) PresetManager.emptyDeckDto(mixer.deckBG, mixer) else mixer.deckBG.toDto(PresetManager.activePresetBG ?: "Deck BG")
            val deckPVDto = if (mixer.deckPV.isEmpty) PresetManager.emptyDeckDto(mixer.deckPV, mixer) else mixer.deckPV.toDto(PresetManager.activePresetPV ?: "Deck PV")
            
            val transSlot = mixer.transitionFilter?.takeIf { it.id.isNotEmpty() }?.let { trans ->
                FXSlotDto(
                    filterId = trans.id,
                    enabled = trans.enabled,
                    dryWet = trans.dryWet.toDto(),
                    parameters = trans.parameters.mapValues { it.value.toDto() }
                )
            }

            val masterFxSlotDtos = (0 until mixer.masterFxSlots.size).map { mixer.toMasterFxSlotDto(it) }

            val mixerDto = MixerDto(
                crossfade = mixer.crossfade.toDto(),
                xfadeSpeed = mixer.xfadeSpeed.toDto(),
                queueNext = mixer.queueNext.toDto(),
                queuePrev = mixer.queuePrev.toDto(),
                bgQueueNext = mixer.bgQueueNext.toDto(),
                bgQueuePrev = mixer.bgQueuePrev.toDto(),
                transQueueNext = mixer.transQueueNext.toDto(),
                transQueuePrev = mixer.transQueuePrev.toDto(),
                tapTempo = mixer.tapTempo.toDto(),
                levelA = mixer.levelA.toDto(),
                levelB = mixer.levelB.toDto(),
                levelBG = mixer.levelBG.toDto(),
                levelPV = mixer.levelPV.toDto(),
                masterLevel = mixer.masterLevel.toDto(),
                transitionSlot = transSlot,
                masterFxSlots = masterFxSlotDtos,
                fxBank1 = mixer.fxBank1.toFxBankDto("FX1"),
                fxBank2 = mixer.fxBank2.toFxBankDto("FX2"),
                masterFxBank = mixer.masterFxBank.toFxBankDto("MFX")
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
                isBgShuffleEnabled = BgQueueManager.isShuffleEnabled,
                transQueue = TransitionQueueManager.queue.map { serializeSessionPath(it) },
                transActiveIndex = TransitionQueueManager.activeIndex,
                isTransAutoAdvanceEnabled = TransitionQueueManager.isAutoAdvanceEnabled,
                isTransRepeatEnabled = TransitionQueueManager.isRepeatEnabled,
                isTransShuffleEnabled = TransitionQueueManager.isShuffleEnabled,
                deckMacroBanks = llm.slop.liquidlsd.macro.MacroEngine.CANONICAL_BANK_IDS.associateWith {
                    llm.slop.liquidlsd.macro.MacroEngine.getBank(it) ?: llm.slop.liquidlsd.macro.MacroEngine.newBankFor(it)
                }
            )
            
            val content = PresetManager.json.encodeToString(session)
            sessionFile.writeText(content)
            logger.info { "Successfully saved session state to ${sessionFile.name}" }
        } catch (e: Exception) {
            logger.error(e) { "Failed to save session state" }
        }
    }

    /**
     * Restores the active session, including registering all canonical per-deck/mixer/FX-bank macro
     * banks with [llm.slop.liquidlsd.macro.MacroEngine] -- independent of whether the Rack
     * workspace or Classic's MACROS tabs are ever opened this run, since both read/write these
     * same resident banks directly (see [llm.slop.liquidlsd.macro.MacroEngine.CANONICAL_BANK_IDS]).
     */
    fun loadSession(mixer: Mixer) {
        try {
            val sessionFile = File(PresetManager.LIBRARY_ROOT, "last_session.json")
            if (!sessionFile.exists()) {
                logger.info { "No previous session file found. Initializing default state." }
                startEmpty(mixer)
                loadInitialPreset(mixer)
                return
            }
            val content = sessionFile.readText()
            val session = PresetManager.json.decodeFromString<SessionStateDto>(content)
            
            val mDto = session.mixer

            mixer.crossfade.applyDto(mDto.crossfade)
            mDto.levelA?.let { mixer.levelA.applyDto(it) }
            mDto.levelB?.let { mixer.levelB.applyDto(it) }
            mDto.levelBG?.let { mixer.levelBG.applyDto(it) }
            mDto.levelPV?.let { mixer.levelPV.applyDto(it) }
            if (mDto.masterLevel != null) {
                mixer.masterLevel.applyDto(mDto.masterLevel)
            } else if (mDto.masterAlpha != null) {
                mixer.masterLevel.applyDto(mDto.masterAlpha)
            }

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

            for (i in 0 until mixer.masterFxSlots.size) {
                mixer.clearMasterFxSlot(i)
                val slotDto = mDto.masterFxSlots.getOrNull(i)
                if (slotDto != null && slotDto.filterId.isNotBlank()) {
                    mixer.applyMasterFxSlot(i, slotDto)
                }
            }

            mDto.fxBank1?.let { mixer.fxBank1.applyFxBank(it) }
            mDto.fxBank2?.let { mixer.fxBank2.applyFxBank(it) }
            mDto.masterFxBank?.let { mixer.masterFxBank.applyFxBank(it) }
            
            mixer.deckA.applyDto(session.deckA)
            mixer.deckB.applyDto(session.deckB)
            
            val bgDto = session.deckBG ?: PresetManager.emptyDeckDto(mixer.deckBG, mixer)
            mixer.deckBG.applyDto(bgDto)

            val pvDto = session.deckPV ?: PresetManager.emptyDeckDto(mixer.deckPV, mixer)
            mixer.deckPV.applyDto(pvDto)
            
            mDto.xfadeSpeed?.let { mixer.xfadeSpeed.applyDto(it) }
            mDto.queueNext?.let { mixer.queueNext.applyDto(it) }
            mDto.queuePrev?.let { mixer.queuePrev.applyDto(it) }
            mDto.bgQueueNext?.let { mixer.bgQueueNext.applyDto(it) }
            mDto.bgQueuePrev?.let { mixer.bgQueuePrev.applyDto(it) }
            mDto.transQueueNext?.let { mixer.transQueueNext.applyDto(it) }
            mDto.transQueuePrev?.let { mixer.transQueuePrev.applyDto(it) }
            mDto.tapTempo?.let { mixer.tapTempo.applyDto(it) }
            mixer.queueNext.baseValue = 0f
            mixer.queuePrev.baseValue = 0f
            mixer.bgQueueNext.baseValue = 0f
            mixer.bgQueuePrev.baseValue = 0f
            mixer.transQueueNext.baseValue = 0f
            mixer.transQueuePrev.baseValue = 0f
            mixer.tapTempo.baseValue = 0f
            mixer.syncQueueTriggerPrevValues()
            
            PresetManager.activePresetA = if (session.deckA.isEmpty) null else session.deckA.name
            PresetManager.cachedDtoA = if (session.deckA.isEmpty) null else mixer.deckA.toDto(session.deckA.name, session.deckA.tags).copy(
                presetNotes = session.deckA.presetNotes,
                paramNotes = session.deckA.paramNotes
            )
            
            PresetManager.activePresetB = if (session.deckB.isEmpty) null else session.deckB.name
            PresetManager.cachedDtoB = if (session.deckB.isEmpty) null else mixer.deckB.toDto(session.deckB.name, session.deckB.tags).copy(
                presetNotes = session.deckB.presetNotes,
                paramNotes = session.deckB.paramNotes
            )

            PresetManager.activePresetBG = if (bgDto.isEmpty) null else bgDto.name
            PresetManager.cachedDtoBG = if (bgDto.isEmpty) null else mixer.deckBG.toDto(bgDto.name, bgDto.tags).copy(
                presetNotes = bgDto.presetNotes,
                paramNotes = bgDto.paramNotes
            )

            PresetManager.activePresetPV = if (pvDto.isEmpty) null else pvDto.name
            PresetManager.cachedDtoPV = if (pvDto.isEmpty) null else mixer.deckPV.toDto(pvDto.name, pvDto.tags).copy(
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

            val restoredTransQueue = resolveRestoredQueue(session.transQueue, session.transActiveIndex)
            allUnresolved.addAll(restoredTransQueue.unresolvedPaths)
            TransitionQueueManager.restoreSessionQueue(
                restoredTransQueue.files,
                restoredTransQueue.activeIndex,
                session.isTransAutoAdvanceEnabled,
                session.isTransRepeatEnabled,
                session.isTransShuffleEnabled
            )

            for (canonicalId in llm.slop.liquidlsd.macro.MacroEngine.CANONICAL_BANK_IDS) {
                val targetCount = llm.slop.liquidlsd.macro.MacroEngine.defaultKnobCountFor(canonicalId)
                val rawBank = session.deckMacroBanks[canonicalId]
                val bank = if (rawBank != null) {
                    if (rawBank.knobs.size != targetCount) {
                        val clampedKnobs = rawBank.knobs.take(targetCount).toMutableList()
                        while (clampedKnobs.size < targetCount) {
                            clampedKnobs.add(llm.slop.liquidlsd.macro.MacroControl(label = "KNOB ${clampedKnobs.size + 1}"))
                        }
                        rawBank.copy(knobs = clampedKnobs)
                    } else {
                        rawBank
                    }
                } else {
                    llm.slop.liquidlsd.macro.MacroEngine.newBankFor(canonicalId)
                }
                llm.slop.liquidlsd.macro.MacroEngine.registerBank(canonicalId, bank)
            }

            PresetManager.sessionState = PresetManager.sessionState.copy(unresolvedItems = allUnresolved.distinct())
            llm.slop.liquidlsd.midi.MidiMappingManager.invalidateBindings()
            llm.slop.liquidlsd.parameters.ParameterResolver.clearCache()
            logger.info { "Successfully loaded session state from ${sessionFile.name} (unresolved items: ${allUnresolved.size})" }
        } catch (e: Exception) {
            logger.error(e) { "Failed to load session state, falling back to empty" }
            startEmpty(mixer)
        }
    }

    internal fun serializeSessionPath(file: File): String {
        val rootPath = PresetManager.LIBRARY_ROOT.absolutePath
        return if (file.absolutePath.startsWith(rootPath)) {
            "\${LIBRARY}" + file.absolutePath.substring(rootPath.length).replace('\\', '/')
        } else {
            file.absolutePath.replace('\\', '/')
        }
    }

    internal fun resolveSessionPath(path: String): File? {
        if (path.startsWith("\${LIBRARY}")) {
            val relative = path.substring("\${LIBRARY}".length).removePrefix("/")
            val f = File(PresetManager.LIBRARY_ROOT, relative)
            return if (f.exists()) f else null
        }
        val relativeInLib = File(PresetManager.LIBRARY_ROOT, path)
        if (relativeInLib.exists()) return relativeInLib
        val f = File(path)
        return if (f.exists()) f else null
    }

    internal fun resolveRestoredQueue(paths: List<String>, activeIndex: Int): PresetManager.RestoredQueueState {
        val resolvedFiles = mutableListOf<File>()
        var resolvedActiveIndex = -1
        val unresolvedPaths = mutableListOf<String>()

        for ((idx, pathStr) in paths.withIndex()) {
            val f = resolveSessionPath(pathStr)
            if (f != null && f.exists()) {
                resolvedFiles.add(f)
                if (idx == activeIndex) {
                    resolvedActiveIndex = resolvedFiles.size - 1
                } else if (idx > activeIndex && resolvedActiveIndex == -1) {
                    resolvedActiveIndex = resolvedFiles.size - 1
                }
            } else {
                unresolvedPaths.add(pathStr)
            }
        }
        if (resolvedActiveIndex == -1 && resolvedFiles.isNotEmpty()) {
            resolvedActiveIndex = 0
        }
        return PresetManager.RestoredQueueState(resolvedFiles, resolvedActiveIndex, unresolvedPaths)
    }

    fun startEmpty(mixer: Mixer) {
        mixer.deckA.applyDto(PresetManager.emptyDeckDto(mixer.deckA, mixer))
        mixer.deckB.applyDto(PresetManager.emptyDeckDto(mixer.deckB, mixer))
        mixer.deckBG.applyDto(PresetManager.emptyDeckDto(mixer.deckBG, mixer))
        mixer.deckPV.applyDto(PresetManager.emptyDeckDto(mixer.deckPV, mixer))
        
        mixer.crossfade.baseValue = -1f
        mixer.levelA.baseValue = 1f
        mixer.levelB.baseValue = 1f
        mixer.levelBG.baseValue = 0f
        mixer.levelPV.baseValue = 1f
        mixer.masterLevel.baseValue = 1f

        PresetManager.activePresetA = null
        PresetManager.activePresetB = null
        PresetManager.activePresetBG = null
        PresetManager.activePresetPV = null
        PresetManager.cachedDtoA = null
        PresetManager.cachedDtoB = null
        PresetManager.cachedDtoBG = null
        PresetManager.cachedDtoPV = null
        PresetManager.activePresetMtimeA = null
        PresetManager.activePresetMtimeB = null
        PresetManager.activePresetMtimeBG = null
        PresetManager.activePresetMtimePV = null

        PlayQueueManager.restoreSessionQueue(emptyList(), -1, false, true, false)
        BgQueueManager.restoreSessionQueue(emptyList(), -1, false, true, false)
        TransitionQueueManager.restoreSessionQueue(emptyList(), -1, false, true, false)
        for (canonicalId in llm.slop.liquidlsd.macro.MacroEngine.CANONICAL_BANK_IDS) {
            llm.slop.liquidlsd.macro.MacroEngine.registerBank(canonicalId, llm.slop.liquidlsd.macro.MacroEngine.newBankFor(canonicalId))
        }
        PresetManager.sessionState = SessionState()
        llm.slop.liquidlsd.midi.MidiMappingManager.invalidateBindings()
        llm.slop.liquidlsd.parameters.ParameterResolver.clearCache()
        mixer.loadDefaultFxBanks()
        // Pre-populate Performance Console's FX row (Super Knob + 3 Metaknobs) so a fresh install
        // is ready to play without the user hand-wiring bindings first. Must run after the
        // canonical MacroBank registration above -- registering later would silently discard
        // these bindings. Only for fresh/empty sessions, not session restore: a returning user's
        // already-tuned Performance Console bindings should never be touched here.
        llm.slop.liquidlsd.macro.FxMacroSync.sync(llm.slop.liquidlsd.macro.MacroEngine.FX_BANK_1, mixer.fxBank1)
        llm.slop.liquidlsd.macro.FxMacroSync.sync(llm.slop.liquidlsd.macro.MacroEngine.FX_BANK_2, mixer.fxBank2)
        llm.slop.liquidlsd.macro.FxMacroSync.sync(llm.slop.liquidlsd.macro.MacroEngine.MASTER_FX, mixer.masterFxBank)
    }

    private fun loadInitialPreset(mixer: Mixer) {
        val initialFile = File(PresetManager.PRESETS_ROOT, "Decks/Liquid LSD Default.json")
        if (initialFile.exists()) {
            PresetRepository.loadDeckPresetAsync(initialFile, isDeckA = true, isManual = false)
        }
    }
}
