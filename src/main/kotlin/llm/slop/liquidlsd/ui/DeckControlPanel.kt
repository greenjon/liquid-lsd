package llm.slop.liquidlsd.ui

import imgui.ImGui
import imgui.flag.ImGuiCol
import imgui.flag.ImGuiStyleVar
import llm.slop.liquidlsd.macro.MacroEngine
import llm.slop.liquidlsd.rendering.Deck
import llm.slop.liquidlsd.rendering.Mixer
import llm.slop.liquidlsd.rendering.VisualSourceRegistry
import java.io.File

class DeckControlPanel(
    private val parametersState: ParametersState
) {
    private var pendingRightDragFrom: String? = null

    /** Focuses [deckLabel] and opens its bay via context-aware monitor click (monitor and badge clicks). */
    private fun openDeepEdit(deckLabel: String) {
        val moduleId = when (deckLabel) {
            "Deck A" -> MacroEngine.DECK_A
            "Deck B" -> MacroEngine.DECK_B
            "Deck BG" -> MacroEngine.DECK_BG
            "Deck PV" -> MacroEngine.DECK_PV
            else -> return
        }
        parametersState.openFromMonitor(moduleId, deckLabel)
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
        onSaveDeck: ((Deck, Boolean, Boolean) -> Unit)? = null,
        onEjectDeck: ((Deck, Boolean, Boolean) -> Unit)? = null,
        // Macros-tab mode: true = this tile is the deck being edited (pulsing frame), false = dimmed.
        // null (Mixer view) = no edit highlighting. onSelect replaces the open-Deep-Edit click action.
        editing: Boolean? = null,
        onSelect: ((String) -> Unit)? = null
    ) {
        ImGui.pushID(label)

        val rgb = when (label) {
            "Deck A" -> llm.slop.liquidlsd.ui.browser.BrowserDeckButtons.colorA()
            "Deck B" -> llm.slop.liquidlsd.ui.browser.BrowserDeckButtons.colorB()
            "Deck BG" -> llm.slop.liquidlsd.ui.browser.BrowserDeckButtons.colorBG()
            else -> llm.slop.liquidlsd.ui.browser.BrowserDeckButtons.colorPV()
        }
        val themeCol = ImGui.colorConvertFloat4ToU32(rgb[0], rgb[1], rgb[2], 1f)

        // Ensure no internal padding interferes with drawing
        ImGui.pushStyleVar(ImGuiStyleVar.WindowPadding, 0f, 0f)
        
        val safePanelW = panelW.coerceAtLeast(1f)
        val inset = DeckTileMetrics.IMAGE_INSET
        val imgAvailW = (safePanelW - (inset * 2f)).coerceAtLeast(1f)
        val aspect = session.uiTheme.renderAspectRatio
        val naturalH = (imgAvailW * aspect)
        val childH = maxOf(previewH.coerceAtLeast(1f), naturalH)
        // Reserve the same inset vertically as horizontally so the border (drawn just outside the
        // image edge) always has room inside the child's clip rect - otherwise, whenever the image
        // fills the child height exactly, the top/bottom border gets clipped off entirely.
        val availHForFit = (childH - (inset * 2f)).coerceAtLeast(1f)
        val imgAvailH = availHForFit.coerceAtMost(imgAvailW * aspect).coerceAtLeast(1f)

        // Explicitly set the Child window width and height
        ImGui.beginChild("Child_$label", safePanelW, childH, false, imgui.flag.ImGuiWindowFlags.NoScrollbar)

        ImGui.setCursorPos(inset, (childH - imgAvailH) * 0.5f)
        val imgX = ImGui.getCursorScreenPosX()
        val imgY = ImGui.getCursorScreenPosY()
        
        val dl = ImGui.getWindowDrawList()
        dl.addRectFilled(imgX, imgY, imgX + imgAvailW, imgY + imgAvailH, ImGui.colorConvertFloat4ToU32(0f, 0f, 0f, 1f))

        ImGui.setCursorScreenPos(imgX, imgY)
        ImGui.image(deck.getOutputTexture().toLong(), imgAvailW, imgAvailH, 0f, 1f, 1f, 0f)

        val leftOverlayW = 70f
        val rightOverlayW = 34f
        val dragBtnX = imgX + leftOverlayW
        val dragBtnW = (imgAvailW - leftOverlayW - rightOverlayW).coerceAtLeast(1f)

        ImGui.setCursorScreenPos(dragBtnX, imgY)
        ImGui.invisibleButton("##drag_source_$label", dragBtnW, imgAvailH.coerceAtLeast(1f))
        itemTooltip("Interactive monitor for $label. Click to open Deep Edit, drag to route to another deck, or drop presets to load.")
        if (ImGui.isItemClicked(0)) {
            if (onSelect != null) onSelect(label) else openDeepEdit(label)
        }
        
        val deckPayloadName = when (label) {
            "Deck A" -> "A"
            "Deck B" -> "B"
            "Deck BG" -> "BG"
            else -> "PV"
        }

        if (ImGui.beginDragDropSource()) {
            ImGui.setDragDropPayload("MONITOR_DRAG", deckPayloadName)
            ImGui.text("Move $label")
            ImGui.endDragDropSource()
        }

        if (ImGui.beginDragDropSource(128)) { // 128 = ImGuiDragDropFlags.SourceButtonMouseButtonRight
            ImGui.setDragDropPayload("MONITOR_DRAG_RIGHT", deckPayloadName)
            ImGui.text("Copy/Move/Swap $label")
            ImGui.endDragDropSource()
        }
        
        if (ImGui.beginDragDropTarget()) {
            val payload = ImGui.acceptDragDropPayload<String>("ASSET_ITEM")
            if (payload != null) {
                val file = File(payload)
                if (file.extension.lowercase() in listOf("patch", "lsd", "json")) {
                    UIManager.loadDeckPresetSafely(mixer, deck, file)
                }
            }
            val stockSourcePayload = ImGui.acceptDragDropPayload<String>(llm.slop.liquidlsd.ui.browser.PresetListPanel.PAYLOAD_STOCK_SOURCE)
            if (stockSourcePayload != null) {
                VisualSourceRegistry.availableSources.find { it.id == stockSourcePayload }?.let { source ->
                    UIManager.changeVisualSourceSafely(mixer, deck, label, source, parametersState)
                }
            }
            val payloadMonitor = ImGui.acceptDragDropPayload<String>("MONITOR_DRAG")
            if (payloadMonitor != null) {
                val fromName = payloadMonitor
                val toDeck = deck
                val fromDeck = when (fromName) {
                    "A" -> mixer.deckA
                    "B" -> mixer.deckB
                    "BG" -> mixer.deckBG
                    else -> mixer.deckPV
                }
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

        pushOpenDropdownPadding()
        if (ImGui.beginPopup("monitor_drag_menu_$label")) {
            pushOpenDropdownFont()
            val fromName = pendingRightDragFrom
            if (fromName != null) {
                val fromDeck = when (fromName) {
                    "A" -> mixer.deckA
                    "B" -> mixer.deckB
                    "BG" -> mixer.deckBG
                    else -> mixer.deckPV
                }
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
            popOpenDropdownFont()
            ImGui.endPopup()
        }
        popOpenDropdownPadding()

        val deckLevel = when (label) {
            "Deck A" -> mixer.levelA.value
            "Deck B" -> mixer.levelB.value
            "Deck BG" -> mixer.levelBG.value
            else -> mixer.levelPV.value
        }
        if (deckLevel < 0.999f) {
            val dimAlpha = (1.0f - deckLevel).coerceIn(0f, 1f)
            dl.addRectFilled(imgX, imgY, imgX + imgAvailW, imgY + imgAvailH, ImGui.colorConvertFloat4ToU32(0f, 0f, 0f, dimAlpha))
        }

        if (editing == false) {
            dl.addRectFilled(imgX, imgY, imgX + imgAvailW, imgY + imgAvailH, ImGui.colorConvertFloat4ToU32(0f, 0f, 0f, 0.45f))
        }

        // Draw border perfectly wrapped around the image.
        // Coordinates are snapped to whole pixels so top/bottom edges don't land on a fractional
        // Y and get anti-aliased across two rows (which made them look thinner than the sides).
        val borderMinX = kotlin.math.round(imgX - 1f)
        val borderMinY = kotlin.math.round(imgY - 1f)
        val borderMaxX = kotlin.math.round(imgX + imgAvailW + 1f)
        val borderMaxY = kotlin.math.round(imgY + imgAvailH + 1f)
        dl.addRect(borderMinX, borderMinY, borderMaxX, borderMaxY, themeCol, 0f, 0, 1.5f)
        if (editing == true) {
            TangoPalette.drawEditingPulseFrame(dl, borderMinX - 1f, borderMinY - 1f, borderMaxX + 1f, borderMaxY + 1f, themeCol)
        }

        // --- Clustered Inner Overlays: Badge, Die, and Vertical Level Fader ---
        val letter = deckPayloadName
        val badgePadX = 8f
        val badgePadY = 3f
        val fontLevel = UITheme.FontLevel.CAPTION
        var textW = 0f
        var textH = 0f
        session.uiTheme.withFont(fontLevel) {
            val sz = ImGui.calcTextSize(letter)
            textW = sz.x
            textH = sz.y
        }
        val badgeW = (textW + badgePadX * 2f).coerceAtLeast(24f)
        val badgeH = (textH + badgePadY * 2f).coerceAtLeast(22f)
        val badgeMargin = 6f
        val badgeMaxY = imgY + imgAvailH - badgeMargin
        val badgeMinY = badgeMaxY - badgeH

        val (badgeMinX, badgeMaxX, dieMinX) = run {
            // Consistent symmetric layout: Deck Name Badge in the LOWER-LEFT corner
            val bMinX = imgX + badgeMargin
            val bMaxX = bMinX + badgeW
            val dMinX = bMaxX + 4f
            Triple(bMinX, bMaxX, dMinX)
        }

        // 1. Badge Pill (Lower-Left Corner)
        dl.addRectFilled(badgeMinX, badgeMinY, badgeMaxX, badgeMaxY, ImGui.colorConvertFloat4ToU32(0.08f, 0.08f, 0.08f, 0.80f), 4f)
        dl.addRect(badgeMinX, badgeMinY, badgeMaxX, badgeMaxY, themeCol, 4f, 0, 1.5f)

        val textX = badgeMinX + (badgeW - textW) * 0.5f
        val textY = badgeMinY + (badgeH - textH) * 0.5f
        session.uiTheme.withFont(fontLevel) {
            dl.addText(textX, textY, themeCol, letter)
        }

        ImGui.setCursorScreenPos(badgeMinX, badgeMinY)
        if (ImGui.invisibleButton("##badge_btn_$label", badgeW, badgeH) || ImGui.isItemClicked(0)) {
            if (onSelect != null) onSelect(label) else openDeepEdit(label)
        }
        itemTooltip("Open $label in Deep Edit.")

        // 2. Die Button (placed directly to the right of the badge in the lower-left row)
        if (session.uiTheme.randomizationEnabled) {
            val dieW = badgeH
            val dieH = badgeH
            ImGui.setCursorScreenPos(dieMinX, badgeMinY)
            val isDieClicked = ImGui.invisibleButton("##btn_rand_die_$label", dieW, dieH)
            val isDieHovered = ImGui.isItemHovered()
            val isDieActive = ImGui.isItemActive()
            if (isDieClicked) {
                ParametersUndo.pushUndoState(parametersState, mixer)
                when (label) {
                    "Deck A" -> mixer.randomizeDeckA()
                    "Deck B" -> mixer.randomizeDeckB()
                    "Deck BG" -> mixer.randomizeDeckBG()
                    else -> mixer.randomizeDeckPV()
                }
            }
            itemTooltip("Randomize $label modulators & base values.\nClick to randomize with undo support.")

            val dieBg = when {
                isDieActive -> ImGui.colorConvertFloat4ToU32(0.48f, 0.36f, 0.46f, 0.95f)
                isDieHovered -> ImGui.colorConvertFloat4ToU32(0.38f, 0.28f, 0.36f, 0.90f)
                else -> ImGui.colorConvertFloat4ToU32(0.12f, 0.12f, 0.14f, 0.80f)
            }
            val dieBorder = if (isDieHovered) themeCol else ImGui.colorConvertFloat4ToU32(0.35f, 0.30f, 0.38f, 0.8f)
            dl.addRectFilled(dieMinX, badgeMinY, dieMinX + dieW, badgeMinY + dieH, dieBg, 4f)
            dl.addRect(dieMinX, badgeMinY, dieMinX + dieW, badgeMinY + dieH, dieBorder, 4f, 0, 1.5f)

            session.uiTheme.withFont(UITheme.FontLevel.BODY) {
                val sz = ImGui.calcTextSize(Icons.DICES)
                val iconX = dieMinX + (dieW - sz.x) * 0.5f
                val iconY = badgeMinY + (dieH - sz.y) * 0.5f
                val iconCol = if (isDieHovered) ImGui.colorConvertFloat4ToU32(1f, 1f, 1f, 1f) else ImGui.colorConvertFloat4ToU32(0.85f, 0.85f, 0.85f, 0.9f)
                dl.addText(iconX, iconY, iconCol, Icons.DICES)
            }
        }

        // Vertical bounds for the Alpha slider (starts at top, ends above the badge)
        val stripW = 4f
        val stripMinY = imgY + badgeMargin
        val rawStripMaxY = badgeMinY - 4f
        val stripH = (rawStripMaxY - stripMinY).coerceAtLeast(20f)
        val stripMaxY = stripMinY + stripH

        // 3. Vertical Level / Alpha Fader (directly above the badge on the left)
        val stripMinX = badgeMinX + (badgeW - stripW) * 0.5f

        ImGui.setCursorScreenPos(stripMinX, stripMinY)
        ImGui.invisibleButton("##fader_$label", stripW, stripH)
        val isFaderHovered = ImGui.isItemHovered()
        val isFaderActive = ImGui.isItemActive()

        if (isFaderActive) {
            val mouseY = ImGui.getIO().mousePos.y
            val pct = ((stripMaxY - mouseY) / stripH).coerceIn(0f, 1f)
            when (label) {
                "Deck A" -> mixer.levelA.baseValue = pct
                "Deck B" -> mixer.levelB.baseValue = pct
                "Deck BG" -> mixer.levelBG.baseValue = pct
                else -> mixer.levelPV.baseValue = pct
            }
        }

        val io = ImGui.getIO()
        if (isFaderHovered || isFaderActive) {
            if (io.mouseWheel != 0f) {
                val delta = if (io.keyShift) 0.01f else 0.05f
                val current = when (label) {
                    "Deck A" -> mixer.levelA.value
                    "Deck B" -> mixer.levelB.value
                    "Deck BG" -> mixer.levelBG.value
                    else -> mixer.levelPV.value
                }
                val newLevel = (current + io.mouseWheel * delta).coerceIn(0f, 1f)
                when (label) {
                    "Deck A" -> mixer.levelA.baseValue = newLevel
                    "Deck B" -> mixer.levelB.baseValue = newLevel
                    "Deck BG" -> mixer.levelBG.baseValue = newLevel
                    else -> mixer.levelPV.baseValue = newLevel
                }
                io.mouseWheel = 0f
            }
            if (ImGui.isMouseClicked(2) || ImGui.isItemClicked(2)) { // Middle-click reset to 100%
                when (label) {
                    "Deck A" -> mixer.levelA.baseValue = 1.0f
                    "Deck B" -> mixer.levelB.baseValue = 1.0f
                    "Deck BG" -> mixer.levelBG.baseValue = 1.0f
                    else -> mixer.levelPV.baseValue = 1.0f
                }
            }
            val desc = if (label == "Deck PV") "Preview Dimmer" else "Channel Level"
            showTooltip("$label $desc\nDrag or scroll to adjust. Middle-click to reset (100%).")
        }

        // Draw Alpha Fader Track
        val faderBg = ImGui.colorConvertFloat4ToU32(0.06f, 0.06f, 0.08f, 0.85f)
        val faderBorder = if (isFaderHovered || isFaderActive) themeCol else ImGui.colorConvertFloat4ToU32(0.25f, 0.28f, 0.35f, 0.7f)
        dl.addRectFilled(stripMinX, stripMinY, stripMinX + stripW, stripMaxY, faderBg, 3f)
        dl.addRect(stripMinX, stripMinY, stripMinX + stripW, stripMaxY, faderBorder, 3f, 0, 1.0f)

        // Draw Filled Level Bar (bottom up) with live level
        val liveLevel = when (label) {
            "Deck A" -> mixer.levelA.value
            "Deck B" -> mixer.levelB.value
            "Deck BG" -> mixer.levelBG.value
            else -> mixer.levelPV.value
        }
        val fillH = stripH * liveLevel
        val fillTop = stripMaxY - fillH
        if (fillH > 1f) {
            dl.addRectFilled(stripMinX + 1f, fillTop, stripMinX + stripW - 1f, stripMaxY - 1f, themeCol, 1f)
        }

        // Draw Alpha Handle Indicator line
        val handleCol = if (isFaderActive) ImGui.colorConvertFloat4ToU32(1f, 1f, 1f, 1f) else ImGui.colorConvertFloat4ToU32(0.9f, 0.9f, 0.9f, 0.85f)
        dl.addLine(stripMinX, fillTop, stripMinX + stripW, fillTop, handleCol, 2f)

        ImGui.endChild()
        ImGui.popStyleVar()
        ImGui.popID()
    }
}

