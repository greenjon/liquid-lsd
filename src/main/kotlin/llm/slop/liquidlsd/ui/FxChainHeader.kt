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
import llm.slop.liquidlsd.ui.browser.BrowseKind
import llm.slop.liquidlsd.ui.browser.BrowseSection
import llm.slop.liquidlsd.ui.browser.LibraryFolders
import java.io.File
import kotlin.math.roundToInt

/**
 * Shared chain-level controls for every Performance FX chain row (deck rows and the Master row):
 *
 *   `[⋮]  Chain Name •  [Save]  [◀] [▶]  [1] [2] [3]`   ...   `[FX ON]`
 *
 * - **⋮ menu** ([drawChainMenu], also on the chain's Edit tab): Focus, Save As, Revert, Clear, Copy / Paste chain.
 * - **Name**: click opens that row's pair view on the whole-chain list (search filter). Drops of .lsdfxchain load here.
 * - **• (dirty dot)**: shows amber when the chain differs from its loaded baseline or has unsaved edits.
 * - **Save**: overwrites source file (or acts as Save As if untitled).
 * - **◀ / ▶**: on Deck A, B, and BG, steps through the respective live FX queue (A/B or BG) directly
 *   on that deck. Bypassed/omitted on Deck PV and Master FX. If the queue is empty, rendered disabled.
 * - **1 / 2 / 3**: slot pills for focusing specific FX slots.
 * - **FX ON**: the chain's on/off switch, lit while on.
 */
object FxChainHeader {

    const val ARROW_W = 16f
    const val MORE_BTN_W = 20f

    /** What Chain view is, for its button (BayHeader) and the chain menu item. */
    const val CHAIN_VIEW_DESC = "Chain view: one row per FX slot, with the source row (Transitions on Master) under them, so all three slots are playable at once."
    private const val MORE_TIP = "Chain operations (Focus, Chain View, Save As, Revert, Clear, Copy/Paste)."
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
    private class Strings(val bankId: String) {
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
        val wet = floatArrayOf(0f)
    }

    private val strings = HashMap<String, Strings>()
    private fun stringsFor(bankId: String): Strings = strings.getOrPut(bankId) { Strings(bankId) }

    private const val EMPTY_TIP_BG = "The BG FX queue is empty. Add items from the Library."
    private const val TIP_PREV_A = "Previous item in the A/B FX queue, applied to Deck A.\nDecks A and B share one queue position."
    private const val TIP_PREV_B = "Previous item in the A/B FX queue, applied to Deck B.\nDecks A and B share one queue position."
    private const val TIP_PREV_BG = "Previous item in the BG FX queue, applied to Deck BG."
    private const val TIP_NEXT_A = "Next item in the A/B FX queue, applied to Deck A.\nDecks A and B share one queue position."
    private const val TIP_NEXT_B = "Next item in the A/B FX queue, applied to Deck B.\nDecks A and B share one queue position."
    private const val TIP_NEXT_BG = "Next item in the BG FX queue, applied to Deck BG."
    private const val EMPTY_TIP = "The A/B FX queue is empty. Add items from the Library."

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
                drawPageStepper(session, mixer, chain, bankId, st, focusedSlot, totalPages, ctrlH)
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
                drawQueueArrows(session, mixer, deck, isDeckAB, st, ctrlH, cellW)
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

