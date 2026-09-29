package llm.slop.liquidlsd.ui

import imgui.ImGui
import imgui.flag.ImGuiCol
import imgui.flag.ImGuiMouseCursor
import llm.slop.liquidlsd.SessionContext
import llm.slop.liquidlsd.macro.MacroEngine
import llm.slop.liquidlsd.osc.OscLearnState
import llm.slop.liquidlsd.osc.OscMappingManager
import llm.slop.liquidlsd.presets.GeneratorDefaults
import llm.slop.liquidlsd.rendering.Deck
import llm.slop.liquidlsd.rendering.ExternalVideoSource
import llm.slop.liquidlsd.rendering.Mixer

/**
 * Widths of the Deck row's left control lines -- the single source for both the drawing in
 * [PerformanceDeckControls] and the space [PerformanceMatrixPanel] reserves for it, so the two
 * can't drift apart and push controls into the knob cluster.
 */
internal object DeckRowMetrics {
    const val GAP = 4f
    const val MODE_PILL_W = 28f
    /** Base width of the merged generator/preset badge, before adding the space the old
     *  separate preset combo used to occupy (see [genBadgeW]). */
    const val GEN_BADGE_BASE_W = 74f
    const val QUEUE_IDX_W = 38f
    const val QUEUE_INNER_GAP = 2f
    const val PV_BADGE_W = 60f
    /** Kebab menu button -- narrower than a square icon button, matching [FxChainHeader]'s ⋮. */
    const val KEBAB_W = 20f

    fun iconBtnW(ctrlH: Float): Float = ctrlH
    fun navBtnW(ctrlH: Float): Float = (ctrlH * 0.85f).coerceAtLeast(20f)

    /** `< n/m >` queue navigation (Decks A, B, BG). */
    fun queueNavW(ctrlH: Float): Float = navBtnW(ctrlH) * 2f + QUEUE_IDX_W + QUEUE_INNER_GAP * 2f

    /**
     * Width of the single generator/preset badge -- absorbs the width once used by the
     * now-removed separate preset combo, so [row1Width] doesn't drift from what's drawn.
     */
    fun genBadgeW(comboW: Float): Float = GEN_BADGE_BASE_W + GAP + comboW

    /**
     * Headroom reserved past the last drawn Row 1 control, past the knob cluster's [SIDE_GUTTER]
     * -- room to add another icon or two later without another geometry pass across every row.
     */
    fun growthSlack(ctrlH: Float): Float = 2f * iconBtnW(ctrlH)

    /**
     * Line 1 (SRC) width: pill, generator/preset badge, eject, [dice], queue nav or PV's PREVIEW
     * badge (whichever is wider), then Save + kebab -- mirroring the Save/⋮ pair Row 2's FX chain
     * header already has, so SRC and FX aren't lopsided -- plus growth slack.
     */
    fun row1Width(ctrlH: Float, comboW: Float, randomization: Boolean): Float =
        MODE_PILL_W + GAP + genBadgeW(comboW) + GAP + iconBtnW(ctrlH) +
            (if (randomization) GAP + iconBtnW(ctrlH) else 0f) +
            GAP + maxOf(queueNavW(ctrlH), PV_BADGE_W) +
            GAP + iconBtnW(ctrlH) + GAP + KEBAB_W +
            growthSlack(ctrlH)
}

/**
 * Deck row controls (Deck A, B, BG, PV):
 * - Left controls, two stacked rows: Row 1 (SRC) knob-assign pill, generator/preset badge,
 *   eject, randomize, queue navigation; Row 2 (FX) knob-assign pill and FX chain header.
 * - Right controls: FX bypass button.
 */
internal class PerformanceDeckControls(private val ctx: PerformanceUiContext) {

