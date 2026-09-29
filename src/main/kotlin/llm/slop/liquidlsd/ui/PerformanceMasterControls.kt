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
        rowW: Float
    ) {
        val gap = 4f
        val modeBtnW = 28f
        val isFx = ctx.isMasterRowFx(parametersState)
        val isMix = !isFx
        val dl = ImGui.getWindowDrawList()

        // Line 1: Shared [MIX] / [FX] toggle hitbox covering Row 1, gap, and Row 2
        ImGui.setCursorScreenPos(startX, row1Y)
        val totalModeH = (row2Y + ctrlH) - row1Y
        ImGui.setNextItemAllowOverlap()
        val toggleClicked = ImGui.invisibleButton("##perf_mode_toggle_mst", modeBtnW, totalModeH)
        val isModeHovered = ImGui.isItemHovered()
        if (toggleClicked) {
            if (isMix) {
                llm.slop.liquidlsd.macro.MacroLearnState.onNavigateSection("Mixer", "FX")
                ctx.masterRowMode = "FX"
                parametersState.activeMixerSubTab = "FX"
                llm.slop.liquidlsd.macro.FxMacroSync.syncFor(MacroEngine.MASTER_FX, mixer)
            } else {
                llm.slop.liquidlsd.macro.MacroLearnState.onNavigateSection("Mixer", "CTRL")
                ctx.masterRowMode = "MIX"
                if (parametersState.activeMixerSubTab == "FX") parametersState.activeMixerSubTab = "CTRL"
            }
        }
        if (isModeHovered) {
            ImGui.setMouseCursor(ImGuiMouseCursor.Hand)
        }
        itemTooltip("Toggle Master knobs between Mix controls (MIX) and Master FX (FX).")

        val mouseY = ImGui.getMousePosY()
        val midY = row1Y + ctrlH + (row2Y - (row1Y + ctrlH)) * 0.5f
        val isMixHovered = isModeHovered && (mouseY <= midY)
        val isFxHovered = isModeHovered && (mouseY > midY)

        PerformanceColors.drawTogglePill(dl, startX, row1Y, modeBtnW, ctrlH, "MIX", isMix, isMixHovered, session)
        PerformanceColors.drawTogglePill(dl, startX, row2Y, modeBtnW, ctrlH, "FX", isFx, isFxHovered, session)

        drawMixBadgeAndReset(session, mixer, parametersState, ctx, startX + modeBtnW + gap, row1Y, ctrlH, rowW - modeBtnW - gap)

        // Line 2: [FX] pill spacer + Master FX chain header
        ImGui.setCursorScreenPos(startX, row2Y)
        ImGui.beginGroup()
        ImGui.dummy(modeBtnW, ctrlH)
        ImGui.sameLine(0f, gap)
        FxChainHeader.drawControls(session, mixer, mixer.masterFxChain, MacroEngine.MASTER_FX, "Master FX", ctrlH, maxW = rowW - modeBtnW - gap) {
            parametersState.openFxChainBrowse(MacroEngine.MASTER, deckLabel = null, slotIndex = null)
        }
        ImGui.endGroup()
    }

    /** Master FX chain bypass, placed to the right of the knobs like a deck row's. */
    fun drawBypassControls(mixer: Mixer, startX: Float, startY: Float, ctrlH: Float, width: Float) {
        ImGui.setCursorScreenPos(startX, startY)
        ImGui.beginGroup()
        FxChainHeader.drawBypassButton(mixer.masterFxChain, "MST", ctrlH, width - 4f)
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
        val isLight = session.uiTheme.theme == UITheme.Theme.ORANGE_SUNSHINE
        val genBgCol = if (isLight) ImGui.getColorU32(ImGuiCol.FrameBg) else ImGui.colorConvertFloat4ToU32(0.14f, 0.16f, 0.20f, 0.85f)
        val genBorderCol = if (isLight) ImGui.getColorU32(ImGuiCol.Border) else ImGui.colorConvertFloat4ToU32(0.35f, 0.40f, 0.50f, 0.70f)
        val genTextCol = if (isLight) ImGui.getColorU32(ImGuiCol.Text) else ImGui.colorConvertFloat4ToU32(0.80f, 0.85f, 0.95f, 1f)
        val badgeText = "Deck Alphas & Master"

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
            ctx.masterRowMode = "MIX"
            parametersState.activeMixerSubTab = "CTRL"
            parametersState.setDisclosure("master", ParametersState.DisclosureLevel.DEEP_EDIT)
            ctx.navigateMacroPanelTo(parametersState, MacroEngine.MASTER)
        }
        itemTooltip("Composite mix levels: Deck A, Deck B, Deck BG alphas and Master output level.\nClick to inspect in Deep Edit. Right-click to reset levels.")

        pushOpenDropdownPadding()
        if (ImGui.beginPopupContextItem("perf_mix_badge_ctx")) {
            pushOpenDropdownFont()
            ImGui.textDisabled("Master Mix Levels")
            ImGui.separator()
            if (ImGui.menuItem("Inspect in Deep Edit")) {
                llm.slop.liquidlsd.macro.MacroLearnState.onNavigateSection("Mixer", "CTRL")
                ctx.masterRowMode = "MIX"
                parametersState.activeMixerSubTab = "CTRL"
                parametersState.setDisclosure("master", ParametersState.DisclosureLevel.DEEP_EDIT)
                ctx.navigateMacroPanelTo(parametersState, MacroEngine.MASTER)
            }
            ImGui.separator()
            if (ImGui.menuItem("Reset Deck Alphas to 100%")) {
                mixer.levelA.baseValue = 1.0f
                mixer.levelB.baseValue = 1.0f
                mixer.levelBG.baseValue = 1.0f
            }
            if (ImGui.menuItem("Reset Master Level to 100%")) {
                mixer.masterLevel.baseValue = 1.0f
            }
            if (ImGui.menuItem("Reset All (Alphas & Master) to 100%")) {
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
        val resetBtnBg = if (isLight) ImGui.getColorU32(ImGuiCol.Button) else ImGui.colorConvertFloat4ToU32(0.16f, 0.18f, 0.22f, 1f)
        val resetBtnHov = if (isLight) ImGui.getColorU32(ImGuiCol.ButtonHovered) else ImGui.colorConvertFloat4ToU32(0.24f, 0.28f, 0.35f, 1f)
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
            if (ImGui.menuItem("Reset Deck Alphas Only")) {
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
