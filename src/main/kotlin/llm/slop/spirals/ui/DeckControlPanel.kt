package llm.slop.spirals.ui

import imgui.ImGui
import imgui.flag.ImGuiCol
import imgui.flag.ImGuiStyleVar
import imgui.flag.ImGuiWindowFlags
import llm.slop.spirals.rendering.Deck
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
    fun drawDeckPresetDropdown(label: String, deck: Deck, isDeckA: Boolean, fixedWidth: Float) {
        ImGui.beginGroup()
        ImGui.pushID("presetRow_$label")

        val activePreset = if (isDeckA) llm.slop.spirals.patches.PatchManager.activePresetA
                           else        llm.slop.spirals.patches.PatchManager.activePresetB
        val isDirty = llm.slop.spirals.patches.PatchManager.isDeckDirty(deck, isDeckA)

        val displayName = when {
            activePreset == null -> "None"
            isDirty              -> "$activePreset *"
            else                 -> activePreset
        }

        val browser = if (isDeckA) deckABrowser else deckBBrowser

        val menuBtnW = 50f
        val browserBtnW = (fixedWidth - menuBtnW - ImGui.getStyle().itemSpacing.x).coerceAtLeast(50f)

        // ── Preset browser trigger button ─────────────────────────────────────
        if (ImGui.button("$displayName##presetBtn_$label", browserBtnW, 0f)) {
            browser.open()
        }

        // ── Menu button ───────────────────────────────────────────────────────
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
                else browser.open()   // no active preset → open browser to save as new
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

        // ── Delete confirmation modal ─────────────────────────────────────────
        if (ImGui.beginPopupModal("delete_deck_preset_popup_$label", ImGuiWindowFlags.AlwaysAutoResize)) {
            ImGui.text("Permanently delete '$activePreset'?")
            ImGui.spacing()
            if (ImGui.button("Delete", 80f, 0f)) {
                val file = File("presets/decks/$activePreset.json")
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

    fun drawDeckControls(label: String, deck: Deck, panelW: Float, previewH: Float, isDeckA: Boolean) {
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
        
        // Explicitly set the Child window width
        ImGui.beginChild("Child_$label", panelW, 0f, false)

        ImGui.spacing()

        val inset = 3f
        val imgAvailW = panelW - (inset * 2f)
        val imgAvailH = imgAvailW * (9f / 16f)
        
        ImGui.setCursorPosX(inset)
        val imgX = ImGui.getCursorScreenPosX()
        val imgY = ImGui.getCursorScreenPosY()
        
        ImGui.image(deck.getOutputTexture(), imgAvailW, imgAvailH, 0f, 1f, 1f, 0f)
        
        val dl = ImGui.getWindowDrawList()
        // Draw border perfectly wrapped around the image
        dl.addRect(imgX - 1f, imgY - 1f, imgX + imgAvailW + 1f, imgY + imgAvailH + 1f, themeCol, 0f, 0, 2f)

        ImGui.spacing()
        ImGui.separator()
        ImGui.spacing()
        
        ImGui.indent(8f)

        // Structural Identity Readouts
        val mandala = deck.source as? Mandala
        if (mandala != null) {
            val lobesParam = mandala.parameters["Lobes"]!!
            val currentLobe = lobesParam.value.roundToInt()
            val closestLobe = llm.slop.spirals.rendering.MandalaLibrary.uniquePetals.minByOrNull { kotlin.math.abs(it - currentLobe) } ?: 3
            
            UITheme.captionColored(0.8f, 0.8f, 0.8f, 1.0f, "Geometry:")
            UITheme.body("  Lobes: $currentLobe")

            val recipeParam = mandala.parameters["Recipe Select"]!!
            val filtered = llm.slop.spirals.rendering.MandalaLibrary.recipesByPetals[closestLobe] ?: emptyList()
            val currentSelect = recipeParam.value
            val recipeIdx = (currentSelect * (filtered.size - 1)).roundToInt().coerceIn(0, filtered.size - 1)

            if (filtered.isNotEmpty() && recipeIdx in filtered.indices) {
                val recipe = filtered[recipeIdx]
                UITheme.body("  Recipe: ${recipe.a},${recipe.b},${recipe.c},${recipe.d}")
            } else {
                UITheme.body("  Recipe: None")
            }

            ImGui.spacing()
            val bgStyleParam = mandala.parameters["Bg Style"]!!
            val bgStyleIdx = bgStyleParam.value.toInt().coerceIn(0, 2)
            val bgStyleLabels = arrayOf("Off", "Solid Color", "Plasma")
            
            UITheme.captionColored(0.8f, 0.8f, 0.8f, 1.0f, "Background:")
            UITheme.body("  Style: ${bgStyleLabels[bgStyleIdx]}")
        }
        val dynamicSource = deck.source as? DynamicVisualSource
        if (dynamicSource != null) {
            UITheme.captionColored(0.8f, 0.8f, 0.8f, 1.0f, dynamicSource.displayName + ":")
            val params = dynamicSource.parameters.entries.take(2)
            for ((key, param) in params) {
                UITheme.body("  $key: %.2f".format(param.value))
            }
        }
        ImGui.unindent(8f)

        ImGui.endChild()
        ImGui.popStyleVar()
        ImGui.popStyleColor()
        ImGui.popID()
    }
}