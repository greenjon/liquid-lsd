package llm.slop.liquidlsd.rack.ui

import imgui.ImGui
import imgui.flag.ImGuiCol
import imgui.flag.ImGuiStyleVar
import llm.slop.liquidlsd.parameters.ModulatableParameter
import llm.slop.liquidlsd.rack.DeckRackUnit
import llm.slop.liquidlsd.rack.GenericRackUnit
import llm.slop.liquidlsd.rack.MixerTransitionUnit
import llm.slop.liquidlsd.rack.QueueStagingRackUnit
import llm.slop.liquidlsd.rack.RackUnit
import llm.slop.liquidlsd.rendering.Renderer
import llm.slop.liquidlsd.ui.itemTooltip
import java.io.File

/**
 * Grid-based faceplate layout system snapping parameter controls into an 8-column modular grid.
 */
object RackFaceplateGrid {

    const val GRID_COLUMNS = 8
    const val COLUMN_GAP = 6.0f

    fun drawFaceplate(
        session: llm.slop.liquidlsd.SessionContext,
        unit: RackUnit,
        faceplateWidth: Float,
        faceplateHeight: Float,
        renderer: Renderer? = null
    ) {
        if (unit.isCollapsed) return

        val paddingX = 12.0f
        val paddingY = 6.0f
        val usableW = faceplateWidth - (paddingX * 2f)
        val colW = (usableW - (COLUMN_GAP * (GRID_COLUMNS - 1))) / GRID_COLUMNS

        ImGui.setCursorPosX(paddingX)
        ImGui.setCursorPosY(ImGui.getCursorPosY() + paddingY)

        if (unit.isMacroCurationOpen) {
            RackUnitMacroCuration.draw(session, unit, usableW)
            return
        }

        // Draw curated active Macro Controls for this unit if any exist
        drawCuratedMacrosRow(session, unit, usableW)

        when (unit) {
            is DeckRackUnit -> drawMergedDeckFaceplate(session, unit, usableW, colW, faceplateHeight, renderer)
            is QueueStagingRackUnit -> drawQueueStagingFaceplate(unit, colW)
            is MixerTransitionUnit -> drawTransitionFaceplate(session, unit, usableW, colW, faceplateHeight, renderer)
            else -> drawGenericFaceplate(session, unit, usableW, colW, faceplateHeight, renderer)
        }
    }

    private fun drawCuratedMacrosRow(session: llm.slop.liquidlsd.SessionContext, unit: RackUnit, usableW: Float) {
        val activeKnobs = unit.macroBank.knobs.filter { it.bindings.isNotEmpty() || it.label.isNotEmpty() }
        val activeSwitches = unit.macroBank.switches.filter { it.bindings.isNotEmpty() || it.label.isNotEmpty() }

        if (activeKnobs.isEmpty() && activeSwitches.isEmpty()) return

        ImGui.beginGroup()
        ImGui.pushStyleVar(ImGuiStyleVar.FramePadding, 2.0f, 2.0f)
        ImGui.pushStyleVar(ImGuiStyleVar.ItemSpacing, 8.0f, 2.0f)

        // Render knobs
        for (i in activeKnobs.indices) {
            val knob = activeKnobs[i]
            if (i > 0) ImGui.sameLine(0f, 8f)

            val isSelected = llm.slop.liquidlsd.macro.MacroLearnState.selectedControlId == knob.id
            val isLearning = llm.slop.liquidlsd.macro.MacroLearnState.isControlLearning(knob.id)
            llm.slop.liquidlsd.ui.MacroKnobWidget.draw(
                session = session,
                id = "unit_${unit.id}_knob_$i",
                label = knob.label.ifEmpty { "K${i + 1}" },
                value = knob.value,
                diameter = 40f,
                isSelected = isSelected,
                isLearning = isLearning,
                bindings = knob.bindings,
                onSelect = { llm.slop.liquidlsd.macro.MacroLearnState.selectedControlId = knob.id },
                onToggleLearn = {
                    if (isLearning) {
                        llm.slop.liquidlsd.macro.MacroLearnState.cancelLearn()
                    } else {
                        llm.slop.liquidlsd.macro.MacroLearnState.startLearn(knob.id)
                    }
                },
                onChanged = { knob.value = it }
            )
        }

        // Render switches
        if (activeSwitches.isNotEmpty()) {
            if (activeKnobs.isNotEmpty()) ImGui.sameLine(0f, 16f)
            for (i in activeSwitches.indices) {
                val sw = activeSwitches[i]
                if (i > 0) ImGui.sameLine(0f, 6f)

                val swActive = sw.value >= 0.5f
                if (swActive) {
                    ImGui.pushStyleColor(ImGuiCol.Button, 0.95f, 0.75f, 0.15f, 1.0f)
                    ImGui.pushStyleColor(ImGuiCol.Text, 0.1f, 0.1f, 0.1f, 1.0f)
                } else {
                    ImGui.pushStyleColor(ImGuiCol.Button, 0.20f, 0.22f, 0.25f, 1.0f)
                    ImGui.pushStyleColor(ImGuiCol.Text, 0.65f, 0.70f, 0.75f, 1.0f)
                }
                val label = sw.label.ifEmpty { "SW ${i + 1}" }
                if (ImGui.button("$label##sw_${unit.id}_$i", 52f, 22f)) {
                    sw.onPress()
                }
                ImGui.popStyleColor(2)
            }
        }

        ImGui.popStyleVar(2)
        ImGui.endGroup()

        // Subtle divider before standard parameters
        val dl = ImGui.getWindowDrawList()
        val sepY = ImGui.getCursorScreenPosY() + 2f
        val startX = ImGui.getCursorScreenPosX()
        val sepCol = ImGui.colorConvertFloat4ToU32(0.20f, 0.22f, 0.26f, 0.6f)
        dl.addLine(startX, sepY, startX + usableW, sepY, sepCol, 1.0f)
        ImGui.setCursorPosY(ImGui.getCursorPosY() + 6f)
    }

