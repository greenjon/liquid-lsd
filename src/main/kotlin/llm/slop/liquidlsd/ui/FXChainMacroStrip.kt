package llm.slop.liquidlsd.ui

import imgui.ImGui
import imgui.type.ImBoolean
import llm.slop.liquidlsd.rendering.FxChain
import llm.slop.liquidlsd.rendering.Mixer
import llm.slop.liquidlsd.macro.FxMacroSync
import llm.slop.liquidlsd.macro.MacroEngine
import llm.slop.liquidlsd.rendering.isf.ISFAutoBindEngine
import llm.slop.liquidlsd.rendering.isf.toBinding

/**
 * The Edit bay's chain-macro block for one [FxChain]: the Chain Super Knob, then one row per slot Metaknob with its
 * Super Knob link checkbox, drawn as full [ParametersRenderer.drawParamRow] rows (modulation columns, MIDI/OSC learn)
 * above the per-slot accordion in [ParametersTabs.drawFxChainContent].
 *
 * Focus is the chain's own [FxChain.focusedSlot] (through [FxMacroSync]), the same state the Perform rows and the
 * hardware use: focusing a slot here retargets the knobs. The block itself keeps showing the Metaknob rows in focus
 * mode and adds a caption naming the parameters on knobs 2-4. A slot's Metaknob targets are edited in the slot's
 * "Metaknob targets" block and on the Edit-row strip, not here.
 */
object FXChainMacroStrip {

    private val linkBufs = Array(FxChain.SLOT_COUNT) { ImBoolean(true) }

    /**
     * Re-runs [llm.slop.liquidlsd.macro.FxMacroSync] after a link toggle: linking a slot
     * must clear that slot's Performance Console Metaknob binding (both would otherwise write
     * [llm.slop.liquidlsd.rendering.isf.ISFFilter.metaKnob]'s base value every frame), and
     * unlinking must restore it. [chainPrefix] is "$chainLabel/FX" (e.g. "Deck A/FX", "Master/FX").
     */
    internal fun resyncMacroKnobs(chainPrefix: String, chain: FxChain) {
        val chainLabel = chainPrefix.removeSuffix("/FX")
        val bankId = MacroEngine.canonicalIdForDeckLabel(chainPrefix)
        FxMacroSync.syncChain(bankId, chainLabel, chain)
    }

    /** Grid-rendering context supplied by the Parameters Panel, so the Super Knob and each Metaknob render as full parameter rows. */
    class GridContext(
        val mixer: Mixer,
        val labelColW: Float,
        val gridStartX: Float,
        val getCvColumns: () -> List<String>,
        val getColumnOffset: (String) -> Float,
        val getCvColor: (String, Float) -> Int
    )

