package llm.slop.liquidlsd.ui

import llm.slop.liquidlsd.midi.MidiLearnTarget
import imgui.ImGui
import imgui.flag.ImGuiCol
import imgui.flag.ImGuiMouseCursor
import llm.slop.liquidlsd.SessionContext
import llm.slop.liquidlsd.macro.MacroEngine
import llm.slop.liquidlsd.osc.OscLearnState
import llm.slop.liquidlsd.osc.OscMapModeState
import llm.slop.liquidlsd.osc.OscMappingManager
import llm.slop.liquidlsd.presets.DeckChange
import llm.slop.liquidlsd.presets.DeckOps
import llm.slop.liquidlsd.presets.DeckSlot
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
    @Deprecated("Playqueue status removed from deck row")
    const val QUEUE_IDX_W = 0f
    @Deprecated("Inner gap removed from queue nav")
    const val QUEUE_INNER_GAP = 0f
    const val PV_BADGE_W = 56f
    /** Kebab menu button -- narrower than a square icon button, matching [FxChainHeader]'s ⋮. */
    const val KEBAB_W = 20f

    fun iconBtnW(ctrlH: Float): Float = ctrlH
    fun navBtnW(ctrlH: Float): Float = ((PV_BADGE_W - GAP) * 0.5f).coerceAtLeast(20f)

    /** `< >` queue navigation (Decks A, B, BG). */
    fun queueNavW(ctrlH: Float): Float = navBtnW(ctrlH) * 2f + GAP

    /**
     * Width of the single generator/preset badge -- absorbs the width once used by the
     * now-removed separate preset combo, so [row1Width] doesn't drift from what's drawn.
     */
    fun genBadgeW(comboW: Float): Float = GEN_BADGE_BASE_W + GAP + comboW

    fun growthSlack(ctrlH: Float): Float = 0f

    /**
     * Line 1 (SRC) width: pill, kebab, generator/preset badge, save, queue nav or PV's PREVIEW
     * badge (whichever is wider), and eject.
     * (Randomize die button is placed in the right wing above the BYPASS button.)
     */
    fun row1Width(ctrlH: Float, comboW: Float): Float =
        MODE_PILL_W + GAP + KEBAB_W + GAP + genBadgeW(comboW) + GAP + iconBtnW(ctrlH) +
            GAP + maxOf(queueNavW(ctrlH), PV_BADGE_W) +
            GAP + iconBtnW(ctrlH)

    @Deprecated("Randomize die is now placed above the BYPASS button in the right wing.", ReplaceWith("row1Width(ctrlH, comboW)"))
    fun row1Width(ctrlH: Float, comboW: Float, randomization: Boolean): Float = row1Width(ctrlH, comboW)
}

/**
 * Deck row controls (Deck A, B, BG, PV):
 * - Left controls, two stacked rows:
 *   Row 1 (SRC): [SRC] knob-assign pill, kebab, generator/preset badge, Save, queue navigation, eject;
 *   Row 2 (FX): [FX] knob-assign pill, kebab, chain name, Save, queue navigation, [1][2][3] slot pills.
 * - Right controls: Row 1 randomize die button (applies to both SRC and FX); Row 2 FX bypass button.
 */
internal class PerformanceDeckControls(private val ctx: PerformanceUiContext) {
    private val deckFxActions = HashMap<String, DeckFxActions>()

    /**
     * Strings a deck row would otherwise rebuild every frame: ImGui IDs (fixed per tag) and tooltips (rebuilt
     * only when the deck label or the badge's content changes). One instance per deck tag.
     */
    private class DeckStrings(tag: String) {
        val modeToggleId = "##perf_mode_toggle_$tag"
        val kebabId = "${Icons.MORE_VERTICAL}##perf_src_more_$tag"
        val badgeCtxId = "##perf_gen_badge_ctx_$tag"
        val badgeId = "##perf_gen_badge_$tag"
        val saveId = "${Icons.SAVE}##perf_src_save_$tag"
        val ejectId = "${Icons.EJECT}##perf_eject_$tag"
        val randId = "${Icons.DICES}##perf_rand_$tag"
        val qPrevId = "◀##perf_q_prev_$tag"
        val qPrevCtxId = "perf_q_prev_ctx_$tag"
        val qNextId = "▶##perf_q_next_$tag"
        val qNextCtxId = "perf_q_next_ctx_$tag"

