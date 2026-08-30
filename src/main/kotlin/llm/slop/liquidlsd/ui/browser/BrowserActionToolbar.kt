package llm.slop.liquidlsd.ui.browser

import imgui.ImGui
import llm.slop.liquidlsd.SessionContext
import llm.slop.liquidlsd.presets.BgQueueManager
import llm.slop.liquidlsd.rendering.Mixer
import llm.slop.liquidlsd.ui.Icons
import llm.slop.liquidlsd.ui.LibraryPanel
import llm.slop.liquidlsd.ui.PresetGridState
import llm.slop.liquidlsd.ui.UIManager
import java.io.File

enum class DeckAuditionTarget(val label: String, val deckIndex: Int) {
    DECK_A("Deck A", 1),
    DECK_B("Deck B", 2),
    DECK_BG("Deck BG", 3),
    DECK_PV("Deck PV", 4)
}

object BrowserActionToolbar {
    const val BTN_WIDTH: Float = 80f
    const val TOOLBAR_WIDTH: Float = (7 * BTN_WIDTH) + (5 * 6f) + (1 * 14f)

    var isAuditionLocked: Boolean = false
    var latchedDeckTarget: DeckAuditionTarget? = null

    fun draw(
        session: SessionContext,
        mixer: Mixer,
        presetState: PresetGridState,
        selectedFile: File?,
        source: LibraryPanel.SelectionSource?,
        btnHeight: Float = 0f
    ) {
        val hasSelection = selectedFile != null && selectedFile.exists()
        val btnH = if (btnHeight > 0f) btnHeight else ImGui.getFrameHeight()
        val btnW = BTN_WIDTH

        // 0. [ LOCK / PADLOCK ]
        val lockColor = BrowserDeckButtons.colorLock()
        BrowserDeckButtons.push(lockColor, alpha = 1f, isLatched = isAuditionLocked)
        val lockIcon = if (isAuditionLocked) Icons.LOCK else Icons.UNLOCK
        if (ImGui.button("$lockIcon##toolbar_lock", btnW, btnH)) {
            isAuditionLocked = !isAuditionLocked
            if (isAuditionLocked) {
                // Auto-latch to PV when turned ON
                latchedDeckTarget = DeckAuditionTarget.DECK_PV
                if (selectedFile != null) {
                    BrowserDeckButtons.loadPresetToDeck(session, mixer, selectedFile, 4)
                }
            } else {
                latchedDeckTarget = null
            }
            LibraryPanel.shouldReclaimFocus = true
        }
        if (ImGui.isItemHovered() && session.uiTheme.tooltipsEnabled) {
            val tooltip = if (isAuditionLocked) {
                "Quick Audition Latch: ON (Target: ${latchedDeckTarget?.label ?: "None"}).\nClick presets or use Up/Down arrows to auto-load."
            } else {
                "Quick Audition Latch: OFF.\nClick to arm audition mode (defaults to Deck PV)."
            }
            ImGui.setTooltip(tooltip)
        }
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
                    BrowserDeckButtons.loadPresetToDeck(session, mixer, selectedFile, 1)
                }
            } else if (selectedFile != null) {
                BrowserDeckButtons.loadPresetToDeck(session, mixer, selectedFile, 1)
            }
            LibraryPanel.shouldReclaimFocus = true
        }
        if (ImGui.isItemHovered() && session.uiTheme.tooltipsEnabled) {
            ImGui.setTooltip(if (isAuditionLocked) "Latch audition target to Deck A." else "Load selected preset to Deck A (Hotkey: 1).")
        }
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
                    BrowserDeckButtons.loadPresetToDeck(session, mixer, selectedFile, 2)
                }
            } else if (selectedFile != null) {
                BrowserDeckButtons.loadPresetToDeck(session, mixer, selectedFile, 2)
            }
            LibraryPanel.shouldReclaimFocus = true
        }
        if (ImGui.isItemHovered() && session.uiTheme.tooltipsEnabled) {
            ImGui.setTooltip(if (isAuditionLocked) "Latch audition target to Deck B." else "Load selected preset to Deck B (Hotkey: 2).")
        }
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
                    BrowserDeckButtons.loadPresetToDeck(session, mixer, selectedFile, 3)
                }
            } else if (selectedFile != null) {
                BrowserDeckButtons.loadPresetToDeck(session, mixer, selectedFile, 3)
            }
            LibraryPanel.shouldReclaimFocus = true
        }
        if (ImGui.isItemHovered() && session.uiTheme.tooltipsEnabled) {
            ImGui.setTooltip(if (isAuditionLocked) "Latch audition target to Deck BG." else "Load selected preset to Deck BG / Background (Hotkey: 3).")
        }
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
                    BrowserDeckButtons.loadPresetToDeck(session, mixer, selectedFile, 4)
                }
            } else if (selectedFile != null) {
                BrowserDeckButtons.loadPresetToDeck(session, mixer, selectedFile, 4)
            }
            LibraryPanel.shouldReclaimFocus = true
        }
        if (ImGui.isItemHovered() && session.uiTheme.tooltipsEnabled) {
            ImGui.setTooltip(if (isAuditionLocked) "Latch audition target to Deck PV." else "Preview selected preset on Deck PV (Hotkey: 4).")
        }
        BrowserDeckButtons.pop()

        ImGui.sameLine(0f, 14f)

        // 5. [ Q ] (Disabled/dimmed if already from Play Queue A/B or no selection)
        val canQueueAB = hasSelection && source != LibraryPanel.SelectionSource.QUEUE_AB
        val alphaQ = if (canQueueAB) 1f else 0.35f
        BrowserDeckButtons.push(BrowserDeckButtons.colorQ(), alphaQ)
        if (ImGui.button("Q##toolbar_deck_q", btnW, btnH) && canQueueAB && selectedFile != null) {
            session.playQueueManager.appendToQueue(selectedFile)
            LibraryPanel.shouldReclaimFocus = true
        }
        if (ImGui.isItemHovered() && session.uiTheme.tooltipsEnabled) {
            val tip = if (source == LibraryPanel.SelectionSource.QUEUE_AB) "Preset is already in the A/B Play Queue (Hotkey: Q)." else "Add selected preset to the A/B Play Queue (Hotkey: Q)."
            ImGui.setTooltip(tip)
        }
        BrowserDeckButtons.pop()

        ImGui.sameLine(0f, 6f)

        // 6. [ BGQ ] (Disabled/dimmed if already from BG Queue or no selection)
        val canQueueBG = hasSelection && source != LibraryPanel.SelectionSource.QUEUE_BG
        val alphaBGQ = if (canQueueBG) 1f else 0.35f
        BrowserDeckButtons.push(BrowserDeckButtons.colorBGQ(), alphaBGQ)
        if (ImGui.button("BGQ##toolbar_deck_bgq", btnW, btnH) && canQueueBG && selectedFile != null) {
            BgQueueManager.appendToQueue(selectedFile)
            LibraryPanel.shouldReclaimFocus = true
        }
        if (ImGui.isItemHovered() && session.uiTheme.tooltipsEnabled) {
            val tip = if (source == LibraryPanel.SelectionSource.QUEUE_BG) "Preset is already in the Background Queue (Hotkey: Shift+Q)." else "Add selected preset to the Background Queue (Hotkey: Shift+Q)."
            ImGui.setTooltip(tip)
        }
        BrowserDeckButtons.pop()
    }
}