    /** Returns the row index after the grid rows drawn this call. */
    fun draw(
        session: llm.slop.liquidlsd.SessionContext,
        chain: FxChain,
        chainPrefix: String,
        state: ParametersState,
        grid: GridContext,
        startRow: Int = 0,
        onPushUndo: () -> Unit
    ): Int {
        val rowStartX = ImGui.getCursorPosX()
        val bankId = MacroEngine.canonicalIdForDeckLabel(chainPrefix)
        val collapsed = chainPrefix in state.fxMacroCollapsed
        session.uiTheme.withFont(UITheme.FontLevel.BODY) {
            if (ImGui.smallButton("${if (collapsed) Icons.CHEVRON_DOWN else Icons.CHEVRON_UP}##fx_macro_collapse_$chainPrefix")) {
                if (collapsed) state.fxMacroCollapsed.remove(chainPrefix) else state.fxMacroCollapsed.add(chainPrefix)
            }
        }
        itemTooltip(if (collapsed) "Show the Super Knob and Metaknobs." else "Hide the Super Knob and Metaknobs.")
        ImGui.sameLine()
        session.uiTheme.withFont(UITheme.FontLevel.CAPTION) { ImGui.textDisabled("SUPER KNOB & METAKNOBS") }
        ImGui.sameLine()
        val allLinked = chain.areAllSlotsLinked()
        val linkLabel = when {
            allLinked -> "Unlink all"
            chain.areAllSlotsUnlinked() -> "Link all"
            else -> "Link all (${chain.linkedSlotCount()}/${chain.filledSlotCount()})"
        }
        if (ImGui.smallButton("$linkLabel##fx_chain_link_$chainPrefix")) {
            chain.toggleAllSlotsLinked()
            resyncMacroKnobs(chainPrefix, chain)
        }
        itemTooltip(
            if (allLinked) "All FX slots follow the Super Knob. Click to unlink all."
            else "Link every FX slot to the Super Knob (soft-takeover: no jumps until the Super Knob crosses each Metaknob's value)."
        )
        ImGui.spacing()
        if (collapsed) {
            ImGui.setCursorPosX(rowStartX)
            return startRow
        }

        val focused = chain.focusedSlot
        val focusedFx = focused?.let { chain.slots.getOrNull(it) }
        if (focused != null && focusedFx != null) {
            ImGui.setCursorPosX(rowStartX)
            drawFocusCaption(session, chain, bankId, grid.mixer, focused, focusedFx)
        }

        var row = startRow
        ImGui.setCursorPosX(rowStartX)
        ParametersRenderer.drawParamRow(
            session, "Super Knob", "$chainPrefix/Super", chain.superKnob, state,
            grid.labelColW, grid.mixer, grid.gridStartX, row++,
            grid.getCvColumns, grid.getColumnOffset, grid.getCvColor, onPushUndo,
            rowStartX = rowStartX
        )
        ImGui.spacing()
        row = drawSlotRows(session, chain, chainPrefix, bankId, state, grid, row, onPushUndo)
        ImGui.spacing()
        ImGui.setCursorPosX(rowStartX)
        return row
    }

    /** Focus mode: which effect has the knobs, what knobs 2-4 hold on this page, page arrows and the way back to group mode. */
    private fun drawFocusCaption(
        session: llm.slop.liquidlsd.SessionContext,
        chain: FxChain,
        bankId: String,
        mixer: Mixer,
        slotIndex: Int,
        fx: llm.slop.liquidlsd.rendering.isf.ISFFilter
    ) {
        val pages = chain.totalParamPages(slotIndex)
        val onPage = fx.focusParameters.drop(chain.focusParamPage * 3).take(3).joinToString(", ") { ParamLabels.of(fx.header, it.key) }
        session.uiTheme.withFont(UITheme.FontLevel.CAPTION) {
            ImGui.textDisabled("FOCUSED: ${fx.displayName} (Slot ${slotIndex + 1}). Knobs 2-4: ${onPage.ifEmpty { "none" }}")
        }
        if (pages > 1) {
            ImGui.sameLine()
            if (ImGui.smallButton("◀##fx_page_prev_$bankId")) FxMacroSync.stepParamPage(bankId, mixer, -1)
            ImGui.sameLine()
            ImGui.textDisabled("P${chain.focusParamPage + 1}/$pages")
            ImGui.sameLine()
            if (ImGui.smallButton("▶##fx_page_next_$bankId")) FxMacroSync.stepParamPage(bankId, mixer, 1)
        }
        ImGui.sameLine()
        if (ImGui.smallButton("${Icons.X} Group Mode##fx_unfocus_$bankId")) FxMacroSync.focusSlot(bankId, mixer, null)
        itemTooltip("Leave focus: knob 1 goes back to the Super Knob and knobs 2-4 to the slot Metaknobs.")
    }