        private var label: String? = null
        var toggleTip = ""; private set
        var saveTip = ""; private set
        var ejectTip = ""; private set
        var randTip = ""; private set
        var fxTitle = ""; private set
        var emptyTip = ""; private set

        /** Rebuilds the label-derived strings when [deckLabel] differs from the last call. */
        fun ensureLabel(deckLabel: String) {
            if (deckLabel == label) return
            label = deckLabel
            toggleTip = "Toggle $deckLabel knobs between Visual Source (SRC) and Insert FX (FX)."
            saveTip = "Save $deckLabel's current source & parameters as a preset."
            ejectTip = "Eject current preset from $deckLabel and reset to defaults."
            randTip = "Randomize $deckLabel modulators & base values (Source and FX).\nClick to randomize with undo support."
            fxTitle = "$deckLabel FX"
            emptyTip = "$deckLabel is empty. Click to browse sources and presets."
            badgeKeyLabel = null // the badge strings embed the label too
        }

        // Source badge: name + tooltip, rebuilt when the deck's content (not the frame) changes.
        private var badgeKeyLabel: String? = null
        private var badgeEmpty = false
        private var badgeExternal = false
        private var badgePreset: String? = null
        private var badgeDirty = false
        private var badgeSource = ""
        var genName = ""; private set
        var genTip = ""; private set

        fun updateBadge(deckLabel: String, empty: Boolean, external: Boolean, preset: String?, dirty: Boolean, sourceName: String) {
            if (badgeKeyLabel === label && badgeEmpty == empty && badgeExternal == external &&
                badgePreset == preset && badgeDirty == dirty && badgeSource == sourceName && label != null) return
            badgeKeyLabel = label; badgeEmpty = empty; badgeExternal = external
            badgePreset = preset; badgeDirty = dirty; badgeSource = sourceName
            genName = when {
                empty -> "${Icons.PLUS} Source"
                preset != null -> "$preset${if (dirty) " *" else ""}"
                else -> sourceName
            }
            genTip = when {
                empty -> emptyTip
                external -> "External Source: $genName ($deckLabel). Click to change the visual source."
                else -> "$genName ($deckLabel). Click to browse sources/presets, right-click for defaults."
            }
        }
    }

    private val deckStrings = Array(PerfRows.DECK_TAGS.size) { DeckStrings(PerfRows.DECK_TAGS[it]) }

    private fun stringsFor(tag: String): DeckStrings = deckStrings[PerfRows.DECK_TAGS.indexOf(tag).coerceAtLeast(0)]

    /** MIDI-mapping suffix + tooltip per learnable-button key, rebuilt only when that key's mapping changes. */
    private class NavTip { var mapping: Any? = null; var text = ""; var built = false }
    private val navTips = HashMap<String, NavTip>()
    private fun navTooltip(key: String, base: String, mapping: llm.slop.liquidlsd.midi.MidiControlMapping?): String {
        val tip = navTips.getOrPut(key) { NavTip() }
        if (!tip.built || tip.mapping != mapping) {
            tip.built = true
            tip.mapping = mapping
            val midiText = mapping?.let { if (it.channel == 0) " [CC ${it.cc}]" else " [Ch ${it.channel + 1} CC ${it.cc}]" } ?: ""
            tip.text = "$base$midiText\nRight-click for MIDI/OSC Learn."
        }
        return tip.text
    }