    private fun drawMergedDeckFaceplate(
        session: llm.slop.liquidlsd.SessionContext,
        unit: DeckRackUnit,
        usableW: Float,
        colW: Float,
        faceplateHeight: Float,
        renderer: Renderer?
    ) {
        val deck = unit.deck
        val namedParams = unit.getNamedParameters().entries.toList()

        ImGui.pushStyleVar(ImGuiStyleVar.FramePadding, 4.0f, 3.0f)
        ImGui.pushStyleVar(ImGuiStyleVar.ItemSpacing, COLUMN_GAP, 6.0f)

        // Columns 1 & 2: Confidence Micro-Monitor + Source/FX-chain info
        val col12W = (colW * 2f) + COLUMN_GAP
        val monitorH = (col12W * session.uiTheme.renderAspectRatio).coerceIn(40f, 86f)

        ImGui.beginGroup()
        RackMicroMonitor.draw(session, unit, col12W, monitorH, renderer)
        ImGui.setCursorPosY(ImGui.getCursorPosY() + 2f)

        ImGui.pushStyleColor(ImGuiCol.Text, 0.65f, 0.70f, 0.75f, 1.0f)
        ImGui.textUnformatted("SRC: ${deck.source.displayName.take(18)}")
        ImGui.popStyleColor()

        val activeFxCount = deck.fxSlots.count { it != null }
        ImGui.pushStyleColor(ImGuiCol.Text, 0.70f, 0.85f, 0.55f, 1.0f)
        ImGui.textUnformatted("FX: $activeFxCount/${deck.fxSlots.size} active")
        ImGui.popStyleColor()
        ImGui.endGroup()
        ImGui.sameLine()

        // Columns 3 to 8: Curated parameters (generator + flattened FX slots) mapped into 2 rows of 3
        val remainingW = usableW - col12W - COLUMN_GAP
        val paramColW = (remainingW - (COLUMN_GAP * 2f)) / 3f

        val activeParams = namedParams.take(6)
        ImGui.beginGroup()
        for (i in activeParams.indices) {
            if (i > 0 && i % 3 != 0) {
                ImGui.sameLine(0f, COLUMN_GAP)
            }
            val (paramName, param) = activeParams[i]
            drawParamSlider(param, paramColW, "deck_${unit.id}_$i", tooltip = paramName)
        }
        ImGui.endGroup()

        ImGui.popStyleVar(2)
    }

