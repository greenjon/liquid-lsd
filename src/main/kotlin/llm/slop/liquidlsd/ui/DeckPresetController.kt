package llm.slop.liquidlsd.ui

import java.io.File
import llm.slop.liquidlsd.SessionContext
import llm.slop.liquidlsd.models.toDto
import llm.slop.liquidlsd.presets.DeckSlot
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
            session.presetRepository.loadDeckPresetAsync(file, isDeckA = isDeckA, isDeckPV = isDeckPV)
        }
    }

    fun saveDeckPreset(mixer: Mixer, name: String, deck: Deck, isDeckA: Boolean, tags: List<String>? = null) {
        if (deck.source is llm.slop.liquidlsd.rendering.ExternalVideoSource) return
        val cleanName = name.removeSuffix(".lsd").trim()
        if (cleanName.isBlank()) return

        val slot = DeckSlot.of(deck, mixer)
        val resolvedTags = tags ?: slot?.let { session.presetManager.cachedDto(it)?.tags } ?: emptyList()

        val dto = deck.toDto(cleanName, resolvedTags)
        slot?.let { session.presetManager.setActive(it, cleanName, dto) }
        val file = File("library/presets/$cleanName.lsd")
        val deckIndex = slot?.index ?: -1
        session.presetRepository.saveDeckPresetAsync(file, deck, cleanName, resolvedTags, deckIndex)
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
                if (deck.source !is llm.slop.liquidlsd.rendering.ExternalVideoSource) {
                    val slot = DeckSlot.of(deck, mixer) ?: DeckSlot.PV
                    val activeName = session.presetManager.activePreset(slot)
                    val saveName = if (!activeName.isNullOrBlank() && activeName != "None") {
                        activeName
                    } else {
                        "AutoSave_${slot.label.replace(" ", "")}_${System.currentTimeMillis()}"
                    }
                    saveDeckPreset(mixer, saveName, deck, deck === mixer.deckA)
                }
                onProceed()
            }
            UITheme.AutoVjDirtyBehavior.AUTO_DISCARD -> {
                onProceed()
            }
            UITheme.AutoVjDirtyBehavior.SKIP -> {
                val deckLabel = (DeckSlot.of(deck, mixer) ?: DeckSlot.PV).label
                popupManager.requestDeckConfirm(deck, deckLabel, onProceed)
            }
        }
    }

    fun handleUtilityAction(mixer: Mixer, mode: Int, from: Deck, to: Deck) {
        guardDeckTransition(mixer, to) {
            when (mode) {
                0 -> session.deckLifecycleManager.moveDeck(mixer, from, to)
                1 -> session.deckLifecycleManager.copyDeck(mixer, from, to)
                2 -> session.deckLifecycleManager.swapDecks(mixer, from, to)
            }
        }
    }

    fun handleSaveDeck(mixer: Mixer, deck: Deck, isDeckA: Boolean, isSaveAs: Boolean) {
        if (deck.source is llm.slop.liquidlsd.rendering.ExternalVideoSource) return
        val slot = DeckSlot.of(deck, mixer)
        val activeName = slot?.let { session.presetManager.activePreset(it) }
        if (activeName != null && !isSaveAs) {
            saveDeckPreset(mixer, activeName, deck, isDeckA)
        } else {
            val cached = slot?.let { session.presetManager.cachedDto(it) }
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
            session.presetRepository.loadDeckPresetAsync(
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
        state: ParametersState
    ) {
        val currentSource = deck.source
        if (currentSource == newSource) return

        val activeName = session.presetManager.activePreset(DeckSlot.of(deck, mixer) ?: DeckSlot.PV)
        val isDirty = session.presetManager.isDeckDirty(deck, mixer)

        val doSwitch = {
            DeckSourcePicker.swapSource(session, state, mixer, deck, deckLabel, newSource)
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
        session.deckLifecycleManager.clearDeckActivePreset(deck, mixer)
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
            session.presetRepository.loadDeckPresetAsync(file, true)
        }
        deckBFileBrowser.draw { file ->
            session.presetRepository.loadDeckPresetAsync(file, false)
        }
    }
}
