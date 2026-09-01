package llm.slop.liquidlsd.ui

import java.io.File
import llm.slop.liquidlsd.SessionContext
import llm.slop.liquidlsd.models.toDto
import llm.slop.liquidlsd.rendering.Deck
import llm.slop.liquidlsd.rendering.Mixer

/**
 * Manages deck preset workflows: saving, loading, ejecting, copying/moving/swapping,
 * dirty-state confirmation routing, and ImGui file dialogs.
 */
class DeckPresetController(
    private val session: SessionContext,
    private val popupManager: PopupManager
) {
    val deckAFileBrowser = ImGuiFileBrowser("deckAFileBrowser")
    val deckBFileBrowser = ImGuiFileBrowser("deckBFileBrowser")

    fun performLoadDeckPreset(isDeckA: Boolean) {
        val browser = if (isDeckA) deckAFileBrowser else deckBFileBrowser
        val dir = File("library/presets")
        browser.open(
            ImGuiFileBrowser.Mode.LOAD,
            startDir = dir.canonicalFile
        )
    }

    fun loadDeckPreset(mixer: Mixer, presetName: String, deck: Deck, isDeckA: Boolean, isDeckPV: Boolean = (deck === mixer.deckPV)) {
        if (presetName == "None") return
        val cleanName = presetName.removeSuffix(".lsd").trim()
        val file = File("library/presets/$cleanName.lsd")
        if (file.exists()) {
            session.presetManager.loadDeckPresetAsync(file, isDeckA = isDeckA, isDeckPV = isDeckPV)
        }
    }

    fun saveDeckPreset(mixer: Mixer, name: String, deck: Deck, isDeckA: Boolean, tags: List<String>? = null) {
        val cleanName = name.removeSuffix(".lsd").trim()
        if (cleanName.isBlank()) return

        val resolvedTags = tags ?: run {
            val cached = when {
                deck === mixer.deckA -> session.presetManager.cachedDtoA
                deck === mixer.deckB -> session.presetManager.cachedDtoB
                deck === mixer.deckBG -> session.presetManager.cachedDtoBG
                deck === mixer.deckPV -> session.presetManager.cachedDtoPV
                else -> null
            }
            cached?.tags ?: emptyList()
        }

        val dto = deck.toDto(cleanName, resolvedTags)
        when {
            deck === mixer.deckA -> {
                session.presetManager.activePresetA = cleanName
                session.presetManager.cachedDtoA = dto
            }
            deck === mixer.deckB -> {
                session.presetManager.activePresetB = cleanName
                session.presetManager.cachedDtoB = dto
            }
            deck === mixer.deckBG -> {
                session.presetManager.activePresetBG = cleanName
                session.presetManager.cachedDtoBG = dto
            }
            deck === mixer.deckPV -> {
                session.presetManager.activePresetPV = cleanName
                session.presetManager.cachedDtoPV = dto
            }
        }
        val file = File("library/presets/$cleanName.lsd")

        val deckIndex = when {
            deck === mixer.deckA -> 0
            deck === mixer.deckB -> 1
            deck === mixer.deckBG -> 2
            deck === mixer.deckPV -> 3
            else -> -1
        }
        session.presetManager.saveDeckPresetAsync(file, deck, cleanName, resolvedTags, deckIndex)
    }

    /**
     * Guards any operation that overwrites or resets a deck (eject, load, move, copy, swap, new preset).
     * If the target deck is clean, [onProceed] is called immediately.
     * If dirty, respects [UITheme.AutoVjDirtyBehavior]:
     * - AUTO_SAVE: Silently saves the dirty preset to disk, then runs [onProceed].
     * - AUTO_DISCARD: Runs [onProceed] immediately without saving.
     * - SKIP (Prompt): Asks the user via [PopupManager] confirmation modal.
     */
    fun guardDeckTransition(mixer: Mixer, deck: Deck, onProceed: () -> Unit) {
        val isDirty = session.presetManager.isDeckDirty(deck, mixer)
        if (!isDirty) {
            onProceed()
            return
        }

        when (session.uiTheme.autoVjDirtyBehavior) {
            UITheme.AutoVjDirtyBehavior.AUTO_SAVE -> {
                val activeName = when {
                    deck === mixer.deckA -> session.presetManager.activePresetA
                    deck === mixer.deckB -> session.presetManager.activePresetB
                    deck === mixer.deckBG -> session.presetManager.activePresetBG
                    else -> session.presetManager.activePresetPV
                }
                val deckLabel = when {
                    deck === mixer.deckA -> "Deck A"
                    deck === mixer.deckB -> "Deck B"
                    deck === mixer.deckBG -> "Deck BG"
                    else -> "Deck PV"
                }
                val saveName = if (!activeName.isNullOrBlank() && activeName != "None") {
                    activeName
                } else {
                    "AutoSave_${deckLabel.replace(" ", "")}_${System.currentTimeMillis()}"
                }
                saveDeckPreset(mixer, saveName, deck, deck === mixer.deckA)
                onProceed()
            }
            UITheme.AutoVjDirtyBehavior.AUTO_DISCARD -> {
                onProceed()
            }
            UITheme.AutoVjDirtyBehavior.SKIP -> {
                val deckLabel = when {
                    deck === mixer.deckA -> "Deck A"
                    deck === mixer.deckB -> "Deck B"
                    deck === mixer.deckBG -> "Deck BG"
                    else -> "Deck PV"
                }
                popupManager.requestDeckConfirm(deck, deckLabel, onProceed)
            }
        }
    }

    fun handleUtilityAction(mixer: Mixer, mode: Int, from: Deck, to: Deck) {
        guardDeckTransition(mixer, to) {
            when (mode) {
                0 -> session.presetManager.moveDeck(mixer, from, to)
                1 -> session.presetManager.copyDeck(mixer, from, to)
                2 -> session.presetManager.swapDecks(mixer, from, to)
            }
        }
    }

    fun handleSaveDeck(mixer: Mixer, deck: Deck, isDeckA: Boolean, isSaveAs: Boolean) {
        val activeName = when {
            deck === mixer.deckA -> session.presetManager.activePresetA
            deck === mixer.deckB -> session.presetManager.activePresetB
            deck === mixer.deckBG -> session.presetManager.activePresetBG
            deck === mixer.deckPV -> session.presetManager.activePresetPV
            else -> null
        }
        if (activeName != null && !isSaveAs) {
            saveDeckPreset(mixer, activeName, deck, isDeckA)
        } else {
            val cached = when {
                deck === mixer.deckA -> session.presetManager.cachedDtoA
                deck === mixer.deckB -> session.presetManager.cachedDtoB
                deck === mixer.deckBG -> session.presetManager.cachedDtoBG
                deck === mixer.deckPV -> session.presetManager.cachedDtoPV
                else -> null
            }
            val defaultName = if (activeName != null && isSaveAs) {
                generateUniqueCopyName(activeName)
            } else {
                activeName ?: ""
            }
            val defaultTags = cached?.tags ?: emptyList()
            SavePresetModal.request(
                title = "Save Preset As",
                confirmLabel = "Save",
                defaultName = defaultName,
                defaultTags = defaultTags,
                originalPath = activeName?.let { "library/presets/$it.lsd" }
            ) { name, tags ->
                saveDeckPreset(mixer, name, deck, isDeckA, tags)
            }
        }
    }

    fun handleEjectDeck(mixer: Mixer, deck: Deck, isDeckA: Boolean = false, isDeckPV: Boolean = false) {
        guardDeckTransition(mixer, deck) {
            performEjectDeck(mixer, deck)
        }
    }

    fun loadDeckPresetSafely(mixer: Mixer, deck: Deck, file: File) {
        guardDeckTransition(mixer, deck) {
            session.presetManager.loadDeckPresetAsync(
                file,
                isDeckA = deck === mixer.deckA,
                isDeckBG = deck === mixer.deckBG,
                isDeckPV = deck === mixer.deckPV
            )
        }
    }

    fun newPresetSafely(mixer: Mixer, deck: Deck) {
        guardDeckTransition(mixer, deck) {
            performEjectDeck(mixer, deck)
        }
    }

    fun changeVisualSourceSafely(
        mixer: Mixer,
        deck: Deck,
        deckLabel: String,
        newSource: llm.slop.liquidlsd.rendering.VisualSource,
        state: PresetGridState
    ) {
        val currentSource = deck.source
        if (currentSource == newSource) return

        val activeName = when {
            deck === mixer.deckA -> session.presetManager.activePresetA
            deck === mixer.deckB -> session.presetManager.activePresetB
            deck === mixer.deckBG -> session.presetManager.activePresetBG
            else -> session.presetManager.activePresetPV
        }
        val isDirty = session.presetManager.isDeckDirty(deck, mixer)

        val doSwitch = {
            deck.source = newSource.clone()
            deck.isEmpty = false
            session.presetManager.clearDeckActivePreset(deck, mixer)
            state.clearSelection()
            state.setDeckSubTab(deckLabel, "SRC")
            PresetGridUndo.pushUndoState(state, mixer)
        }

        if (!activeName.isNullOrBlank() || isDirty) {
            val oldName = if (currentSource is llm.slop.liquidlsd.rendering.Mandala) "Mandala" else currentSource.displayName
            val newName = if (newSource is llm.slop.liquidlsd.rendering.Mandala) "Mandala" else newSource.displayName
            popupManager.requestSourceChangeConfirm(deck, deckLabel, oldName, newName, doSwitch)
        } else {
            doSwitch()
        }
    }

    fun performEjectDeck(mixer: Mixer, deck: Deck) {
        deck.reset()
        session.presetManager.clearDeckActivePreset(deck, mixer)
    }

    fun generateUniqueCopyName(baseName: String): String {
        val presetsDir = FileSystemManager.getPresetsRoot()
        val cleanBase = baseName.removeSuffix(".lsd").trim()
        val candidate1 = "${cleanBase}_copy"
        if (!File(presetsDir, "$candidate1.lsd").exists()) return candidate1

        var idx = 2
        while (true) {
            val candidate = "${cleanBase}_copy$idx"
            if (!File(presetsDir, "$candidate.lsd").exists()) return candidate
            idx++
        }
    }

    fun triggerDeckDragDrop(file: File, deck: Deck, isDeckA: Boolean, mixer: Mixer) {
        loadDeckPresetSafely(mixer, deck, file)
    }

    fun drawFileBrowsers() {
        deckAFileBrowser.draw { file ->
            session.presetManager.loadDeckPresetAsync(file, true)
        }
        deckBFileBrowser.draw { file ->
            session.presetManager.loadDeckPresetAsync(file, false)
        }
    }
}
