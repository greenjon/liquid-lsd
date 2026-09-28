package llm.slop.liquidlsd.ui.browser

import imgui.ImGui
import llm.slop.liquidlsd.SessionContext
import llm.slop.liquidlsd.presets.BgQueueManager
import llm.slop.liquidlsd.rendering.Deck
import llm.slop.liquidlsd.rendering.Mixer
import llm.slop.liquidlsd.ui.Icons
import llm.slop.liquidlsd.ui.LibraryPanel
import llm.slop.liquidlsd.ui.ParametersState
import llm.slop.liquidlsd.ui.itemTooltip
import llm.slop.liquidlsd.ui.pushOpenDropdownPadding
import llm.slop.liquidlsd.ui.popOpenDropdownPadding
import llm.slop.liquidlsd.ui.pushOpenDropdownFont
import llm.slop.liquidlsd.ui.popOpenDropdownFont
import java.io.File

object BrowserActionToolbar {
    fun calculateButtonWidth(btnHeight: Float): Float = kotlin.math.round(btnHeight * 1.5f)

    fun calculateToolbarWidth(btnHeight: Float): Float {
        val btnW = calculateButtonWidth(btnHeight)
        return (3 * btnW) + (1 * 6f) + (1 * 14f)
    }

    const val BTN_WIDTH: Float = 36f
    const val TOOLBAR_WIDTH: Float = (3 * BTN_WIDTH) + (1 * 6f) + (1 * 14f)

    var isAuditionLocked: Boolean = false

    private var pendingOverwriteDeck: Deck? = null
    private var pendingOverwriteDeckLabel: String = ""
    private var pendingFxFile: File? = null

    /** Extension-aware deck load: FX singles/chains resolve via [llm.slop.liquidlsd.presets.FxOps]
     *  (first vacant slot, or an overwrite prompt when full); everything else loads as a full preset.
     *  Used by the Quick Audition Latch to preview selections on Deck PV. */
    fun handleDeckLoad(session: SessionContext, mixer: Mixer, deckIndex: Int, deck: Deck, deckLabel: String, selectedFile: File) {
        val ext = selectedFile.extension.lowercase()
        when (ext) {
            "lsdfxchain" -> {
                llm.slop.liquidlsd.presets.FxOps.loadChain(session, selectedFile, deck.fxChain)
            }
            "lsdfx" -> {
                val vacantIndex = (0 until llm.slop.liquidlsd.rendering.FxChain.SLOT_COUNT).firstOrNull { deck.fxSlots[it] == null }
                if (vacantIndex != null) {
                    llm.slop.liquidlsd.presets.FxOps.loadSlot(session, selectedFile, deck.fxChain, vacantIndex)
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
            // 0. [ LOCK / PADLOCK ] — Quick Audition Latch: auto-preview selections on Deck PV
            val lockColor = BrowserDeckButtons.colorLock()
            BrowserDeckButtons.push(lockColor, alpha = 1f, isLatched = isAuditionLocked)
            val lockIcon = if (isAuditionLocked) Icons.LOCK else Icons.UNLOCK
            if (ImGui.button("$lockIcon##toolbar_lock", btnW, btnH)) {
                isAuditionLocked = !isAuditionLocked
                if (isAuditionLocked && selectedFile != null) {
                    handleDeckLoad(session, mixer, 4, mixer.deckPV, "Deck PV", selectedFile)
                }
                LibraryPanel.shouldReclaimFocus = true
            }
            val tooltip = if (isAuditionLocked) {
                "Quick Audition Latch: ON (Deck PV).\nClick presets or use Up/Down arrows to auto-load."
            } else {
                "Quick Audition Latch: OFF.\nClick to auto-preview selections on Deck PV."
            }
            itemTooltip(tooltip)
            BrowserDeckButtons.pop()

            ImGui.sameLine(0f, 14f)

            // 1. [ Q ] (Disabled for FX items or when already in Play Queue A/B)
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

            // 2. [ BGQ ] (Disabled for FX items or when already in BG Queue)
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
            pushOpenDropdownPadding()
            if (ImGui.beginPopup("OverwriteSlotPopup")) {
                pushOpenDropdownFont()
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
                            llm.slop.liquidlsd.presets.FxOps.loadSlot(session, file, deck.fxChain, s)
                        }
                    }
                }
                popOpenDropdownFont()
                ImGui.endPopup()
            }
            popOpenDropdownPadding()
        }
    }
}
