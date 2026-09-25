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
                masterFxChain = mixer.masterFxChain.toFxChainDto(),
                masterFxChainSource = mixer.masterFxChain.sourceFile?.let { serializeSessionPath(it) },
                deckAFxChain = mixer.deckA.fxChain.toFxChainDto(),
                deckAFxChainSource = mixer.deckA.fxChain.sourceFile?.let { serializeSessionPath(it) },
                deckBFxChain = mixer.deckB.fxChain.toFxChainDto(),
                deckBFxChainSource = mixer.deckB.fxChain.sourceFile?.let { serializeSessionPath(it) },
                deckBGFxChain = mixer.deckBG.fxChain.toFxChainDto(),
                deckBGFxChainSource = mixer.deckBG.fxChain.sourceFile?.let { serializeSessionPath(it) },
                deckPVFxChain = mixer.deckPV.fxChain.toFxChainDto(),
                deckPVFxChainSource = mixer.deckPV.fxChain.sourceFile?.let { serializeSessionPath(it) }
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
     * banks with [llm.slop.liquidlsd.macro.MacroEngine] -- independent of whether the Performance
     * panel or Column 3's MACROS tab is ever opened this run, since both read/write these
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

            mixer.deckA.applyDto(session.deckA)
            mixer.deckB.applyDto(session.deckB)
            
            val bgDto = session.deckBG ?: PresetManager.emptyDeckDto(mixer.deckBG, mixer)
            mixer.deckBG.applyDto(bgDto)

            val pvDto = session.deckPV ?: PresetManager.emptyDeckDto(mixer.deckPV, mixer)
            mixer.deckPV.applyDto(pvDto)

            mDto.masterFxChain?.let { mixer.masterFxChain.applyFxChain(it, mDto.masterFxChainSource?.let { p -> resolveSessionPath(p) }, isBaseline = false) }
            mDto.deckAFxChain?.let { mixer.deckA.fxChain.applyFxChain(it, mDto.deckAFxChainSource?.let { p -> resolveSessionPath(p) }, isBaseline = false) }
            mDto.deckBFxChain?.let { mixer.deckB.fxChain.applyFxChain(it, mDto.deckBFxChainSource?.let { p -> resolveSessionPath(p) }, isBaseline = false) }
            mDto.deckBGFxChain?.let { mixer.deckBG.fxChain.applyFxChain(it, mDto.deckBGFxChainSource?.let { p -> resolveSessionPath(p) }, isBaseline = false) }
            mDto.deckPVFxChain?.let { mixer.deckPV.fxChain.applyFxChain(it, mDto.deckPVFxChainSource?.let { p -> resolveSessionPath(p) }, isBaseline = false) }

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
            val fxChainDtos = listOf(
                "Master FX" to mDto.masterFxChain, "Deck A FX" to mDto.deckAFxChain, "Deck B FX" to mDto.deckBFxChain,
                "Deck BG FX" to mDto.deckBGFxChain, "Deck PV FX" to mDto.deckPVFxChain
            )
            for ((chainLabel, chainDto) in fxChainDtos) {
                chainDto?.slots?.forEachIndexed { i, fxDto ->
                    if (fxDto != null && fxDto.filterId.isNotBlank() && llm.slop.liquidlsd.rendering.isf.ISFFilterRegistry.availableFilters.none { it.id == fxDto.filterId }) {
                        allUnresolved.add("$chainLabel${i + 1} filter not found: ${fxDto.filterId}")
                    }
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
            // Refresh FX row knob labels/bindings against the chains actually restored above. Not
            // forced: FxMacroSync's ownership rule leaves any knob the user retargeted untouched.
            llm.slop.liquidlsd.macro.FxMacroSync.syncAll(mixer)

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
        // loadDefaultFxChains() also pre-populates every FX row's knobs (Super Knob + 3 Metaknobs)
        // via FxMacroSync, so a fresh install is ready to play. Must run after the canonical
        // MacroBank registration above -- registering later would silently discard those bindings.
        // Only for fresh/empty sessions: a restored session's tuned bindings are never touched here.
        mixer.loadDefaultFxChains()
    }

    private fun loadInitialPreset(mixer: Mixer) {
        val initialFile = File(PresetManager.PRESETS_ROOT, "Decks/Liquid LSD Default.json")
        if (initialFile.exists()) {
            PresetRepository.loadDeckPresetAsync(initialFile, isDeckA = true, isManual = false)
        }
    }
}
