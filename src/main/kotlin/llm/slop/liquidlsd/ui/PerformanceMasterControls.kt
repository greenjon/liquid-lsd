package llm.slop.liquidlsd.ui

import imgui.ImGui
import imgui.flag.ImGuiCol
import imgui.flag.ImGuiMouseCursor
import llm.slop.liquidlsd.SessionContext
import llm.slop.liquidlsd.macro.MacroEngine
import llm.slop.liquidlsd.rendering.Mixer

/**
 * Master row controls, laid out like a deck row (the MASTER title badge is drawn by
 * [PerformanceMatrixPanel]):
 * - Left of the knobs, two stacked lines: [MIX] knob-assign pill + Mix levels badge (with click
 *   to inspect in Deep Edit, right-click to reset levels) over the [FX] knob-assign pill + Master FX
 *   chain header.
 * - Right of the knobs: Master FX bypass.
 */
internal object PerformanceMasterControls {
    private val masterFxActions = MasterFxActions()

    /**
     * [MIX] / [FX] knob-assign pills (see [PerformanceUiContext.isMasterRowFx]) stacked like a
     * deck row's [SRC] / [FX], with the Master FX chain header beside [FX].
     */
    fun drawModeControls(
        session: SessionContext,
        mixer: Mixer,
        parametersState: ParametersState,
        ctx: PerformanceUiContext,
        startX: Float,
        row1Y: Float,
        row2Y: Float,
        ctrlH: Float,
        rowW: Float,
        pinned: String? = null
    ) {
        val gap = 4f
        val modeBtnW = 28f
        // A pinned row shows one half only and neither reads nor writes the shared Master mode.
        val isFx = if (pinned != null) pinned == "FX" else ctx.isMasterRowFx(parametersState)
        val isMix = !isFx
        val showMix = pinned != "FX"
        val showFx = pinned != "MIX"
        val dl = ImGui.getWindowDrawList()

        // Line 1: Shared [MIX] / [FX] toggle hitbox covering Row 1, gap, and Row 2
        ImGui.setCursorScreenPos(startX, row1Y)
        val totalModeH = (row2Y + ctrlH) - row1Y
        ImGui.setNextItemAllowOverlap()
        val toggleClicked = if (pinned == null) ImGui.invisibleButton("##perf_mode_toggle_mst", modeBtnW, totalModeH)
                            else { ImGui.dummy(modeBtnW, totalModeH); false }
        val isModeHovered = pinned == null && ImGui.isItemHovered()
        if (toggleClicked) {
            if (isMix) {
                llm.slop.liquidlsd.macro.MacroLearnState.onNavigateSection("Mixer", "FX")
                parametersState.activeMixerSubTab = "FX"
                llm.slop.liquidlsd.macro.FxMacroSync.syncFor(MacroEngine.MASTER_FX, mixer)
            } else {
                llm.slop.liquidlsd.macro.MacroLearnState.onNavigateSection("Mixer", "CTRL")
                parametersState.activeMixerSubTab = "CTRL"
            }
        }
        if (isModeHovered) {
            ImGui.setMouseCursor(ImGuiMouseCursor.Hand)
        }
        if (pinned == null) itemTooltip("Toggle Master knobs between Mix controls (MIX) and Master FX (FX).")

        val mouseY = ImGui.getMousePosY()
        val midY = row1Y + ctrlH + (row2Y - (row1Y + ctrlH)) * 0.5f
        val isMixHovered = isModeHovered && (mouseY <= midY)
        val isFxHovered = isModeHovered && (mouseY > midY)

        if (showMix) PerformanceColors.drawTogglePill(dl, startX, row1Y, modeBtnW, ctrlH, "MIX", isMix, isMixHovered, session)
        if (showFx) PerformanceColors.drawTogglePill(dl, startX, row2Y, modeBtnW, ctrlH, "FX", isFx, isFxHovered, session)

        if (showMix) drawMixBadgeAndReset(session, mixer, parametersState, ctx, startX + modeBtnW + gap, row1Y, ctrlH, rowW - modeBtnW - gap)

        // Line 2: [FX] pill spacer + Master FX chain header
        if (showFx) {
            ImGui.setCursorScreenPos(startX, row2Y)
            ImGui.beginGroup()
            ImGui.dummy(modeBtnW, ctrlH)
            ImGui.sameLine(0f, gap)
            FxChainHeader.drawControls(
                session, mixer, mixer.masterFxChain, MacroEngine.MASTER_FX, "Master FX", ctrlH,
                maxW = rowW - modeBtnW - gap, deck = null,
                actions = masterFxActions.also { it.set(parametersState, ctx, mixer, isFx, pinned != null) }
            )
            ImGui.endGroup()
        }
    }

    /** Master FX chain bypass, placed to the right of the knobs like a deck row's. */
    fun drawBypassControls(session: SessionContext, mixer: Mixer, startX: Float, startY: Float, ctrlH: Float, width: Float) {
        ImGui.setCursorScreenPos(startX, startY)
        ImGui.beginGroup()
        FxChainHeader.drawBypassButton(session, mixer.masterFxChain, "MST", ctrlH, width - 4f)
        ImGui.endGroup()
    }