    private fun drawTransitionFaceplate(
        session: llm.slop.liquidlsd.SessionContext,
        unit: MixerTransitionUnit,
        usableW: Float,
        colW: Float,
        faceplateHeight: Float,
        renderer: Renderer?
    ) {
        val mixer = unit.mixer
        ImGui.pushStyleVar(ImGuiStyleVar.FramePadding, 4.0f, 3.0f)
        ImGui.pushStyleVar(ImGuiStyleVar.ItemSpacing, COLUMN_GAP, 6.0f)

        // Columns 1 & 2: Master Output Confidence Micro-Monitor
        val col12W = (colW * 2f) + COLUMN_GAP
        val monitorH = (col12W * session.uiTheme.renderAspectRatio).coerceIn(40f, 86f)

        ImGui.beginGroup()
        RackMicroMonitor.draw(session, unit, col12W, monitorH, renderer)
        ImGui.pushStyleColor(ImGuiCol.Text, 0.50f, 0.55f, 0.60f, 1.0f)
        ImGui.textUnformatted("MASTER OUT")
        ImGui.popStyleColor()
        ImGui.endGroup()
        ImGui.sameLine()

        // Columns 3 to 5: Crossfader slider (Deck A <-> Deck B)
        val crossfaderW = (colW * 3f) + (COLUMN_GAP * 2f)
        ImGui.beginGroup()
        drawParamSlider(
            mixer.crossfade, crossfaderW, "crossfade_${unit.id}",
            customLabel = "CROSSFADER [A <-> B]",
            tooltip = "Blend between Deck A (-1.0) and Deck B (+1.0)."
        )
        ImGui.endGroup()
        ImGui.sameLine()

        // Columns 6 to 8: Mode, Master Alpha, Bloom
        val masterColW = colW
        drawParamSlider(
            mixer.mode, masterColW, "mm_${unit.id}",
            customLabel = "MODE",
            tooltip = "Blend mode: 0=Add, 1=Screen, 2=Mult, 3=Max, 4=Crossfade."
        )
        ImGui.sameLine(0f, COLUMN_GAP)
        drawParamSlider(
            mixer.masterAlpha, masterColW, "ma_${unit.id}",
            customLabel = "ALPHA",
            tooltip = "Master output gain/opacity."
        )
        ImGui.sameLine(0f, COLUMN_GAP)
        drawParamSlider(
            mixer.bloom, masterColW, "mb_${unit.id}",
            customLabel = "BLOOM",
            tooltip = "Post-process bloom/glow intensity."
        )

        ImGui.popStyleVar(2)
    }

    private fun drawGenericFaceplate(
        session: llm.slop.liquidlsd.SessionContext,
        unit: RackUnit,
        usableW: Float,
        colW: Float,
        faceplateHeight: Float,
        renderer: Renderer?
    ) {
        val namedParams = unit.getNamedParameters().entries.toList()
        ImGui.pushStyleVar(ImGuiStyleVar.FramePadding, 4.0f, 3.0f)
        ImGui.pushStyleVar(ImGuiStyleVar.ItemSpacing, COLUMN_GAP, 6.0f)

        // Columns 1 & 2: Confidence Micro-Monitor
        val col12W = (colW * 2f) + COLUMN_GAP
        val monitorH = (col12W * session.uiTheme.renderAspectRatio).coerceIn(40f, 86f)

        ImGui.beginGroup()
        RackMicroMonitor.draw(session, unit, col12W, monitorH, renderer)
        ImGui.endGroup()
        ImGui.sameLine()

        // Columns 3 to 8: Up to 6 parameters
        val remainingW = usableW - col12W - COLUMN_GAP
        val activeParams = namedParams.take(6)
        val paramColW = if (activeParams.isNotEmpty()) {
            (remainingW - (COLUMN_GAP * (activeParams.size - 1))) / activeParams.size
        } else {
            remainingW
        }

        if (activeParams.isEmpty()) {
            ImGui.textDisabled("No exposed faceplate parameters")
        } else {
            ImGui.beginGroup()
            for (i in activeParams.indices) {
                if (i > 0) ImGui.sameLine(0f, COLUMN_GAP)
                val (paramName, param) = activeParams[i]
                drawParamSlider(param, paramColW, "gen_${unit.id}_$i", tooltip = paramName)
            }
            ImGui.endGroup()
        }

        ImGui.popStyleVar(2)
    }

