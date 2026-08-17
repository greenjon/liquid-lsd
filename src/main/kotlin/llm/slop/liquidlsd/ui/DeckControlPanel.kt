package llm.slop.liquidlsd.ui

import imgui.ImGui
import imgui.flag.ImGuiCol
import imgui.flag.ImGuiStyleVar
import imgui.flag.ImGuiWindowFlags
import llm.slop.liquidlsd.notes.NotesManager
import llm.slop.liquidlsd.presets.PresetManager
import llm.slop.liquidlsd.presets.PresetIOState
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
            isDeckA -> session.presetManager.activePresetA
            deck === mixer.deckC -> session.presetManager.activePresetC
            else -> session.presetManager.activePresetB
        }
        val isDirty = session.presetManager.isDeckDirty(deck, mixer)

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
        val status = session.presetManager.deckStatus[deckIndex].get()

        val statusText = when (status.state) {
            PresetIOState.LOADING -> " [L...]"
            PresetIOState.SAVING -> " [S...]"
            PresetIOState.ERROR -> " [!]"
            else -> ""
        }
        val btnText = "$displayName$statusText##presetBtn_$label"

        // -- Preset browser trigger button -------------------------------------
        if (ImGui.button(btnText, browserBtnW, 0f)) {
            browser.open()
        }
        if (status.state == PresetIOState.ERROR && ImGui.isItemHovered()) {
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
                val file = File("library/presets/$activePreset.lsd")
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

    fun drawDeckControls(
        session: llm.slop.liquidlsd.SessionContext,
        mixer: Mixer,
        label: String,
        deck: Deck,
        panelW: Float,
        previewH: Float,
        isDeckA: Boolean,
        onUtilityAction: (Int, Deck, Deck) -> Unit,
        onSaveDeck: (Deck, Boolean, Boolean) -> Unit,
        onEjectDeck: (Deck, Boolean, Boolean) -> Unit
    ) {
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
        val bottomBarH = session.uiTheme.withFont(UITheme.FontLevel.BODY) { maxOf(ImGui.getFrameHeight(), ImGui.getTextLineHeight() + 6f) } + 6f
        val childH = maxOf(previewH, (imgAvailW * (9f / 16f)) + bottomBarH + 6f)
        val imgAvailH = (childH - bottomBarH - 6f).coerceAtMost(imgAvailW * (9f / 16f)).coerceAtLeast(1f)

        // Explicitly set the Child window width and height
        ImGui.beginChild("Child_$label", panelW, childH, false, imgui.flag.ImGuiWindowFlags.NoScrollbar)

        ImGui.spacing()

        // Interactive top preset bar: Save button, Eject button, Preset bar
        ImGui.setCursorPosX(inset)
        val browser = if (isDeckA) deckABrowser else deckBBrowser
        drawDeckBottomBar(session, label, deck, isDeckA = isDeckA, isDeckC = false, mixer = mixer, onSaveDeck = onSaveDeck, onEjectDeck = onEjectDeck, browser = browser, targetW = imgAvailW)
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
                    val isDirty = session.presetManager.isDeckDirty(deck, mixer)
                    if (!isDirty) {
                        session.presetManager.loadDeckPresetAsync(file, isDeckA, deck === mixer.deckC)
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

        // Draw lower-left letter badge overlay ("A" or "B") on monitor
        val letter = if (isDeckA) "A" else "B"
        val badgePadX = 8f
        val badgePadY = 3f
        val fontLevel = UITheme.FontLevel.H2
        var textW = 0f
        var textH = 0f
        session.uiTheme.withFont(fontLevel) {
            val sz = ImGui.calcTextSize(letter)
            textW = sz.x
            textH = sz.y
        }
        val badgeW = (textW + badgePadX * 2f).coerceAtLeast(24f)
        val badgeH = (textH + badgePadY * 2f).coerceAtLeast(24f)
        val badgeMargin = 6f
        val badgeMinX = imgX + badgeMargin
        val badgeMaxY = imgY + imgAvailH - badgeMargin
        val badgeMinY = badgeMaxY - badgeH
        val badgeMaxX = badgeMinX + badgeW

        dl.addRectFilled(badgeMinX, badgeMinY, badgeMaxX, badgeMaxY, ImGui.colorConvertFloat4ToU32(0.08f, 0.08f, 0.08f, 0.80f), 4f)
        dl.addRect(badgeMinX, badgeMinY, badgeMaxX, badgeMaxY, themeCol, 4f, 0, 1.5f)

        val textX = badgeMinX + (badgeW - textW) * 0.5f
        val textY = badgeMinY + (badgeH - textH) * 0.5f
        session.uiTheme.withFont(fontLevel) {
            dl.addText(textX, textY, themeCol, letter)
        }

        ImGui.endChild()
        ImGui.popStyleVar()
        ImGui.popStyleColor()
        ImGui.popID()
    }
}

/**
 * Draws the interactive bottom bar for a deck preview monitor.
 *
 * Order: [Save Button] [Eject Button] [Preset Bar]
 * Buttons and Preset Bar stay aligned along their bottom baselines,
 * and the bar height grows dynamically as text size/scale increases.
 */
fun drawDeckBottomBar(
    session: llm.slop.liquidlsd.SessionContext,
    deckLabel: String,
    deck: Deck,
    isDeckA: Boolean,
    isDeckC: Boolean,
    mixer: Mixer,
    onSaveDeck: (Deck, Boolean, Boolean) -> Unit,
    onEjectDeck: (Deck, Boolean, Boolean) -> Unit,
    browser: DeckPresetBrowser? = null,
    targetW: Float = 0f
) {
    ImGui.pushID("bottom_bar_$deckLabel")

    val (activePreset, mtime, dtoVersion) = when {
        isDeckA -> Triple(
            session.presetManager.activePresetA,
            session.presetManager.activePresetMtimeA,
            session.presetManager.cachedDtoA?.version ?: 1
        )
        isDeckC -> Triple(
            session.presetManager.activePresetC,
            session.presetManager.activePresetMtimeC,
            session.presetManager.cachedDtoC?.version ?: 1
        )
        else -> Triple(
            session.presetManager.activePresetB,
            session.presetManager.activePresetMtimeB,
            session.presetManager.cachedDtoB?.version ?: 1
        )
    }
    val isDirty = session.presetManager.isDeckDirty(deck, mixer)

    var textH = 0f
    session.uiTheme.withFont(UITheme.FontLevel.BODY) { textH = ImGui.getTextLineHeight() }
    val frameH = ImGui.getFrameHeight()
    val rowH = maxOf(frameH, textH + 6f)

    val startX = ImGui.getCursorScreenPosX()
    val startY = ImGui.getCursorScreenPosY()
    val bottomY = startY + rowH

    val tag = deckLabel.replace(" ", "")

    // 1. Save Button
    ImGui.setCursorScreenPos(startX, startY)
    if (drawIconButton(session, "##btn_Save_$tag", Icons.SAVE, rowH, "Save or save as a new preset for $deckLabel.")) {
        ImGui.openPopup("save_menu_$tag")
    }
    if (ImGui.beginPopup("save_menu_$tag")) {
        if (ImGui.menuItem("Save")) {
            onSaveDeck(deck, isDeckA, false)
        }
        if (ImGui.menuItem("Save As...")) {
            onSaveDeck(deck, isDeckA, true)
        }
        ImGui.endPopup()
    }

    // 2. Eject Button
    ImGui.sameLine()
    val ejectX = ImGui.getCursorScreenPosX()
    ImGui.setCursorScreenPos(ejectX, startY)
    if (drawIconButton(session, "##btn_Eject_$tag", Icons.EJECT, rowH, "Eject this preset")) {
        onEjectDeck(deck, isDeckA, isDeckC)
    }

    // 3. Preset Bar
    ImGui.sameLine()
    val barX = ImGui.getCursorScreenPosX()
    val totalW = if (targetW > 0f) targetW else ImGui.getContentRegionAvailX()
    val barW = (startX + totalW - barX).coerceAtLeast(10f)

    val dl = ImGui.getWindowDrawList()
    val bgCol = ImGui.colorConvertFloat4ToU32(0.12f, 0.14f, 0.18f, 0.7f)
    val borderCol = ImGui.colorConvertFloat4ToU32(0.25f, 0.30f, 0.38f, 0.8f)
    dl.addRectFilled(barX, startY, barX + barW, bottomY, bgCol, 3f)
    dl.addRect(barX, startY, barX + barW, bottomY, borderCol, 3f)

    val labelText = if (activePreset == null) {
        "Preset: None"
    } else {
        val dirtyMarker = if (isDirty) " *" else ""
        "Preset: $activePreset$dirtyMarker"
    }

    val textY = startY + (rowH - textH) * 0.5f
    val textPaddingX = 8f
    session.uiTheme.withFont(UITheme.FontLevel.BODY) {
        val textCol = if (activePreset == null) {
            ImGui.colorConvertFloat4ToU32(0.55f, 0.55f, 0.55f, 1.0f)
        } else {
            ImGui.colorConvertFloat4ToU32(0.85f, 0.90f, 1.0f, 1.0f)
        }
        dl.addText(barX + textPaddingX, textY, textCol, labelText)
    }

    ImGui.setCursorScreenPos(barX, startY)
    ImGui.invisibleButton("##preset_bar_btn_$tag", barW, rowH)

    if (ImGui.isItemClicked(0) && browser != null) {
        browser.open()
    }

    if (ImGui.isItemHovered() && session.uiTheme.tooltipsEnabled) {
        val presetNote = NotesManager.getPresetNote(deckLabel)
        val mtimeStr = mtime?.let {
            SimpleDateFormat("yyyy-MM-dd HH:mm").format(Date(it))
        } ?: "unknown"

        ImGui.beginTooltip()
        ImGui.text(activePreset ?: "None")
        ImGui.separator()
        ImGui.textDisabled("Last saved: $mtimeStr   v$dtoVersion")
        if (presetNote.isNotEmpty()) {
            ImGui.spacing()
            ImGui.textWrapped(presetNote)
        } else {
            ImGui.spacing()
            ImGui.textDisabled("(no preset note — right-click to add one)")
        }
        ImGui.endTooltip()
    }

    if (ImGui.beginPopupContextItem("preset_name_menu_$tag")) {
        val presetNote = NotesManager.getPresetNote(deckLabel)
        val noteLabel = if (presetNote.isNotEmpty()) "Edit Preset Note..." else "Add Preset Note..."
        if (ImGui.menuItem(noteLabel)) {
            NoteEditorModal.request(NoteContext.Preset(deckLabel, activePreset ?: "Untitled"))
        }
        ImGui.endPopup()
    }

    ImGui.setCursorScreenPos(startX, bottomY + 2f)

    ImGui.popID()
}

private fun drawIconButton(
    session: llm.slop.liquidlsd.SessionContext,
    id: String,
    icon: String,
    rowH: Float,
    tooltip: String? = null
): Boolean {
    var iconW = 0f
    var iconH = 0f
    session.uiTheme.withFont(UITheme.FontLevel.BODY) {
        val sz = ImGui.calcTextSize(icon)
        iconW = sz.x
        iconH = sz.y
    }

    val padX = 10f
    val btnW = (iconW + padX * 2f).coerceAtLeast(24f)
    val startX = ImGui.getCursorScreenPosX()
    val startY = ImGui.getCursorScreenPosY()
    val endX = startX + btnW
    val endY = startY + rowH

    ImGui.invisibleButton(id, btnW, rowH)
    val isHovered = ImGui.isItemHovered()
    val isActive = ImGui.isItemActive()
    val isClicked = ImGui.isItemClicked(0)

    val dl = ImGui.getWindowDrawList()

    val bgCol = when {
        isActive -> ImGui.colorConvertFloat4ToU32(0.28f, 0.35f, 0.45f, 0.9f)
        isHovered -> ImGui.colorConvertFloat4ToU32(0.20f, 0.25f, 0.35f, 0.8f)
        else -> ImGui.colorConvertFloat4ToU32(0.12f, 0.14f, 0.18f, 0.7f)
    }
    val borderCol = if (isHovered) {
        ImGui.colorConvertFloat4ToU32(0.35f, 0.45f, 0.60f, 0.9f)
    } else {
        ImGui.colorConvertFloat4ToU32(0.25f, 0.30f, 0.38f, 0.8f)
    }
    val iconCol = if (isHovered) {
        ImGui.colorConvertFloat4ToU32(1.0f, 1.0f, 1.0f, 1.0f)
    } else {
        ImGui.colorConvertFloat4ToU32(0.85f, 0.90f, 0.95f, 0.9f)
    }

    dl.addRectFilled(startX, startY, endX, endY, bgCol, 3f)
    dl.addRect(startX, startY, endX, endY, borderCol, 3f)

    val iconX = startX + (btnW - iconW) * 0.5f
    val iconY = startY + (rowH - iconH) * 0.5f

    session.uiTheme.withFont(UITheme.FontLevel.BODY) {
        dl.addText(iconX, iconY, iconCol, icon)
    }

    if (isHovered && tooltip != null && session.uiTheme.tooltipsEnabled) {
        ImGui.setTooltip(tooltip)
    }

    return isClicked
}

