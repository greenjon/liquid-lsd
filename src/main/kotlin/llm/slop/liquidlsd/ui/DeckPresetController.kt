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

    fun loadDeckPreset(mixer: Mixer, presetName: String, deck: Deck, isDeckA: Boolean, isDeckC: Boolean = (deck === mixer.deckC)) {
        if (presetName == "None") return
        val cleanName = presetName.removeSuffix(".lsd").trim()
        val file = File("library/presets/$cleanName.lsd")
        if (file.exists()) {
            session.presetManager.loadDeckPresetAsync(file, isDeckA, isDeckC)
        }
    }

    fun saveDeckPreset(mixer: Mixer, name: String, deck: Deck, isDeckA: Boolean, tags: List<String>? = null) {
        val cleanName = name.removeSuffix(".lsd").trim()
        if (cleanName.isBlank()) return

        val resolvedTags = tags ?: run {
            val cached = when {
                deck === mixer.deckA -> session.presetManager.cachedDtoA
                deck === mixer.deckB -> session.presetManager.cachedDtoB
                deck === mixer.deckC -> session.presetManager.cachedDtoC
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
            deck === mixer.deckC -> {
                session.presetManager.activePresetC = cleanName
                session.presetManager.cachedDtoC = dto
            }
        }
        val file = File("library/presets/$cleanName.lsd")

        val deckIndex = when {
            deck === mixer.deckA -> 0
            deck === mixer.deckB -> 1
            deck === mixer.deckC -> 2
            else -> -1
        }
        session.presetManager.saveDeckPresetAsync(file, deck, cleanName, resolvedTags, deckIndex)
    }

    fun handleUtilityAction(mixer: Mixer, mode: Int, from: Deck, to: Deck) {
        val isDirty = session.presetManager.isDeckDirty(to, mixer)
        if (!isDirty) {
            when (mode) {
                0 -> session.presetManager.moveDeck(mixer, from, to)
                1 -> session.presetManager.copyDeck(mixer, from, to)
                2 -> session.presetManager.swapDecks(mixer, from, to)
            }
        } else {
            when (to) {
                mixer.deckA -> {
                    popupManager.pendingDeckActionA = when (mode) { 0 -> PopupManager.PendingDeckAction.MOVE; 1 -> PopupManager.PendingDeckAction.COPY; else -> PopupManager.PendingDeckAction.SWAP }
                    popupManager.pendingDeckUtilitySourceA = from
                }
                mixer.deckB -> {
                    popupManager.pendingDeckActionB = when (mode) { 0 -> PopupManager.PendingDeckAction.MOVE; 1 -> PopupManager.PendingDeckAction.COPY; else -> PopupManager.PendingDeckAction.SWAP }
                    popupManager.pendingDeckUtilitySourceB = from
                }
                mixer.deckC -> {
                    popupManager.pendingDeckActionC = when (mode) { 0 -> PopupManager.PendingDeckAction.MOVE; 1 -> PopupManager.PendingDeckAction.COPY; else -> PopupManager.PendingDeckAction.SWAP }
                    popupManager.pendingDeckUtilitySourceC = from
                }
            }
        }
    }

    fun handleSaveDeck(mixer: Mixer, deck: Deck, isDeckA: Boolean, isSaveAs: Boolean) {
        val activeName = when {
            deck === mixer.deckA -> session.presetManager.activePresetA
            deck === mixer.deckB -> session.presetManager.activePresetB
            deck === mixer.deckC -> session.presetManager.activePresetC
            else -> null
        }
        if (activeName != null && !isSaveAs) {
            saveDeckPreset(mixer, activeName, deck, isDeckA)
        } else {
            val cached = when {
                deck === mixer.deckA -> session.presetManager.cachedDtoA
                deck === mixer.deckB -> session.presetManager.cachedDtoB
                deck === mixer.deckC -> session.presetManager.cachedDtoC
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

    fun handleEjectDeck(mixer: Mixer, deck: Deck, isDeckA: Boolean, isDeckC: Boolean = false) {
        val isDirty = session.presetManager.isDeckDirty(deck, mixer)
        if (!isDirty) {
            performEjectDeck(mixer, deck)
        } else {
            when (session.uiTheme.autoVjDirtyBehavior) {
                UITheme.AutoVjDirtyBehavior.AUTO_SAVE -> {
                    val activeName = when {
                        deck === mixer.deckC -> session.presetManager.activePresetC
                        deck === mixer.deckA -> session.presetManager.activePresetA
                        else -> session.presetManager.activePresetB
                    }
                    if (activeName != null && activeName != "None") {
                        saveDeckPreset(mixer, activeName, deck, isDeckA)
                    }
                    performEjectDeck(mixer, deck)
                }
                UITheme.AutoVjDirtyBehavior.AUTO_DISCARD -> {
                    performEjectDeck(mixer, deck)
                }
                UITheme.AutoVjDirtyBehavior.SKIP -> {
                    if (deck === mixer.deckC) {
                        popupManager.pendingDeckActionC = PopupManager.PendingDeckAction.NEW
                    } else if (deck === mixer.deckA) {
                        popupManager.pendingDeckActionA = PopupManager.PendingDeckAction.NEW
                    } else {
                        popupManager.pendingDeckActionB = PopupManager.PendingDeckAction.NEW
                    }
                }
            }
        }
    }

    fun performEjectDeck(mixer: Mixer, deck: Deck) {
        deck.reset()
        when {
            deck === mixer.deckA -> {
                session.presetManager.activePresetA = null
                session.presetManager.cachedDtoA = null
            }
            deck === mixer.deckB -> {
                session.presetManager.activePresetB = null
                session.presetManager.cachedDtoB = null
            }
            deck === mixer.deckC -> {
                session.presetManager.activePresetC = null
                session.presetManager.cachedDtoC = null
            }
        }
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
        val isDeckC = deck === mixer.deckC
        if (isDeckC) {
            popupManager.pendingDeckActionC = PopupManager.PendingDeckAction.DRAG_DROP
            popupManager.pendingDeckSourceFileC = file
        } else if (isDeckA) {
            popupManager.pendingDeckActionA = PopupManager.PendingDeckAction.DRAG_DROP
            popupManager.pendingDeckSourceFileA = file
        } else {
            popupManager.pendingDeckActionB = PopupManager.PendingDeckAction.DRAG_DROP
            popupManager.pendingDeckSourceFileB = file
        }
    }

    fun onExecuteDeckAction(mixer: Mixer, deck: Deck, isDeckA: Boolean, action: PopupManager.PendingDeckAction, targetPreset: String?) {
        when (action) {
            PopupManager.PendingDeckAction.NEW -> {
                deck.reset()
                when {
                    deck === mixer.deckA -> {
                        session.presetManager.activePresetA = null
                        session.presetManager.cachedDtoA = null
                    }
                    deck === mixer.deckB -> {
                        session.presetManager.activePresetB = null
                        session.presetManager.cachedDtoB = null
                    }
                    deck === mixer.deckC -> {
                        session.presetManager.activePresetC = null
                        session.presetManager.cachedDtoC = null
                    }
                }
            }
            PopupManager.PendingDeckAction.LOAD_FILE -> {
                performLoadDeckPreset(isDeckA)
            }
            PopupManager.PendingDeckAction.LOAD_PRESET -> {
                if (targetPreset != null) {
                    if (targetPreset == "None") {
                        when {
                            deck === mixer.deckA -> {
                                session.presetManager.activePresetA = null
                                session.presetManager.cachedDtoA = null
                            }
                            deck === mixer.deckB -> {
                                session.presetManager.activePresetB = null
                                session.presetManager.cachedDtoB = null
                            }
                            deck === mixer.deckC -> {
                                session.presetManager.activePresetC = null
                                session.presetManager.cachedDtoC = null
                            }
                        }
                    } else {
                        loadDeckPreset(mixer, targetPreset, deck, deck === mixer.deckA)
                    }
                }
            }
            else -> {}
        }
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
