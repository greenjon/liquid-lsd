package llm.slop.liquidlsd.ui

import imgui.ImGui
import imgui.type.ImBoolean
import llm.slop.liquidlsd.rendering.FxChain
import llm.slop.liquidlsd.rendering.Mixer
import llm.slop.liquidlsd.rendering.isf.FxMetaBinding
import llm.slop.liquidlsd.rendering.isf.ISFAutoBindEngine
import llm.slop.liquidlsd.rendering.isf.MetaCurve
import llm.slop.liquidlsd.rendering.isf.MetaLinkMode

/**
 * Traktor/Mixxx-style performance strip for one [FxChain]: a Chain Super Knob that drives the
 * 3 slots' own Metaknobs when linked, plus a "Single FX Focus Mode" that swaps the 3 knobs to
 * the focused slot's own top parameters instead. Drawn above the detailed per-slot accordion in
 * [ParametersTabs.drawFxBankGroupContent].
 *
 * Hardware note: each control here exposes the same "$chainPrefix/..." path already registered
 * by [FxChain.getParameterPaths]/[llm.slop.liquidlsd.rendering.isf.ISFFilter.getParameterPaths],
 * so right-click MIDI/OSC Learn (via [CustomRangeSlider.drawCompactSlider]'s `paramKey`) binds
 * directly to the real chain/slot Metaknob -- consistent with how every other control in this
 * app exposes hardware learn. A physical CC bound to a slot's Metaknob in group mode is a
 * different path than that slot's focused-mode parameter rows, so it goes unbound (not
 * retargeted) when focus toggles; a fixed-path retarget scheme was considered but would need new
 * MacroEngine/MacroBank plumbing this pass didn't build -- flagged for a follow-up if the
 * always-fixed-physical-knob behavior turns out to matter in practice.
 */
object FXChainMacroStrip {

    private val linkBufs = Array(FxChain.SLOT_COUNT) { ImBoolean(true) }

    /**
     * Re-runs [llm.slop.liquidlsd.macro.FxMacroSync] after a Link checkbox toggle: linking a slot
     * must clear that slot's Performance Console Metaknob binding (both would otherwise write
     * [llm.slop.liquidlsd.rendering.isf.ISFFilter.metaKnob]'s base value every frame), and
     * unlinking must restore it. [chainPrefix] is "$bankLabel/C$chainNum" (e.g. "FX1/C1").
     */
    private fun resyncMacroKnobs(chainPrefix: String, chain: FxChain) {
        val bankLabel = chainPrefix.substringBefore("/C")
        val chainIndex = chainPrefix.substringAfterLast("/C").toIntOrNull()?.minus(1) ?: 0
        val bankId = llm.slop.liquidlsd.macro.MacroEngine.canonicalIdForDeckLabel(bankLabel)
        llm.slop.liquidlsd.macro.FxMacroSync.syncChain(bankId, bankLabel, chain, chainIndex)
    }

    /**
     * Grid-rendering context supplied by the Parameters Panel so the Super Knob and each slot's
     * Metaknob render as full [ParametersRenderer.drawParamRow] rows (gaining Seq/LFO/Audio/MIDI
     * modulation columns) instead of the compact MIDI-learn-only slider. Left null by narrower
     * callers -- e.g. the Column-3 "MACROS" performance strip in [MacroPanel] -- which don't have
     * grid columns to spare and keep the original compact rendering.
     */
    class GridContext(
        val mixer: Mixer,
        val labelColW: Float,
        val gridStartX: Float,
        val getCvColumns: () -> List<String>,
        val getColumnOffset: (String) -> Float,
        val getCvColor: (String, Float) -> Int
    )