    private fun drawMixBadgeAndReset(
        session: SessionContext,
        mixer: Mixer,
        parametersState: ParametersState,
        ctx: PerformanceUiContext,
        startX: Float,
        headerY: Float,
        headerH: Float,
        width: Float
    ) {
        val gap = 4f
        val resetBtnW = 44f
        val badgeW = (width - resetBtnW - gap).coerceAtLeast(60f)
        val dl = ImGui.getWindowDrawList()

        val curX = startX
        val curY = headerY
        val genBgCol = TangoPalette.BADGE_BG.u32()
        val genBorderCol = TangoPalette.BADGE_BORDER.u32()
        val genTextCol = TangoPalette.BADGE_TEXT.u32()
        val badgeText = "Deck Levels & Master"

        dl.addRectFilled(curX, curY, curX + badgeW, curY + headerH, genBgCol, 4f)
        dl.addRect(curX, curY, curX + badgeW, curY + headerH, genBorderCol, 4f, 0, 1f)

        session.uiTheme.withFont(UITheme.FontLevel.CAPTION) {
            val textSz = ImGui.calcTextSize(badgeText)
            val tx = curX + (badgeW - textSz.x) * 0.5f
            val ty = curY + (headerH - textSz.y) * 0.5f
            dl.addText(tx.coerceAtLeast(curX + 4f), ty, genTextCol, badgeText)
        }

        ImGui.setCursorScreenPos(curX, curY)
        if (ImGui.invisibleButton("##perf_mix_badge", badgeW, headerH)) {
            llm.slop.liquidlsd.macro.MacroLearnState.onNavigateSection("Mixer", "CTRL")
            parametersState.activeMixerSubTab = "CTRL"
            parametersState.setDisclosure("master", ParametersState.DisclosureLevel.DEEP_EDIT)
            ctx.focusDeepEditTab(parametersState, MacroEngine.MASTER)
        }
        itemTooltip("Composite mix levels: Deck A, Deck B, Deck BG levels and Master output level.\nClick to inspect in Edit. Right-click to reset levels.")

        pushOpenDropdownPadding()
        if (ImGui.beginPopupContextItem("perf_mix_badge_ctx")) {
            pushOpenDropdownFont()
            ImGui.textDisabled("Master Mix Levels")
            ImGui.separator()
            if (ImGui.menuItem("Inspect in Edit")) {
                llm.slop.liquidlsd.macro.MacroLearnState.onNavigateSection("Mixer", "CTRL")
                parametersState.activeMixerSubTab = "CTRL"
                parametersState.setDisclosure("master", ParametersState.DisclosureLevel.DEEP_EDIT)
                ctx.focusDeepEditTab(parametersState, MacroEngine.MASTER)
            }
            ImGui.separator()
            if (ImGui.menuItem("Reset Deck Levels to 100%")) {
                mixer.levelA.baseValue = 1.0f
                mixer.levelB.baseValue = 1.0f
                mixer.levelBG.baseValue = 1.0f
            }
            if (ImGui.menuItem("Reset Master Level to 100%")) {
                mixer.masterLevel.baseValue = 1.0f
            }
            if (ImGui.menuItem("Reset All (Deck & Master Levels) to 100%")) {
                mixer.levelA.baseValue = 1.0f
                mixer.levelB.baseValue = 1.0f
                mixer.levelBG.baseValue = 1.0f
                mixer.masterLevel.baseValue = 1.0f
            }
            popOpenDropdownFont()
            ImGui.endPopup()
        }
        popOpenDropdownPadding()

        ImGui.sameLine(0f, gap)

        // Reset button [ 100% ]
        val resetX = curX + badgeW + gap
        ImGui.setCursorScreenPos(resetX, curY)
        val resetBtnBg = TangoPalette.BUTTON_BG.u32()
        val resetBtnHov = TangoPalette.BUTTON_HOVER.u32()
        ImGui.pushStyleColor(ImGuiCol.Button, resetBtnBg)
        ImGui.pushStyleColor(ImGuiCol.ButtonHovered, resetBtnHov)
        session.uiTheme.withFont(UITheme.FontLevel.CAPTION) {
            if (ImGui.button("100%##perf_mix_reset_btn", resetBtnW, headerH)) {
                mixer.levelA.baseValue = 1.0f
                mixer.levelB.baseValue = 1.0f
                mixer.levelBG.baseValue = 1.0f
                mixer.masterLevel.baseValue = 1.0f
            }
        }
        ImGui.popStyleColor(2)
        itemTooltip("Reset all deck alphas and master level to 100% (1.0).\nRight-click for partial reset options.")

        pushOpenDropdownPadding()
        if (ImGui.beginPopupContextItem("perf_mix_reset_btn_ctx")) {
            pushOpenDropdownFont()
            ImGui.textDisabled("Reset Levels")
            ImGui.separator()
            if (ImGui.menuItem("Reset All to 100%")) {
                mixer.levelA.baseValue = 1.0f
                mixer.levelB.baseValue = 1.0f
                mixer.levelBG.baseValue = 1.0f
                mixer.masterLevel.baseValue = 1.0f
            }
            if (ImGui.menuItem("Reset Deck Levels Only")) {
                mixer.levelA.baseValue = 1.0f
                mixer.levelB.baseValue = 1.0f
                mixer.levelBG.baseValue = 1.0f
            }
            if (ImGui.menuItem("Reset Master Level Only")) {
                mixer.masterLevel.baseValue = 1.0f
            }
            popOpenDropdownFont()
            ImGui.endPopup()
        }
        popOpenDropdownPadding()
    }
}