    /**
     * The two lines of a Chain-view slot row ([slotBankId] is the slot's own bank, `FxMacroSync.slotBankId`): line 1 the
     * effect's name (click to browse that slot), line 2 `[⋮] [Wet %] [◀ Px/y ▶]` -- the focus-mode header for this slot,
     * without the focus pills, since a slot row is always "focused" on its slot. The ⋮ menu is the chain's.
     */
    fun drawSlotHeader(
        session: SessionContext,
        mixer: Mixer,
        chain: FxChain,
        slotBankId: String,
        slotIdx: Int,
        chainLabel: String,
        x: Float,
        row1Y: Float,
        row2Y: Float,
        nameW: Float,
        width: Float,
        ctrlH: Float,
        actions: Actions
    ) {
        val st = stringsFor(slotBankId)
        val chainBankId = FxMacroSync.chainBankIdOf(slotBankId) ?: slotBankId

        ImGui.setCursorScreenPos(x, row1Y)
        ImGui.beginGroup()
        drawFocusedEffectButton(session, chain, st, slotIdx, ctrlH, nameW, actions)
        ImGui.endGroup()

        ImGui.setCursorScreenPos(x, row2Y)
        ImGui.beginGroup()
        val gap = 3f
        ImGui.pushStyleVar(ImGuiStyleVar.ItemSpacing, gap, 0f)
        if (ButtonChrome.button(st.moreBtn, MORE_BTN_W, ctrlH)) ImGui.openPopup(st.menuId)
        itemTooltip(MORE_TIP)
        drawChainMenu(session, mixer, chain, chainBankId, chainLabel, st.menuId)
        val totalPages = chain.totalParamPages(slotIdx)
        val stepperW = if (totalPages > 1) gap + ARROW_W * 2f + PAGE_TEXT_W + gap * 2f else 0f
        ImGui.sameLine()
        drawFocusedDryWet(chain, st, slotIdx, ctrlH, (width - MORE_BTN_W - gap - stepperW).coerceAtLeast(DRYWET_W))
        if (totalPages > 1) {
            ImGui.sameLine()
            drawPageStepper(session, mixer, chain, slotBankId, st, slotIdx, totalPages, ctrlH)
        }
        ImGui.popStyleVar()
        ImGui.endGroup()
    }

