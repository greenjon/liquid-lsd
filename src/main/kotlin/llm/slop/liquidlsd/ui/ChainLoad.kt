package llm.slop.liquidlsd.ui

import llm.slop.liquidlsd.SessionContext
import llm.slop.liquidlsd.macro.FxMacroSync
import llm.slop.liquidlsd.presets.FxOps
import llm.slop.liquidlsd.rendering.Mixer
import llm.slop.liquidlsd.ui.browser.ApplyTarget
import llm.slop.liquidlsd.ui.browser.BrowseCatalogs
import llm.slop.liquidlsd.ui.browser.BrowseRecents
import llm.slop.liquidlsd.ui.browser.FXBrowserPanel
import java.io.File

/**
 * Chain view's load row ([RowDescriptor.loadRow]): puts the FX list's cursor item into one slot of the focused pair's chain. Shared by
 * the row's on-screen targets and (step 5) the controller's knobs 1-3, so both load the same way the Browse dock does for a slot.
 */
internal object ChainLoad {
    /** The cursor item if a slot can take it (a stock effect or a saved single effect, not a whole chain), else null. */
    fun cursorAsset(): AssetItem? =
        FXBrowserPanel.selectedAsset?.takeIf { it.isValid && ApplyTarget.acceptsFxSlot(it.type) }

    /**
     * Loads the cursor item into [slotIndex] of the focused pair's chain (undoable, with the usual slot fade) and moves the dock onto
     * that slot, so the list marks what was loaded and a following click replaces it. Returns false when nothing was loaded.
     */
    fun loadCursorInto(session: SessionContext, mixer: Mixer, parametersState: ParametersState, slotIndex: Int): Boolean {
        val tag = parametersState.focusedPair ?: return false
        val pair = PerfRows.pairFor(tag) ?: return false
        val chain = PerfRows.chainBankFor(tag)?.let { FxMacroSync.chainFor(it, mixer) } ?: return false
        val asset = cursorAsset() ?: return false
        BrowseRecents.note(asset)
        if (asset.type == AssetType.FX_STOCK) FxOps.setSlotFilter(chain, slotIndex, asset.path.removePrefix(BrowseCatalogs.STOCK_FX_PREFIX), undoable = true)
        else FxOps.loadSlot(session, File(asset.path), chain, slotIndex, undoable = true)
        parametersState.openBrowse(pair.moduleId, ParametersState.BrowseTarget.FxChain(slotIndex), retab = false)
        return true
    }
}
