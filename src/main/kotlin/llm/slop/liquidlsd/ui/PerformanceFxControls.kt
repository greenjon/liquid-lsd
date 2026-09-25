package llm.slop.liquidlsd.ui

import imgui.ImGui
import imgui.flag.ImGuiCol
import llm.slop.liquidlsd.SessionContext
import llm.slop.liquidlsd.macro.MacroEngine
import llm.slop.liquidlsd.rendering.Mixer

internal class PerformanceFxControls(private val ctx: PerformanceUiContext) {

    fun drawAllFxRowLeftControls(
        session: SessionContext,
        mixer: Mixer,
        bankId: String,
        startX: Float,
        startY: Float,
        ctrlH: Float
    ) {
        val gap = 4f
        val chain = ctx.resolveFxChain(mixer, bankId)
        val chainLabel = llm.slop.liquidlsd.macro.FxMacroSync.labelFor(bankId) ?: "Deck A"

        ImGui.setCursorScreenPos(startX, startY)
        ImGui.beginGroup()

        // 1. Badge with target name
        val badgeW = 76f
        val accent = ctx.targetAccentFor(when (bankId) {
            MacroEngine.DECK_A_FX -> "A"
            MacroEngine.DECK_B_FX -> "B"
            MacroEngine.DECK_BG_FX -> "BG"
            MacroEngine.DECK_PV_FX -> "PV"
            else -> "MST"
        })
        val bgCol = ImGui.colorConvertFloat4ToU32(accent[0], accent[1], accent[2], 0.20f)
        val borderCol = ImGui.colorConvertFloat4ToU32(accent[0], accent[1], accent[2], 0.85f)
        val textCol = ImGui.colorConvertFloat4ToU32(1f, 1f, 1f, 0.95f)

        val curX = ImGui.getCursorScreenPosX()
        val curY = ImGui.getCursorScreenPosY()
        val dl = ImGui.getWindowDrawList()
        dl.addRectFilled(curX, curY, curX + badgeW, curY + ctrlH, bgCol, 4f)
        dl.addRect(curX, curY, curX + badgeW, curY + ctrlH, borderCol, 4f, 0, 1.5f)

        session.uiTheme.withFont(UITheme.FontLevel.CAPTION) {
            val textSz = ImGui.calcTextSize(chainLabel)
            val tx = curX + (badgeW - textSz.x) * 0.5f
            val ty = curY + (ctrlH - textSz.y) * 0.5f
            dl.addText(tx.coerceAtLeast(curX + 2f), ty, textCol, chainLabel)
        }
        ImGui.invisibleButton("##perf_allfx_badge_$bankId", badgeW, ctrlH)
        itemTooltip("Target: $chainLabel FX Chain")

        ImGui.sameLine(0f, gap)

        // 2. Chain header controls: [◀] Name • [▶] [Save] [⋮]
        FxChainHeader.drawControls(session, mixer, chain, bankId, chainLabel, ctrlH)

        ImGui.endGroup()
    }

    fun drawFxSendsLeftControls(
        session: SessionContext,
        startX: Float,
        startY: Float,
        ctrlH: Float
    ) {
        val badgeW = 76f
        val accent = PerformanceColors.COLOR_FX
        val bgCol = ImGui.colorConvertFloat4ToU32(accent[0], accent[1], accent[2], 0.20f)
        val borderCol = ImGui.colorConvertFloat4ToU32(accent[0], accent[1], accent[2], 0.85f)
        val textCol = ImGui.colorConvertFloat4ToU32(1f, 1f, 1f, 0.95f)

        ImGui.setCursorScreenPos(startX, startY)
        ImGui.beginGroup()

        val dl = ImGui.getWindowDrawList()
        dl.addRectFilled(startX, startY, startX + badgeW, startY + ctrlH, bgCol, 4f)
        dl.addRect(startX, startY, startX + badgeW, startY + ctrlH, borderCol, 4f, 0, 1.5f)

        session.uiTheme.withFont(UITheme.FontLevel.CAPTION) {
            val label = "FX SENDS"
            val textSz = ImGui.calcTextSize(label)
            val tx = startX + (badgeW - textSz.x) * 0.5f
            val ty = startY + (ctrlH - textSz.y) * 0.5f
            dl.addText(tx.coerceAtLeast(startX + 2f), ty, textCol, label)
        }
        ImGui.invisibleButton("##perf_fxsends_badge", badgeW, ctrlH)
        itemTooltip("Master FX Wet/Dry Send Levels")

        ImGui.endGroup()
    }