    private fun drawQueueStagingFaceplate(unit: QueueStagingRackUnit, colW: Float) {
        val mixer = unit.mixer
        ImGui.pushStyleVar(ImGuiStyleVar.FramePadding, 3.0f, 2.0f)
        ImGui.pushStyleVar(ImGuiStyleVar.ItemSpacing, 6.0f, 3.0f)

        drawQueueSection(
            idSuffix = "pq_${unit.id}",
            title = "PLAY QUEUE",
            accentR = 0.4f, accentG = 1.0f, accentB = 0.8f,
            isActive = llm.slop.liquidlsd.presets.PlayQueueManager.isAutoVJEnabled,
            colW = colW,
            onPrev = { llm.slop.liquidlsd.presets.PlayQueueManager.triggerPrevious(mixer) },
            onToggle = {
                val next = !llm.slop.liquidlsd.presets.PlayQueueManager.isAutoVJEnabled
                llm.slop.liquidlsd.presets.PlayQueueManager.isAutoVJEnabled = next
                if (next) mixer.muteCrossfadeNonMidiCv()
            },
            onNext = { llm.slop.liquidlsd.presets.PlayQueueManager.triggerNext(mixer) },
            nowLabel = queueNowLabel(llm.slop.liquidlsd.presets.PlayQueueManager.queue, llm.slop.liquidlsd.presets.PlayQueueManager.activeIndex),
            nextLabel = queueNextLabel(
                llm.slop.liquidlsd.presets.PlayQueueManager.queue,
                llm.slop.liquidlsd.presets.PlayQueueManager.activeIndex,
                llm.slop.liquidlsd.presets.PlayQueueManager.isShuffleEnabled
            ),
            prevTooltip = "Trigger previous preset in Play Queue (Mixer/queuePrev).",
            toggleTooltip = "Auto-VJ: Automatically cycle through queue presets at set intervals.",
            nextTooltip = "Trigger next preset in Play Queue (Mixer/queueNext)."
        )

        ImGui.spacing()

        drawQueueSection(
            idSuffix = "bgq_${unit.id}",
            title = "BG QUEUE",
            accentR = 0.9f, accentG = 0.35f, accentB = 0.65f,
            isActive = llm.slop.liquidlsd.presets.BgQueueManager.isAutoBGEnabled,
            colW = colW,
            onPrev = { llm.slop.liquidlsd.presets.BgQueueManager.triggerPrevious(mixer) },
            onToggle = {
                llm.slop.liquidlsd.presets.BgQueueManager.isAutoBGEnabled = !llm.slop.liquidlsd.presets.BgQueueManager.isAutoBGEnabled
            },
            onNext = { llm.slop.liquidlsd.presets.BgQueueManager.triggerNext(mixer) },
            nowLabel = queueNowLabel(llm.slop.liquidlsd.presets.BgQueueManager.queue, llm.slop.liquidlsd.presets.BgQueueManager.activeIndex),
            nextLabel = queueNextLabel(
                llm.slop.liquidlsd.presets.BgQueueManager.queue,
                llm.slop.liquidlsd.presets.BgQueueManager.activeIndex,
                llm.slop.liquidlsd.presets.BgQueueManager.isShuffleEnabled
            ),
            prevTooltip = "Trigger previous preset in Background Queue (Mixer/bgQueuePrev).",
            toggleTooltip = "Auto-BG: Automatically cycle through background presets with smooth dip-to-black transitions.",
            nextTooltip = "Trigger next preset in Background Queue (Mixer/bgQueueNext)."
        )

        ImGui.spacing()

        drawQueueSection(
            idSuffix = "tq_${unit.id}",
            title = "TRANSITION STAGING",
            accentR = 0.55f, accentG = 0.75f, accentB = 1.0f,
            isActive = llm.slop.liquidlsd.presets.TransitionQueueManager.isAutoAdvanceEnabled,
            colW = colW,
            onPrev = { llm.slop.liquidlsd.presets.TransitionQueueManager.advancePrevious(mixer) },
            onToggle = {
                llm.slop.liquidlsd.presets.TransitionQueueManager.isAutoAdvanceEnabled =
                    !llm.slop.liquidlsd.presets.TransitionQueueManager.isAutoAdvanceEnabled
            },
            onNext = { llm.slop.liquidlsd.presets.TransitionQueueManager.advanceNext(mixer) },
            nowLabel = queueNowLabel(llm.slop.liquidlsd.presets.TransitionQueueManager.queue, llm.slop.liquidlsd.presets.TransitionQueueManager.activeIndex),
            nextLabel = queueNextLabel(
                llm.slop.liquidlsd.presets.TransitionQueueManager.queue,
                llm.slop.liquidlsd.presets.TransitionQueueManager.activeIndex,
                llm.slop.liquidlsd.presets.TransitionQueueManager.isShuffleEnabled
            ),
            prevTooltip = "Trigger previous transition in Live Transition Queue.",
            toggleTooltip = "Auto-Advance: Automatically advance to the next transition preset when a crossfade triggers.",
            nextTooltip = "Trigger next transition in Live Transition Queue."
        )

        ImGui.popStyleVar(2)
    }

