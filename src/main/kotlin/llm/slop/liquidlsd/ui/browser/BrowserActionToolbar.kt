package llm.slop.liquidlsd.ui.browser

import imgui.ImGui
import llm.slop.liquidlsd.SessionContext
import llm.slop.liquidlsd.presets.BgQueueManager
import llm.slop.liquidlsd.rendering.Mixer
import llm.slop.liquidlsd.ui.Icons
import llm.slop.liquidlsd.ui.PresetGridState
import llm.slop.liquidlsd.ui.UIManager
import java.io.File

object BrowserActionToolbar {
    fun draw(
        session: SessionContext,
        mixer: Mixer,
        presetState: PresetGridState,
        getSelectedFile: () -> File?,
        btnHeight: Float = 0f
    ) {
        val rawSelectedFile = getSelectedFile()
        val selectedFile = if (rawSelectedFile != null && rawSelectedFile.exists()) rawSelectedFile else null
        val hasSelection = selectedFile != null
        val btnH = if (btnHeight > 0f) btnHeight else ImGui.getFrameHeight()
        val availW = ImGui.getContentRegionAvailX()
        val spacing = ImGui.getStyle().itemSpacingX
        val btnW = ((availW - (spacing * 6f)) / 7f).coerceAtLeast(18f)
        val alpha = if (hasSelection) 1f else 0.4f

        // 1. [ A ]
        BrowserDeckButtons.push(BrowserDeckButtons.colorA(), alpha)
        if (ImGui.button("A##toolbar_deck_a", btnW, btnH) && selectedFile != null) {
            val targetDeck = mixer.deckA
            if (!session.presetManager.isDeckDirty(targetDeck, mixer)) {
                session.presetManager.loadDeckPresetAsync(selectedFile, isDeckA = true)
            } else {
                UIManager.triggerDeckDragDrop(selectedFile, targetDeck, true, mixer)
            }
        }
        if (ImGui.isItemHovered() && session.uiTheme.tooltipsEnabled) ImGui.setTooltip("Load selected preset to Deck A.")
        BrowserDeckButtons.pop()

        ImGui.sameLine()

        // 2. [ B ]
        BrowserDeckButtons.push(BrowserDeckButtons.colorB(), alpha)
        if (ImGui.button("B##toolbar_deck_b", btnW, btnH) && selectedFile != null) {
            val targetDeck = mixer.deckB
            if (!session.presetManager.isDeckDirty(targetDeck, mixer)) {
                session.presetManager.loadDeckPresetAsync(selectedFile, isDeckA = false, isDeckBG = false, isDeckPV = false)
            } else {
                UIManager.triggerDeckDragDrop(selectedFile, targetDeck, false, mixer)
            }
        }
        if (ImGui.isItemHovered() && session.uiTheme.tooltipsEnabled) ImGui.setTooltip("Load selected preset to Deck B.")
        BrowserDeckButtons.pop()

        ImGui.sameLine()

        // 3. [ BG ]
        BrowserDeckButtons.push(BrowserDeckButtons.colorBG(), alpha)
        if (ImGui.button("BG##toolbar_deck_bg", btnW, btnH) && selectedFile != null) {
            val targetDeck = mixer.deckBG
            if (!session.presetManager.isDeckDirty(targetDeck, mixer)) {
                session.presetManager.loadDeckPresetAsync(selectedFile, isDeckBG = true)
            } else {
                UIManager.triggerDeckDragDrop(selectedFile, targetDeck, false, mixer)
            }
        }
        if (ImGui.isItemHovered() && session.uiTheme.tooltipsEnabled) ImGui.setTooltip("Load selected preset to Deck BG (Background).")
        BrowserDeckButtons.pop()

        ImGui.sameLine()

        // 4. [ PV ]
        BrowserDeckButtons.push(BrowserDeckButtons.colorPV(), alpha)
        if (ImGui.button("PV##toolbar_deck_pv", btnW, btnH) && selectedFile != null) {
            val targetDeck = mixer.deckPV
            if (!session.presetManager.isDeckDirty(targetDeck, mixer)) {
                session.presetManager.loadDeckPresetAsync(selectedFile, isDeckPV = true)
            } else {
                UIManager.triggerDeckDragDrop(selectedFile, targetDeck, false, mixer)
            }
        }
        if (ImGui.isItemHovered() && session.uiTheme.tooltipsEnabled) ImGui.setTooltip("Preview selected preset on Deck PV.")
        BrowserDeckButtons.pop()

        ImGui.sameLine()

        // 5. [ Q ]
        BrowserDeckButtons.push(BrowserDeckButtons.colorQ(), alpha)
        if (ImGui.button("Q##toolbar_deck_q", btnW, btnH) && selectedFile != null) {
            session.playQueueManager.appendToQueue(selectedFile)
        }
        if (ImGui.isItemHovered() && session.uiTheme.tooltipsEnabled) ImGui.setTooltip("Add selected preset to the A/B Play Queue.")
        BrowserDeckButtons.pop()

        ImGui.sameLine()

        // 6. [ BGQ ]
        BrowserDeckButtons.push(BrowserDeckButtons.colorBGQ(), alpha)
        if (ImGui.button("BGQ##toolbar_deck_bgq", btnW, btnH) && selectedFile != null) {
            BgQueueManager.appendToQueue(selectedFile)
        }
        if (ImGui.isItemHovered() && session.uiTheme.tooltipsEnabled) ImGui.setTooltip("Add selected preset to the Background Queue.")
        BrowserDeckButtons.pop()

        ImGui.sameLine()

        // 7. [ + ] (Create New Preset Dropdown)
        if (ImGui.button("${Icons.PLUS}##toolbar_new_preset", btnW, btnH)) {
            ImGui.openPopup("create_new_preset_popup")
        }
        if (ImGui.isItemHovered() && session.uiTheme.tooltipsEnabled) ImGui.setTooltip("Create new preset on a deck...")

        if (ImGui.beginPopup("create_new_preset_popup")) {
            ImGui.textDisabled("Create new preset on:")
            ImGui.separator()
            if (ImGui.menuItem("Deck A")) {
                UIManager.triggerDeckEject(mixer.deckA, isDeckA = true, isDeckC = false)
                presetState.activeTopTab = "Deck A"
            }
            if (ImGui.menuItem("Deck B")) {
                UIManager.triggerDeckEject(mixer.deckB, isDeckA = false, isDeckC = false)
                presetState.activeTopTab = "Deck B"
            }
            if (ImGui.menuItem("Deck BG")) {
                UIManager.triggerDeckEject(mixer.deckBG, isDeckA = false, isDeckC = false)
                presetState.activeTopTab = "Deck BG"
            }
            if (ImGui.menuItem("Deck PV")) {
                UIManager.triggerDeckEject(mixer.deckPV, isDeckA = false, isDeckC = true)
                presetState.activeTopTab = "Deck PV"
            }
            ImGui.endPopup()
        }
    }
}
