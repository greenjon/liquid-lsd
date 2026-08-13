package llm.slop.liquidlsd.ui

import imgui.ImGui
import imgui.flag.ImGuiCol
import imgui.flag.ImGuiStyleVar
import imgui.flag.ImGuiWindowFlags
import llm.slop.liquidlsd.notes.NotesManager
import llm.slop.liquidlsd.patches.PatchManager
import llm.slop.liquidlsd.rendering.Deck
import llm.slop.liquidlsd.rendering.DynamicVisualSource
import llm.slop.liquidlsd.rendering.Mandala
import llm.slop.liquidlsd.rendering.Mixer
import llm.slop.liquidlsd.rendering.SourceDocRegistry
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
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

    fun drawDeckPresetDropdown(session: llm.slop.liquidlsd.SessionContext, mixer: Mixer, label: String, deck: Deck, isDeckA: Boolean, fixedWidth: Float) {
        ImGui.beginGroup()
        ImGui.pushID("presetRow_$label")

        val activePreset = when {
            isDeckA -> session.patchManager.activePresetA
            deck === mixer.deckC -> session.patchManager.activePresetC
            else -> session.patchManager.activePresetB
        }
        val isDirty = session.patchManager.isDeckDirty(deck, mixer)

        val displayName = when {
            activePreset == null -> "None"
            isDirty              -> "$activePreset *"
            else                 -> activePreset
        }

        val browser = if (isDeckA) deckABrowser else deckBBrowser

        val menuBtnW = 50f
        val browserBtnW = (fixedWidth - menuBtnW - ImGui.getStyle().itemSpacing.x).coerceAtLeast(50f)

        val deckIndex = when {
            isDeckA -> 0
            deck === mixer.deckC -> 2
            else -> 1
        }
        val status = session.patchManager.deckStatus[deckIndex].get()

        val statusText = when (status.state) {
            llm.slop.liquidlsd.patches.PatchIOState.LOADING -> " [L...]"
            llm.slop.liquidlsd.patches.PatchIOState.SAVING -> " [S...]"
            llm.slop.liquidlsd.patches.PatchIOState.ERROR -> " [!]"
            else -> ""
        }
        val btnText = "$displayName$statusText##presetBtn_$label"

        // -- Preset browser trigger button -------------------------------------
        if (ImGui.button(btnText, browserBtnW, 0f)) {
            browser.open()
        }
        if (status.state == llm.slop.liquidlsd.patches.PatchIOState.ERROR && ImGui.isItemHovered()) {
            ImGui.setTooltip("Error: ${status.errorMessage}")
        } else if (ImGui.isItemHovered() && session.uiTheme.tooltipsEnabled) {
            // Show engine description + global source note
            val sourceId = (deck.source as? DynamicVisualSource)?.id ?: "mandala"
            val engineDesc = SourceDocRegistry.getSourceDescription(sourceId)
            val sourceNote = NotesManager.getSourceNote(sourceId)
            if (engineDesc.isNotEmpty() || sourceNote.isNotEmpty()) {
                ImGui.beginTooltip()
                if (engineDesc.isNotEmpty()) {
                    ImGui.textWrapped(engineDesc)
                }
                if (sourceNote.isNotEmpty()) {
                    if (engineDesc.isNotEmpty()) ImGui.spacing()
                    ImGui.pushStyleColor(ImGuiCol.Text, 1.0f, 0.85f, 0.4f, 1.0f)
                    ImGui.textWrapped("\uD83D\uDCDD $sourceNote")
                    ImGui.popStyleColor()
                }
                ImGui.endTooltip()
            } else {
                ImGui.setTooltip("Click to open the Tag Preset Browser for Deck $label.")
            }
        }

        // -- Menu button -------------------------------------------------------
        var openDeleteConfirm = false

        ImGui.sameLine()
        if (ImGui.button("Menu##menu_$label", menuBtnW, 0f)) {
            ImGui.openPopup("deck_preset_menu_$label")
        }
        if (ImGui.isItemHovered() && session.uiTheme.tooltipsEnabled) {
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

            ImGui.separator()
            // Source note editing
            val sourceId = (deck.source as? DynamicVisualSource)?.id ?: "mandala"
            val sourceName = (deck.source as? DynamicVisualSource)?.displayName ?: "Mandala"
            val sourceNote = NotesManager.getSourceNote(sourceId)
            val sourceNoteLabel = if (sourceNote.isNotEmpty()) "\uD83D\uDCDD Edit Source Note\u2026" else "\uD83D\uDCDD Add Source Note\u2026"
            if (ImGui.menuItem(sourceNoteLabel)) {
                NoteEditorModal.request(NoteContext.Source(sourceId, sourceName))
            }

            ImGui.endPopup()
        }

        if (openDeleteConfirm) ImGui.openPopup("delete_deck_preset_popup_$label")

        // -- Delete confirmation modal -----------------------------------------
        if (ImGui.beginPopupModal("delete_deck_preset_popup_$label", ImGuiWindowFlags.AlwaysAutoResize)) {
            ImGui.text("Permanently delete '$activePreset'?")
            ImGui.spacing()
            if (ImGui.button("Delete", 80f, 0f)) {
                var file = File("presets/patches/$activePreset.lsd")
                if (!file.exists()) file = File("presets/patches/$activePreset.json")
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

    fun drawDeckControls(session: llm.slop.liquidlsd.SessionContext, mixer: Mixer, label: String, deck: Deck, panelW: Float, previewH: Float, isDeckA: Boolean, onUtilityAction: (Int, Deck, Deck) -> Unit) {
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
        if (ImGui.isItemHovered() && session.uiTheme.tooltipsEnabled) {
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
                    val isDirty = session.patchManager.isDeckDirty(deck, mixer)
                    if (!isDirty) {
                        session.patchManager.loadDeckPresetAsync(file, isDeckA, deck === mixer.deckC)
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

        // Patch name label with hover tooltip + right-click note editing (Idea A)
        ImGui.spacing()
        ImGui.setCursorPosX(inset)
        val (activePreset, mtime, dtoVersion) = when {
            isDeckA -> Triple(
                session.patchManager.activePresetA,
                session.patchManager.activePresetMtimeA,
                session.patchManager.cachedDtoA?.version ?: 1
            )
            else -> Triple(
                session.patchManager.activePresetB,
                session.patchManager.activePresetMtimeB,
                session.patchManager.cachedDtoB?.version ?: 1
            )
        }
        val isDirtyLabel = session.patchManager.isDeckDirty(deck, mixer)
        drawPatchNameLabel(session, label, activePreset, mtime, dtoVersion, isDirtyLabel)


        ImGui.endChild()
        ImGui.popStyleVar()
        ImGui.popStyleColor()
        ImGui.popID()
    }

    /**
     * Draws a read-only patch name label below the preset row for a deck.
     *
     * - When [activePreset] is null (unsaved/new deck): shows "Untitled" in dim text; no interaction.
     * - When [activePreset] is set: hovering shows a tooltip with the patch note, last-saved date,
     *   and DTO version; right-clicking opens the note editor.
     *
     * @param deckLabel "Deck A", "Deck B", or "Deck C"
     * @param activePreset The currently loaded preset name, or null if unsaved.
     * @param mtime File modification time in ms since epoch, or null if unknown.
     * @param dtoVersion The [DeckPatchDto.version] of the loaded patch.
     * @param isDirty Whether the deck has unsaved changes.
     */
    fun drawPatchNameLabel(
        session: llm.slop.liquidlsd.SessionContext,
        deckLabel: String,
        activePreset: String?,
        mtime: Long?,
        dtoVersion: Int,
        isDirty: Boolean,
    ) {
        ImGui.pushID("patch_name_label_$deckLabel")

        if (activePreset == null) {
            // Unsaved: dim "Untitled" text, no interaction
            ImGui.pushStyleColor(ImGuiCol.Text, 0.45f, 0.45f, 0.45f, 1.0f)
            ImGui.text("  Untitled")
            ImGui.popStyleColor()
        } else {
            val dirtyMarker = if (isDirty) " ●" else ""
            val displayName = "  $activePreset$dirtyMarker"

            // Invisible button over the text to capture hover/click
            val btnW = ImGui.getContentRegionAvailX()
            val btnH = ImGui.getTextLineHeightWithSpacing()
            val textX = ImGui.getCursorScreenPosX()
            val textY = ImGui.getCursorScreenPosY()

            ImGui.pushStyleColor(ImGuiCol.Text, 0.75f, 0.85f, 1.0f, 1.0f) // soft blue-white
            ImGui.text(displayName)
            ImGui.popStyleColor()

            ImGui.setCursorScreenPos(textX, textY)
            ImGui.invisibleButton("##patch_name_btn_$deckLabel", btnW.coerceAtLeast(10f), btnH.coerceAtLeast(10f))

            if (ImGui.isItemHovered() && session.uiTheme.tooltipsEnabled) {
                val patchNote = NotesManager.getPatchNote(deckLabel)
                val mtimeStr = mtime?.let {
                    SimpleDateFormat("yyyy-MM-dd HH:mm").format(Date(it))
                } ?: "unknown"

                ImGui.beginTooltip()
                ImGui.text(activePreset)
                ImGui.separator()
                ImGui.textDisabled("Last saved: $mtimeStr   v$dtoVersion")
                if (patchNote.isNotEmpty()) {
                    ImGui.spacing()
                    ImGui.textWrapped(patchNote)
                } else {
                    ImGui.spacing()
                    ImGui.textDisabled("(no patch note — right-click to add one)")
                }
                ImGui.endTooltip()
            }

            // Right-click context menu
            if (ImGui.beginPopupContextItem("patch_name_menu_$deckLabel")) {
                val patchNote = NotesManager.getPatchNote(deckLabel)
                val noteLabel = if (patchNote.isNotEmpty()) "\uD83D\uDCDD Edit Patch Note\u2026" else "\uD83D\uDCDD Add Patch Note\u2026"
                if (ImGui.menuItem(noteLabel)) {
                    NoteEditorModal.request(NoteContext.Patch(deckLabel, activePreset))
                }
                ImGui.endPopup()
            }
        }

        ImGui.popID()
    }
}