    private fun drawSlotRows(
        session: llm.slop.liquidlsd.SessionContext,
        chain: FxChain,
        chainPrefix: String,
        bankId: String,
        state: ParametersState,
        grid: GridContext,
        startRow: Int,
        onPushUndo: () -> Unit
    ): Int {
        var row = startRow
        val rowStartX = ImGui.getCursorPosX()
        for (i in 0 until FxChain.SLOT_COUNT) {
            ImGui.setCursorPosX(rowStartX)
            val fx = chain.slots[i]

            if (fx == null) {
                session.uiTheme.withFont(UITheme.FontLevel.CAPTION) { ImGui.textDisabled("Slot ${i + 1}") }
                ImGui.sameLine()
                drawLinkCheckbox(chain, chainPrefix, i, "Link")
                ImGui.spacing()
                continue
            }

            // Link sits directly before the row's (truncated) name; Focus and the default actions live in the
            // row's right-click/kebab menu (drawParamRow's label already claims right-click for its row menu).
            drawLinkCheckbox(chain, chainPrefix, i, "")
            ImGui.sameLine(0f, 4f)

            val isFocused = chain.focusedSlot == i
            ParametersRenderer.drawParamRow(
                session, truncateLabel(fx.displayName), "$chainPrefix/FX${i + 1}/Meta", fx.metaKnob, state,
                grid.labelColW, grid.mixer, grid.gridStartX, row++,
                grid.getCvColumns, grid.getColumnOffset, grid.getCvColor, onPushUndo,
                extraMenuItems = {
                    if (ImGui.menuItem(if (isFocused) "Exit Focus Mode" else "Focus This Slot")) {
                        FxMacroSync.focusSlot(bankId, grid.mixer, if (isFocused) null else i)
                    }
                    drawDefaultMenuItems(fx) { change -> FxMetaEdit.apply(state, chainPrefix, chain, fx, change = change) }
                },
                descriptionOverride = fx.header.DESCRIPTION?.takeIf { it.isNotBlank() },
                rowStartX = rowStartX
            )
        }
        ImGui.setCursorPosX(rowStartX)
        return row
    }

    private fun drawLinkCheckbox(chain: FxChain, chainPrefix: String, slotIndex: Int, label: String) {
        linkBufs[slotIndex].set(chain.slotSuperKnobLink[slotIndex])
        if (ImGui.checkbox("$label##fx_link_${chainPrefix}_$slotIndex", linkBufs[slotIndex])) {
            chain.setSlotLinked(slotIndex, linkBufs[slotIndex].get())
            resyncMacroKnobs(chainPrefix, chain)
        }
        itemTooltip("Follow the Chain Super Knob (soft-takeover: won't jump until the Super Knob crosses this Metaknob's current value).\nMoving the Metaknob yourself lets go of the Super Knob until it reaches the Metaknob again.")
    }

    private fun truncateLabel(name: String, maxChars: Int = 16): String =
        if (name.length <= maxChars) name else name.take(maxChars - 1).trimEnd() + "…"

    /** Save / remove the effect's own default (Metaknob targets, knob order, parameters and modulators, keyed by shader content). */
    private fun drawDefaultMenuItems(fx: llm.slop.liquidlsd.rendering.isf.ISFFilter, edit: ((llm.slop.liquidlsd.rendering.isf.ISFFilter) -> Unit) -> Unit) {
        if (fx.contentHash == null) return
        ImGui.separator()
        if (ImGui.menuItem("Save as Default for ${fx.displayName}")) {
            ISFAutoBindEngine.saveFilterDefault(fx)
        }
        itemTooltip("New copies of ${fx.displayName} start with this slot's Metaknob targets, knob order, parameter values and modulators (MIDI mappings are not saved).\nSlots already in chains keep their own.")
        if (ISFAutoBindEngine.hasFilterDefault(fx)) {
            if (ImGui.menuItem("Remove Saved Default for ${fx.displayName}")) {
                ISFAutoBindEngine.deleteFilterDefault(fx)
                val defaultDto = ISFAutoBindEngine.resolveDefault(fx)
                edit { f ->
                    f.applyMetaBindingsFromPreset(defaultDto.metaBindings.map { it.toBinding() })
                    f.applyKnobLayoutFromPreset(defaultDto.knobLayout)
                    defaultDto.applyParametersTo(f)
                }
            }
            itemTooltip("New copies of ${fx.displayName} go back to the built-in Metaknob targets.\nThis slot is reset to them too; other slots keep their own.")
        }
    }
}