    /** Returns the row index after any grid rows drawn this call (unchanged from [startRow] when [grid] is null). */
    fun draw(
        session: llm.slop.liquidlsd.SessionContext,
        chain: FxChain,
        chainPrefix: String,
        state: ParametersState,
        grid: GridContext? = null,
        startRow: Int = 0,
        onPushUndo: () -> Unit
    ): Int {
        session.uiTheme.withFont(UITheme.FontLevel.CAPTION) { ImGui.textDisabled("CHAIN MACRO") }
        ImGui.spacing()

        var row = startRow
        if (grid != null) {
            ParametersRenderer.drawParamRow(
                session, "Super Knob", "$chainPrefix/Super", chain.superKnob, state,
                grid.labelColW, grid.mixer, grid.gridStartX, row++,
                grid.getCvColumns, grid.getColumnOffset, grid.getCvColor, onPushUndo
            )
        } else {
            ImGui.beginGroup()
            CustomRangeSlider.drawCompactSlider(
                session = session,
                label = "Super Knob",
                currentValue = chain.superKnob.baseValue,
                minLimit = 0f,
                maxLimit = 1f,
                idPrefix = "fx_super_$chainPrefix",
                paramKey = "$chainPrefix/Super",
                onValueChanged = { chain.superKnob.set(it); onPushUndo() }
            )
            ImGui.endGroup()
            itemTooltip("Sweeps every linked slot's Metaknob together. Right-click to bind hardware MIDI/OSC.")
        }

        val focusedIndex = state.focusedSlotIndexFor(chainPrefix)
        val focusedFx = focusedIndex?.let { chain.slots.getOrNull(it) }

        ImGui.spacing()
        if (focusedIndex != null && focusedFx != null) {
            drawFocusedMode(session, chain, chainPrefix, focusedIndex, focusedFx, state, onPushUndo)
        } else {
            row = drawGroupMode(session, chain, chainPrefix, state, grid, row, onPushUndo)
        }
        ImGui.spacing()
        return row
    }

