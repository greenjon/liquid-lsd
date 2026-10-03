package llm.slop.liquidlsd.ui

import imgui.ImGui
import imgui.flag.ImGuiCol
import imgui.flag.ImGuiStyleVar
import llm.slop.liquidlsd.SessionContext
import llm.slop.liquidlsd.macro.FxMacroSync
import llm.slop.liquidlsd.models.ClipboardManager
import llm.slop.liquidlsd.presets.FXBgQueueManager
import llm.slop.liquidlsd.presets.FXQueueManager
import llm.slop.liquidlsd.presets.FxOps
import llm.slop.liquidlsd.rendering.Deck
import llm.slop.liquidlsd.rendering.FxChain
import llm.slop.liquidlsd.rendering.Mixer
import java.io.File

/**
 * Shared chain-level controls for every Performance FX chain row (deck rows and the Master row):
 *
 *   `[⋮]  Chain Name •  [Save]  [◀] [▶]  [1] [2] [3]`   ...   `[BYPASS]`
 *
 * - **⋮ menu**: Save As, New, Revert, Clear, Copy / Paste chain, Resync knobs.
 * - **Name**: click opens that row's Browse content on the whole-chain list (search filter). Drops of .lsdfxchain load here.
 * - **• (dirty dot)**: shows amber when the chain differs from its loaded baseline or has unsaved edits.
 * - **Save**: overwrites source file (or acts as Save As if untitled).
 * - **◀ / ▶**: on Deck A, B, and BG, steps through the respective live FX queue (A/B or BG) directly
 *   on that deck. Bypassed/omitted on Deck PV and Master FX. If the queue is empty, rendered disabled.
 * - **1 / 2 / 3**: slot pills for focusing specific FX slots.
 * - **BYPASS**: top-level chain kill-switch.
 */
object FxChainHeader {

    const val ARROW_W = 16f
    const val MORE_BTN_W = 20f
    const val EXIT_BTN_W = 54f
    const val PAGE_TEXT_W = 34f
    const val DRYWET_W = 64f
    fun saveBtnW(ctrlH: Float): Float = ctrlH

    /**
     * Calculates the width of the chain name button so the entire header row
     * fills exactly [maxW] and lines up cleanly with Row 1.
     */
    fun calculateNameWidth(maxW: Float, ctrlH: Float, showArrows: Boolean): Float {
        val gap = 3f
        val saveW = saveBtnW(ctrlH)
        val slotPillsW = 20f * FxChain.SLOT_COUNT + gap * (FxChain.SLOT_COUNT - 1)
        val arrowsReservation = if (showArrows) (ARROW_W * 2f + gap * 2f) else 0f
        return (maxW - (arrowsReservation + saveW + MORE_BTN_W + slotPillsW + gap * 3f)).coerceAtLeast(48f)
    }

    /**
     * Calculates the width of the focused effect name button in Focus Mode
     * so the row fills exactly [maxW].
     */
    fun calculateFocusedNameWidth(maxW: Float, totalPages: Int): Float {
        val gap = 3f
        val slotPillsW = 20f * FxChain.SLOT_COUNT + gap * (FxChain.SLOT_COUNT - 1)
        val stepperReservation = if (totalPages > 1) (ARROW_W * 2f + PAGE_TEXT_W + gap * 3f) else 0f
        return (maxW - (MORE_BTN_W + gap + DRYWET_W + gap + stepperReservation + gap + slotPillsW)).coerceAtLeast(48f)
    }

    /** Steps [chain] to the previous (-1) or next (+1) chain file in its folder. */
    fun stepChain(session: SessionContext, chain: FxChain, dir: Int) {
        val folder = chain.sourceFile?.parentFile ?: FileSystemManager.getFxChainsRoot()
        val files = folder.listFiles { f -> f.isFile && f.extension.equals("lsdfxchain", ignoreCase = true) }
            ?.sortedBy { it.name.lowercase() }
            ?: return
        if (files.isEmpty()) return
        val currentPath = chain.sourceFile?.absolutePath
        val idx = files.indexOfFirst { it.absolutePath == currentPath }
        val nextIdx = when {
            idx < 0 -> if (dir >= 0) 0 else files.lastIndex
            else -> Math.floorMod(idx + dir, files.size)
        }
        FxOps.loadChain(session, files[nextIdx], chain)
    }

