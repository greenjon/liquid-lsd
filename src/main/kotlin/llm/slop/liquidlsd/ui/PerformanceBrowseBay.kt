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
import llm.slop.liquidlsd.ui.browser.BrowserPane
import java.io.File

/**
 * Controller cursor over the saved-chain list drawn by [PerformanceBrowseBay]: the draw code publishes
 * what is on screen and how to apply or clear it, [move]/[accept]/[clear] act on that.
 */
internal object ChainListBrowse {
    private var items: List<AssetItem> = emptyList()
    private var apply: ((AssetItem) -> Unit)? = null
    private var clearChain: (() -> Unit)? = null
    private var cursor = -1
    private var scrollToCursor = false
    @Volatile private var lastDrawMs = 0L

    val isShowing: Boolean get() = UiClock.nowMs() - lastDrawMs < 300L

    fun reset() { cursor = -1 }

    fun publish(items: List<AssetItem>, apply: (AssetItem) -> Unit, clearChain: () -> Unit) {
        this.items = items
        this.apply = apply
        this.clearChain = clearChain
        lastDrawMs = UiClock.nowMs()
        if (cursor > items.lastIndex) cursor = items.lastIndex
    }

    fun move(steps: Int) {
        if (items.isEmpty()) return
        cursor = (if (cursor < 0) (if (steps > 0) steps - 1 else items.size + steps) else cursor + steps).coerceIn(0, items.lastIndex)
        scrollToCursor = true
    }

    fun accept(): Boolean {
        val item = items.getOrNull(cursor) ?: return false
        apply?.invoke(item)
        return true
    }

    fun clear() { clearChain?.invoke() }

    /** Whether row [index] is the cursor row; consumes the pending scroll request when it is. */
    fun isCursor(index: Int): Boolean = index == cursor

    fun consumeScroll(): Boolean = scrollToCursor.also { scrollToCursor = false }
}

/**
 * Inline "Browse" content for the Performance row bay: picking a deck's generator, a saved whole
 * FX chain, one FX chain slot's effect, or the active transition -- everything that used to be
 * [ShaderPickerPopup]'s modal popup or [FxChainHeader]'s small chain-browser popup. Lives beside
 * [PerformanceDeepEditBay]'s Params content; the bay's tab row (Edit | SRC | Chain | FX1-3) picks which
 * one shows. Picking something applies it immediately (Ctrl+Z undoes it) and leaves the list open, so
 * trying several generators/effects/chains in a row doesn't mean reopening anything.
 */
internal class PerformanceBrowseBay(private val ctx: PerformanceUiContext) {

    private var cachedChains: List<AssetItem>? = null
    private var cachedChainsKey: String? = null

