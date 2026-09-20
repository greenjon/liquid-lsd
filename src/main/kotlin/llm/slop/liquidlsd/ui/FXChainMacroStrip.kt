package llm.slop.liquidlsd.ui

import imgui.ImGui
import imgui.type.ImBoolean
import llm.slop.liquidlsd.rendering.FxChain
import llm.slop.liquidlsd.rendering.isf.FxMetaBinding
import llm.slop.liquidlsd.rendering.isf.ISFAutoBindEngine
import llm.slop.liquidlsd.rendering.isf.MetaCurve

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

    fun draw(
        session: llm.slop.liquidlsd.SessionContext,
        chain: FxChain,
        chainPrefix: String,
        state: ParametersState,
        onPushUndo: () -> Unit
    ) {
        session.uiTheme.withFont(UITheme.FontLevel.CAPTION) { ImGui.textDisabled("CHAIN MACRO") }
        ImGui.spacing()

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

        val focusedIndex = state.focusedSlotIndexFor(chainPrefix)
        val focusedFx = focusedIndex?.let { chain.slots.getOrNull(it) }

        ImGui.spacing()
        if (focusedIndex != null && focusedFx != null) {
            drawFocusedMode(session, chain, chainPrefix, focusedIndex, focusedFx, state, onPushUndo)
        } else {
            drawGroupMode(session, chain, chainPrefix, state, onPushUndo)
        }
        ImGui.spacing()
    }

    private fun drawGroupMode(
        session: llm.slop.liquidlsd.SessionContext,
        chain: FxChain,
        chainPrefix: String,
        state: ParametersState,
        onPushUndo: () -> Unit
    ) {
        for (i in 0 until FxChain.SLOT_COUNT) {
            val fx = chain.slots[i]
            val slotLabel = fx?.displayName ?: "Slot ${i + 1}"
            session.uiTheme.withFont(UITheme.FontLevel.CAPTION) { ImGui.textDisabled("$slotLabel") }
            ImGui.sameLine()

            linkBufs[i].set(chain.slotSuperKnobLink[i])
            if (ImGui.checkbox("Link##fx_link_${chainPrefix}_$i", linkBufs[i])) {
                chain.setSlotLinked(i, linkBufs[i].get())
            }
            itemTooltip("Follow the Chain Super Knob (soft-takeover: won't jump until the Super Knob crosses this Metaknob's current value).")

            if (fx != null) {
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
            } else {
                ImGui.spacing()
            }
        }
    }

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

    /** Right-click on a slot's Metaknob to quickly rebind it to a different parameter (or the safety-net Dry/Wet). */
    private fun drawRebindContextMenu(fx: llm.slop.liquidlsd.rendering.isf.ISFFilter, chainPrefix: String, slotIndex: Int) {
        val popupId = "fx_meta_rebind_${chainPrefix}_$slotIndex"
        if (ImGui.beginPopupContextItem(popupId)) {
            ImGui.textDisabled("Rebind Metaknob")
            ImGui.separator()
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
            ImGui.endPopup()
        }
    }
}