    /**
     * Draws the chain selection and management controls:
     * - Group Mode: `[⋮]  Chain Name •  [Save]  [◀] [▶]  [1] [2] [3]`
     * - Focus Mode: `[⋮]  [Focused Effect Name ▾]  [Wet 100%]  [◀ Px/y ▶]  [1] [2] [3]`
     *
     * [onOpenChainBrowse] opens that row's Browse content on the whole-chain list.
     * [onOpenSlotBrowse] opens that row's Browse content targeted at a specific FX slot.
     * [onFocusSlot] notifies callers when a slot is focused or unfocused (allowing auto-switch to FX).
     */
    fun drawControls(
        session: SessionContext,
        mixer: Mixer,
        chain: FxChain,
        bankId: String,
        chainLabel: String,
        ctrlH: Float,
        maxW: Float = 220f,
        deck: Deck? = null,
        onOpenSlotBrowse: ((Int) -> Unit)? = null,
        onFocusSlot: ((Int?) -> Unit)? = null,
        onOpenChainBrowse: () -> Unit
    ) {
        val gap = 3f
        val isDirty = chain.isDirty()
        val isFocused = chain.isFocused()
        val menuId = "##fx_chain_more_$bankId"

        ImGui.pushStyleVar(ImGuiStyleVar.ItemSpacing, gap, 0f)

        if (isFocused) {
            // -- FOCUS MODE HEADER ----------------------------------------------------------------
            val focusedSlot = chain.focusedSlot!!
            val totalPages = chain.totalParamPages(focusedSlot)

            // 1. [⋮] More actions menu
            drawMoreButton(session, mixer, chain, bankId, chainLabel, ctrlH, menuId)

            ImGui.sameLine()

            // 2. [Focused Effect Name] button (click to browse/replace effect in this slot)
            val focusedNameW = calculateFocusedNameWidth(maxW, totalPages)
            drawFocusedEffectButton(session, chain, bankId, focusedSlot, ctrlH, focusedNameW) { slotIdx ->
                onOpenSlotBrowse?.invoke(slotIdx)
            }

            // 3. Focused slot's Dry/Wet (moved off the knobs: knob 1 is the slot's Metaknob)
            ImGui.sameLine()
            drawFocusedDryWet(chain, bankId, focusedSlot, ctrlH)

            // 4. Parameter page stepper [◀ P1/2 ▶] (if totalPages > 1)
            if (totalPages > 1) {
                ImGui.sameLine()
                if (ImGui.button("◀##focus_prev_page_$bankId", ARROW_W, ctrlH)) {
                    FxMacroSync.stepParamPage(bankId, mixer, -1)
                }
                itemTooltip("Previous parameter page.")

                ImGui.sameLine()
                session.uiTheme.withFont(UITheme.FontLevel.CAPTION) {
                    val pageText = "P${chain.focusParamPage + 1}/$totalPages"
                    val curX = ImGui.getCursorScreenPosX()
                    val curY = ImGui.getCursorScreenPosY()
                    ImGui.dummy(PAGE_TEXT_W, ctrlH)
                    val textSz = ImGui.calcTextSize(pageText)
                    val textX = curX + (PAGE_TEXT_W - textSz.x) * 0.5f
                    val textY = curY + (ctrlH - textSz.y) * 0.5f
                    ImGui.getWindowDrawList().addText(textX, textY, TangoPalette.FX_PAGE_TEXT.u32(), pageText)
                }
                itemTooltip("Parameter page ${chain.focusParamPage + 1} of $totalPages.")

                ImGui.sameLine()
                if (ImGui.button("▶##focus_next_page_$bankId", ARROW_W, ctrlH)) {
                    FxMacroSync.stepParamPage(bankId, mixer, 1)
                }
                itemTooltip("Next parameter page.")
            }

            ImGui.sameLine()

            // 5. Slot focus pills [1] [2] [3]
            drawSlotPills(session, mixer, chain, bankId, ctrlH, focusedSlot, onFocusSlot)
        } else {
            // -- GROUP MODE HEADER ----------------------------------------------------------------
            val isDeckAB = deck === mixer.deckA || deck === mixer.deckB
            val isDeckBG = deck === mixer.deckBG
            val showArrows = isDeckAB || isDeckBG

            val isQueueEmpty = when {
                isDeckAB -> FXQueueManager.queue.isEmpty()
                isDeckBG -> FXBgQueueManager.queue.isEmpty()
                else -> true
            }

            val deckTag = when {
                deck === mixer.deckA -> "Deck A"
                deck === mixer.deckB -> "Deck B"
                deck === mixer.deckBG -> "Deck BG"
                else -> ""
            }

            val emptyTooltip = if (isDeckBG) "BG FX Queue is empty. Add items from the Library." else "FX Queue is empty. Add items from the Library."

            // 1. [⋮] More actions menu
            drawMoreButton(session, mixer, chain, bankId, chainLabel, ctrlH, menuId)

            ImGui.sameLine()

            // 2. Chain name button
            val nameW = calculateNameWidth(maxW, ctrlH, showArrows)
            drawChainNameButton(session, chain, bankId, ctrlH, nameW, isDirty, onOpenChainBrowse)

            ImGui.sameLine()

            // 3. [Save] button
            drawSaveButton(session, chain, bankId, ctrlH, isDirty)

            // 4. [◀] and [▶] FX queue items (only if deck supports queues)
            if (showArrows) {
                ImGui.sameLine()

                if (isQueueEmpty) ImGui.beginDisabled(true)
                if (ImGui.button("◀##prev_chain_$bankId", ARROW_W, ctrlH)) {
                    if (isDeckAB) {
                        FXQueueManager.advancePrevious(session, mixer, explicitTargetDeck = deck)
                    } else if (isDeckBG) {
                        FXBgQueueManager.advancePrevious(session, mixer, explicitTargetDeck = deck)
                    }
                }
                if (isQueueEmpty) {
                    ImGui.endDisabled()
                    itemTooltip(emptyTooltip, allowWhenDisabled = true)
                } else {
                    itemTooltip("Previous FX in queue ($deckTag).")
                }

                ImGui.sameLine()

                if (isQueueEmpty) ImGui.beginDisabled(true)
                if (ImGui.button("▶##next_chain_$bankId", ARROW_W, ctrlH)) {
                    if (isDeckAB) {
                        FXQueueManager.advanceNext(session, mixer, explicitTargetDeck = deck)
                    } else if (isDeckBG) {
                        FXBgQueueManager.advanceNext(session, mixer, explicitTargetDeck = deck)
                    }
                }
                if (isQueueEmpty) {
                    ImGui.endDisabled()
                    itemTooltip(emptyTooltip, allowWhenDisabled = true)
                } else {
                    itemTooltip("Next FX in queue ($deckTag).")
                }
            }

            ImGui.sameLine()

            // 5. Slot focus pills [1] [2] [3]
            drawSlotPills(session, mixer, chain, bankId, ctrlH, null, onFocusSlot)
        }

        ImGui.popStyleVar()
    }

