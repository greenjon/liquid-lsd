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

    fun draw(session: SessionContext, mixer: Mixer, parametersState: ParametersState, moduleId: String) {
        val deckLabel = ctx.deckLabelForModuleId(moduleId)
        if (deckLabel != null) {
            drawDeckBrowse(session, mixer, parametersState, moduleId, deckLabel)
        } else if (moduleId == MacroEngine.MASTER) {
            drawMasterBrowse(session, mixer, parametersState)
        }
    }

    private fun drawDeckBrowse(session: SessionContext, mixer: Mixer, parametersState: ParametersState, moduleId: String, deckLabel: String) {
        val deck = ctx.deckForLabel(mixer, deckLabel)
        if (parametersState.getActiveSubTab(deckLabel) == "FX") {
            drawFxChainBrowse(session, mixer, parametersState, moduleId, deck.fxChain)
        } else {
            drawGenBrowse(session, parametersState, mixer, deck, deckLabel)
        }
    }

    private fun drawMasterBrowse(session: SessionContext, mixer: Mixer, parametersState: ParametersState) {
        when (parametersState.activeMixerSubTab) {
            "TRANS" -> drawTransitionBrowse(session, mixer, parametersState)
            "FX" -> drawFxChainBrowse(session, mixer, parametersState, MacroEngine.MASTER, mixer.masterFxChain)
            // MIX (CTRL) has no Browse target -- e.g. the user flipped the row's pill back to MIX
            // while Browse was open. Nothing to show here, so fall back to Params.
            else -> parametersState.openParams(MacroEngine.MASTER)
        }
    }

    private fun drawGenBrowse(session: SessionContext, parametersState: ParametersState, mixer: Mixer, deck: llm.slop.liquidlsd.rendering.Deck, deckLabel: String) {
        val applyId = { id: String -> DeckSourcePicker.applyPickedSourceId(session, parametersState, mixer, deck, deckLabel, id, ctx.deckPresetController) }
        val target = ApplyTarget(
            kind = BrowseKind.SRC,
            contextKey = "gen/$deckLabel",
            defaultScope = BrowseScope.All,
            accepts = { ApplyTarget.acceptsSource(it.type) },
            isApplied = { it.type == AssetType.SOURCE_STOCK && it.path.removePrefix(BrowseCatalogs.STOCK_SOURCE_PREFIX) == deck.source.id },
            apply = { asset ->
                if (asset.type == AssetType.SOURCE_STOCK) applyId(asset.path.removePrefix(BrowseCatalogs.STOCK_SOURCE_PREFIX))
                else DeckSlot.entries.firstOrNull { it.label == deckLabel }?.let { DeckOps.request(it, DeckChange.Preset(File(asset.path))) }
            }
        )
        val actions = { drawGenBrowseSaveButton(session, mixer, deck, deckLabel) { drawExternalVideoMenu(deckLabel, applyId) } }
        drawDock(session, mixer, parametersState, DockBinding(target, "$deckLabel source", actions))
    }

    /** The one dock the Library also draws, bound to this row's [binding]: tabs and toolbar, the chip line, the pane, then its shortcuts and popups. */
    private fun drawDock(session: SessionContext, mixer: Mixer, parametersState: ParametersState, binding: DockBinding) {
        val btnH = 21f
        BrowserDock.drawHeader(session, mixer, parametersState, ImGui.getWindowWidth(), btnH, ImGui.getCursorPosY(), ImGui.getCursorPosX())
        ImGui.spacing()
        BrowserDock.drawBody(session, mixer, parametersState, binding)
        BrowserDock.drawShortcuts(session, mixer)
        BrowserDock.drawPopups(session)
    }

    /** Floppy-disk Save/Save As -- same [DeckPresetController.handleSaveDeck] flow the Mixer's
     *  own Save button and Ctrl+Shift+S already use, just also reachable from this Browse list. */
    private fun drawGenBrowseSaveButton(session: SessionContext, mixer: Mixer, deck: llm.slop.liquidlsd.rendering.Deck, deckLabel: String, extra: (() -> Unit)? = null) {
        val isDeckA = deckLabel == "Deck A"
        val isExternal = deck.source is llm.slop.liquidlsd.rendering.ExternalVideoSource
        val rowH = ImGui.getFrameHeight()
        if (isExternal) {
            ImGui.beginDisabled(true)
        }
        session.uiTheme.withFont(UITheme.FontLevel.TOOLTIP) {
            if (ImGui.button("${Icons.SAVE}##browse_gen_save_$deckLabel", rowH, rowH)) {
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
        if (extra != null) {
            ImGui.sameLine()
            extra()
        }
    }

    /** The unified pane lists saved and stock sources only; live external video feeds (the old picker's "External Sources") live in this menu. */
    private fun drawExternalVideoMenu(deckLabel: String, apply: (String) -> Unit) {
        val popupId = "browse_gen_ext_video_$deckLabel"
        if (ImGui.button("External video...##$popupId")) ImGui.openPopup(popupId)
        itemTooltip("Use a live external video stream as this deck's source.")
        pushOpenDropdownPadding()
        if (ImGui.beginPopup(popupId)) {
            pushOpenDropdownFont()
            val servers = llm.slop.liquidlsd.rendering.ExternalVideoDiscovery.availableServers.value
            if (servers.isEmpty()) ImGui.textDisabled("No external streams active")
            servers.forEach { if (ImGui.menuItem(it)) apply("ext_video:$it") }
            popOpenDropdownFont()
            ImGui.endPopup()
        }
        popOpenDropdownPadding()
    }

    private fun drawTransitionBrowse(session: SessionContext, mixer: Mixer, parametersState: ParametersState) {
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
        drawDock(session, mixer, parametersState, DockBinding(target, "Transition"))
    }

    /** [moduleId] is the canonical rack module (a deck, or MASTER) -- used only to look up the Chain/FX1/FX2/FX3 target chosen in the bay's tab row. */
    private fun drawFxChainBrowse(session: SessionContext, mixer: Mixer, parametersState: ParametersState, moduleId: String, chain: FxChain) {
        val target = parametersState.browseTargetFor(moduleId) as? ParametersState.BrowseTarget.FxChain
        drawFxPane(session, mixer, parametersState, chain, ctx.deckLabelForModuleId(moduleId) ?: "Master", target?.slotIndex)
    }

    /** The unified pane hosted for an FX slot (one effect) or, with [slotIndex] null, the whole chain. */
    private fun drawFxPane(session: SessionContext, mixer: Mixer, parametersState: ParametersState, chain: FxChain, chainLabel: String, slotIndex: Int?) {
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
                if (ImGui.button("${Icons.TRASH} ${if (slotIndex == null) "Clear Chain" else "Clear Slot ${slotIndex + 1}"}##browse_fx_clear")) target.clear?.invoke()
            }
        }
        drawDock(session, mixer, parametersState, DockBinding(target, if (slotIndex == null) "$chainLabel FX chain" else "$chainLabel FX ${slotIndex + 1}", actions))
    }
}
