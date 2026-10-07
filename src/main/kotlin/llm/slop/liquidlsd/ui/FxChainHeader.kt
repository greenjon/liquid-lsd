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
 * - **Name**: click opens that row's pair view on the whole-chain list (search filter). Drops of .lsdfxchain load here.
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
    private const val FOCUS_PILL_W = 26f
    /** Gap between controls on a deck's line 2; SRC and FX halves share it so their buttons line up. */
    const val LINE_GAP = 3f
    /** Extra space left of the left arrow and right of the right arrow. */
    const val ARROW_PAD = 6f

    /** Width of each of the six equal cells (Save, ◀, ▶, 1, 2, 3) on a deck's line 2 of [width], after the kebab. */
    fun cellW(width: Float): Float = ((width - MORE_BTN_W - 6 * LINE_GAP - 2 * ARROW_PAD) / 6f).coerceAtLeast(16f)
    fun saveBtnW(ctrlH: Float): Float = ctrlH

    /** Row-specific reactions to header clicks. One long-lived instance per row, so drawing allocates no closures. */
    interface Actions {
        /** Opens the row's pair view targeted at FX slot [slotIdx]. */
        fun openSlotBrowse(slotIdx: Int)
        /** Opens the row's pair view on the whole-chain list. */
        fun openChainBrowse()
    }

    /**
     * Per-bank widget ids and cached strings. ImGui ids are built once per bank; texts are rebuilt only when
     * the values they are made from change. The draw path is single-threaded, so the scratch array is shared safely.
     */
    private class Strings(bankId: String) {
        val menuId = "##fx_chain_more_$bankId"
        val moreBtn = "${Icons.MORE_VERTICAL}##more_btn_$bankId"
        val prevPage = "◀##focus_prev_page_$bankId"
        val nextPage = "▶##focus_next_page_$bankId"
        val prevChain = "◀##prev_chain_$bankId"
        val nextChain = "▶##next_chain_$bankId"
        val chainNameSuffix = "##fx_chain_name_$bankId"
        val save = "${Icons.SAVE}##save_$bankId"
        val dryWet = "##focus_drywet_$bankId"
        val pillLabels = Array(FxChain.SLOT_COUNT) { "${it + 1}##slot_focus_${bankId}_$it" }
        val focusedEffectIds = Array(FxChain.SLOT_COUNT) { "##focused_effect_${bankId}_$it" }
        val pillTips = Array(FxChain.SLOT_COUNT) { TipCache() }
        val pageText = TipCache()
        val pageTip = TipCache()
        val chainLabel = TipCache()
        val chainTip = TipCache()
        val focusedLabel = TipCache()
        val focusedTip = TipCache()
        val saveTip = TipCache()
        val menuTitle = TipCache()
        val wet = floatArrayOf(0f)
    }

    private val strings = HashMap<String, Strings>()
    private fun stringsFor(bankId: String): Strings = strings.getOrPut(bankId) { Strings(bankId) }

    private const val EMPTY_TIP_BG = "BG FX Queue is empty. Add items from the Library."
    private const val TIP_PREV_A = "Previous FX in queue (Deck A)."
    private const val TIP_PREV_B = "Previous FX in queue (Deck B)."
    private const val TIP_PREV_BG = "Previous FX in queue (Deck BG)."
    private const val TIP_NEXT_A = "Next FX in queue (Deck A)."
    private const val TIP_NEXT_B = "Next FX in queue (Deck B)."
    private const val TIP_NEXT_BG = "Next FX in queue (Deck BG)."
    private const val EMPTY_TIP = "FX Queue is empty. Add items from the Library."

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
     * [actions] receives chain-browse, slot-browse and slot-focus clicks (focus lets callers auto-switch to FX).
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
        actions: Actions
    ) {
        val gap = 3f
        val isDirty = chain.isDirty()
        val isFocused = chain.isFocused()
        val st = stringsFor(bankId)
        val menuId = st.menuId

        ImGui.pushStyleVar(ImGuiStyleVar.ItemSpacing, gap, 0f)

        if (isFocused) {
            // -- FOCUS MODE HEADER ----------------------------------------------------------------
            val focusedSlot = chain.focusedSlot!!
            val totalPages = chain.totalParamPages(focusedSlot)

            // 1. [⋮] More actions menu
            drawMoreButton(session, mixer, chain, bankId, chainLabel, ctrlH, st)

            ImGui.sameLine()

            // 2. [Focused Effect Name] button (click to browse/replace effect in this slot)
            val focusedNameW = calculateFocusedNameWidth(maxW, totalPages)
            drawFocusedEffectButton(session, chain, st, focusedSlot, ctrlH, focusedNameW, actions)

            // 3. Focused slot's Dry/Wet (moved off the knobs: knob 1 is the slot's Metaknob)
            ImGui.sameLine()
            drawFocusedDryWet(chain, st, focusedSlot, ctrlH)

            // 4. Parameter page stepper [◀ P1/2 ▶] (if totalPages > 1)
            if (totalPages > 1) {
                ImGui.sameLine()
                drawPageStepper(session, mixer, chain, bankId, st, totalPages, ctrlH)
            }

            ImGui.sameLine()

            // 5. Slot focus pills [1] [2] [3]
            drawSlotPills(mixer, chain, bankId, st, ctrlH, focusedSlot, actions)
        } else {
            // -- GROUP MODE HEADER ----------------------------------------------------------------
            val isDeckAB = deck === mixer.deckA || deck === mixer.deckB
            val isDeckBG = deck === mixer.deckBG
            val showArrows = isDeckAB || isDeckBG

            // 1. [⋮] More actions menu
            drawMoreButton(session, mixer, chain, bankId, chainLabel, ctrlH, st)

            ImGui.sameLine()

            // 2. Chain name button
            val nameW = calculateNameWidth(maxW, ctrlH, showArrows)
            drawChainNameButton(session, chain, st, ctrlH, nameW, isDirty, actions, DockOutline.selects(session.parametersState, bankId, ParametersState.BrowseTarget.FxChain(null)), bankId)

            ImGui.sameLine()

            // 3. [Save] button
            drawSaveButton(session, chain, st, ctrlH, isDirty)

            // 4. [◀] and [▶] FX queue items (only if deck supports queues)
            if (showArrows) {
                ImGui.sameLine()
                drawQueueArrows(session, mixer, deck!!, isDeckAB, st, ctrlH, ARROW_W)
            }

            ImGui.sameLine()

            // 5. Slot focus pills [1] [2] [3]
            drawSlotPills(mixer, chain, bankId, st, ctrlH, null, actions)
        }

        ImGui.popStyleVar()
    }

    /**
     * Line 1 of a deck FX half, [nameW] wide: the chain name (group mode) or the focused effect's name
     * (focus mode). Both open the row's pair view on click.
     */
    fun drawNameLine(
        session: SessionContext,
        mixer: Mixer,
        chain: FxChain,
        bankId: String,
        nameW: Float,
        ctrlH: Float,
        actions: Actions
    ) {
        val st = stringsFor(bankId)
        val focusedSlot = chain.focusedSlot
        if (chain.isFocused() && focusedSlot != null) {
            drawFocusedEffectButton(session, chain, st, focusedSlot, ctrlH, nameW, actions)
        } else {
            drawChainNameButton(
                session, chain, st, ctrlH, nameW, chain.isDirty(), actions,
                DockOutline.selects(session.parametersState, bankId, ParametersState.BrowseTarget.FxChain(null)), bankId
            )
        }
    }

    /**
     * Line 2 of a deck FX half, stretched to exactly [width]: `[⋮]` then the remaining controls sharing the
     * rest of the line equally -- group mode `[Save] [◀] [▶] [1] [2] [3]` (arrows only on Decks A, B, BG),
     * focus mode `[Wet %] [◀ Px/y ▶] [1] [2] [3]` (stepper only with more than one parameter page).
     */
    fun drawControlLine(
        session: SessionContext,
        mixer: Mixer,
        chain: FxChain,
        bankId: String,
        chainLabel: String,
        ctrlH: Float,
        width: Float,
        deck: Deck?,
        actions: Actions
    ) {
        val gap = 3f
        val st = stringsFor(bankId)
        ImGui.pushStyleVar(ImGuiStyleVar.ItemSpacing, gap, 0f)
        drawMoreButton(session, mixer, chain, bankId, chainLabel, ctrlH, st)

        val focusedSlot = chain.focusedSlot
        if (chain.isFocused() && focusedSlot != null) {
            val totalPages = chain.totalParamPages(focusedSlot)
            val pillW = FOCUS_PILL_W
            val pillsW = pillW * FxChain.SLOT_COUNT + gap * (FxChain.SLOT_COUNT - 1)
            val stepperW = if (totalPages > 1) gap + ARROW_W * 2f + PAGE_TEXT_W + gap * 2f else 0f
            val wetW = (width - MORE_BTN_W - gap * 2f - stepperW - pillsW).coerceAtLeast(DRYWET_W)

            ImGui.sameLine()
            drawFocusedDryWet(chain, st, focusedSlot, ctrlH, wetW)
            if (totalPages > 1) {
                ImGui.sameLine()
                drawPageStepper(session, mixer, chain, bankId, st, totalPages, ctrlH)
            }
            ImGui.sameLine()
            drawSlotPills(mixer, chain, bankId, st, ctrlH, focusedSlot, actions, pillW)
        } else {
            val isDeckAB = deck === mixer.deckA || deck === mixer.deckB
            val isDeckBG = deck === mixer.deckBG
            val showArrows = isDeckAB || isDeckBG
            // Equal-width cells: Save, 2 arrow cells (left empty where the deck has no queue), 3 pills.
            val cellW = cellW(width)

            ImGui.sameLine()
            drawSaveButton(session, chain, st, ctrlH, chain.isDirty(), cellW)
            if (showArrows) {
                ImGui.sameLine(0f, gap + ARROW_PAD)
                drawQueueArrows(session, mixer, deck!!, isDeckAB, st, ctrlH, cellW)
                ImGui.sameLine(0f, gap + ARROW_PAD)
            } else {
                ImGui.sameLine()
                ImGui.dummy(cellW * 2f + gap + ARROW_PAD * 2f, ctrlH)
                ImGui.sameLine()
            }
            drawSlotPills(mixer, chain, bankId, st, ctrlH, null, actions, cellW)
        }
        ImGui.popStyleVar()
    }

    private fun drawPageStepper(session: SessionContext, mixer: Mixer, chain: FxChain, bankId: String, st: Strings, totalPages: Int, ctrlH: Float) {
        if (ButtonChrome.button(st.prevPage, ARROW_W, ctrlH)) {
            FxMacroSync.stepParamPage(bankId, mixer, -1)
        }
        itemTooltip("Previous parameter page.")

        ImGui.sameLine()
        session.uiTheme.withFont(UITheme.FontLevel.CAPTION) {
            val pageText = st.pageText.get(chain.focusParamPage, totalPages) { "P${chain.focusParamPage + 1}/$totalPages" }
            val curX = ImGui.getCursorScreenPosX()
            val curY = ImGui.getCursorScreenPosY()
            ImGui.dummy(PAGE_TEXT_W, ctrlH)
            val textSz = ImGui.calcTextSize(pageText)
            val textX = curX + (PAGE_TEXT_W - textSz.x) * 0.5f
            val textY = curY + (ctrlH - textSz.y) * 0.5f
            ImGui.getWindowDrawList().addText(textX, textY, TangoPalette.FX_PAGE_TEXT.u32(), pageText)
        }
        itemTooltip(st.pageTip.get(chain.focusParamPage, totalPages) { "Parameter page ${chain.focusParamPage + 1} of $totalPages." })

        ImGui.sameLine()
        if (ButtonChrome.button(st.nextPage, ARROW_W, ctrlH)) {
            FxMacroSync.stepParamPage(bankId, mixer, 1)
        }
        itemTooltip("Next parameter page.")
    }

    private fun drawQueueArrows(session: SessionContext, mixer: Mixer, deck: Deck, isDeckAB: Boolean, st: Strings, ctrlH: Float, arrowW: Float) {
        val isDeckBG = !isDeckAB
        val isQueueEmpty = if (isDeckAB) FXQueueManager.queue.isEmpty() else FXBgQueueManager.queue.isEmpty()
        val emptyTooltip = if (isDeckBG) EMPTY_TIP_BG else EMPTY_TIP
        if (isQueueEmpty) ImGui.beginDisabled(true)
        if (ButtonChrome.button(st.prevChain, arrowW, ctrlH)) {
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
            itemTooltip(if (isDeckAB) (if (deck === mixer.deckA) TIP_PREV_A else TIP_PREV_B) else TIP_PREV_BG)
        }

        ImGui.sameLine()

        if (isQueueEmpty) ImGui.beginDisabled(true)
        if (ButtonChrome.button(st.nextChain, arrowW, ctrlH)) {
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
            itemTooltip(if (isDeckAB) (if (deck === mixer.deckA) TIP_NEXT_A else TIP_NEXT_B) else TIP_NEXT_BG)
        }
    }

    private fun drawSlotPills(
        mixer: Mixer,
        chain: FxChain,
        bankId: String,
        st: Strings,
        ctrlH: Float,
        focusedSlot: Int?,
        actions: Actions,
        pillW: Float = 20f
    ) {
        for (i in 0 until FxChain.SLOT_COUNT) {
            if (i > 0) ImGui.sameLine()
            val isFocused = focusedSlot == i
            val slot = chain.slots.getOrNull(i)

            val activeCol = TangoPalette.FX_PILL_ON.u32()
            val inactiveCol = if (slot != null) TangoPalette.FX_PILL_FILLED.u32() else TangoPalette.FX_PILL_EMPTY.u32()
            val textCol = when {
                isFocused -> TangoPalette.WHITE.u32()
                slot != null -> TangoPalette.FX_PILL_TEXT.u32()
                else -> TangoPalette.FX_PILL_TEXT_EMPTY.u32()
            }

            ButtonChrome.pushColor(if (isFocused) activeCol else inactiveCol)
            ImGui.pushStyleColor(ImGuiCol.Text, textCol)
            if (ButtonChrome.button(st.pillLabels[i], pillW, ctrlH)) {
                FxMacroSync.focusSlot(bankId, mixer, if (isFocused) null else i)
            }
            ImGui.popStyleColor(4)

            val slotName = slot?.displayName
            itemTooltip(st.pillTips[i].get(isFocused, slotName) {
                val slotNum = i + 1
                when {
                    isFocused -> "Slot $slotNum (${slotName ?: "empty"}) is focused.\nClick to exit Focus Mode."
                    slot != null -> "Focus Slot $slotNum ($slotName).\nKnob 1 = Metaknob, Knobs 2-4 = top parameters; Dry/Wet is on the header."
                    else -> "Focus Slot $slotNum (empty).\nClick to focus and edit."
                }
            })
        }
    }

    private fun drawFocusedDryWet(chain: FxChain, st: Strings, slotIdx: Int, ctrlH: Float, width: Float = DRYWET_W) {
        val slot = chain.slots.getOrNull(slotIdx)
        ImGui.beginDisabled(slot == null)
        ImGui.setNextItemWidth(width)
        val pct = st.wet
        pct[0] = (slot?.dryWet?.baseValue ?: 1f) * 100f
        if (ImGui.sliderFloat(st.dryWet, pct, 0f, 100f, "Wet %.0f%%")) {
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
        st: Strings,
        focusedSlot: Int,
        ctrlH: Float,
        nameW: Float,
        actions: Actions
    ) {
        val slot = chain.slots.getOrNull(focusedSlot)
        val slotName = slot?.displayName
        val fullLabel = st.focusedLabel.get(slotName, focusedSlot) { "${slotName ?: "Slot ${focusedSlot + 1} (Empty)"} ${Icons.CHEVRON_DOWN}" }

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

        if (ImGui.invisibleButton(st.focusedEffectIds[focusedSlot], nameW, ctrlH)) {
            actions.openSlotBrowse(focusedSlot)
        }
        if (ImGui.isItemHovered()) {
            val hoverBorderCol = TangoPalette.BADGE_HOVER_BORDER.u32()
            dl.addRect(curX, curY, curX + nameW, curY + ctrlH, hoverBorderCol, 4f, 0, 1.5f)
        }
        itemTooltip(st.focusedTip.get(slotName, focusedSlot) {
            if (slot != null) "Focused Effect: ${slot.displayName} (Slot ${focusedSlot + 1})\nClick to browse/replace the effect (opens this deck's source and FX rows over the browser)."
            else "Slot ${focusedSlot + 1} is empty.\nClick to browse and load an effect (opens the pair view)."
        })
    }

    private fun drawChainNameButton(
        session: SessionContext,
        chain: FxChain,
        st: Strings,
        ctrlH: Float,
        nameW: Float,
        isDirty: Boolean,
        actions: Actions,
        dockSelected: Boolean,
        bankId: String
    ) {
        val name = chain.name
        val fullLabel = st.chainLabel.get(name, isDirty) {
            "${name.ifBlank { "Untitled" }}${if (isDirty) " •" else ""} ${Icons.CHEVRON_DOWN}${st.chainNameSuffix}"
        }

        if (isDirty) {
            ImGui.pushStyleColor(ImGuiCol.Text, TangoPalette.FX_DIRTY_TEXT.u32())
        }
        if (ButtonChrome.button(fullLabel, nameW, ctrlH)) {
            actions.openChainBrowse()
        }
        if (dockSelected) DockOutline.drawAroundLastItem(bankId)
        if (isDirty) {
            ImGui.popStyleColor()
        }
        val sourceName = chain.sourceFile?.name
        itemTooltip(st.chainTip.get(name, isDirty, sourceName) {
            "${name.ifBlank { "Untitled" }}${if (isDirty) " (Modified)" else ""}\n" +
            "Source: ${sourceName ?: "Unsaved"}\n" +
            "Click to browse saved chains (opens the pair view), or drop a .lsdfxchain here."
        })

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

    private fun drawSaveButton(session: SessionContext, chain: FxChain, st: Strings, ctrlH: Float, isDirty: Boolean, width: Float = saveBtnW(ctrlH)) {
        val canOverwrite = chain.sourceFile != null
        val saveCol = if (isDirty) TangoPalette.u32(TangoPalette.ALERT.dark) else TangoPalette.FX_SAVE_BG.u32()
        val inkCol = if (isDirty) TangoPalette.u32(TangoPalette.inkFor(TangoPalette.ALERT.dark)) else TangoPalette.FX_SAVE_INK.u32()
        ButtonChrome.pushColor(saveCol)
        ImGui.pushStyleColor(ImGuiCol.Text, inkCol)
        session.uiTheme.withFont(UITheme.FontLevel.BODY) {
            if (ButtonChrome.button(st.save, width, ctrlH)) {
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
        ImGui.popStyleColor(4)
        val saveName = chain.sourceFile?.name
        itemTooltip(st.saveTip.get(saveName) { if (saveName != null) "Save changes to $saveName." else "Save as new FX chain (.lsdfxchain)." })
    }

    private fun drawMoreButton(
        session: SessionContext,
        mixer: Mixer,
        chain: FxChain,
        bankId: String,
        chainLabel: String,
        ctrlH: Float,
        st: Strings
    ) {
        val menuId = st.menuId
        if (ButtonChrome.button(st.moreBtn, MORE_BTN_W, ctrlH)) {
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
            if (ButtonChrome.button("$label##bypass_$id", width, ctrlH)) {
                chain.enabled = !chain.enabled
            }
            }
        PerformanceColors.popActiveToggleStyle()
        itemTooltip(if (isActive) "FX chain is active. Click to bypass." else "FX chain is bypassed. Click to enable.")
    }
}