    private fun drawSlotPills(
        session: SessionContext,
        mixer: Mixer,
        chain: FxChain,
        bankId: String,
        ctrlH: Float,
        focusedSlot: Int?,
        onFocusSlot: ((Int?) -> Unit)? = null
    ) {
        val pillW = 20f
        for (i in 0 until FxChain.SLOT_COUNT) {
            if (i > 0) ImGui.sameLine()
            val isFocused = focusedSlot == i
            val slot = chain.slots.getOrNull(i)
            val slotNum = i + 1
            val btnLabel = "$slotNum"

            val activeCol = TangoPalette.FX_PILL_ON.u32()
            val inactiveCol = if (slot != null) TangoPalette.FX_PILL_FILLED.u32() else TangoPalette.FX_PILL_EMPTY.u32()
            val textCol = when {
                isFocused -> TangoPalette.WHITE.u32()
                slot != null -> TangoPalette.FX_PILL_TEXT.u32()
                else -> TangoPalette.FX_PILL_TEXT_EMPTY.u32()
            }

            ImGui.pushStyleColor(ImGuiCol.Button, if (isFocused) activeCol else inactiveCol)
            ImGui.pushStyleColor(ImGuiCol.Text, textCol)
            if (ImGui.button("$btnLabel##slot_focus_${bankId}_$i", pillW, ctrlH)) {
                if (isFocused) {
                    FxMacroSync.focusSlot(bankId, mixer, null)
                    onFocusSlot?.invoke(null)
                } else {
                    FxMacroSync.focusSlot(bankId, mixer, i)
                    onFocusSlot?.invoke(i)
                }
            }
            ImGui.popStyleColor(2)

            itemTooltip(
                when {
                    isFocused -> "Slot $slotNum (${slot?.displayName ?: "empty"}) is focused.\nClick to exit Focus Mode."
                    slot != null -> "Focus Slot $slotNum (${slot.displayName}).\nKnob 1 = Metaknob, Knobs 2-4 = top parameters; Dry/Wet is on the header."
                    else -> "Focus Slot $slotNum (empty).\nClick to focus and edit."
                }
            )
        }
    }

