package llm.slop.liquidlsd.ui

import llm.slop.liquidlsd.presets.DeckSlot

import llm.slop.liquidlsd.presets.DeckOps

import llm.slop.liquidlsd.presets.DeckChange

import imgui.ImGui
import llm.slop.liquidlsd.SessionContext
import llm.slop.liquidlsd.macro.MacroEngine
import llm.slop.liquidlsd.presets.FxOps
import llm.slop.liquidlsd.rendering.FxChain
import llm.slop.liquidlsd.rendering.Mixer
import llm.slop.liquidlsd.presets.TransitionOps
import llm.slop.liquidlsd.ui.browser.ApplyTarget
import llm.slop.liquidlsd.ui.browser.BrowseCatalogs
import llm.slop.liquidlsd.ui.browser.BrowseKind
import llm.slop.liquidlsd.ui.browser.BrowseScope
import llm.slop.liquidlsd.ui.browser.BrowserDock
import llm.slop.liquidlsd.ui.browser.BrowserDock.DockBinding
import java.io.File

/**
 * Inline "Browse" content for the Performance row bay: picking a deck's generator, a saved whole
 * FX chain, one FX chain slot's effect, or the active transition -- everything that used to be
 * the modal pickers that the unified [BrowserPane] replaced. Draws the same [BrowserDock] as the Library, bound to the row. Lives beside
 * [PerformanceDeepEditBay]'s Params content; the bay's tab row (Edit | SRC | Chain | FX1-3) picks which
 * one shows. Picking something applies it immediately (Ctrl+Z undoes it) and leaves the list open, so
 * trying several generators/effects/chains in a row doesn't mean reopening anything.
 */
internal class PerformanceBrowseBay(private val ctx: PerformanceUiContext) {

    /**
     * What a row's own Browse shows right now: the deck's sub-tab (SRC | FX) or Master's (TRANS | FX) picks the kind, and the
     * dock selection supplies the slot while it is for this row. Null for a Master row on MIX (nothing to browse).
     */
    fun targetForRow(parametersState: ParametersState, moduleId: String): ParametersState.BrowseTarget? {
        val remembered = parametersState.browseTargetFor(moduleId)
        val deckLabel = ctx.deckLabelForModuleId(moduleId)
        return when {
            deckLabel != null ->
                if (parametersState.getActiveSubTab(deckLabel) == "FX") remembered as? ParametersState.BrowseTarget.FxChain ?: ParametersState.BrowseTarget.FxChain()
                else ParametersState.BrowseTarget.Gen
            moduleId == MacroEngine.MASTER -> when (parametersState.activeMixerSubTab) {
                "TRANS" -> ParametersState.BrowseTarget.Transition
                "FX" -> remembered as? ParametersState.BrowseTarget.FxChain ?: ParametersState.BrowseTarget.FxChain()
                else -> null
            }
            else -> null
        }
    }

    fun draw(session: SessionContext, mixer: Mixer, parametersState: ParametersState, moduleId: String) {
        val target = targetForRow(parametersState, moduleId)
        // MIX (CTRL) has no Browse target -- e.g. the user flipped the row's pill back to MIX while Browse was open. Fall back to Params.
        // In the Edit bay, ending the binding (chip ✕, double-click commit) leaves Browse for Params; the row stays open.
        val binding = target?.let { bindingFor(session, mixer, ParametersState.DockSelection(moduleId, it)) { parametersState.openParams(moduleId) } }
        if (binding == null) { parametersState.openParams(moduleId); return }
        drawDock(session, mixer, parametersState, binding)
    }

