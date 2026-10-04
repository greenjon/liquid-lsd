package llm.slop.liquidlsd.ui.browser

import llm.slop.liquidlsd.ui.AssetItem
import llm.slop.liquidlsd.ui.AssetType

/**
 * What a [BrowserPane] hosted in the Edit bay applies to: one deck's source, one FX slot, a whole FX chain or the mixer transition.
 * With a target a single click applies the row ([apply]) instead of the Library's double-click load, rows the target cannot take are
 * hidden ([accepts]), and the row that is already applied carries a marker ([isApplied]). [contextKey] names the target: when it
 * changes the pane restores the scope last used there (or [defaultScope]) and drops the list selection.
 */
class ApplyTarget(
    val kind: BrowseKind,
    val contextKey: String,
    val defaultScope: BrowseScope,
    val accepts: (AssetItem) -> Boolean,
    val isApplied: (AssetItem) -> Boolean,
    val apply: (AssetItem) -> Unit,
    val clear: (() -> Unit)? = null,
) {
    companion object {
        /** A deck source takes stock generators and saved presets. */
        fun acceptsSource(type: AssetType): Boolean = type == AssetType.SOURCE_STOCK || type == AssetType.PRESET

        /** An FX slot takes one effect (stock or saved single), never a whole chain. */
        fun acceptsFxSlot(type: AssetType): Boolean = type == AssetType.FX_STOCK || type == AssetType.FX_PRESET

        /** The chain target takes saved chains only. */
        fun acceptsFxChain(type: AssetType): Boolean = type == AssetType.FX_CHAIN

        /** The mixer transition takes stock transitions and saved presets. */
        fun acceptsTransition(type: AssetType): Boolean = type == AssetType.TRANSITION_STOCK || type == AssetType.TRANSITION_PRESET

        /** The scope a freshly opened target starts in: the chain list for the Chain tab, Stock for an FX slot, everything otherwise. */
        fun defaultFxScope(slotIndex: Int?): BrowseScope =
            if (slotIndex == null) BrowseScope.Folder(BrowseSection.CHAIN) else BrowseScope.Folder(BrowseSection.STOCK)
    }
}

/** Scope bookkeeping of a pane that is hosted in more than one place: which scope each (context, kind) last used. */
internal class ScopeMemory {
    companion object { const val LIBRARY = "library" }

    private val last = HashMap<BrowseKind, String>()
    private val stash = HashMap<Pair<String, BrowseKind>, BrowseScope>()

    /**
     * Called every frame with the context drawing [kind] and the scope in force. Returns the scope to use now and whether it
     * changed because the context did (the caller then drops the list selection and search).
     */
    fun enter(kind: BrowseKind, context: String, current: BrowseScope, defaultScope: BrowseScope): Pair<BrowseScope, Boolean> {
        val previous = last.put(kind, context) ?: LIBRARY
        if (previous == context) return current to false
        stash[previous to kind] = current
        return (stash[context to kind] ?: defaultScope) to true
    }
}
