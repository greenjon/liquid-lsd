package llm.slop.liquidlsd.ui.browser

import imgui.ImGui
import llm.slop.liquidlsd.SessionContext
import llm.slop.liquidlsd.presets.BgQueueManager
import llm.slop.liquidlsd.rendering.Deck
import llm.slop.liquidlsd.rendering.FxBank
import llm.slop.liquidlsd.rendering.Mixer
import llm.slop.liquidlsd.ui.Icons
import llm.slop.liquidlsd.ui.LibraryPanel
import llm.slop.liquidlsd.ui.ParametersState
import llm.slop.liquidlsd.ui.itemTooltip
import java.io.File

enum class DeckAuditionTarget(val label: String, val deckIndex: Int) {
    DECK_A("Deck A", 1),
    DECK_B("Deck B", 2),
    DECK_BG("Deck BG", 3),
    DECK_PV("Deck PV", 4)
}

object BrowserActionToolbar {
    fun calculateButtonWidth(btnHeight: Float): Float = kotlin.math.round(btnHeight * 1.5f)

    fun calculateToolbarWidth(btnHeight: Float): Float {
        val btnW = calculateButtonWidth(btnHeight)
        return (7 * btnW) + (5 * 6f) + (1 * 14f)
    }

    const val BTN_WIDTH: Float = 36f
    const val TOOLBAR_WIDTH: Float = (7 * BTN_WIDTH) + (5 * 6f) + (1 * 14f)

    var isAuditionLocked: Boolean = false
    var latchedDeckTarget: DeckAuditionTarget? = null

    private var pendingOverwriteDeck: Deck? = null
    private var pendingOverwriteDeckLabel: String = ""
    private var pendingFxFile: File? = null

    /** Extension-aware deck load: FX singles/chains resolve via [Deck.applyFxSlot]/[Deck.applyFxChain]
     *  (first vacant slot, or an overwrite prompt when full); everything else loads as a full preset.
     *  Shared by the toolbar `[A][B][BG][PV]` buttons and LibraryPanel's numeric-key shortcuts so both
     *  paths apply FX items the same way regardless of Library view mode. */
    fun handleDeckLoad(session: SessionContext, mixer: Mixer, deckIndex: Int, deck: Deck, deckLabel: String, selectedFile: File) {
        val ext = selectedFile.extension.lowercase()
        when (ext) {
            "lsdfxchain" -> {
                session.presetRepository.loadFxChainAsync(selectedFile).thenAccept { chainDto ->
                    deck.applyFxChain(chainDto)
                }
            }
            "lsdfx" -> {
                val vacantIndex = (0 until llm.slop.liquidlsd.rendering.FxChain.SLOT_COUNT).firstOrNull { deck.fxSlots[it] == null }
                if (vacantIndex != null) {
                    session.presetRepository.loadFxPresetAsync(selectedFile).thenAccept { presetDto ->
                        deck.applyFxSlot(vacantIndex, presetDto.slot)
                    }
                } else {
                    pendingOverwriteDeck = deck
                    pendingOverwriteDeckLabel = deckLabel
                    pendingFxFile = selectedFile
                    ImGui.openPopup("OverwriteSlotPopup")
                }
            }
            else -> {
                BrowserDeckButtons.loadPresetToDeck(session, mixer, selectedFile, deckIndex)
            }
        }
    }