    fun drawFxSendsRightControls(
        session: SessionContext,
        mixer: Mixer,
        startX: Float,
        startY: Float,
        ctrlH: Float
    ) {
        val resyncW = 58f

        ImGui.setCursorScreenPos(startX, startY)
        ImGui.beginGroup()

        if (ImGui.button("Resync##perf_fxsends_resync", resyncW, ctrlH)) {
            val bank = MacroEngine.getBank(MacroEngine.FX_SENDS)
            bank?.knobs?.forEach { knob ->
                knob.value = 1.0f
            }
        }
        itemTooltip("Reset all FX Wet/Dry Send knobs to 100%.")

        ImGui.endGroup()
    }

    fun drawFxRowLeftControls(
        session: SessionContext,
        mixer: Mixer,
        parametersState: ParametersState,
        startX: Float,
        startY: Float,
        ctrlH: Float
    ) {
        val gap = 3f

        ImGui.setCursorScreenPos(startX, startY)
        ImGui.beginGroup()

        // Target switcher [ A ] [ B ] [ BG ] [ PV ] [ MST ]
        val targets = listOf("A", "B", "BG", "PV", "MST")
        val targetBtnW = 28f
        for ((i, target) in targets.withIndex()) {
            if (i > 0) ImGui.sameLine(0f, gap)
            val isActive = target == ctx.focusedFxTarget
            val accent = ctx.targetAccentFor(target)
            val activeCol = ImGui.colorConvertFloat4ToU32(accent[0], accent[1], accent[2], 0.90f)
            val inactiveCol = ImGui.colorConvertFloat4ToU32(0.16f, 0.18f, 0.22f, 1f)
            ImGui.pushStyleColor(ImGuiCol.Button, if (isActive) activeCol else inactiveCol)
            if (ImGui.button("$target##perf_fx_target_$target", targetBtnW, ctrlH)) {
                ctx.focusedFxTarget = target
                llm.slop.liquidlsd.macro.FxMacroSync.syncFor(ctx.targetBankIdFor(target), mixer)
            }
            ImGui.popStyleColor()
        }
        itemTooltip("Focus Row 3 FX knobs on Deck A, B, BG, PV, or Master FX.")

        ImGui.sameLine(0f, 6f)

        // Chain header controls: [◀] Name • [▶] [Save] [⋮]
        val currentBankId = ctx.targetBankIdFor(ctx.focusedFxTarget)
        val currentChain = ctx.resolveFxChain(mixer, currentBankId)
        val currentLabel = llm.slop.liquidlsd.macro.FxMacroSync.labelFor(currentBankId) ?: "Deck A"
        FxChainHeader.drawControls(session, mixer, currentChain, currentBankId, currentLabel, ctrlH)

        ImGui.endGroup()
    }

    fun drawFxRowRightControls(
        session: SessionContext,
        mixer: Mixer,
        startX: Float,
        startY: Float,
        ctrlH: Float,
        targetBankId: String = ctx.targetBankIdFor(ctx.focusedFxTarget)
    ) {
        val chain = ctx.resolveFxChain(mixer, targetBankId)
        ImGui.setCursorScreenPos(startX, startY)
        ImGui.beginGroup()
        FxChainHeader.drawBypassButton(chain, targetBankId, ctrlH)
        ImGui.endGroup()
    }
}