    private fun drawGroupMode(
        session: llm.slop.liquidlsd.SessionContext,
        chain: FxChain,
        chainPrefix: String,
        state: ParametersState,
        grid: GridContext?,
        startRow: Int,
        onPushUndo: () -> Unit
    ): Int {
        var row = startRow
        for (i in 0 until FxChain.SLOT_COUNT) {
            val fx = chain.slots[i]

            if (fx == null) {
                session.uiTheme.withFont(UITheme.FontLevel.CAPTION) { ImGui.textDisabled("Slot ${i + 1}") }
                ImGui.sameLine()
                linkBufs[i].set(chain.slotSuperKnobLink[i])
                if (ImGui.checkbox("Link##fx_link_${chainPrefix}_$i", linkBufs[i])) {
                    chain.setSlotLinked(i, linkBufs[i].get())
                    resyncMacroKnobs(chainPrefix, chain)
                }
                itemTooltip("Follow the Chain Super Knob (soft-takeover: won't jump until the Super Knob crosses this Metaknob's current value).")
                ImGui.spacing()
                continue
            }

            if (grid != null) {
                // Link sits directly before the row's (truncated) name, per request; Focus and
                // Rebind move into the row's own right-click/kebab menu via extraMenuItems --
                // drawParamRow's label already claims right-click for its standard row menu, and
                // a menu entry is easier to find than text that can get clipped by a long name.
                linkBufs[i].set(chain.slotSuperKnobLink[i])
                if (ImGui.checkbox("##fx_link_${chainPrefix}_$i", linkBufs[i])) {
                    chain.setSlotLinked(i, linkBufs[i].get())
                    resyncMacroKnobs(chainPrefix, chain)
                }
                itemTooltip("Follow the Chain Super Knob (soft-takeover: won't jump until the Super Knob crosses this Metaknob's current value).")
                ImGui.sameLine(0f, 4f)

                val isFocused = state.focusedSlotIndexFor(chainPrefix) == i
                ParametersRenderer.drawParamRow(
                    session, truncateLabel(fx.displayName), "$chainPrefix/FX${i + 1}/Meta", fx.metaKnob, state,
                    grid.labelColW, grid.mixer, grid.gridStartX, row++,
                    grid.getCvColumns, grid.getColumnOffset, grid.getCvColor, onPushUndo,
                    extraMenuItems = {
                        if (ImGui.menuItem(if (isFocused) "Exit Focus Mode" else "Focus This Slot…")) {
                            state.toggleFxFocus(chainPrefix, i)
                        }
                        ImGui.separator()
                        ImGui.textDisabled("Rebind Metaknob To…")
                        drawRebindMenuItems(fx)
                    },
                    descriptionOverride = fx.header.DESCRIPTION?.takeIf { it.isNotBlank() }
                )
            } else {
                session.uiTheme.withFont(UITheme.FontLevel.CAPTION) { ImGui.textDisabled(truncateLabel(fx.displayName)) }
                fx.header.DESCRIPTION?.takeIf { it.isNotBlank() }?.let { itemTooltip(it) }
                ImGui.sameLine()

                linkBufs[i].set(chain.slotSuperKnobLink[i])
                if (ImGui.checkbox("Link##fx_link_${chainPrefix}_$i", linkBufs[i])) {
                    chain.setSlotLinked(i, linkBufs[i].get())
                    resyncMacroKnobs(chainPrefix, chain)
                }
                itemTooltip("Follow the Chain Super Knob (soft-takeover: won't jump until the Super Knob crosses this Metaknob's current value).")

                ImGui.sameLine()
                if (ImGui.smallButton("Focus##fx_focus_${chainPrefix}_$i")) {
                    state.toggleFxFocus(chainPrefix, i)
                }
                itemTooltip("Focus Slot ${i + 1}: swap these 3 knobs for its own top parameters.")

                ImGui.beginGroup()
                CustomRangeSlider.drawCompactSlider(
                    session = session,
                    label = "Meta",
                    currentValue = fx.metaKnob.baseValue,
                    minLimit = 0f,
                    maxLimit = 1f,
                    idPrefix = "fx_meta_${chainPrefix}_$i",
                    paramKey = "$chainPrefix/FX${i + 1}/Meta",
                    onValueChanged = { fx.metaKnob.set(it); onPushUndo() }
                )
                ImGui.endGroup()
                itemTooltip("${fx.displayName}'s macro control (auto-bound to ${fx.metaBinding.targetParamName ?: "Dry/Wet"}). Right-click to rebind or bind hardware MIDI/OSC.")
                drawRebindContextMenu(fx, chainPrefix, i)
            }
        }
        return row
    }

    private fun truncateLabel(name: String, maxChars: Int = 16): String =
        if (name.length <= maxChars) name else name.take(maxChars - 1).trimEnd() + "…"

