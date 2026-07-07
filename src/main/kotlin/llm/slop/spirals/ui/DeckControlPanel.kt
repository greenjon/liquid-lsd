package llm.slop.spirals.ui

import imgui.ImGui
import imgui.flag.ImGuiCol
import imgui.flag.ImGuiStyleVar
import imgui.flag.ImGuiWindowFlags
import llm.slop.spirals.config.ProjectConfig
import llm.slop.spirals.rendering.Deck
import llm.slop.spirals.rendering.Mixer
import llm.slop.spirals.patches.PatchManager
import llm.slop.spirals.rendering.Mandala
import llm.slop.spirals.rendering.DynamicVisualSource
import java.io.File
import kotlin.math.roundToInt

class DeckControlPanel(
    private val deckABrowser: DeckPresetBrowser,
    private val deckBBrowser: DeckPresetBrowser,
    private val onNewDeck: (Boolean, Boolean) -> Unit, // (isDeckA, isDirty)
    private val onLoadDeck: (Boolean, Boolean) -> Unit, // (isDeckA, isDirty)
    private val onSaveDeck: (String, Deck, Boolean) -> Unit,
    private val onDeleteDeck: (Boolean) -> Unit
) {
    private var pendingRightDragFrom: String? = null

    fun drawDeckPresetDropdown(mixer: Mixer, label: String, deck: Deck, isDeckA: Boolean, fixedWidth: Float) {
        ImGui.beginGroup()
        ImGui.pushID("presetRow_$label")

        val activePreset = if (isDeckA) PatchManager.activePresetA
                           else        PatchManager.activePresetB
        val isDirty = PatchManager.isDeckDirty(deck, mixer)

        val displayName = when {
            activePreset == null -> "None"
            isDirty              -> "$activePreset *"
            else                 -> activePreset
        }

        val browser = if (isDeckA) deckABrowser else deckBBrowser

        val menuBtnW = 50f
        val browserBtnW = (fixedWidth - menuBtnW - ImGui.getStyle().itemSpacing.x).coerceAtLeast(50f)

        // -- Preset browser trigger button -------------------------------------
        if (ImGui.button("$displayName##presetBtn_$label", browserBtnW, 0f)) {
            browser.open()
        }
        if (ImGui.isItemHovered() && UITheme.tooltipsEnabled) {
            ImGui.setTooltip("Click to open the Tag Preset Browser for Deck $label.")
        }

        // -- Menu button -------------------------------------------------------
        var openDeleteConfirm = false

        ImGui.sameLine()
        if (ImGui.button("Menu##menu_$label", menuBtnW, 0f)) {
            ImGui.openPopup("deck_preset_menu_$label")
        }
        if (ImGui.isItemHovered() && UITheme.tooltipsEnabled) {
            ImGui.setTooltip("Deck operations menu (Reset, Load, Save, Save As, Delete).")
        }

        if (ImGui.beginPopup("deck_preset_menu_$label")) {
            if (ImGui.menuItem("New (Reset Deck)")) {
                onNewDeck(isDeckA, isDirty)
            }
            if (ImGui.menuItem("Load File...")) {
                onLoadDeck(isDeckA, isDirty)
            }

            ImGui.separator()

            if (ImGui.menuItem("Save")) {
                if (activePreset != null) onSaveDeck(activePreset, deck, isDeckA)
                else browser.open()   // no active preset -> open browser to save as new
            }
            if (ImGui.menuItem("Save As...")) {
                browser.open()        // browser's built-in Save As modal handles tags
            }

            if (activePreset != null) {
                ImGui.separator()
                if (ImGui.menuItem("Delete '$activePreset'")) {
                    openDeleteConfirm = true
                }
            }
            ImGui.endPopup()
        }

        if (openDeleteConfirm) ImGui.openPopup("delete_deck_preset_popup_$label")

        // -- Delete confirmation modal -----------------------------------------
        if (ImGui.beginPopupModal("delete_deck_preset_popup_$label", ImGuiWindowFlags.AlwaysAutoResize)) {
            ImGui.text("Permanently delete '$activePreset'?")
            ImGui.spacing()
            if (ImGui.button("Delete", 80f, 0f)) {
                var file = File(ProjectConfig.Paths.PATCHES_DIR, "$activePreset.lsd")
                if (!file.exists()) file = File(ProjectConfig.Paths.PATCHES_DIR, "$activePreset.json")
                if (file.exists()) file.delete()
                onDeleteDeck(isDeckA)
                ImGui.closeCurrentPopup()
            }
            ImGui.sameLine()
            if (ImGui.button("Cancel", 80f, 0f)) {
                ImGui.closeCurrentPopup()
            }
            ImGui.endPopup()
        }

        ImGui.popID()
        ImGui.endGroup()
    }

    fun drawDeckControls(mixer: Mixer, label: String, deck: Deck, panelW: Float, previewH: Float, isDeckA: Boolean, onUtilityAction: (Int, Deck, Deck) -> Unit) {
        ImGui.pushID(label)

        val themeCol = if (isDeckA) {
            ImGui.colorConvertFloat4ToU32(0.2f, 0.4f, 0.8f, 1f) // Deck A Blue
        } else {
            ImGui.colorConvertFloat4ToU32(0.8f, 0.4f, 0.2f, 1f) // Deck B Orange
        }
        
        val bgCol = if (isDeckA) {
            ImGui.colorConvertFloat4ToU32(0.2f, 0.4f, 0.8f, 0.15f)
        } else {
            ImGui.colorConvertFloat4ToU32(0.8f, 0.4f, 0.2f, 0.15f)
        }

        ImGui.pushStyleColor(ImGuiCol.ChildBg, bgCol)
        // Ensure no internal padding interferes with drawing
        ImGui.pushStyleVar(ImGuiStyleVar.WindowPadding, 0f, 0f)
        
        val inset = 3f
        val imgAvailW = panelW - (inset * 2f)
        val childH = previewH.coerceAtLeast(20f)
        val imgAvailH = (childH - 10f).coerceAtMost(imgAvailW * (9f / 16f)).coerceAtLeast(1f)

        // Explicitly set the Child window width and height
        ImGui.beginChild("Child_$label", panelW, childH, false)

        ImGui.spacing()

        ImGui.setCursorPosX(inset)
        val imgX = ImGui.getCursorScreenPosX()
        val imgY = ImGui.getCursorScreenPosY()
        
        ImGui.image(deck.getOutputTexture(), imgAvailW, imgAvailH, 0f, 1f, 1f, 0f)
        
        ImGui.setCursorScreenPos(imgX, imgY)
        ImGui.invisibleButton("##drag_source_$label", imgAvailW, imgAvailH)
        if (ImGui.isItemHovered() && UITheme.tooltipsEnabled) {
            ImGui.setTooltip("Interactive monitor for Deck $label. Drag to route to another deck or drop presets to load.")
        }
        
        if (ImGui.beginDragDropSource()) {
            val deckName = if (isDeckA) "A" else "B"
            ImGui.setDragDropPayload("MONITOR_DRAG", deckName)
            ImGui.text("Move Deck $deckName")
            ImGui.endDragDropSource()
        }

        if (ImGui.beginDragDropSource(128)) { // 128 = ImGuiDragDropFlags.SourceButtonMouseButtonRight
            val deckName = if (isDeckA) "A" else "B"
            ImGui.setDragDropPayload("MONITOR_DRAG_RIGHT", deckName)
            ImGui.text("Copy/Move/Swap Deck $deckName")
            ImGui.endDragDropSource()
        }
        
        if (ImGui.beginDragDropTarget()) {
            val payload = ImGui.acceptDragDropPayload<String>("ASSET_ITEM")
            if (payload != null) {
                val file = File(payload)
                if (file.extension.lowercase() in listOf("patch", "lsd", "json")) {
                    val isDirty = PatchManager.isDeckDirty(deck, mixer)
                    if (!isDirty) {
                        PatchManager.loadDeckPresetAsync(file, isDeckA, deck === mixer.deckC)
                    } else {
                        // Pass this to UIManager via a new callback or use PopupManager directly if we can
                        // For now, let's assume we need to trigger the popup
                        UIManager.triggerDeckDragDrop(file, deck, isDeckA, mixer)
                    }
                }
            }
            val payloadMonitor = ImGui.acceptDragDropPayload<String>("MONITOR_DRAG")
            if (payloadMonitor != null) {
                val fromName = payloadMonitor
                val toDeck = deck
                val fromDeck = if (fromName == "A") mixer.deckA
                               else if (fromName == "B") mixer.deckB
                               else mixer.deckC
                if (fromDeck !== toDeck) {
                    onUtilityAction(0, fromDeck, toDeck)
                }
            }
            val payloadMonitorRight = ImGui.acceptDragDropPayload<String>("MONITOR_DRAG_RIGHT")
            if (payloadMonitorRight != null) {
                pendingRightDragFrom = payloadMonitorRight
                ImGui.openPopup("monitor_drag_menu_$label")
            }
            ImGui.endDragDropTarget()
        }

        if (ImGui.beginPopup("monitor_drag_menu_$label")) {
            val fromName = pendingRightDragFrom
            if (fromName != null) {
                val fromDeck = if (fromName == "A") mixer.deckA
                               else if (fromName == "B") mixer.deckB
                               else mixer.deckC
                if (ImGui.menuItem("Move")) {
                    onUtilityAction(0, fromDeck, deck)
                }
                if (ImGui.menuItem("Copy")) {
                    onUtilityAction(1, fromDeck, deck)
                }
                if (ImGui.menuItem("Swap")) {
                    onUtilityAction(2, fromDeck, deck)
                }
            }
            ImGui.endPopup()
        }
        
        val dl = ImGui.getWindowDrawList()
        // Draw border perfectly wrapped around the image
        dl.addRect(imgX - 1f, imgY - 1f, imgX + imgAvailW + 1f, imgY + imgAvailH + 1f, themeCol, 0f, 0, 2f)

        ImGui.endChild()
        ImGui.popStyleVar()
        ImGui.popStyleColor()
        ImGui.popID()
    }
}