    private fun drawPageStepper(session: SessionContext, mixer: Mixer, chain: FxChain, bankId: String, st: Strings, slotIdx: Int, totalPages: Int, ctrlH: Float) {
        val page = chain.slotParamPage[slotIdx]
        if (ButtonChrome.button(st.prevPage, ARROW_W, ctrlH)) {
            FxMacroSync.stepParamPage(bankId, mixer, -1)
        }
        llm.slop.liquidlsd.tour.WidgetRegistry.note { "fxpage_prev_$bankId" }
        itemTooltip("Previous parameter page.")

        ImGui.sameLine()
        session.uiTheme.withFont(UITheme.FontLevel.CAPTION) {
            val pageText = st.pageText.get(page, totalPages) { "P${page + 1}/$totalPages" }
            val curX = ImGui.getCursorScreenPosX()
            val curY = ImGui.getCursorScreenPosY()
            ImGui.dummy(PAGE_TEXT_W, ctrlH)
            val textSz = ImGui.calcTextSize(pageText)
            val textX = curX + (PAGE_TEXT_W - textSz.x) * 0.5f
            val textY = curY + (ctrlH - textSz.y) * 0.5f
            ImGui.getWindowDrawList().addText(textX, textY, TangoPalette.FX_PAGE_TEXT.u32(), pageText)
        }
        itemTooltip(st.pageTip.get(page, totalPages) { "Parameter page ${page + 1} of $totalPages." })

        ImGui.sameLine()
        if (ButtonChrome.button(st.nextPage, ARROW_W, ctrlH)) {
            FxMacroSync.stepParamPage(bankId, mixer, 1)
        }
        llm.slop.liquidlsd.tour.WidgetRegistry.note { "fxpage_next_$bankId" }
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
            llm.slop.liquidlsd.tour.WidgetRegistry.note { "fxpill_${bankId}_$i" }
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
            slot?.let { setSlotWet(st.bankId, slotIdx, it, pct[0] / 100f) }
        }
        llm.slop.liquidlsd.tour.WidgetRegistry.note { "fxwet_" + st.bankId }
        // Middle-click resets to fully wet, matching the level faders.
        if (ImGui.isItemHovered() && ImGui.isMouseClicked(2)) slot?.let { setSlotWet(st.bankId, slotIdx, it, 1f) }
        ImGui.endDisabled()
        itemTooltip(
            if (slot?.dryWet?.metaDrivenBy != null) "Dry/Wet of this effect, driven by its Metaknob: moving this moves the Metaknob.\nMiddle-click to reset (100%)."
            else "Dry/Wet of this effect.\nMiddle-click to reset (100%)."
        )
    }

    /** Writes a slot's Dry/Wet, or runs it back onto the Metaknob when a Metaknob link owns it (see KnobDrive). */
    private fun setSlotWet(bankId: String, slotIdx: Int, slot: llm.slop.liquidlsd.rendering.isf.ISFFilter, wet: Float) {
        val v = wet.coerceIn(0f, 1f)
        val label = FxMacroSync.labelFor(bankId)
        if (label != null && llm.slop.liquidlsd.macro.KnobDrive.setViaKnob("$label/FX/FX${slotIdx + 1}/DryWet", slot.dryWet, v)) return
        slot.dryWet.baseValue = v
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
        llm.slop.liquidlsd.tour.WidgetRegistry.note { "fxfocus_name_" + st.bankId }
        if (ImGui.isItemHovered()) {
            val hoverBorderCol = TangoPalette.BADGE_HOVER_BORDER.u32()
            dl.addRect(curX, curY, curX + nameW, curY + ctrlH, hoverBorderCol, 4f, 0, 1.5f)
        }
        itemTooltip(st.focusedTip.get(slotName, focusedSlot) {
            if (slot != null) "${slot.displayName} (Slot ${focusedSlot + 1})\nClick to browse/replace the effect in the list below (click again to stop)."
            else "Slot ${focusedSlot + 1} is empty.\nClick to browse and load an effect in the list below (click again to stop)."
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
        // The chain's own Dry/Wet lives in Edit, so the name says when it is turned down (dimmed at 0: the FX is gone).
        val wetPct = (chain.dryWet.value * 100f).roundToInt().coerceIn(0, 100)
        val fullLabel = st.chainLabel.get(name, isDirty, wetPct) {
            "${name.ifBlank { "Untitled" }}${if (isDirty) " •" else ""}${if (wetPct < 100) "  Wet $wetPct%" else ""} ${Icons.CHEVRON_DOWN}${st.chainNameSuffix}"
        }

        val textCol = when {
            wetPct == 0 -> TangoPalette.FX_SLOT_NAME_OFF.u32()
            isDirty -> TangoPalette.FX_DIRTY_TEXT.u32()
            else -> null
        }
        if (textCol != null) ImGui.pushStyleColor(ImGuiCol.Text, textCol)
        if (ButtonChrome.button(fullLabel, nameW, ctrlH)) {
            actions.openChainBrowse()
        }
        llm.slop.liquidlsd.tour.WidgetRegistry.note { "fxchain_name_$bankId" }
        if (dockSelected) DockOutline.drawAroundLastItem(bankId)
        if (textCol != null) ImGui.popStyleColor()
        val source = chain.sourceFile
        itemTooltip(st.chainTip.get(name, source, wetPct * 2 + if (isDirty) 1 else 0) {
            "${name.ifBlank { "Untitled" }}${if (isDirty) " (Modified)" else ""}\n" +
            "Source: ${source?.name ?: "Unsaved"}\n" +
            (if (wetPct < 100) "Chain Dry/Wet is $wetPct% (set it on the chain's Edit tab).\n" else "") +
            "Click to browse saved chains in the list below (click again to stop), or drop a .lsdfxchain here."
        })

        // Drag & drop receiver for chain name button
        if (ImGui.beginDragDropTarget()) {
            ImGui.acceptDragDropPayload<String>("ASSET_ITEM")?.let { path ->
                val file = File(path)
                if (file.extension.equals("lsdfxchain", ignoreCase = true)) {
                    FxOps.loadChain(session, file, chain, undoable = true)
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
                    FxOps.saveChain(session, chain, chain.sourceFile!!)
                } else {
                    openSaveAsModal(session, chain)
                }
            }
        }
        llm.slop.liquidlsd.tour.WidgetRegistry.note { "fxchain_save_" + st.bankId }
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
        if (ButtonChrome.button(st.moreBtn, MORE_BTN_W, ctrlH)) {
            ImGui.openPopup(st.menuId)
        }
        llm.slop.liquidlsd.tour.WidgetRegistry.note { "fxchain_more_$bankId" }
        itemTooltip(MORE_TIP)
        drawChainMenu(session, mixer, chain, bankId, chainLabel, st.menuId)
    }

    /**
     * The chain's ⋮ menu, shared by the Perform header and the chain's Edit tab: open it with `ImGui.openPopup([menuId])`.
     * [chainLabel] titles it ("Deck A", "Master"). Everything that changes the chain can be undone with Ctrl+Z.
     */
    fun drawChainMenu(session: SessionContext, mixer: Mixer, chain: FxChain, bankId: String, chainLabel: String, menuId: String) {
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
                if (ImGui.beginMenu("Focus Slot...")) {
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
            // Chain view: this chain's slots as rows of their own, under the deck's source row (Master: Transitions).
            val chainTag = (FxMacroSync.chainBankIdOf(bankId) ?: bankId).let { id -> PerfRows.DECK_TAGS.firstOrNull { PerfRows.chainBankFor(it) == id } ?: PerfRows.PAIR_MASTER }
            val inChainView = session.parametersState.chainView != null && session.parametersState.focusedPair == chainTag
            if (ImGui.menuItem(if (inChainView) "Leave Chain View" else "Chain View")) session.parametersState.toggleChainView(chainTag)
            itemTooltip(CHAIN_VIEW_DESC)
            ImGui.separator()

            if (ImGui.menuItem("Save As...")) {
                openSaveAsModal(session, chain)
            }
            val canRevert = chain.isDirty() && chain.baselineDto != null
            if (ImGui.menuItem("Revert to Saved", "", false, canRevert)) {
                FxOps.revertChain(chain, undoable = true)
            }
            if (ImGui.menuItem("Clear All Slots")) {
                FxOps.clearChain(chain, undoable = true)
            }
            itemTooltip("Empties every slot and starts a new Untitled chain.")
            ImGui.separator()

            if (ImGui.menuItem("Copy Chain")) {
                ClipboardManager.copyFxChain(chain.toFxChainDto())
            }
            val canPaste = ClipboardManager.fxChainClipboard != null
            if (ImGui.menuItem("Paste Chain", "", false, canPaste)) {
                ClipboardManager.fxChainClipboard?.let { FxOps.applyChain(chain, it, undoable = true) }
            }
            popOpenDropdownFont()
            ImGui.endPopup()
        }
        popOpenDropdownPadding()
    }

    /**
     * Opens Save As for [chain], starting in the folder its file is in (for an unsaved chain, the folder selected under Saved chains,
     * else the FX chains folder); the dialog's Folder dropdown can change it.
     */
    fun openSaveAsModal(session: SessionContext, chain: FxChain) {
        SavePresetModal.request(
            title = "Save FX Chain As",
            confirmLabel = "Save",
            defaultName = chain.name.ifBlank { "fx_chain" },
            defaultTags = chain.tags,
            targetDir = LibraryFolders.saveDirFor(BrowseKind.FX, BrowseSection.CHAIN, chain.sourceFile),
            extension = "lsdfxchain",
            folderRoot = FileSystemManager.getFxChainsRoot()
        ) { name, tags ->
            // Captured at confirm time (the chain may change while the dialog is open).
            FxOps.saveChain(session, chain, File(SavePresetModal.directory, "$name.lsdfxchain"), name, tags)
        }
    }

    /**
     * Opens Save As for slot [slotIndex] of [chain] as a single FX (`.lsdfx`), starting in the folder of the saved FX the slot was loaded
     * from, else the folder selected under Saved single FX, else the FX folder. Nothing happens for an empty slot.
     */
    fun openSaveSlotAsModal(session: SessionContext, chain: FxChain, slotIndex: Int) {
        val fx = chain.slots.getOrNull(slotIndex) ?: return
        val slotDto = chain.toFxSlotDto(slotIndex) ?: return
        SavePresetModal.request(
            title = "Save FX Slot Preset As",
            confirmLabel = "Save",
            defaultName = fx.displayName.lowercase().replace(" ", "_").ifBlank { "fx_preset" },
            targetDir = LibraryFolders.saveDirFor(BrowseKind.FX, BrowseSection.SINGLE, FxOps.slotSourceFile(chain, slotIndex)),
            extension = "lsdfx",
            folderRoot = FileSystemManager.getFxPresetsRoot()
        ) { name, tags ->
            session.presetRepository.saveFxPresetAsync(File(SavePresetModal.directory, "$name.lsdfx"), name, slotDto, tags)
        }
    }

    /**
     * Draws the chain's on/off switch for [chain]: a static "FX ON" label, lit while the chain processes and dark while it
     * is bypassed -- the same convention as each slot's power button.
     */
    fun drawFxOnButton(session: SessionContext, chain: FxChain, id: String, ctrlH: Float, width: Float = 64f) {
        val isActive = chain.enabled

        PerformanceColors.pushActiveToggleStyle(isActive)
        session.uiTheme.withFont(UITheme.FontLevel.CAPTION) {
            if (ButtonChrome.button("FX ON##fx_on_$id", width, ctrlH)) {
                chain.enabled = !chain.enabled
            }
            llm.slop.liquidlsd.tour.WidgetRegistry.note { "fxchain_on_$id" }
        }
        PerformanceColors.popActiveToggleStyle()
        itemTooltip(if (isActive) "FX chain is on. Click to bypass it." else "FX chain is bypassed. Click to turn it on.")
    }
}