    private fun drawFocusedDryWet(chain: FxChain, bankId: String, slotIdx: Int, ctrlH: Float) {
        val slot = chain.slots.getOrNull(slotIdx)
        ImGui.beginDisabled(slot == null)
        ImGui.setNextItemWidth(DRYWET_W)
        val pct = floatArrayOf(((slot?.dryWet?.baseValue ?: 1f) * 100f))
        if (ImGui.sliderFloat("##focus_drywet_$bankId", pct, 0f, 100f, "Wet %.0f%%")) {
            slot?.dryWet?.baseValue = (pct[0] / 100f).coerceIn(0f, 1f)
        }
        // Middle-click resets to fully wet, matching the level faders.
        if (ImGui.isItemHovered() && ImGui.isMouseClicked(2)) slot?.dryWet?.baseValue = 1f
        ImGui.endDisabled()
        itemTooltip("Dry/Wet of the focused effect.\nMiddle-click to reset (100%).")
    }

    private fun drawFocusedEffectButton(
        session: SessionContext,
        chain: FxChain,
        bankId: String,
        focusedSlot: Int,
        ctrlH: Float,
        nameW: Float,
        onOpenSlotBrowse: (Int) -> Unit
    ) {
        val slot = chain.slots.getOrNull(focusedSlot)
        val effectName = slot?.displayName ?: "Slot ${focusedSlot + 1} (Empty)"
        val fullLabel = "$effectName ${Icons.CHEVRON_DOWN}"

        val bgCol = TangoPalette.BADGE_BG.u32()
        val borderCol = TangoPalette.BADGE_BORDER.u32()
        val textCol = TangoPalette.BADGE_TEXT.u32()

        val curX = ImGui.getCursorScreenPosX()
        val curY = ImGui.getCursorScreenPosY()
        val dl = ImGui.getWindowDrawList()
        dl.addRectFilled(curX, curY, curX + nameW, curY + ctrlH, bgCol, 4f)
        dl.addRect(curX, curY, curX + nameW, curY + ctrlH, borderCol, 4f, 0, 1f)

        session.uiTheme.withFont(UITheme.FontLevel.CAPTION) {
            val textSz = ImGui.calcTextSize(fullLabel)
            val tx = curX + (nameW - textSz.x) * 0.5f
            val ty = curY + (ctrlH - textSz.y) * 0.5f
            dl.addText(tx.coerceAtLeast(curX + 4f), ty, textCol, fullLabel)
        }

        if (ImGui.invisibleButton("##focused_effect_${bankId}_$focusedSlot", nameW, ctrlH)) {
            onOpenSlotBrowse(focusedSlot)
        }
        if (ImGui.isItemHovered()) {
            val hoverBorderCol = TangoPalette.BADGE_HOVER_BORDER.u32()
            dl.addRect(curX, curY, curX + nameW, curY + ctrlH, hoverBorderCol, 4f, 0, 1.5f)
        }
        itemTooltip(
            if (slot != null) "Focused Effect: ${slot.displayName} (Slot ${focusedSlot + 1})\nClick to browse/replace effect for this slot."
            else "Slot ${focusedSlot + 1} is empty.\nClick to browse and load an effect."
        )
    }