    /**
     * Prev/next queue button shared by Decks A/B (PlayQueue) and BG (BG queue): click triggers
     * [trigger] (or starts/cancels OSC learn in OSC map mode), draws the MIDI/OSC learn border, and
     * offers the right-click MIDI/OSC Learn/Clear menu. [btnId]/[ctxId] are the complete ImGui IDs.
     * Inline so [trigger] doesn't allocate a lambda per frame.
     */
    private inline fun learnableNavButton(
        session: SessionContext,
        mixer: Mixer,
        dl: imgui.ImDrawList,
        key: String,
        oscKey: String,
        glyph: String,
        btnId: String,
        ctxId: String,
        navBtnW: Float,
        ctrlH: Float,
        oscName: String,
        menuName: String,
        triggerLabel: String,
        tooltipBase: String,
        trigger: () -> Unit
    ) {
        val isMidiLearn = session.parametersState.isMidiTargetLearning(key)
        val isOscLearn = OscLearnState.isTargetLearning(oscKey)
        val midiMapping = session.midiMappingManager.getMappingForParameter(key)

        val x = ImGui.getCursorScreenPosX()
        val y = ImGui.getCursorScreenPosY()
        if (ImGui.button(btnId, navBtnW, ctrlH)) {
            if (OscMapModeState.active) {
                if (isOscLearn) OscLearnState.cancelLearn() else OscLearnState.startLearn(oscKey, 0f, 1f, oscName)
            } else {
                trigger()
            }
        }
        if (isMidiLearn) {
            dl.addRect(x - 1f, y - 1f, x + navBtnW + 1f, y + ctrlH + 1f, TangoPalette.learnBorder(), 3f, 0, 1.5f)
        } else if (isOscLearn) {
            TangoPalette.drawOscLearnPulseBorder(dl, x - 1f, y - 1f, x + navBtnW + 1f, y + ctrlH + 1f)
        }
        pushOpenDropdownPadding()
        if (ImGui.beginPopupContextItem(ctxId)) {
            pushOpenDropdownFont()
            ImGui.textDisabled("$oscName ($glyph)")
            ImGui.separator()
            if (ImGui.menuItem(triggerLabel)) {
                trigger()
            }
            ImGui.separator()
            if (isMidiLearn) {
                if (ImGui.menuItem("${Icons.ALERT} Cancel MIDI Learn")) {
                    session.parametersState.midiLearnTarget = null
                }
            } else {
                if (ImGui.menuItem("${Icons.SETTINGS} Learn MIDI ($menuName)")) {
                    session.parametersState.startMidiLearn(MidiLearnTarget.GlobalAction(key))
                }
            }
            if (midiMapping != null) {
                if (ImGui.menuItem("${Icons.TRASH} Clear MIDI Mapping")) {
                    session.midiMappingManager.removeMapping(key)
                    session.midiMappingManager.saveActiveProfile()
                }
            }
            if (isOscLearn) {
                if (ImGui.menuItem("${Icons.ALERT} Cancel OSC Learn")) {
                    OscLearnState.cancelLearn()
                }
            } else {
                if (ImGui.menuItem("${Icons.ACTIVITY} Learn OSC ($menuName)")) {
                    OscLearnState.startLearn(oscKey, 0f, 1f, oscName)
                }
            }
            val oscAddress = OscMappingManager.getAddressForParameter(oscKey)
            if (ImGui.menuItem("${Icons.TRASH} Clear OSC Mapping", null, false, oscAddress != null)) {
                OscMappingManager.removeMapping(oscAddress!!)
                OscMappingManager.saveActiveProfile()
            }
            popOpenDropdownFont()
            ImGui.endPopup()
        }
        popOpenDropdownPadding()
        itemTooltip(navTooltip(key, tooltipBase, midiMapping))
    }

