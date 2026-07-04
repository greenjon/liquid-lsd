package llm.slop.spirals.ui

import imgui.ImGui
import imgui.flag.ImGuiCol
import imgui.flag.ImGuiStyleVar
import imgui.flag.ImGuiWindowFlags
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

        // -- Menu button -------------------------------------------------------
        var openDeleteConfirm = false

        ImGui.sameLine()
        if (ImGui.button("Menu##menu_$label", menuBtnW, 0f)) {
            ImGui.openPopup("deck_preset_menu_$label")
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
                var file = File("presets/decks/$activePreset.lsd")
                if (!file.exists()) file = File("presets/decks/$activePreset.json")
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

    fun drawDeckControls(mixer: Mixer, label: String, deck: Deck, panelW: Float, previewH: Float, isDeckA: Boolean) {
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
        val imgAvailH = imgAvailW * (9f / 16f)
        val childH = imgAvailH + 10f

        // Explicitly set the Child window width and height
        ImGui.beginChild("Child_$label", panelW, childH, false)

        ImGui.spacing()

        ImGui.setCursorPosX(inset)
        val imgX = ImGui.getCursorScreenPosX()
        val imgY = ImGui.getCursorScreenPosY()
        
        ImGui.image(deck.getOutputTexture(), imgAvailW, imgAvailH, 0f, 1f, 1f, 0f)
        
        if (ImGui.beginDragDropTarget()) {
            val payload = ImGui.acceptDragDropPayload<String>("ASSET_ITEM")
            if (payload != null) {
                val file = File(payload)
                if (file.extension.lowercase() in listOf("patch", "lsd", "json")) {
                    PatchManager.loadDeckPresetAsync(file, isDeckA)
                }
            }
            ImGui.endDragDropTarget()
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