    private fun drawChainNameButton(
        session: SessionContext,
        chain: FxChain,
        bankId: String,
        ctrlH: Float,
        nameW: Float,
        isDirty: Boolean,
        onOpenChainBrowse: () -> Unit
    ) {
        val displayName = if (chain.name.isBlank()) "Untitled" else chain.name
        val dirtyMarker = if (isDirty) " •" else ""
        val fullLabel = "$displayName$dirtyMarker ${Icons.CHEVRON_DOWN}"

        if (isDirty) {
            ImGui.pushStyleColor(ImGuiCol.Text, TangoPalette.FX_DIRTY_TEXT.u32())
        }
        if (ImGui.button("$fullLabel##fx_chain_name_$bankId", nameW, ctrlH)) {
            onOpenChainBrowse()
        }
        if (isDirty) {
            ImGui.popStyleColor()
        }
        itemTooltip(
            "${chain.name.ifBlank { "Untitled" }}${if (isDirty) " (Modified)" else ""}\n" +
            "Source: ${chain.sourceFile?.name ?: "Unsaved"}\n" +
            "Click to browse saved chains, or drop a .lsdfxchain here."
        )

        // Drag & drop receiver for chain name button
        if (ImGui.beginDragDropTarget()) {
            ImGui.acceptDragDropPayload<String>("ASSET_ITEM")?.let { path ->
                val file = File(path)
                if (file.extension.equals("lsdfxchain", ignoreCase = true)) {
                    FxOps.loadChain(session, file, chain)
                }
            }
            ImGui.endDragDropTarget()
        }
    }

    private fun drawSaveButton(session: SessionContext, chain: FxChain, bankId: String, ctrlH: Float, isDirty: Boolean) {
        val canOverwrite = chain.sourceFile != null
        val saveCol = if (isDirty) TangoPalette.u32(TangoPalette.ALERT.dark) else TangoPalette.FX_SAVE_BG.u32()
        val inkCol = if (isDirty) TangoPalette.u32(TangoPalette.inkFor(TangoPalette.ALERT.dark)) else TangoPalette.FX_SAVE_INK.u32()
        ImGui.pushStyleColor(ImGuiCol.Button, saveCol)
        ImGui.pushStyleColor(ImGuiCol.Text, inkCol)
        val saveW = saveBtnW(ctrlH)
        session.uiTheme.withFont(UITheme.FontLevel.BODY) {
            if (ImGui.button("${Icons.SAVE}##save_$bankId", saveW, ctrlH)) {
                if (canOverwrite) {
                    val file = chain.sourceFile!!
                    val dto = chain.toFxChainDto(chain.name)
                    session.presetRepository.saveFxChainAsync(file, chain.name, dto)
                    chain.markClean(file)
                } else {
                    openSaveAsModal(session, chain)
                }
            }
        }
        ImGui.popStyleColor(2)
        itemTooltip(if (canOverwrite) "Save changes to ${chain.sourceFile?.name}." else "Save as new FX chain (.lsdfxchain).")
    }