    // Filtered list + item IDs rebuilt only when the query or the scanned list changes; publish callbacks only when the chain/session changes.
    private var filterQuery: String? = null
    private var filterSource: List<AssetItem>? = null
    private var filteredChains: List<AssetItem> = emptyList()
    private var filteredLabels: Array<String> = emptyArray()
    private var publishChain: FxChain? = null
    private var publishSession: SessionContext? = null
    private var publishApply: (AssetItem) -> Unit = {}
    private var publishClear: () -> Unit = {}
    private val chainSearchBuf = imgui.type.ImString(llm.slop.liquidlsd.ui.browser.SearchMatcher.BUFFER_SIZE)

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
            drawFxChainBrowse(session, mixer, parametersState, moduleId, deck.fxChain, "$deckLabel FX")
        } else {
            drawGenBrowse(session, parametersState, mixer, deck, deckLabel)
        }
    }

    private fun drawMasterBrowse(session: SessionContext, mixer: Mixer, parametersState: ParametersState) {
        when (parametersState.activeMixerSubTab) {
            "TRANS" -> drawTransitionBrowse(session, mixer, parametersState)
            "FX" -> drawFxChainBrowse(session, mixer, parametersState, MacroEngine.MASTER, mixer.masterFxChain, "Master FX")
            // MIX (CTRL) has no Browse target -- e.g. the user flipped the row's pill back to MIX
            // while Browse was open. Nothing to show here, so fall back to Params.
            else -> parametersState.openParams(MacroEngine.MASTER)
        }
    }

    private fun drawGenBrowse(session: SessionContext, parametersState: ParametersState, mixer: Mixer, deck: llm.slop.liquidlsd.rendering.Deck, deckLabel: String) {
        if (BrowserPane.enabled) {
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
            drawGenBrowseSaveButton(session, mixer, deck, deckLabel) { drawExternalVideoMenu(deckLabel, applyId) }
            BrowserPane.draw(session, mixer, parametersState, BrowseKind.SRC, target)
            return
        }
        ShaderPickerPopup.ensureInlineSource("gen/$deckLabel", "Select Source for $deckLabel", applied = { deck.source.id }) { pick ->
            when (pick) {
                is ShaderPickerPopup.SourcePick.Id ->
                    DeckSourcePicker.applyPickedSourceId(session, parametersState, mixer, deck, deckLabel, pick.sourceId, ctx.deckPresetController)
                is ShaderPickerPopup.SourcePick.Saved ->
                    DeckSlot.entries.firstOrNull { it.label == deckLabel }?.let { DeckOps.request(it, DeckChange.Preset(pick.file)) }
                ShaderPickerPopup.SourcePick.None -> {}
            }
        }
        drawGenBrowseSaveButton(session, mixer, deck, deckLabel)
        ShaderPickerPopup.drawInline(session)
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
        ImGui.spacing()
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
        if (BrowserPane.enabled) {
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
                    if (asset.type == AssetType.TRANSITION_STOCK) TransitionOps.setStock(asset.path.removePrefix(BrowseCatalogs.STOCK_TRANS_PREFIX))
                    else TransitionOps.loadPreset(File(asset.path))
                }
            )
            BrowserPane.draw(session, mixer, parametersState, BrowseKind.TRANS, target)
            return
        }
        ShaderPickerPopup.ensureInline("transition", "Select Mixer Transition", ShaderPickerPopup.PickerType.MIXER_TRANSITION, applied = { mixer.transitionFilter?.id ?: "linear_crossfade" }) { id ->
            llm.slop.liquidlsd.presets.TransitionOps.setStock(id)
        }
        ShaderPickerPopup.drawInline(session)
        if (ImGui.button("Save current as preset...##trans_save_current")) TransitionSave.requestSaveCurrent(session, mixer)
        itemTooltip("Save the mixer's current transition and its settings as a .lsdtrans preset in the Library.")
    }

    /** [moduleId] is the canonical rack module (a deck, or MASTER) -- used only to look up the Chain/FX1/FX2/FX3 target chosen in the bay's tab row. */
    private fun drawFxChainBrowse(session: SessionContext, mixer: Mixer, parametersState: ParametersState, moduleId: String, chain: FxChain, chainLabel: String) {
        val target = parametersState.browseTargetFor(moduleId) as? ParametersState.BrowseTarget.FxChain
        val activeSlot = target?.slotIndex

        if (BrowserPane.enabled) {
            drawFxPane(session, mixer, parametersState, chain, activeSlot)
            return
        }
        if (activeSlot == null) {
            drawChainList(session, chain)
        } else {
            drawFxSlotPicker(session, chain, activeSlot, chainLabel)
        }
    }

    /** The unified pane hosted for an FX slot (one effect) or, with [slotIndex] null, the whole chain. */
    private fun drawFxPane(session: SessionContext, mixer: Mixer, parametersState: ParametersState, chain: FxChain, slotIndex: Int?) {
        val key = System.identityHashCode(chain)
        val target = if (slotIndex == null) {
            ApplyTarget(
                kind = BrowseKind.FX,
                contextKey = "chain/$key",
                defaultScope = ApplyTarget.defaultFxScope(null),
                accepts = { ApplyTarget.acceptsFxChain(it.type) },
                isApplied = { it.type == AssetType.FX_CHAIN && chain.sourceFile?.absolutePath == it.path },
                apply = { FxOps.loadChain(session, File(it.path), chain) },
                clear = { FxOps.clearChain(chain) }
            )
        } else {
            ApplyTarget(
                kind = BrowseKind.FX,
                contextKey = "fxslot/$key/$slotIndex",
                defaultScope = ApplyTarget.defaultFxScope(slotIndex),
                accepts = { ApplyTarget.acceptsFxSlot(it.type) },
                isApplied = { it.type == AssetType.FX_STOCK && chain.slots[slotIndex]?.id == it.path.removePrefix(BrowseCatalogs.STOCK_FX_PREFIX) },
                apply = { asset ->
                    if (asset.type == AssetType.FX_STOCK) FxOps.setSlotFilter(chain, slotIndex, asset.path.removePrefix(BrowseCatalogs.STOCK_FX_PREFIX))
                    else FxOps.loadSlot(session, File(asset.path), chain, slotIndex)
                },
                clear = { FxOps.clearSlot(chain, slotIndex) }
            )
        }
        session.uiTheme.withFont(UITheme.FontLevel.TOOLTIP) {
            if (ImGui.button("${Icons.TRASH} ${if (slotIndex == null) "Clear Chain" else "Clear Slot ${slotIndex + 1}"}##browse_fx_clear")) target.clear?.invoke()
        }
        ImGui.spacing()
        BrowserPane.draw(session, mixer, parametersState, BrowseKind.FX, target)
    }

    private fun drawFxSlotPicker(session: SessionContext, chain: FxChain, slotIndex: Int, chainLabel: String) {
        val contextKey = "fxslot/${System.identityHashCode(chain)}/$slotIndex"
        ShaderPickerPopup.ensureInlineFx(contextKey, "Select FX Slot ${slotIndex + 1} for $chainLabel", slotIndex, applied = { chain.slots[slotIndex]?.id }) { pick ->
            when (pick) {
                is ShaderPickerPopup.FxPick.Stock -> FxOps.setSlotFilter(chain, slotIndex, pick.filterId)
                is ShaderPickerPopup.FxPick.Saved -> FxOps.loadSlot(session, pick.file, chain, slotIndex)
                ShaderPickerPopup.FxPick.None -> FxOps.clearSlot(chain, slotIndex)
            }
        }
        ShaderPickerPopup.drawInline(session)
    }

    private fun drawChainList(session: SessionContext, chain: FxChain) {
        val contextKey = "chainlist/${System.identityHashCode(chain)}"
        if (cachedChainsKey != contextKey) {
            cachedChainsKey = contextKey
            cachedChains = FileSystemManager.scanAllFxChains()
            chainSearchBuf.set("")
            ChainListBrowse.reset()
        }
        session.uiTheme.withFont(UITheme.FontLevel.TOOLTIP) {
            ImGui.setNextItemWidth(260f)
            ImGui.inputTextWithHint("##browse_chain_search", "Search chains...", chainSearchBuf)
            ImGui.sameLine()
            if (ImGui.button("${Icons.REFRESH}##browse_chain_refresh")) {
                cachedChains = FileSystemManager.scanAllFxChains()
            }
            itemTooltip("Refresh the saved chains list.")
            ImGui.sameLine()
            if (ImGui.button("${Icons.TRASH} Clear Chain##browse_chain_clear")) {
                FxOps.clearChain(chain)
            }

            val query = chainSearchBuf.get().trim().lowercase()
            val chains = cachedChains ?: emptyList()
            if (filterQuery != query || filterSource !== chains) {
                filterQuery = query
                filterSource = chains
                val tokens = llm.slop.liquidlsd.ui.browser.SearchMatcher.tokens(query)
                filteredChains = if (query.isBlank()) chains else chains.filter { llm.slop.liquidlsd.ui.browser.SearchMatcher.matches(tokens, listOf(it.name), it.tags) }
                filteredLabels = Array(filteredChains.size) { "${filteredChains[it].name}##browse_chain_item_${filteredChains[it].path.hashCode()}" }
            }
            val filtered = filteredChains

            if (publishChain !== chain || publishSession !== session) {
                publishChain = chain
                publishSession = session
                publishApply = { FxOps.loadChain(session, File(it.path), chain) }
                publishClear = { FxOps.clearChain(chain) }
            }
            ChainListBrowse.publish(filtered, publishApply, publishClear)

            ImGui.spacing()
            ImGui.separator()
            ImGui.spacing()

            if (ImGui.beginChild("##browse_chain_list", 0f, 0f, false)) {
                if (filtered.isEmpty()) {
                    ImGui.textDisabled("No matching chains")
                } else {
                    for (index in filtered.indices) {
                        val asset = filtered[index]
                        val isCurrent = chain.sourceFile?.absolutePath == asset.path
                        val isCursor = ChainListBrowse.isCursor(index)
                        if (selectableRow(filteredLabels[index], isCurrent || isCursor)) {
                            FxOps.loadChain(session, File(asset.path), chain)
                        }
                        if (isCursor && ChainListBrowse.consumeScroll()) ImGui.setScrollHereY()
                    }
                }
            }
            ImGui.endChild()
        }
    }
}