    /**
     * Controls to the left of the knobs for Deck rows (Deck A, B, BG, PV) in two stacked rows:
     * - Row 1 (SRC): [SRC] knob-assign pill, generator/preset badge, eject button,
     *   randomize die button, and play queue / bg queue navigation.
     * - Row 2 (FX): [FX] knob-assign pill and dedicated FX chain controls.
     */
    fun drawDeckRowLeftControls(
        session: SessionContext,
        mixer: Mixer,
        parametersState: ParametersState,
        deckLabel: String,
        deck: Deck,
        startX: Float,
        row1Y: Float,
        row2Y: Float,
        ctrlH: Float,
        comboW: Float,
        rowW: Float
    ) {
        val gap = DeckRowMetrics.GAP
        val isDeckA = deck === mixer.deckA
        val isDeckB = deck === mixer.deckB
        val isDeckBG = deck === mixer.deckBG
        val isDeckPV = deck === mixer.deckPV
        val tag = when {
            isDeckA -> "A"
            isDeckB -> "B"
            isDeckBG -> "BG"
            else -> "PV"
        }
        val dl = ImGui.getWindowDrawList()
        val isFx = ctx.isDeckRowFx(tag, parametersState)
        val isSrc = !isFx
        val modeBtnW = DeckRowMetrics.MODE_PILL_W

        // --- ROW 1 (SRC) -------------------------------------------------------------
        ImGui.setCursorScreenPos(startX, row1Y)
        ImGui.beginGroup()

        // 1. Shared [SRC] / [FX] toggle hitbox covering Row 1, gap, and Row 2
        val totalModeH = (row2Y + ctrlH) - row1Y
        ImGui.setNextItemAllowOverlap()
        val toggleClicked = ImGui.invisibleButton("##perf_mode_toggle_$tag", modeBtnW, totalModeH)
        val isModeHovered = ImGui.isItemHovered()
        if (toggleClicked) {
            if (isSrc) {
                llm.slop.liquidlsd.macro.MacroLearnState.onNavigateSection(deckLabel, "FX")
                ctx.deckRowMode[tag] = "FX"
                parametersState.setDeckSubTab(deckLabel, "FX")
                llm.slop.liquidlsd.macro.FxMacroSync.syncFor(ctx.targetBankIdFor(tag), mixer)
            } else {
                llm.slop.liquidlsd.macro.MacroLearnState.onNavigateSection(deckLabel, "SRC")
                ctx.deckRowMode[tag] = "SRC"
                parametersState.setDeckSubTab(deckLabel, "SRC")
            }
        }
        if (isModeHovered) {
            ImGui.setMouseCursor(ImGuiMouseCursor.Hand)
        }
        itemTooltip("Toggle $deckLabel knobs between Visual Source (SRC) and Insert FX (FX).")

        val mouseY = ImGui.getMousePosY()
        val midY = row1Y + ctrlH + (row2Y - (row1Y + ctrlH)) * 0.5f
        val isSrcHovered = isModeHovered && (mouseY <= midY)
        val isFxHovered = isModeHovered && (mouseY > midY)

        PerformanceColors.drawTogglePill(dl, startX, row1Y, modeBtnW, ctrlH, "SRC", isSrc, isSrcHovered, session)
        PerformanceColors.drawTogglePill(dl, startX, row2Y, modeBtnW, ctrlH, "FX", isFx, isFxHovered, session)

        ImGui.sameLine(0f, gap)

        // 2. Generator/preset badge -- click to Browse stock generators + saved presets
        // (a preset is just a generator with its params saved under a name, so there's one
        // control, not two -- see PerformanceBrowseBay.drawGenBrowse for the merged Browse list).
        val genBadgeW = DeckRowMetrics.genBadgeW(comboW)
        val activePreset = when {
            isDeckA -> session.presetManager.activePresetA
            isDeckB -> session.presetManager.activePresetB
            isDeckBG -> session.presetManager.activePresetBG
            else -> session.presetManager.activePresetPV
        }
        val isDirty = session.presetManager.isDeckDirty(deck, mixer)
        val dirtyMarker = if (isDirty) " *" else ""
        val genName = when {
            deck.isEmpty -> "${Icons.PLUS} Source"
            activePreset != null -> "$activePreset$dirtyMarker"
            else -> deck.source.displayName
        }
        val isLight = session.uiTheme.theme == UITheme.Theme.ORANGE_SUNSHINE
        val genBorderCol = if (isLight) ImGui.getColorU32(ImGuiCol.Border) else ImGui.colorConvertFloat4ToU32(0.35f, 0.40f, 0.50f, 0.70f)
        val genBgCol = if (isLight) ImGui.getColorU32(ImGuiCol.FrameBg) else ImGui.colorConvertFloat4ToU32(0.14f, 0.16f, 0.20f, 0.85f)
        val genTextCol = if (isLight) ImGui.getColorU32(ImGuiCol.Text) else ImGui.colorConvertFloat4ToU32(0.80f, 0.85f, 0.95f, 1f)
        val curX = ImGui.getCursorScreenPosX()
        val curY = ImGui.getCursorScreenPosY()
        dl.addRectFilled(curX, curY, curX + genBadgeW, curY + ctrlH, genBgCol, 4f)
        dl.addRect(curX, curY, curX + genBadgeW, curY + ctrlH, genBorderCol, 4f, 0, 1f)

        session.uiTheme.withFont(UITheme.FontLevel.CAPTION) {
            val textSz = ImGui.calcTextSize(genName)
            val tx = curX + (genBadgeW - textSz.x) * 0.5f
            val ty = curY + (ctrlH - textSz.y) * 0.5f
            dl.addText(tx.coerceAtLeast(curX + 4f), ty, genTextCol, genName)
        }
        val canonicalBankId = MacroEngine.deckBankIdFor(deck, mixer) ?: MacroEngine.DECK_A
        if (ImGui.invisibleButton("##perf_gen_badge_$tag", genBadgeW, ctrlH)) {
            parametersState.openGenBrowse(canonicalBankId, deckLabel)
        }
        val isExternalVideo = deck.source is ExternalVideoSource
        val sourceId = GeneratorDefaults.sourceIdFor(deck.source)
        val hasUserDef = GeneratorDefaults.hasUserDefault(sourceId)

        pushOpenDropdownPadding()
        if (ImGui.beginPopupContextItem("##perf_gen_badge_ctx_$tag")) {
            pushOpenDropdownFont()
            if (ImGui.menuItem("Browse...")) {
                parametersState.openGenBrowse(canonicalBankId, deckLabel)
            }
            if (!isExternalVideo && ImGui.menuItem("Save As...")) {
                ctx.deckPresetController?.handleSaveDeck(mixer, deck, isDeckA, isSaveAs = true)
            }
            if (!deck.isEmpty && !isExternalVideo) {
                ImGui.separator()
                if (ImGui.menuItem("Save as Default for ${deck.source.displayName}")) {
                    GeneratorDefaults.saveDefault(deck, canonicalBankId)
                }
                if (ImGui.menuItem("Apply Default Now")) {
                    GeneratorDefaults.applyToDeck(deck, deckLabel, canonicalBankId)
                }
                if (hasUserDef && ImGui.menuItem("Reset to Factory Default")) {
                    GeneratorDefaults.deleteDefault(sourceId)
                    GeneratorDefaults.applyToDeck(deck, deckLabel, canonicalBankId)
                }
            }
            popOpenDropdownFont()
            ImGui.endPopup()
        }
        popOpenDropdownPadding()
        if (ImGui.isItemHovered()) {
            ImGui.setMouseCursor(ImGuiMouseCursor.Hand)
            val hoverBorderCol = if (isLight) TangoPalette.u32(TangoPalette.ORANGE.normal) else ImGui.colorConvertFloat4ToU32(0.60f, 0.70f, 0.90f, 1f)
            dl.addRect(curX, curY, curX + genBadgeW, curY + ctrlH, hoverBorderCol, 4f, 0, 1.5f)
        }
        itemTooltip(
            if (deck.isEmpty) "$deckLabel is empty. Click to browse sources and presets."
            else if (isExternalVideo) "External Source: $genName ($deckLabel). Click to change the visual source."
            else "$genName ($deckLabel). Click to browse sources/presets, right-click for defaults."
        )

        ImGui.sameLine(0f, gap)

        // 3. Eject Button [ EJECT ]
        val iconBtnW = DeckRowMetrics.iconBtnW(ctrlH)
        val ejectBtnBg = if (isLight) ImGui.getColorU32(ImGuiCol.Button) else ImGui.colorConvertFloat4ToU32(0.16f, 0.18f, 0.22f, 1f)
        val ejectBtnHov = if (isLight) TangoPalette.u32(TangoPalette.DANGER.light) else ImGui.colorConvertFloat4ToU32(0.45f, 0.20f, 0.20f, 1f)
        ImGui.pushStyleColor(ImGuiCol.Button, ejectBtnBg)
        ImGui.pushStyleColor(ImGuiCol.ButtonHovered, ejectBtnHov)
        session.uiTheme.withFont(UITheme.FontLevel.BODY) {
            if (ImGui.button("${Icons.EJECT}##perf_eject_$tag", iconBtnW, ctrlH)) {
                UIManager.triggerDeckEject(deck, isDeckA = isDeckA, isDeckPV = isDeckPV)
            }
        }
        ImGui.popStyleColor(2)
        itemTooltip("Eject current preset from $deckLabel and reset to defaults.")

        // 4. Randomize Die Button [ DICES ]
        if (session.uiTheme.randomizationEnabled) {
            ImGui.sameLine(0f, gap)
            val randBtnBg = if (isLight) ImGui.getColorU32(ImGuiCol.Button) else ImGui.colorConvertFloat4ToU32(0.20f, 0.16f, 0.24f, 0.90f)
            val randBtnHov = if (isLight) TangoPalette.u32(TangoPalette.PLUM.light) else ImGui.colorConvertFloat4ToU32(0.35f, 0.22f, 0.42f, 1f)
            ImGui.pushStyleColor(ImGuiCol.Button, randBtnBg)
            ImGui.pushStyleColor(ImGuiCol.ButtonHovered, randBtnHov)
            session.uiTheme.withFont(UITheme.FontLevel.BODY) {
                if (ImGui.button("${Icons.DICES}##perf_rand_$tag", iconBtnW, ctrlH)) {
                    ParametersUndo.pushUndoState(parametersState, mixer)
                    when {
                        isDeckA -> mixer.randomizeDeckA()
                        isDeckB -> mixer.randomizeDeckB()
                        isDeckBG -> mixer.randomizeDeckBG()
                        else -> mixer.randomizeDeckPV()
                    }
                }
            }
            ImGui.popStyleColor(2)
            itemTooltip("Randomize $deckLabel modulators & base values.\nClick to randomize with undo support.")
        }

        ImGui.sameLine(0f, gap)

        // 5. PlayQueue / BG Queue navigation (or preview indicator for PV)
        val navBtnW = DeckRowMetrics.navBtnW(ctrlH)
        if (isDeckA || isDeckB) {
            val q = session.playQueueManager.queue
            val qIdx = session.playQueueManager.activeIndex
            val qCountStr = if (q.isNotEmpty() && qIdx in q.indices) "${qIdx + 1}/${q.size}" else if (q.isNotEmpty()) "-/${q.size}" else "--"

            val qPrevKey = "Global/queuePrev"
            val qPrevOscKey = "Mixer/queuePrev"
            val isMidiLearnQPrev = session.parametersState.isMidiTargetLearning(qPrevKey)
            val isOscLearnQPrev = OscLearnState.isTargetLearning(qPrevOscKey)
            val qPrevMidiMapping = session.midiMappingManager.getMappingForParameter(qPrevKey)
            val qPrevMidiText = qPrevMidiMapping?.let { if (it.channel == 0) " [CC ${it.cc}]" else " [Ch ${it.channel + 1} CC ${it.cc}]" } ?: ""

            val qPrevX = ImGui.getCursorScreenPosX()
            val qPrevY = ImGui.getCursorScreenPosY()
            if (ImGui.button("<##perf_q_prev_$tag", navBtnW, ctrlH)) {
                session.playQueueManager.triggerPrevious(mixer)
            }
            if (isMidiLearnQPrev) {
                dl.addRect(qPrevX - 1f, qPrevY - 1f, qPrevX + navBtnW + 1f, qPrevY + ctrlH + 1f, ImGui.colorConvertFloat4ToU32(0f, 0.85f, 1f, 1f), 3f, 0, 1.5f)
            } else if (isOscLearnQPrev) {
                TangoPalette.drawOscLearnPulseBorder(dl, qPrevX - 1f, qPrevY - 1f, qPrevX + navBtnW + 1f, qPrevY + ctrlH + 1f)
            }
            pushOpenDropdownPadding()
            if (ImGui.beginPopupContextItem("perf_q_prev_ctx_$tag")) {
                pushOpenDropdownFont()
                ImGui.textDisabled("PlayQueue Prev (<)")
                ImGui.separator()
                if (ImGui.menuItem("Trigger Previous")) {
                    session.playQueueManager.triggerPrevious(mixer)
                }
                ImGui.separator()
                if (isMidiLearnQPrev) {
                    if (ImGui.menuItem("${Icons.ALERT} Cancel MIDI Learn")) {
                        session.parametersState.midiLearnTarget = null
                    }
                } else {
                    if (ImGui.menuItem("${Icons.SETTINGS} Learn MIDI (Queue Prev)")) {
                        session.parametersState.startMidiLearn(MidiLearnTarget.GlobalAction(qPrevKey))
                    }
                }
                if (qPrevMidiMapping != null) {
                    if (ImGui.menuItem("${Icons.TRASH} Clear MIDI Mapping")) {
                        session.midiMappingManager.removeMapping(qPrevKey)
                        session.midiMappingManager.saveActiveProfile()
                    }
                }
                if (isOscLearnQPrev) {
                    if (ImGui.menuItem("${Icons.ALERT} Cancel OSC Learn")) {
                        OscLearnState.cancelLearn()
                    }
                } else {
                    if (ImGui.menuItem("${Icons.ACTIVITY} Learn OSC (Queue Prev)")) {
                        OscLearnState.startLearn(qPrevOscKey, 0f, 1f, "PlayQueue Prev")
                    }
                }
                val qPrevOscAddress = OscMappingManager.getAddressForParameter(qPrevOscKey)
                if (ImGui.menuItem("${Icons.TRASH} Clear OSC Mapping", null, false, qPrevOscAddress != null)) {
                    OscMappingManager.removeMapping(qPrevOscAddress!!)
                    OscMappingManager.saveActiveProfile()
                }
                popOpenDropdownFont()
                ImGui.endPopup()
            }
            popOpenDropdownPadding()
            itemTooltip("Advance to previous item in PlayQueue.$qPrevMidiText\nRight-click for MIDI/OSC Learn.")

            ImGui.sameLine(0f, DeckRowMetrics.QUEUE_INNER_GAP)

            val qTextW = DeckRowMetrics.QUEUE_IDX_W
            val qCurX = ImGui.getCursorScreenPosX()
            val qCurY = ImGui.getCursorScreenPosY()
            dl.addRectFilled(qCurX, qCurY, qCurX + qTextW, qCurY + ctrlH, genBgCol, 3f)
            session.uiTheme.withFont(UITheme.FontLevel.CAPTION) {
                val sz = ImGui.calcTextSize(qCountStr)
                dl.addText(qCurX + (qTextW - sz.x) * 0.5f, qCurY + (ctrlH - sz.y) * 0.5f, genTextCol, qCountStr)
            }
            ImGui.invisibleButton("##perf_q_idx_$tag", qTextW, ctrlH)
            itemTooltip("PlayQueue status: $qCountStr")

            ImGui.sameLine(0f, DeckRowMetrics.QUEUE_INNER_GAP)

            val qNextKey = "Global/queueNext"
            val qNextOscKey = "Mixer/queueNext"
            val isMidiLearnQNext = session.parametersState.isMidiTargetLearning(qNextKey)
            val isOscLearnQNext = OscLearnState.isTargetLearning(qNextOscKey)
            val qNextMidiMapping = session.midiMappingManager.getMappingForParameter(qNextKey)
            val qNextMidiText = qNextMidiMapping?.let { if (it.channel == 0) " [CC ${it.cc}]" else " [Ch ${it.channel + 1} CC ${it.cc}]" } ?: ""

            val qNextX = ImGui.getCursorScreenPosX()
            val qNextY = ImGui.getCursorScreenPosY()
            if (ImGui.button(">##perf_q_next_$tag", navBtnW, ctrlH)) {
                session.playQueueManager.triggerNext(mixer)
            }
            if (isMidiLearnQNext) {
                dl.addRect(qNextX - 1f, qNextY - 1f, qNextX + navBtnW + 1f, qNextY + ctrlH + 1f, ImGui.colorConvertFloat4ToU32(0f, 0.85f, 1f, 1f), 3f, 0, 1.5f)
            } else if (isOscLearnQNext) {
                TangoPalette.drawOscLearnPulseBorder(dl, qNextX - 1f, qNextY - 1f, qNextX + navBtnW + 1f, qNextY + ctrlH + 1f)
            }
            pushOpenDropdownPadding()
            if (ImGui.beginPopupContextItem("perf_q_next_ctx_$tag")) {
                pushOpenDropdownFont()
                ImGui.textDisabled("PlayQueue Next (>)")
                ImGui.separator()
                if (ImGui.menuItem("Trigger Next")) {
                    session.playQueueManager.triggerNext(mixer)
                }
                ImGui.separator()
                if (isMidiLearnQNext) {
                    if (ImGui.menuItem("${Icons.ALERT} Cancel MIDI Learn")) {
                        session.parametersState.midiLearnTarget = null
                    }
                } else {
                    if (ImGui.menuItem("${Icons.SETTINGS} Learn MIDI (Queue Next)")) {
                        session.parametersState.startMidiLearn(MidiLearnTarget.GlobalAction(qNextKey))
                    }
                }
                if (qNextMidiMapping != null) {
                    if (ImGui.menuItem("${Icons.TRASH} Clear MIDI Mapping")) {
                        session.midiMappingManager.removeMapping(qNextKey)
                        session.midiMappingManager.saveActiveProfile()
                    }
                }
                if (isOscLearnQNext) {
                    if (ImGui.menuItem("${Icons.ALERT} Cancel OSC Learn")) {
                        OscLearnState.cancelLearn()
                    }
                } else {
                    if (ImGui.menuItem("${Icons.ACTIVITY} Learn OSC (Queue Next)")) {
                        OscLearnState.startLearn(qNextOscKey, 0f, 1f, "PlayQueue Next")
                    }
                }
                val qNextOscAddress = OscMappingManager.getAddressForParameter(qNextOscKey)
                if (ImGui.menuItem("${Icons.TRASH} Clear OSC Mapping", null, false, qNextOscAddress != null)) {
                    OscMappingManager.removeMapping(qNextOscAddress!!)
                    OscMappingManager.saveActiveProfile()
                }
                popOpenDropdownFont()
                ImGui.endPopup()
            }
            popOpenDropdownPadding()
            itemTooltip("Advance to next item in PlayQueue.$qNextMidiText\nRight-click for MIDI/OSC Learn.")
        } else if (isDeckBG) {
            val bgQ = session.bgQueueManager.queue
            val bgQIdx = session.bgQueueManager.activeIndex
            val bgQCountStr = if (bgQ.isNotEmpty() && bgQIdx in bgQ.indices) "${bgQIdx + 1}/${bgQ.size}" else if (bgQ.isNotEmpty()) "-/${bgQ.size}" else "--"

            val bgPrevKey = "Global/bgQueuePrev"
            val bgPrevOscKey = "Mixer/bgQueuePrev"
            val isMidiLearnBgPrev = session.parametersState.isMidiTargetLearning(bgPrevKey)
            val isOscLearnBgPrev = OscLearnState.isTargetLearning(bgPrevOscKey)
            val bgPrevMidiMapping = session.midiMappingManager.getMappingForParameter(bgPrevKey)
            val bgPrevMidiText = bgPrevMidiMapping?.let { if (it.channel == 0) " [CC ${it.cc}]" else " [Ch ${it.channel + 1} CC ${it.cc}]" } ?: ""

            val bgPrevX = ImGui.getCursorScreenPosX()
            val bgPrevY = ImGui.getCursorScreenPosY()
            if (ImGui.button("<##perf_bg_prev", navBtnW, ctrlH)) {
                session.bgQueueManager.triggerPrevious(mixer)
            }
            if (isMidiLearnBgPrev) {
                dl.addRect(bgPrevX - 1f, bgPrevY - 1f, bgPrevX + navBtnW + 1f, bgPrevY + ctrlH + 1f, ImGui.colorConvertFloat4ToU32(0f, 0.85f, 1f, 1f), 3f, 0, 1.5f)
            } else if (isOscLearnBgPrev) {
                TangoPalette.drawOscLearnPulseBorder(dl, bgPrevX - 1f, bgPrevY - 1f, bgPrevX + navBtnW + 1f, bgPrevY + ctrlH + 1f)
            }
            pushOpenDropdownPadding()
            if (ImGui.beginPopupContextItem("perf_bg_prev_ctx")) {
                pushOpenDropdownFont()
                ImGui.textDisabled("BG Queue Prev (<)")
                ImGui.separator()
                if (ImGui.menuItem("Trigger Previous")) {
                    session.bgQueueManager.triggerPrevious(mixer)
                }
                ImGui.separator()
                if (isMidiLearnBgPrev) {
                    if (ImGui.menuItem("${Icons.ALERT} Cancel MIDI Learn")) {
                        session.parametersState.midiLearnTarget = null
                    }
                } else {
                    if (ImGui.menuItem("${Icons.SETTINGS} Learn MIDI (BG Queue Prev)")) {
                        session.parametersState.startMidiLearn(MidiLearnTarget.GlobalAction(bgPrevKey))
                    }
                }
                if (bgPrevMidiMapping != null) {
                    if (ImGui.menuItem("${Icons.TRASH} Clear MIDI Mapping")) {
                        session.midiMappingManager.removeMapping(bgPrevKey)
                        session.midiMappingManager.saveActiveProfile()
                    }
                }
                if (isOscLearnBgPrev) {
                    if (ImGui.menuItem("${Icons.ALERT} Cancel OSC Learn")) {
                        OscLearnState.cancelLearn()
                    }
                } else {
                    if (ImGui.menuItem("${Icons.ACTIVITY} Learn OSC (BG Queue Prev)")) {
                        OscLearnState.startLearn(bgPrevOscKey, 0f, 1f, "BG Queue Prev")
                    }
                }
                val bgPrevOscAddress = OscMappingManager.getAddressForParameter(bgPrevOscKey)
                if (ImGui.menuItem("${Icons.TRASH} Clear OSC Mapping", null, false, bgPrevOscAddress != null)) {
                    OscMappingManager.removeMapping(bgPrevOscAddress!!)
                    OscMappingManager.saveActiveProfile()
                }
                popOpenDropdownFont()
                ImGui.endPopup()
            }
            popOpenDropdownPadding()
            itemTooltip("Advance to previous item in BG Queue.$bgPrevMidiText\nRight-click for MIDI/OSC Learn.")

            ImGui.sameLine(0f, DeckRowMetrics.QUEUE_INNER_GAP)

            val bgQTextW = DeckRowMetrics.QUEUE_IDX_W
            val bgCurX = ImGui.getCursorScreenPosX()
            val bgCurY = ImGui.getCursorScreenPosY()
            dl.addRectFilled(bgCurX, bgCurY, bgCurX + bgQTextW, bgCurY + ctrlH, genBgCol, 3f)
            session.uiTheme.withFont(UITheme.FontLevel.CAPTION) {
                val sz = ImGui.calcTextSize(bgQCountStr)
                dl.addText(bgCurX + (bgQTextW - sz.x) * 0.5f, bgCurY + (ctrlH - sz.y) * 0.5f, genTextCol, bgQCountStr)
            }
            ImGui.invisibleButton("##perf_bg_idx", bgQTextW, ctrlH)
            itemTooltip("BG Queue status: $bgQCountStr")

            ImGui.sameLine(0f, DeckRowMetrics.QUEUE_INNER_GAP)

            val bgNextKey = "Global/bgQueueNext"
            val bgNextOscKey = "Mixer/bgQueueNext"
            val isMidiLearnBgNext = session.parametersState.isMidiTargetLearning(bgNextKey)
            val isOscLearnBgNext = OscLearnState.isTargetLearning(bgNextOscKey)
            val bgNextMidiMapping = session.midiMappingManager.getMappingForParameter(bgNextKey)
            val bgNextMidiText = bgNextMidiMapping?.let { if (it.channel == 0) " [CC ${it.cc}]" else " [Ch ${it.channel + 1} CC ${it.cc}]" } ?: ""

            val bgNextX = ImGui.getCursorScreenPosX()
            val bgNextY = ImGui.getCursorScreenPosY()
            if (ImGui.button(">##perf_bg_next", navBtnW, ctrlH)) {
                session.bgQueueManager.triggerNext(mixer)
            }
            if (isMidiLearnBgNext) {
                dl.addRect(bgNextX - 1f, bgNextY - 1f, bgNextX + navBtnW + 1f, bgNextY + ctrlH + 1f, ImGui.colorConvertFloat4ToU32(0f, 0.85f, 1f, 1f), 3f, 0, 1.5f)
            } else if (isOscLearnBgNext) {
                TangoPalette.drawOscLearnPulseBorder(dl, bgNextX - 1f, bgNextY - 1f, bgNextX + navBtnW + 1f, bgNextY + ctrlH + 1f)
            }
            pushOpenDropdownPadding()
            if (ImGui.beginPopupContextItem("perf_bg_next_ctx")) {
                pushOpenDropdownFont()
                ImGui.textDisabled("BG Queue Next (>)")
                ImGui.separator()
                if (ImGui.menuItem("Trigger Next")) {
                    session.bgQueueManager.triggerNext(mixer)
                }
                ImGui.separator()
                if (isMidiLearnBgNext) {
                    if (ImGui.menuItem("${Icons.ALERT} Cancel MIDI Learn")) {
                        session.parametersState.midiLearnTarget = null
                    }
                } else {
                    if (ImGui.menuItem("${Icons.SETTINGS} Learn MIDI (BG Queue Next)")) {
                        session.parametersState.startMidiLearn(MidiLearnTarget.GlobalAction(bgNextKey))
                    }
                }
                if (bgNextMidiMapping != null) {
                    if (ImGui.menuItem("${Icons.TRASH} Clear MIDI Mapping")) {
                        session.midiMappingManager.removeMapping(bgNextKey)
                        session.midiMappingManager.saveActiveProfile()
                    }
                }
                if (isOscLearnBgNext) {
                    if (ImGui.menuItem("${Icons.ALERT} Cancel OSC Learn")) {
                        OscLearnState.cancelLearn()
                    }
                } else {
                    if (ImGui.menuItem("${Icons.ACTIVITY} Learn OSC (BG Queue Next)")) {
                        OscLearnState.startLearn(bgNextOscKey, 0f, 1f, "BG Queue Next")
                    }
                }
                val bgNextOscAddress = OscMappingManager.getAddressForParameter(bgNextOscKey)
                if (ImGui.menuItem("${Icons.TRASH} Clear OSC Mapping", null, false, bgNextOscAddress != null)) {
                    OscMappingManager.removeMapping(bgNextOscAddress!!)
                    OscMappingManager.saveActiveProfile()
                }
                popOpenDropdownFont()
                ImGui.endPopup()
            }
            popOpenDropdownPadding()
            itemTooltip("Advance to next item in BG Queue.$bgNextMidiText\nRight-click for MIDI/OSC Learn.")
        } else {
            // Deck PV indicator / focus button
            val pvBadgeW = DeckRowMetrics.PV_BADGE_W
            val pvBtnBg = if (isLight) ImGui.getColorU32(ImGuiCol.Button) else ImGui.colorConvertFloat4ToU32(0.12f, 0.22f, 0.18f, 0.85f)
            val pvBtnHov = if (isLight) TangoPalette.u32(TangoPalette.PLUM.light) else ImGui.colorConvertFloat4ToU32(0.18f, 0.32f, 0.25f, 1f)
            ImGui.pushStyleColor(ImGuiCol.Button, pvBtnBg)
            ImGui.pushStyleColor(ImGuiCol.ButtonHovered, pvBtnHov)
            session.uiTheme.withFont(UITheme.FontLevel.CAPTION) {
                if (ImGui.button("PREVIEW##perf_pv_badge", pvBadgeW, ctrlH)) {
                    parametersState.openFromMonitor(MacroEngine.DECK_PV, "Deck PV")
                }
            }
            ImGui.popStyleColor(2)
            itemTooltip("Deck PV (Preview Deck)\nClick to open Deck PV in Deep Edit.")
        }

        ImGui.sameLine(0f, gap)

        // 6. Save button -- save-if-possible, else Save As modal (mirrors the FX chain's [Save]
        // in Row 2, so SRC has the same visible save affordance FX already does).
        val saveBtnBg = when {
            isDirty -> TangoPalette.u32(TangoPalette.ALERT.dark)
            isLight -> ImGui.getColorU32(ImGuiCol.Button)
            else -> ImGui.colorConvertFloat4ToU32(0.18f, 0.20f, 0.24f, 0.8f)
        }
        ImGui.pushStyleColor(ImGuiCol.Button, saveBtnBg)
        session.uiTheme.withFont(UITheme.FontLevel.BODY) {
            if (ImGui.button("${Icons.SAVE}##perf_src_save_$tag", iconBtnW, ctrlH)) {
                ctx.deckPresetController?.handleSaveDeck(mixer, deck, isDeckA, isSaveAs = false)
            }
        }
        ImGui.popStyleColor()
        itemTooltip("Save $deckLabel's current source & parameters as a preset.")

        ImGui.sameLine(0f, gap)

        // 7. [⋮] Kebab -- same menu as the gen badge's right-click (Browse, Save As, defaults),
        // just also reachable without knowing to right-click the badge (mirrors FX chain's ⋮).
        if (ImGui.button("${Icons.MORE_VERTICAL}##perf_src_more_$tag", DeckRowMetrics.KEBAB_W, ctrlH)) {
            ImGui.openPopup("##perf_gen_badge_ctx_$tag")
        }
        itemTooltip("Source operations (Browse, Save As, defaults).")

        ImGui.endGroup()

        // --- ROW 2 (FX) --------------------------------------------------------------
        ImGui.setCursorScreenPos(startX, row2Y)
        ImGui.beginGroup()

        // 1. Spacing for [FX] pill (rendered and hit-tested with [SRC] in Row 1 above)
        ImGui.dummy(modeBtnW, ctrlH)
        ImGui.sameLine(0f, gap)

        // 2. Dedicated FX chain controls
        val deckChain = deck.fxChain
        val targetBank = ctx.targetBankIdFor(tag)
        val fxCanonicalBankId = MacroEngine.deckBankIdFor(deck, mixer) ?: MacroEngine.DECK_A
        FxChainHeader.drawControls(session, mixer, deckChain, targetBank, "$deckLabel FX", ctrlH, maxW = rowW - modeBtnW - gap) {
            parametersState.openFxChainBrowse(fxCanonicalBankId, deckLabel, slotIndex = null)
        }

        ImGui.endGroup()
    }

    /**
     * Deck FX chain bypass + Resync buttons placed to the right of the knobs for Deck rows.
     */
    fun drawDeckRowRightControls(
        session: SessionContext,
        mixer: Mixer,
        deckLabel: String,
        deck: Deck,
        startX: Float,
        startY: Float,
        ctrlH: Float
    ) {
        val gap = 4f
        val isDeckA = deckLabel.endsWith("A")
        val isDeckB = deckLabel.endsWith("B")
        val isDeckBG = deckLabel.endsWith("BG")
        val tag = when {
            isDeckA -> "A"
            isDeckB -> "B"
            isDeckBG -> "BG"
            else -> "PV"
        }

        ImGui.setCursorScreenPos(startX, startY)
        ImGui.beginGroup()
        FxChainHeader.drawBypassButton(session, deck.fxChain, tag, ctrlH, 56f)
        ImGui.endGroup()
    }
}