    private fun drawMoreButton(
        session: SessionContext,
        mixer: Mixer,
        chain: FxChain,
        bankId: String,
        chainLabel: String,
        ctrlH: Float,
        menuId: String
    ) {
        if (ImGui.button("${Icons.MORE_VERTICAL}##more_btn_$bankId", MORE_BTN_W, ctrlH)) {
            ImGui.openPopup(menuId)
        }
        itemTooltip("Chain operations (Save As, New, Revert, Clear, Copy/Paste, Focus, Resync).")

        pushOpenDropdownPadding()
        if (ImGui.beginPopup(menuId)) {
            pushOpenDropdownFont()
            ImGui.textDisabled("$chainLabel FX Chain")
            ImGui.separator()

            if (chain.isFocused()) {
                if (ImGui.menuItem("Exit Focus Mode")) {
                    FxMacroSync.focusSlot(bankId, mixer, null)
                }
            } else {
                if (ImGui.beginMenu("Focus Slot…")) {
                    for (i in 0 until FxChain.SLOT_COUNT) {
                        val slot = chain.slots.getOrNull(i)
                        val label = "Slot ${i + 1}" + (slot?.displayName?.let { " ($it)" } ?: " (empty)")
                        if (ImGui.menuItem(label)) {
                            FxMacroSync.focusSlot(bankId, mixer, i)
                        }
                    }
                    ImGui.endMenu()
                }
            }
            ImGui.separator()

            if (ImGui.menuItem("Save As…")) {
                openSaveAsModal(session, chain)
            }
            ImGui.separator()

            if (ImGui.menuItem("New Chain")) {
                FxOps.newChain(chain)
            }
            val canRevert = chain.isDirty() && chain.baselineDto != null
            if (ImGui.menuItem("Revert to Saved", "", false, canRevert)) {
                FxOps.revertChain(chain)
            }
            if (ImGui.menuItem("Clear All Slots")) {
                FxOps.clearChain(chain)
            }
            ImGui.separator()

            if (ImGui.menuItem("Copy Chain")) {
                ClipboardManager.copyFxChain(chain.toFxChainDto())
            }
            val canPaste = ClipboardManager.fxChainClipboard != null
            if (ImGui.menuItem("Paste Chain", "", false, canPaste)) {
                ClipboardManager.fxChainClipboard?.let { FxOps.applyChain(chain, it) }
            }
            popOpenDropdownFont()
            ImGui.endPopup()
        }
        popOpenDropdownPadding()
    }

    private fun openSaveAsModal(session: SessionContext, chain: FxChain) {
        SavePresetModal.request(
            title = "Save FX Chain As",
            confirmLabel = "Save",
            defaultName = chain.name.ifBlank { "fx_chain" },
            targetDir = FileSystemManager.getFxChainsRoot(),
            extension = "lsdfxchain"
        ) { name, tags ->
            val file = File(FileSystemManager.getFxChainsRoot(), "$name.lsdfxchain")
            val dto = chain.toFxChainDto(name, tags)
            session.presetRepository.saveFxChainAsync(file, name, dto, tags)
            chain.markClean(file)
        }
    }

    /**
     * Draws the top-level chain kill-switch for [chain]. Static "BYPASS" label (state shown by
     * color, not text) so it reads as a signal-chain switch, distinct from the [SRC]/[FX]
     * knob-assign pill this sits beside -- the pill picks *what the knobs show*, this picks
     * *whether the chain processes audio/video at all*.
     */
    fun drawBypassButton(session: SessionContext, chain: FxChain, id: String, ctrlH: Float, width: Float = 64f) {
        val isActive = chain.enabled
        val label = "BYPASS"

        PerformanceColors.pushActiveToggleStyle(isActive)
        session.uiTheme.withFont(UITheme.FontLevel.CAPTION) {
            if (ImGui.button("$label##bypass_$id", width, ctrlH)) {
                chain.enabled = !chain.enabled
            }
        }
        PerformanceColors.popActiveToggleStyle()
        itemTooltip(if (isActive) "FX chain is active. Click to bypass." else "FX chain is bypassed. Click to enable.")
    }
}