    private fun drawFocusedMode(
        session: llm.slop.liquidlsd.SessionContext,
        chain: FxChain,
        chainPrefix: String,
        slotIndex: Int,
        fx: llm.slop.liquidlsd.rendering.isf.ISFFilter,
        state: ParametersState,
        onPushUndo: () -> Unit
    ) {
        session.uiTheme.withFont(UITheme.FontLevel.CAPTION) {
            ImGui.textDisabled("FOCUSED: ${fx.displayName} (Slot ${slotIndex + 1})")
        }
        ImGui.sameLine()
        if (ImGui.smallButton("${Icons.X} Group Mode##fx_unfocus_$chainPrefix")) {
            state.toggleFxFocus(chainPrefix, slotIndex)
        }

        val topInputs = fx.header.INPUTS.filter { !it.TYPE.equals("image", ignoreCase = true) }.take(3)
        if (topInputs.isEmpty()) {
            session.uiTheme.withFont(UITheme.FontLevel.CAPTION) { ImGui.textDisabled("(No adjustable parameters)") }
            return
        }
        for ((idx, input) in topInputs.withIndex()) {
            val param = fx.parameters[input.NAME] ?: continue
            val existingBinding = fx.getBindingForParam(input.NAME)

            LinkModeButton.drawMetaLink(
                id = "fx_focus_link_${chainPrefix}_${slotIndex}_$idx",
                mode = existingBinding?.linkMode,
                inverted = existingBinding?.invert ?: false,
                onCycleMode = {
                    val nextMode = when (existingBinding?.linkMode) {
                        null -> MetaLinkMode.FULL
                        MetaLinkMode.FULL -> MetaLinkMode.FIRST_HALF
                        MetaLinkMode.FIRST_HALF -> MetaLinkMode.SECOND_HALF
                        MetaLinkMode.SECOND_HALF -> MetaLinkMode.TRIANGLE
                        MetaLinkMode.TRIANGLE -> MetaLinkMode.BIPOLAR
                        MetaLinkMode.BIPOLAR -> null
                    }
                    fx.setParamLink(input.NAME, nextMode, existingBinding?.invert ?: false)
                    onPushUndo()
                },
                onSelectMode = { newMode ->
                    fx.setParamLink(input.NAME, newMode, existingBinding?.invert ?: false)
                    onPushUndo()
                },
                onToggleInvert = {
                    val currMode = existingBinding?.linkMode ?: MetaLinkMode.FULL
                    val currInv = existingBinding?.invert ?: false
                    fx.setParamLink(input.NAME, currMode, !currInv)
                    onPushUndo()
                }
            )
            ImGui.sameLine(0f, 6f)
            ImGui.beginGroup()
            CustomRangeSlider.drawCompactSlider(
                session = session,
                label = input.LABEL ?: input.NAME,
                currentValue = param.baseValue,
                minLimit = param.minClamp,
                maxLimit = param.maxClamp,
                idPrefix = "fx_focus_${chainPrefix}_${slotIndex}_$idx",
                paramKey = "$chainPrefix/FX${slotIndex + 1}/${input.NAME}",
                onValueChanged = { param.set(it); onPushUndo() }
            )
            ImGui.endGroup()
            itemTooltip("${fx.displayName}'s own ${input.LABEL ?: input.NAME} parameter (Focus Mode). Right-click to bind hardware MIDI/OSC.")
        }
    }

    /** Menu items to rebind a slot's Metaknob to a different parameter (or the safety-net Dry/Wet); shared by both the compact-mode right-click popup and the grid-mode row menu. */
    private fun drawRebindMenuItems(fx: llm.slop.liquidlsd.rendering.isf.ISFFilter) {
        val floatInputs = fx.header.INPUTS.filter { it.TYPE.equals("float", ignoreCase = true) }
        for (input in floatInputs) {
            val param = fx.parameters[input.NAME] ?: continue
            if (ImGui.menuItem(input.LABEL ?: input.NAME)) {
                fx.rebindMetaKnob(FxMetaBinding(input.NAME, param.minClamp, param.maxClamp, MetaCurve.LINEAR))
            }
        }
        ImGui.separator()
        if (ImGui.menuItem("Bind to Dry/Wet (safety net)")) {
            fx.rebindMetaKnob(FxMetaBinding.DRY_WET_SAFETY_NET)
        }
        if (fx.contentHash != null && ImGui.menuItem("Reset to Auto-Bind Default")) {
            ISFAutoBindEngine.deleteOverride(fx.contentHash)
            fx.applyMetaBindingFromPreset(ISFAutoBindEngine.resolveBinding(fx))
        }
    }

    /** Right-click on a slot's compact-mode Metaknob to quickly rebind it to a different parameter. */
    private fun drawRebindContextMenu(fx: llm.slop.liquidlsd.rendering.isf.ISFFilter, chainPrefix: String, slotIndex: Int) {
        val popupId = "fx_meta_rebind_${chainPrefix}_$slotIndex"
        if (ImGui.beginPopupContextItem(popupId)) {
            ImGui.textDisabled("Rebind Metaknob")
            ImGui.separator()
            drawRebindMenuItems(fx)
            ImGui.endPopup()
        }
    }
}
