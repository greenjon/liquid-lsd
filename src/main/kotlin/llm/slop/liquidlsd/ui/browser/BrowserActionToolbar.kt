package llm.slop.liquidlsd.ui.browser

import imgui.ImGui
import llm.slop.liquidlsd.SessionContext
import llm.slop.liquidlsd.rendering.Deck
import llm.slop.liquidlsd.rendering.Mixer
import llm.slop.liquidlsd.ui.AssetItem
import llm.slop.liquidlsd.ui.AssetType
import llm.slop.liquidlsd.ui.Icons
import llm.slop.liquidlsd.ui.LibraryNavigation
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
                val vacantIndex = llm.slop.liquidlsd.presets.FxOps.firstVacantSlot(deck.fxChain)
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
        val btnH = if (btnHeight > 0f) btnHeight else ImGui.getFrameHeight()
        val btnW = calculateButtonWidth(btnH)

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

            // 1. [ Q ] / 2. [ BGQ ]: act on the *visible tab's* selection and queue, exactly like the
            // Q / Shift+Q hotkeys (LibraryNavigation.enqueue). Sources -> A/B or BG play queue,
            // FX -> FX queues, Trans -> transition queue (which has no BG variant).
            val mode = LibraryPanel.viewMode
            val selectedFiles = LibraryNavigation.enqueueTargets(session)
            val inQueueA = source == LibraryPanel.SelectionSource.QUEUE_AB ||
                source == LibraryPanel.SelectionSource.FX_QUEUE_AB ||
                source == LibraryPanel.SelectionSource.TRANSITION_QUEUE
            val inQueueBG = source == LibraryPanel.SelectionSource.QUEUE_BG ||
                source == LibraryPanel.SelectionSource.FX_QUEUE_BG
            val noun = when (mode) {
                LibraryPanel.LibraryViewMode.PRESETS -> "preset"
                LibraryPanel.LibraryViewMode.FX -> "FX item"
                LibraryPanel.LibraryViewMode.TRANS -> "transition"
                LibraryPanel.LibraryViewMode.MAPS -> "item"
            }
            val queueA = when (mode) {
                LibraryPanel.LibraryViewMode.PRESETS -> "A/B Play Queue"
                LibraryPanel.LibraryViewMode.FX -> "FX Queue"
                LibraryPanel.LibraryViewMode.TRANS -> "Transition Queue"
                LibraryPanel.LibraryViewMode.MAPS -> "queue"
            }
            val queueBG = when (mode) {
                LibraryPanel.LibraryViewMode.PRESETS -> "Background Queue"
                LibraryPanel.LibraryViewMode.FX -> "FX Background Queue"
                LibraryPanel.LibraryViewMode.TRANS -> null
                LibraryPanel.LibraryViewMode.MAPS -> null
            }
            val many = selectedFiles.size > 1

            val canQueueAB = selectedFiles.isNotEmpty() && !inQueueA
            BrowserDeckButtons.push(BrowserDeckButtons.colorQ(), if (canQueueAB) 1f else 0.35f)
            if (ImGui.button("Q##toolbar_deck_q", btnW, btnH) && canQueueAB) {
                LibraryNavigation.enqueue(session, bg = false)
                LibraryPanel.shouldReclaimFocus = true
            }
            itemTooltip(
                if (inQueueA) "Already in the $queueA."
                else if (many) "Add ${selectedFiles.size} selected ${noun}s to the $queueA (Hotkey: Q)."
                else "Add selected $noun to the $queueA (Hotkey: Q)."
            )
            BrowserDeckButtons.pop()

            ImGui.sameLine(0f, 6f)

            val canQueueBG = selectedFiles.isNotEmpty() && queueBG != null && !inQueueBG
            BrowserDeckButtons.push(BrowserDeckButtons.colorBGQ(), if (canQueueBG) 1f else 0.35f)
            if (ImGui.button("BGQ##toolbar_deck_bgq", btnW, btnH) && canQueueBG) {
                LibraryNavigation.enqueue(session, bg = true)
                LibraryPanel.shouldReclaimFocus = true
            }
            itemTooltip(
                if (queueBG == null) "Transitions have no background queue."
                else if (inQueueBG) "Already in the $queueBG."
                else if (many) "Add ${selectedFiles.size} selected ${noun}s to the $queueBG (Hotkey: Shift+Q)."
                else "Add selected $noun to the $queueBG (Hotkey: Shift+Q)."
            )
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
