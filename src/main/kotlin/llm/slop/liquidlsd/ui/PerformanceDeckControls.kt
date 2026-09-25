package llm.slop.liquidlsd.ui

import imgui.ImGui
import imgui.flag.ImGuiCol
import imgui.flag.ImGuiKey
import imgui.flag.ImGuiMouseCursor
import imgui.flag.ImGuiStyleVar
import imgui.type.ImString
import llm.slop.liquidlsd.SessionContext
import llm.slop.liquidlsd.macro.MacroEngine
import llm.slop.liquidlsd.osc.OscLearnState
import llm.slop.liquidlsd.rendering.Deck
import llm.slop.liquidlsd.rendering.Mixer
import java.io.File

/**
 * Deck row controls (Deck A, B, BG, PV):
 * - Left controls, two stacked rows: Row 1 (SRC) knob-assign pill, generator badge, preset combo,
 *   eject, randomize, queue navigation; Row 2 (FX) knob-assign pill and FX chain header.
 * - Right controls: FX bypass button.
 */
internal class PerformanceDeckControls(private val ctx: PerformanceUiContext) {

    private val presetSearchA = ImString(64)
    private val presetSearchB = ImString(64)
    private val presetSearchBG = ImString(64)
    private val presetSearchPV = ImString(64)
    private var comboWasOpenA = false
    private var comboWasOpenB = false
    private var comboWasOpenBG = false
    private var comboWasOpenPV = false