    private fun drawQueueSection(
        idSuffix: String,
        title: String,
        accentR: Float,
        accentG: Float,
        accentB: Float,
        isActive: Boolean,
        colW: Float,
        onPrev: () -> Unit,
        onToggle: () -> Unit,
        onNext: () -> Unit,
        nowLabel: String,
        nextLabel: String,
        prevTooltip: String,
        toggleTooltip: String,
        nextTooltip: String
    ) {
        ImGui.pushID(idSuffix)

        ImGui.pushStyleColor(ImGuiCol.Text, accentR, accentG, accentB, 1.0f)
        ImGui.textUnformatted(title)
        ImGui.popStyleColor()
        ImGui.sameLine(colW * 4.5f)

        val btnW = colW * 0.9f
        if (ImGui.button("<##prev", btnW, 0f)) onPrev()
        itemTooltip(prevTooltip)
        ImGui.sameLine(0f, 3f)

        if (isActive) {
            ImGui.pushStyleColor(ImGuiCol.Button, 0.1f, 0.4f, 0.3f, 1.0f)
        }
        val toggleLabel = if (isActive) llm.slop.liquidlsd.ui.Icons.PAUSE else llm.slop.liquidlsd.ui.Icons.PLAY
        if (ImGui.button("$toggleLabel##toggle", btnW, 0f)) onToggle()
        if (isActive) {
            ImGui.popStyleColor()
        }
        itemTooltip(toggleTooltip)
        ImGui.sameLine(0f, 3f)

        if (ImGui.button(">##next", btnW, 0f)) onNext()
        itemTooltip(nextTooltip)

        ImGui.pushStyleColor(ImGuiCol.Text, 0.65f, 0.70f, 0.75f, 1.0f)
        ImGui.textUnformatted("${nowLabel.take(16)}  ->  ${nextLabel.take(16)}")
        ImGui.popStyleColor()

        ImGui.popID()
    }

    private fun queueNowLabel(queue: List<File>, activeIndex: Int): String =
        queue.getOrNull(activeIndex)?.nameWithoutExtension ?: "--"

    private fun queueNextLabel(queue: List<File>, activeIndex: Int, isShuffle: Boolean): String {
        if (queue.isEmpty()) return "--"
        if (isShuffle) return "(shuffle)"
        val nextIdx = if (activeIndex + 1 < queue.size) activeIndex + 1 else 0
        return queue.getOrNull(nextIdx)?.nameWithoutExtension ?: "--"
    }

    private fun drawParamSlider(
        param: ModulatableParameter,
        width: Float,
        idSuffix: String,
        customLabel: String? = null,
        tooltip: String? = null
    ) {
        ImGui.pushID(idSuffix)
        ImGui.beginGroup()

        // Parameter label
        val label = customLabel ?: "Param"
        ImGui.pushStyleColor(ImGuiCol.Text, 0.70f, 0.75f, 0.80f, 1.0f)
        ImGui.textUnformatted(label.take(14))
        ImGui.popStyleColor()

        // Slider
        ImGui.setNextItemWidth(width)
        val arr = floatArrayOf(param.baseValue)
        if (ImGui.sliderFloat("##val", arr, param.minClamp, param.maxClamp, "%.2f")) {
            param.baseValue = arr[0]
        }
        if (tooltip != null) itemTooltip(tooltip)

        ImGui.endGroup()
        ImGui.popID()
    }
}