    fun draw(
        session: SessionContext,
        mixer: Mixer,
        parametersState: ParametersState,
        selectedFile: File?,
        source: LibraryPanel.SelectionSource?,
        btnHeight: Float = 0f
    ) {
        val hasSelection = selectedFile != null && selectedFile.exists()
        val btnH = if (btnHeight > 0f) btnHeight else ImGui.getFrameHeight()
        val btnW = calculateButtonWidth(btnH)

        val ext = selectedFile?.extension?.lowercase() ?: ""
        val isFxItem = ext == "lsdfx" || ext == "lsdfxchain"

        session.uiTheme.withFont(llm.slop.liquidlsd.ui.UITheme.FontLevel.BODY) {
            // 0. [ LOCK / PADLOCK ]
            val lockColor = BrowserDeckButtons.colorLock()
            BrowserDeckButtons.push(lockColor, alpha = 1f, isLatched = isAuditionLocked)
            val lockIcon = if (isAuditionLocked) Icons.LOCK else Icons.UNLOCK
            if (ImGui.button("$lockIcon##toolbar_lock", btnW, btnH)) {
                isAuditionLocked = !isAuditionLocked
                if (isAuditionLocked) {
                    latchedDeckTarget = DeckAuditionTarget.DECK_PV
                    if (selectedFile != null) {
                        handleDeckLoad(session, mixer, 4, mixer.deckPV, "Deck PV", selectedFile)
                    }
                } else {
                    latchedDeckTarget = null
                }
                LibraryPanel.shouldReclaimFocus = true
            }
            val tooltip = if (isAuditionLocked) {
                "Quick Audition Latch: ON (Target: ${latchedDeckTarget?.label ?: "None"}).\nClick presets or use Up/Down arrows to auto-load."
            } else {
                "Quick Audition Latch: OFF.\nClick to arm audition mode (defaults to Deck PV)."
            }
            itemTooltip(tooltip)
            BrowserDeckButtons.pop()

            ImGui.sameLine(0f, 6f)

            // 1. [ A ]
            val isLatchedA = isAuditionLocked && latchedDeckTarget == DeckAuditionTarget.DECK_A
            val alphaA = if (isLatchedA || hasSelection) 1f else 0.35f
            BrowserDeckButtons.push(BrowserDeckButtons.colorA(), alphaA, isLatched = isLatchedA)
            if (ImGui.button("A##toolbar_deck_a", btnW, btnH)) {
                if (isAuditionLocked) {
                    latchedDeckTarget = if (latchedDeckTarget == DeckAuditionTarget.DECK_A) null else DeckAuditionTarget.DECK_A
                    if (latchedDeckTarget == DeckAuditionTarget.DECK_A && selectedFile != null) {
                        handleDeckLoad(session, mixer, 1, mixer.deckA, "Deck A", selectedFile)
                    }
                } else if (selectedFile != null) {
                    handleDeckLoad(session, mixer, 1, mixer.deckA, "Deck A", selectedFile)
                }
                LibraryPanel.shouldReclaimFocus = true
            }
            itemTooltip(if (isAuditionLocked) "Latch audition target to Deck A." else "Load selected item to Deck A (Hotkey: 1).")
            BrowserDeckButtons.pop()

            ImGui.sameLine(0f, 6f)

            // 2. [ B ]
            val isLatchedB = isAuditionLocked && latchedDeckTarget == DeckAuditionTarget.DECK_B
            val alphaB = if (isLatchedB || hasSelection) 1f else 0.35f
            BrowserDeckButtons.push(BrowserDeckButtons.colorB(), alphaB, isLatched = isLatchedB)
            if (ImGui.button("B##toolbar_deck_b", btnW, btnH)) {
                if (isAuditionLocked) {
                    latchedDeckTarget = if (latchedDeckTarget == DeckAuditionTarget.DECK_B) null else DeckAuditionTarget.DECK_B
                    if (latchedDeckTarget == DeckAuditionTarget.DECK_B && selectedFile != null) {
                        handleDeckLoad(session, mixer, 2, mixer.deckB, "Deck B", selectedFile)
                    }
                } else if (selectedFile != null) {
                    handleDeckLoad(session, mixer, 2, mixer.deckB, "Deck B", selectedFile)
                }
                LibraryPanel.shouldReclaimFocus = true
            }
            itemTooltip(if (isAuditionLocked) "Latch audition target to Deck B." else "Load selected item to Deck B (Hotkey: 2).")
            BrowserDeckButtons.pop()

            ImGui.sameLine(0f, 6f)

            // 3. [ BG ]
            val isLatchedBG = isAuditionLocked && latchedDeckTarget == DeckAuditionTarget.DECK_BG
            val alphaBG = if (isLatchedBG || hasSelection) 1f else 0.35f
            BrowserDeckButtons.push(BrowserDeckButtons.colorBG(), alphaBG, isLatched = isLatchedBG)
            if (ImGui.button("BG##toolbar_deck_bg", btnW, btnH)) {
                if (isAuditionLocked) {
                    latchedDeckTarget = if (latchedDeckTarget == DeckAuditionTarget.DECK_BG) null else DeckAuditionTarget.DECK_BG
                    if (latchedDeckTarget == DeckAuditionTarget.DECK_BG && selectedFile != null) {
                        handleDeckLoad(session, mixer, 3, mixer.deckBG, "Deck BG", selectedFile)
                    }
                } else if (selectedFile != null) {
                    handleDeckLoad(session, mixer, 3, mixer.deckBG, "Deck BG", selectedFile)
                }
                LibraryPanel.shouldReclaimFocus = true
            }
            itemTooltip(if (isAuditionLocked) "Latch audition target to Deck BG." else "Load selected item to Deck BG (Hotkey: 3).")
            BrowserDeckButtons.pop()

            ImGui.sameLine(0f, 6f)

            // 4. [ PV ]
            val isLatchedPV = isAuditionLocked && latchedDeckTarget == DeckAuditionTarget.DECK_PV
            val alphaPV = if (isLatchedPV || hasSelection) 1f else 0.35f
            BrowserDeckButtons.push(BrowserDeckButtons.colorPV(), alphaPV, isLatched = isLatchedPV)
            if (ImGui.button("PV##toolbar_deck_pv", btnW, btnH)) {
                if (isAuditionLocked) {
                    latchedDeckTarget = if (latchedDeckTarget == DeckAuditionTarget.DECK_PV) null else DeckAuditionTarget.DECK_PV
                    if (latchedDeckTarget == DeckAuditionTarget.DECK_PV && selectedFile != null) {
                        handleDeckLoad(session, mixer, 4, mixer.deckPV, "Deck PV", selectedFile)
                    }
                } else if (selectedFile != null) {
                    handleDeckLoad(session, mixer, 4, mixer.deckPV, "Deck PV", selectedFile)
                }
                LibraryPanel.shouldReclaimFocus = true
            }
            itemTooltip(if (isAuditionLocked) "Latch audition target to Deck PV." else "Preview selected item on Deck PV (Hotkey: 4).")
            BrowserDeckButtons.pop()

            ImGui.sameLine(0f, 14f)

            // 5. [ Q ] (Disabled for FX items or when already in Play Queue A/B)
            val canQueueAB = hasSelection && !isFxItem && source != LibraryPanel.SelectionSource.QUEUE_AB
            val alphaQ = if (canQueueAB) 1f else 0.35f
            BrowserDeckButtons.push(BrowserDeckButtons.colorQ(), alphaQ)
            if (ImGui.button("Q##toolbar_deck_q", btnW, btnH) && canQueueAB) {
                session.playQueueManager.appendToQueue(selectedFile)
                LibraryPanel.shouldReclaimFocus = true
            }
            val qTip = if (isFxItem) "Queueing is for full visual presets." else if (source == LibraryPanel.SelectionSource.QUEUE_AB) "Preset is already in the A/B Play Queue." else "Add selected preset to the A/B Play Queue (Hotkey: Q)."
            itemTooltip(qTip)
            BrowserDeckButtons.pop()

            ImGui.sameLine(0f, 6f)

            // 6. [ BGQ ] (Disabled for FX items or when already in BG Queue)
            val canQueueBG = hasSelection && !isFxItem && source != LibraryPanel.SelectionSource.QUEUE_BG
            val alphaBGQ = if (canQueueBG) 1f else 0.35f
            BrowserDeckButtons.push(BrowserDeckButtons.colorBGQ(), alphaBGQ)
            if (ImGui.button("BGQ##toolbar_deck_bgq", btnW, btnH) && canQueueBG) {
                BgQueueManager.appendToQueue(selectedFile)
                LibraryPanel.shouldReclaimFocus = true
            }
            val bgqTip = if (isFxItem) "Queueing is for full visual presets." else if (source == LibraryPanel.SelectionSource.QUEUE_BG) "Preset is already in the Background Queue." else "Add selected preset to the Background Queue (Hotkey: Shift+Q)."
            itemTooltip(bgqTip)
            BrowserDeckButtons.pop()

            // Overwrite Slot Selection Popup
            if (ImGui.beginPopup("OverwriteSlotPopup")) {
                val deck = pendingOverwriteDeck
                val file = pendingFxFile
                ImGui.textDisabled("${pendingOverwriteDeckLabel} FX slots are full. Select slot to overwrite:")
                ImGui.separator()
                if (deck != null && file != null && file.exists()) {
                    for (s in 0 until llm.slop.liquidlsd.rendering.FxChain.SLOT_COUNT) {
                        val slotNum = s + 1
                        val fx = deck.fxSlots[s]
                        val label = if (fx != null) "Slot $slotNum: ${fx.displayName}" else "Slot $slotNum: Empty"
                        if (ImGui.menuItem(label)) {
                            session.presetRepository.loadFxPresetAsync(file).thenAccept { presetDto ->
                                deck.applyFxSlot(s, presetDto.slot)
                            }
                        }
                    }
                }
                ImGui.endPopup()
            }
        }
    }
}