    /** The dock binding for [selection] -- the apply-target (with its Save / Clear buttons) for a deck source, an FX chain or slot, or the transition. */
    fun bindingFor(session: SessionContext, mixer: Mixer, selection: ParametersState.DockSelection, onEnd: (() -> Unit)? = null): DockBinding? {
        val moduleId = selection.moduleId
        val deckLabel = ctx.deckLabelForModuleId(moduleId)
        val binding = when (val t = selection.target) {
            is ParametersState.BrowseTarget.Gen ->
                deckLabel?.let { genBinding(session, mixer, ctx.deckForLabel(mixer, it), it) }
            is ParametersState.BrowseTarget.Transition -> transitionBinding(mixer)
            is ParametersState.BrowseTarget.FxChain -> when {
                deckLabel != null -> fxBinding(session, ctx.deckForLabel(mixer, deckLabel).fxChain, deckLabel, t.slotIndex)
                moduleId == MacroEngine.MASTER -> fxBinding(session, mixer.masterFxChain, "Master", t.slotIndex)
                else -> null
            }
        }
        if (binding == null) return null
        val accent = if (selection.target is ParametersState.BrowseTarget.Transition) PerformanceColors.COLOR_TRANS else accentFor(moduleId)
        if (onEnd == null) return DockBinding(binding.target, binding.label, binding.actions, accent)
        // The dock can end the binding ([onEnd]): a double-click commits it, and the chip gets its close button.
        val target = binding.target
        val committing = ApplyTarget(
            target.kind, target.contextKey, target.defaultScope, target.accepts, target.isApplied, target.apply, target.clear,
            onCommit = onEnd
        )
        return DockBinding(committing, binding.label, binding.actions, accent, onClose = onEnd)
    }

    private fun accentFor(moduleId: String): FloatArray = when (moduleId) {
        MacroEngine.DECK_A -> PerformanceColors.COLOR_DECK_A
        MacroEngine.DECK_B -> PerformanceColors.COLOR_DECK_B
        MacroEngine.DECK_BG -> PerformanceColors.COLOR_DECK_BG
        MacroEngine.DECK_PV -> PerformanceColors.COLOR_DECK_PV
        else -> PerformanceColors.COLOR_MASTER
    }

    private fun genBinding(session: SessionContext, mixer: Mixer, deck: llm.slop.liquidlsd.rendering.Deck, deckLabel: String): DockBinding {
        val parametersState = session.parametersState
        val applyId = { id: String -> DeckSourcePicker.applyPickedSourceId(session, parametersState, mixer, deck, deckLabel, id, ctx.deckPresetController) }
        val target = ApplyTarget(
            kind = BrowseKind.SRC,
            contextKey = "gen/$deckLabel",
            defaultScope = BrowseScope.All,
            accepts = { ApplyTarget.acceptsSource(it.type) },
            isApplied = {
                when (it.type) {
                    AssetType.SOURCE_STOCK -> it.path.removePrefix(BrowseCatalogs.STOCK_SOURCE_PREFIX) == deck.source.id
                    AssetType.SOURCE_EXTERNAL -> (deck.source as? llm.slop.liquidlsd.rendering.ExternalVideoSource)?.serverName == BrowseCatalogs.externalName(it)
                    else -> false
                }
            },
            apply = { asset ->
                if (asset.type == AssetType.SOURCE_STOCK) applyId(asset.path.removePrefix(BrowseCatalogs.STOCK_SOURCE_PREFIX))
                else if (asset.type == AssetType.SOURCE_EXTERNAL) applyId("ext_video:${BrowseCatalogs.externalName(asset)}")
                else DeckSlot.entries.firstOrNull { it.label == deckLabel }?.let { DeckOps.request(it, DeckChange.Preset(File(asset.path))) }
            }
        )
        val actions = { drawGenBrowseSaveButton(session, mixer, deck, deckLabel) }
        return DockBinding(target, "$deckLabel · Source", actions)
    }

    /** The one dock the Library also draws, bound to this row's [binding]: tabs and toolbar, the chip line, the pane, then its shortcuts and popups. */
    private fun drawDock(session: SessionContext, mixer: Mixer, parametersState: ParametersState, binding: DockBinding) {
        val btnH = 21f
        BrowserDock.drawHeader(session, mixer, parametersState, ImGui.getWindowWidth(), btnH, ImGui.getCursorPosY(), ImGui.getCursorPosX(), binding)
        ImGui.spacing()
        BrowserDock.drawBody(session, mixer, parametersState, binding)
        BrowserDock.drawShortcuts(session, mixer)
        BrowserDock.drawPopups(session)
    }