    /**
     * Controls to the left of the knobs for Deck rows (Deck A, B, BG, PV) in two stacked rows:
     * - Row 1 (SRC): [SRC] knob-assign pill, generator badge, preset selector combo, eject button,
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
        val gap = 4f
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
        val activeSubTab = parametersState.getActiveDeckSubTabByTag(tag)
        val currentMode = if (activeSubTab == "FX") "FX" else ctx.deckRowMode.getOrDefault(tag, "SRC")
        val isSrc = currentMode == "SRC"
        val isFx = currentMode == "FX"
        val modeBtnW = 28f

        // --- ROW 1 (SRC) -------------------------------------------------------------
        ImGui.setCursorScreenPos(startX, row1Y)
        ImGui.beginGroup()

        // 1. [SRC] mode pill
        ImGui.pushStyleColor(ImGuiCol.Button, if (isSrc) ImGui.colorConvertFloat4ToU32(0.20f, 0.45f, 0.70f, 1f) else ImGui.colorConvertFloat4ToU32(0.14f, 0.16f, 0.20f, 0.7f))
        if (ImGui.button("SRC##perf_mode_src_$tag", modeBtnW, ctrlH)) {
            ctx.deckRowMode[tag] = "SRC"
            parametersState.setDeckSubTab(deckLabel, "SRC")
        }
        ImGui.popStyleColor()
        itemTooltip("Assign $deckLabel's on-screen knobs to Visual Generator macros. Source controls stay available either way.")

        ImGui.sameLine(0f, gap)

        // 2. Generator badge -- click to change the deck's visual source
        val genBadgeW = 74f
        val genName = if (deck.isEmpty) "${Icons.PLUS} Source" else deck.source.displayName
        val genBorderCol = ImGui.colorConvertFloat4ToU32(0.35f, 0.40f, 0.50f, 0.70f)
        val genBgCol = ImGui.colorConvertFloat4ToU32(0.14f, 0.16f, 0.20f, 0.85f)
        val genTextCol = ImGui.colorConvertFloat4ToU32(0.80f, 0.85f, 0.95f, 1f)
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
        if (ImGui.invisibleButton("##perf_gen_badge_$tag", genBadgeW, ctrlH)) {
            DeckSourcePicker.open(session, parametersState, mixer, deck, deckLabel, ctx.deckPresetController)
        }
        if (ImGui.isItemHovered()) {
            ImGui.setMouseCursor(ImGuiMouseCursor.Hand)
            dl.addRect(curX, curY, curX + genBadgeW, curY + ctrlH, ImGui.colorConvertFloat4ToU32(0.60f, 0.70f, 0.90f, 1f), 4f, 0, 1.5f)
        }
        itemTooltip(if (deck.isEmpty) "$deckLabel is empty. Click to choose a visual source." else "Generator: $genName ($deckLabel). Click to change the visual source.")

        ImGui.sameLine(0f, gap)

        // 3. Preset dropdown combo
        val activePreset = when {
            isDeckA -> session.presetManager.activePresetA
            isDeckB -> session.presetManager.activePresetB
            isDeckBG -> session.presetManager.activePresetBG
            else -> session.presetManager.activePresetPV
        }
        val isDirty = session.presetManager.isDeckDirty(deck, mixer)
        val dirtyMarker = if (isDirty) " *" else ""
        val presetDisplay = (activePreset ?: "Default") + dirtyMarker

        ImGui.setNextItemWidth(comboW)
        val searchBuf = when {
            isDeckA -> presetSearchA
            isDeckB -> presetSearchB
            isDeckBG -> presetSearchBG
            else -> presetSearchPV
        }
        val wasOpen = when {
            isDeckA -> comboWasOpenA
            isDeckB -> comboWasOpenB
            isDeckBG -> comboWasOpenBG
            else -> comboWasOpenPV
        }
        // Combos size their height from font + frame padding rather than taking one explicitly, so
        // pad them out to ctrlH to match the buttons beside them. Popped before the popup body.
        ImGui.pushStyleVar(ImGuiStyleVar.FramePadding, ImGui.getStyle().getFramePaddingX(), ((ctrlH - ImGui.getFontSize()) / 2f).coerceAtLeast(0f))
        val isComboOpen = ImGui.beginCombo("##perf_preset_combo_$tag", presetDisplay)
        ImGui.popStyleVar()
        if (isComboOpen) {
            if (!wasOpen) {
                ImGui.setKeyboardFocusHere()
                when {
                    isDeckA -> comboWasOpenA = true
                    isDeckB -> comboWasOpenB = true
                    isDeckBG -> comboWasOpenBG = true
                    else -> comboWasOpenPV = true
                }
            }
            ImGui.setNextItemWidth(-1f)
            ImGui.inputTextWithHint("##preset_search_$tag", "Search presets... (Esc to clear)", searchBuf)
            if (ImGui.isItemActive() && ImGui.isKeyPressed(ImGuiKey.Escape)) {
                searchBuf.set("")
            }
            ImGui.separator()

            val query = searchBuf.get().trim()
            val allPresets = FileSystemManager.scanAllPresets()
            val filtered = if (query.isEmpty()) allPresets else allPresets.filter { it.name.contains(query, ignoreCase = true) }

            if (filtered.isEmpty()) {
                ImGui.textDisabled(if (query.isEmpty()) "No presets found" else "No matching presets")
            } else {
                for (preset in filtered) {
                    val isSelected = preset.name == activePreset
                    if (ImGui.selectable("${preset.name}##perf_pselect_${tag}_${preset.path.hashCode()}", isSelected)) {
                        session.presetRepository.loadDeckPresetAsync(
                            File(preset.path),
                            isDeckA = isDeckA,
                            isDeckBG = isDeckBG,
                            isDeckPV = isDeckPV
                        )
                        searchBuf.set("")
                    }
                    if (isSelected) {
                        ImGui.setItemDefaultFocus()
                    }
                }
            }
            ImGui.endCombo()
        } else {
            if (wasOpen) {
                searchBuf.set("")
                when {
                    isDeckA -> comboWasOpenA = false
                    isDeckB -> comboWasOpenB = false
                    isDeckBG -> comboWasOpenBG = false
                    else -> comboWasOpenPV = false
                }
            }
        }
        itemTooltip(
            if (activePreset != null) "Active preset: $activePreset$dirtyMarker\nClick to search and select presets."
            else "Select a preset for $deckLabel."
        )

        ImGui.sameLine(0f, gap)

        // 3. Eject Button [ EJECT ]
        val iconBtnW = ctrlH
        ImGui.pushStyleColor(ImGuiCol.Button, ImGui.colorConvertFloat4ToU32(0.16f, 0.18f, 0.22f, 1f))
        ImGui.pushStyleColor(ImGuiCol.ButtonHovered, ImGui.colorConvertFloat4ToU32(0.45f, 0.20f, 0.20f, 1f))
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
            ImGui.pushStyleColor(ImGuiCol.Button, ImGui.colorConvertFloat4ToU32(0.20f, 0.16f, 0.24f, 0.90f))
            ImGui.pushStyleColor(ImGuiCol.ButtonHovered, ImGui.colorConvertFloat4ToU32(0.35f, 0.22f, 0.42f, 1f))
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
        val navBtnW = (ctrlH * 0.85f).coerceAtLeast(20f)
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
            }
            if (ImGui.beginPopupContextItem("perf_q_prev_ctx_$tag")) {
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
                ImGui.endPopup()
            }
            itemTooltip("Advance to previous item in PlayQueue.$qPrevMidiText\nRight-click for MIDI/OSC Learn.")

            ImGui.sameLine(0f, 2f)

            val qTextW = 38f
            val qCurX = ImGui.getCursorScreenPosX()
            val qCurY = ImGui.getCursorScreenPosY()
            dl.addRectFilled(qCurX, qCurY, qCurX + qTextW, qCurY + ctrlH, genBgCol, 3f)
            session.uiTheme.withFont(UITheme.FontLevel.CAPTION) {
                val sz = ImGui.calcTextSize(qCountStr)
                dl.addText(qCurX + (qTextW - sz.x) * 0.5f, qCurY + (ctrlH - sz.y) * 0.5f, genTextCol, qCountStr)
            }
            ImGui.invisibleButton("##perf_q_idx_$tag", qTextW, ctrlH)
            itemTooltip("PlayQueue status: $qCountStr")

            ImGui.sameLine(0f, 2f)

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
            }
            if (ImGui.beginPopupContextItem("perf_q_next_ctx_$tag")) {
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
                ImGui.endPopup()
            }
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
            }
            if (ImGui.beginPopupContextItem("perf_bg_prev_ctx")) {
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
                ImGui.endPopup()
            }
            itemTooltip("Advance to previous item in BG Queue.$bgPrevMidiText\nRight-click for MIDI/OSC Learn.")

            ImGui.sameLine(0f, 2f)

            val bgQTextW = 38f
            val bgCurX = ImGui.getCursorScreenPosX()
            val bgCurY = ImGui.getCursorScreenPosY()
            dl.addRectFilled(bgCurX, bgCurY, bgCurX + bgQTextW, bgCurY + ctrlH, genBgCol, 3f)
            session.uiTheme.withFont(UITheme.FontLevel.CAPTION) {
                val sz = ImGui.calcTextSize(bgQCountStr)
                dl.addText(bgCurX + (bgQTextW - sz.x) * 0.5f, bgCurY + (ctrlH - sz.y) * 0.5f, genTextCol, bgQCountStr)
            }
            ImGui.invisibleButton("##perf_bg_idx", bgQTextW, ctrlH)
            itemTooltip("BG Queue status: $bgQCountStr")

            ImGui.sameLine(0f, 2f)

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
            }
            if (ImGui.beginPopupContextItem("perf_bg_next_ctx")) {
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
                ImGui.endPopup()
            }
            itemTooltip("Advance to next item in BG Queue.$bgNextMidiText\nRight-click for MIDI/OSC Learn.")
        } else {
            // Deck PV indicator / focus button
            val pvBadgeW = 60f
            ImGui.pushStyleColor(ImGuiCol.Button, ImGui.colorConvertFloat4ToU32(0.12f, 0.22f, 0.18f, 0.85f))
            ImGui.pushStyleColor(ImGuiCol.ButtonHovered, ImGui.colorConvertFloat4ToU32(0.18f, 0.32f, 0.25f, 1f))
            session.uiTheme.withFont(UITheme.FontLevel.CAPTION) {
                if (ImGui.button("PREVIEW##perf_pv_badge", pvBadgeW, ctrlH)) {
                    parametersState.activeTopTab = "Deck PV"
                    parametersState.setDisclosure(MacroEngine.DECK_PV, ParametersState.DisclosureLevel.DEEP_EDIT)
                }
            }
            ImGui.popStyleColor(2)
            itemTooltip("Deck PV (Preview Deck)\nClick to open Deck PV in Deep Edit.")
        }

        ImGui.endGroup()

        // --- ROW 2 (FX) --------------------------------------------------------------
        ImGui.setCursorScreenPos(startX, row2Y)
        ImGui.beginGroup()

        // 1. [FX] mode pill
        ImGui.pushStyleColor(ImGuiCol.Button, if (isFx) ImGui.colorConvertFloat4ToU32(0.80f, 0.40f, 0.15f, 1f) else ImGui.colorConvertFloat4ToU32(0.14f, 0.16f, 0.20f, 0.7f))
        if (ImGui.button("FX##perf_mode_fx_$tag", modeBtnW, ctrlH)) {
            ctx.deckRowMode[tag] = "FX"
            parametersState.setDeckSubTab(deckLabel, "FX")
            llm.slop.liquidlsd.macro.FxMacroSync.syncFor(ctx.targetBankIdFor(tag), mixer)
        }
        ImGui.popStyleColor()
        itemTooltip("Assign $deckLabel's on-screen knobs to its insert FX chain (Super Knob + 3 Metaknobs). FX chain controls stay available either way.")

        ImGui.sameLine(0f, gap)

        // 2. Dedicated FX chain controls
        val deckChain = deck.fxChain
        val targetBank = ctx.targetBankIdFor(tag)
        FxChainHeader.drawControls(session, mixer, deckChain, targetBank, "$deckLabel FX", ctrlH, maxW = rowW - modeBtnW - gap)

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
        FxChainHeader.drawBypassButton(deck.fxChain, tag, ctrlH, 56f)
        ImGui.endGroup()
    }
}