    /**
     * Controls to the left of the knobs for Deck rows (Deck A, B, BG, PV) in two stacked rows:
     * - Row 1 (SRC): [SRC] knob-assign pill, kebab, generator/preset badge, Save,
     *   play queue / bg queue navigation (or preview button for PV), eject button.
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
        rowW: Float,
        pinned: String? = null
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
        val str = stringsFor(tag)
        str.ensureLabel(deckLabel)
        // A pinned row shows one half only and neither reads nor writes the shared per-deck mode.
        val isFx = if (pinned != null) pinned == "FX" else ctx.isDeckRowFx(tag, parametersState)
        val isSrc = !isFx
        val showSrc = pinned != "FX"
        val showFx = pinned != "SRC"
        val modeBtnW = DeckRowMetrics.MODE_PILL_W
        val iconBtnW = DeckRowMetrics.iconBtnW(ctrlH)

        // --- ROW 1 (SRC) -------------------------------------------------------------
        ImGui.setCursorScreenPos(startX, row1Y)
        ImGui.beginGroup()

        // 1. Shared [SRC] / [FX] toggle hitbox covering Row 1, gap, and Row 2
        val totalModeH = (row2Y + ctrlH) - row1Y
        ImGui.setNextItemAllowOverlap()
        // Pinned rows keep the toggle's footprint (layout stability) but have nothing to toggle.
        val toggleClicked = if (pinned == null) ImGui.invisibleButton(str.modeToggleId, modeBtnW, totalModeH)
                            else { ImGui.dummy(modeBtnW, totalModeH); false }
        val isModeHovered = pinned == null && ImGui.isItemHovered()
        if (toggleClicked) {
            if (isSrc) {
                llm.slop.liquidlsd.macro.MacroLearnState.onNavigateSection(deckLabel, "FX")
                parametersState.setDeckSubTab(deckLabel, "FX")
                llm.slop.liquidlsd.macro.FxMacroSync.syncFor(ctx.targetBankIdFor(tag), mixer)
            } else {
                llm.slop.liquidlsd.macro.MacroLearnState.onNavigateSection(deckLabel, "SRC")
                parametersState.setDeckSubTab(deckLabel, "SRC")
            }
        }
        if (isModeHovered) {
            ImGui.setMouseCursor(ImGuiMouseCursor.Hand)
        }
        if (pinned == null) itemTooltip(str.toggleTip)

        val mouseY = ImGui.getMousePosY()
        val midY = row1Y + ctrlH + (row2Y - (row1Y + ctrlH)) * 0.5f
        val isSrcHovered = isModeHovered && (mouseY <= midY)
        val isFxHovered = isModeHovered && (mouseY > midY)

        if (showSrc) PerformanceColors.drawTogglePill(dl, startX, row1Y, modeBtnW, ctrlH, "SRC", isSrc, isSrcHovered, session)
        if (showFx) PerformanceColors.drawTogglePill(dl, startX, row2Y, modeBtnW, ctrlH, "FX", isFx, isFxHovered, session)

        if (showSrc) {
            ImGui.sameLine(0f, gap)

            // 2. [⋮] Kebab -- source operations (Browse, Save As, defaults)
            if (ImGui.button(str.kebabId, DeckRowMetrics.KEBAB_W, ctrlH)) {
                ImGui.openPopup(str.badgeCtxId)
            }
            itemTooltip("Source operations (Browse, Save As, defaults).")

            ImGui.sameLine(0f, gap)

            // 3. Generator/preset badge -- click to Browse stock generators + saved presets
            val genBadgeW = DeckRowMetrics.genBadgeW(comboW)
            val activePreset = when {
                isDeckA -> session.presetManager.activePresetA
                isDeckB -> session.presetManager.activePresetB
                isDeckBG -> session.presetManager.activePresetBG
                else -> session.presetManager.activePresetPV
            }
            val isDirty = session.presetManager.isDeckDirty(deck, mixer)
            val isExternalVideo = deck.source is ExternalVideoSource
            str.updateBadge(deckLabel, deck.isEmpty, isExternalVideo, activePreset, isDirty, deck.source.displayName)
            val genName = str.genName
            val genBorderCol = TangoPalette.BADGE_BORDER.u32()
            val genBgCol = TangoPalette.BADGE_BG.u32()
            val genTextCol = TangoPalette.BADGE_TEXT.u32()
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
            if (ImGui.invisibleButton(str.badgeId, genBadgeW, ctrlH)) {
                parametersState.selectGen(canonicalBankId, deckLabel)
            }
            if (DockOutline.selects(parametersState, canonicalBankId, ParametersState.BrowseTarget.Gen)) DockOutline.drawAroundLastItem(canonicalBankId)
            val sourceId = GeneratorDefaults.sourceIdFor(deck.source)
            val hasUserDef = GeneratorDefaults.hasUserDefault(sourceId)

            pushOpenDropdownPadding()
            if (ImGui.beginPopupContextItem(str.badgeCtxId)) {
                pushOpenDropdownFont()
                if (ImGui.menuItem("Browse...")) {
                    parametersState.selectGen(canonicalBankId, deckLabel)
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
                        DeckSlot.of(deck, mixer)?.let { DeckOps.request(it, DeckChange.Source(deck.source, force = true)) }
                    }
                    if (hasUserDef && ImGui.menuItem("Reset to Factory Default")) {
                        GeneratorDefaults.deleteDefault(sourceId)
                        DeckSlot.of(deck, mixer)?.let { DeckOps.request(it, DeckChange.Source(deck.source, force = true)) }
                    }
                }
                popOpenDropdownFont()
                ImGui.endPopup()
            }
            popOpenDropdownPadding()
            if (ImGui.isItemHovered()) {
                ImGui.setMouseCursor(ImGuiMouseCursor.Hand)
                val hoverBorderCol = TangoPalette.BADGE_HOVER_BORDER.u32()
                dl.addRect(curX, curY, curX + genBadgeW, curY + ctrlH, hoverBorderCol, 4f, 0, 1.5f)
            }
            itemTooltip(str.genTip)

            ImGui.sameLine(0f, gap)

            // 4. Save button -- save-if-possible, else Save As modal
            val saveBtnBg = when {
                isDirty -> TangoPalette.u32(TangoPalette.ALERT.dark)
                else -> TangoPalette.BUTTON_SOFT_BG.u32()
            }
            ImGui.pushStyleColor(ImGuiCol.Button, saveBtnBg)
            session.uiTheme.withFont(UITheme.FontLevel.BODY) {
                if (ImGui.button(str.saveId, iconBtnW, ctrlH)) {
                    ctx.deckPresetController?.handleSaveDeck(mixer, deck, isDeckA, isSaveAs = false)
                }
            }
            ImGui.popStyleColor()
            itemTooltip(str.saveTip)

            ImGui.sameLine(0f, gap)

            // 5. PlayQueue / BG Queue navigation (or preview indicator for PV) -- status text removed
            val navBtnW = DeckRowMetrics.navBtnW(ctrlH)
            if (isDeckA || isDeckB) {
                learnableNavButton(session, mixer, dl, "Global/queuePrev", "Mixer/queuePrev", "◀", str.qPrevId, str.qPrevCtxId,
                    navBtnW, ctrlH, "PlayQueue Prev", "Queue Prev", "Trigger Previous",
                    "Advance to previous item in PlayQueue.") { session.playQueueManager.triggerPrevious(mixer) }

                ImGui.sameLine(0f, gap)

                learnableNavButton(session, mixer, dl, "Global/queueNext", "Mixer/queueNext", "▶", str.qNextId, str.qNextCtxId,
                    navBtnW, ctrlH, "PlayQueue Next", "Queue Next", "Trigger Next",
                    "Advance to next item in PlayQueue.") { session.playQueueManager.triggerNext(mixer) }
            } else if (isDeckBG) {
                learnableNavButton(session, mixer, dl, "Global/bgQueuePrev", "Mixer/bgQueuePrev", "◀", "◀##perf_bg_prev", "perf_bg_prev_ctx",
                    navBtnW, ctrlH, "BG Queue Prev", "BG Queue Prev", "Trigger Previous",
                    "Advance to previous item in BG Queue.") { session.bgQueueManager.triggerPrevious(mixer) }

                ImGui.sameLine(0f, gap)

                learnableNavButton(session, mixer, dl, "Global/bgQueueNext", "Mixer/bgQueueNext", "▶", "▶##perf_bg_next", "perf_bg_next_ctx",
                    navBtnW, ctrlH, "BG Queue Next", "BG Queue Next", "Trigger Next",
                    "Advance to next item in BG Queue.") { session.bgQueueManager.triggerNext(mixer) }
            } else {
                // Deck PV indicator / focus button
                val pvBadgeW = maxOf(DeckRowMetrics.queueNavW(ctrlH), DeckRowMetrics.PV_BADGE_W)
                val pvBtnBg = TangoPalette.PREVIEW_BG.u32()
                val pvBtnHov = TangoPalette.PREVIEW_HOVER.u32()
                ImGui.pushStyleColor(ImGuiCol.Button, pvBtnBg)
                ImGui.pushStyleColor(ImGuiCol.ButtonHovered, pvBtnHov)
                session.uiTheme.withFont(UITheme.FontLevel.CAPTION) {
                    if (ImGui.button("PREVIEW##perf_pv_badge", pvBadgeW, ctrlH)) {
                        parametersState.openFromMonitor(MacroEngine.DECK_PV, "Deck PV")
                    }
                }
                ImGui.popStyleColor(2)
                itemTooltip("Deck PV (Preview Deck)\nClick to open Deck PV in Edit.")
            }

            ImGui.sameLine(0f, gap)

            // 6. Eject Button [ EJECT ]
            val ejectBtnBg = TangoPalette.BUTTON_BG.u32()
            val ejectBtnHov = TangoPalette.EJECT_HOVER.u32()
            ImGui.pushStyleColor(ImGuiCol.Button, ejectBtnBg)
            ImGui.pushStyleColor(ImGuiCol.ButtonHovered, ejectBtnHov)
            session.uiTheme.withFont(UITheme.FontLevel.BODY) {
                if (ImGui.button(str.ejectId, iconBtnW, ctrlH)) {
                    UIManager.triggerDeckEject(deck, isDeckA = isDeckA, isDeckPV = isDeckPV)
                }
            }
            ImGui.popStyleColor(2)
            itemTooltip(str.ejectTip)

        }
        ImGui.endGroup()

        // --- ROW 2 (FX) --------------------------------------------------------------
        if (showFx) {
            ImGui.setCursorScreenPos(startX, row2Y)
            ImGui.beginGroup()

            // 1. Spacing for [FX] pill (rendered and hit-tested with [SRC] in Row 1 above)
            ImGui.dummy(modeBtnW, ctrlH)
            ImGui.sameLine(0f, gap)

            // 2. Dedicated FX chain controls
            val deckChain = deck.fxChain
            val targetBank = ctx.targetBankIdFor(tag)
            val fxCanonicalBankId = MacroEngine.deckBankIdFor(deck, mixer) ?: MacroEngine.DECK_A
            val targetRowW = DeckRowMetrics.row1Width(ctrlH, comboW)
            FxChainHeader.drawControls(
                session, mixer, deckChain, targetBank, str.fxTitle, ctrlH,
                maxW = targetRowW - modeBtnW - gap, deck = deck,
                actions = deckFxActions.getOrPut(tag) { DeckFxActions() }.also {
                    it.set(parametersState, ctx, mixer, deckLabel, tag, fxCanonicalBankId, targetBank, isFx, pinned != null)
                }
            )

            ImGui.endGroup()
        }
    }

    /**
     * Deck controls placed to the right of the knobs for Deck rows:
     * - Row 1 (above BYPASS): Randomize die button, randomizing modulators & base values across
     *   both Visual Source and Insert FX.
     * - Row 2: Deck FX chain bypass button.
     */
    fun drawDeckRowRightControls(
        session: SessionContext,
        mixer: Mixer,
        parametersState: ParametersState,
        deckLabel: String,
        deck: Deck,
        startX: Float,
        row1Y: Float,
        row2Y: Float,
        ctrlH: Float,
        width: Float = 56f,
        pinned: String? = null
    ) {
        val isDeckA = deckLabel.endsWith("A")
        val isDeckB = deckLabel.endsWith("B")
        val isDeckBG = deckLabel.endsWith("BG")
        val tag = when {
            isDeckA -> "A"
            isDeckB -> "B"
            isDeckBG -> "BG"
            else -> "PV"
        }

        val str = stringsFor(tag)
        str.ensureLabel(deckLabel)

        // Row 1: Randomize Die Button [ DICES ] (above BYPASS button; applies to both SRC and FX)
        if (session.uiTheme.randomizationEnabled) {
            ImGui.setCursorScreenPos(startX, row1Y)
            ImGui.beginGroup()
            val randBtnBg = TangoPalette.RANDOM_BG.u32()
            val randBtnHov = TangoPalette.RANDOM_HOVER.u32()
            ImGui.pushStyleColor(ImGuiCol.Button, randBtnBg)
            ImGui.pushStyleColor(ImGuiCol.ButtonHovered, randBtnHov)
            session.uiTheme.withFont(UITheme.FontLevel.BODY) {
                if (ImGui.button(str.randId, width, ctrlH)) {
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
            itemTooltip(str.randTip)
            ImGui.endGroup()
        }

        // Row 2: FX chain bypass button (a pinned SRC row has no FX half)
        if (pinned != "SRC") {
            ImGui.setCursorScreenPos(startX, row2Y)
            ImGui.beginGroup()
            FxChainHeader.drawBypassButton(session, deck.fxChain, tag, ctrlH, width)
            ImGui.endGroup()
        }
    }
}