    /** Floppy-disk Save/Save As -- same [DeckPresetController.handleSaveDeck] flow the Mixer's
     *  own Save button and Ctrl+Shift+S already use, just also reachable from this Browse list. */
    private fun drawGenBrowseSaveButton(session: SessionContext, mixer: Mixer, deck: llm.slop.liquidlsd.rendering.Deck, deckLabel: String) {
        val isDeckA = deckLabel == "Deck A"
        val isExternal = deck.source is llm.slop.liquidlsd.rendering.ExternalVideoSource
        val rowH = ImGui.getFrameHeight()
        if (isExternal) {
            ImGui.beginDisabled(true)
        }
        session.uiTheme.withFont(UITheme.FontLevel.TOOLTIP) {
            if (ButtonChrome.button("${Icons.SAVE}##browse_gen_save_$deckLabel", rowH, rowH)) {
                if (!isExternal) {
                    ImGui.openPopup("browse_gen_save_menu_$deckLabel")
                }
            }
        }
        if (isExternal) {
            ImGui.endDisabled()
            itemTooltip("External video streams (${deck.source.displayName}) cannot be saved as presets.", allowWhenDisabled = true)
        } else {
            itemTooltip("Save or save as a new preset for $deckLabel.")
        }
        pushOpenDropdownPadding()
        if (ImGui.beginPopup("browse_gen_save_menu_$deckLabel")) {
            pushOpenDropdownFont()
            if (ImGui.menuItem("Save")) {
                ctx.deckPresetController?.handleSaveDeck(mixer, deck, isDeckA, isSaveAs = false)
            }
            if (ImGui.menuItem("Save As...")) {
                ctx.deckPresetController?.handleSaveDeck(mixer, deck, isDeckA, isSaveAs = true)
            }
            popOpenDropdownFont()
            ImGui.endPopup()
        }
        popOpenDropdownPadding()
    }

    private fun transitionBinding(mixer: Mixer): DockBinding {
        val target = ApplyTarget(
            kind = BrowseKind.TRANS,
            contextKey = "transition",
            defaultScope = BrowseScope.All,
            accepts = { ApplyTarget.acceptsTransition(it.type) },
            isApplied = {
                it.type == AssetType.TRANSITION_STOCK &&
                    it.path.removePrefix(BrowseCatalogs.STOCK_TRANS_PREFIX) == (mixer.transitionFilter?.id ?: "linear_crossfade")
            },
            apply = { asset ->
                if (asset.type == AssetType.TRANSITION_STOCK) TransitionOps.setStock(asset.path.removePrefix(BrowseCatalogs.STOCK_TRANS_PREFIX), undoable = true)
                else TransitionOps.loadPreset(File(asset.path), undoable = true)
            }
        )
        return DockBinding(target, "Transition")
    }

    /** The unified pane hosted for an FX slot (one effect) or, with [slotIndex] null, the whole chain. */
    private fun fxBinding(session: SessionContext, chain: FxChain, chainLabel: String, slotIndex: Int?): DockBinding {
        val key = System.identityHashCode(chain)
        val target = if (slotIndex == null) {
            ApplyTarget(
                kind = BrowseKind.FX,
                contextKey = "chain/$key",
                defaultScope = ApplyTarget.defaultFxScope(null),
                accepts = { ApplyTarget.acceptsFxChain(it.type) },
                isApplied = { it.type == AssetType.FX_CHAIN && chain.sourceFile?.absolutePath == it.path },
                apply = { FxOps.loadChain(session, File(it.path), chain, undoable = true) },
                clear = { FxOps.clearChain(chain, undoable = true) }
            )
        } else {
            ApplyTarget(
                kind = BrowseKind.FX,
                contextKey = "fxslot/$key/$slotIndex",
                defaultScope = ApplyTarget.defaultFxScope(slotIndex),
                accepts = { ApplyTarget.acceptsFxSlot(it.type) },
                isApplied = { it.type == AssetType.FX_STOCK && chain.slots[slotIndex]?.id == it.path.removePrefix(BrowseCatalogs.STOCK_FX_PREFIX) },
                apply = { asset ->
                    if (asset.type == AssetType.FX_STOCK) FxOps.setSlotFilter(chain, slotIndex, asset.path.removePrefix(BrowseCatalogs.STOCK_FX_PREFIX), undoable = true)
                    else FxOps.loadSlot(session, File(asset.path), chain, slotIndex, undoable = true)
                },
                clear = { FxOps.clearSlot(chain, slotIndex, undoable = true) }
            )
        }
        val actions = {
            session.uiTheme.withFont(UITheme.FontLevel.TOOLTIP) {
                if (ButtonChrome.button("${Icons.TRASH} ${if (slotIndex == null) "Clear Chain" else "Clear Slot ${slotIndex + 1}"}##browse_fx_clear")) target.clear?.invoke()
            }
        }
        return DockBinding(target, if (slotIndex == null) "$chainLabel · Chain" else "$chainLabel · FX ${slotIndex + 1}", actions)
    }
}
