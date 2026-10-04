package llm.slop.liquidlsd.ui

import imgui.ImGui
import llm.slop.liquidlsd.SessionContext
import llm.slop.liquidlsd.macro.MacroEngine
import llm.slop.liquidlsd.presets.FxOps
import llm.slop.liquidlsd.rendering.FxChain
import llm.slop.liquidlsd.rendering.Mixer
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
            drawFxChainBrowse(session, parametersState, moduleId, deck.fxChain, "$deckLabel FX")
        } else {
            drawGenBrowse(session, parametersState, mixer, deck, deckLabel)
        }
    }

    private fun drawMasterBrowse(session: SessionContext, mixer: Mixer, parametersState: ParametersState) {
        when (parametersState.activeMixerSubTab) {
            "TRANS" -> drawTransitionBrowse(session, mixer)
            "FX" -> drawFxChainBrowse(session, parametersState, MacroEngine.MASTER, mixer.masterFxChain, "Master FX")
            // MIX (CTRL) has no Browse target -- e.g. the user flipped the row's pill back to MIX
            // while Browse was open. Nothing to show here, so fall back to Params.
            else -> parametersState.openParams(MacroEngine.MASTER)
        }
    }

    private fun drawGenBrowse(session: SessionContext, parametersState: ParametersState, mixer: Mixer, deck: llm.slop.liquidlsd.rendering.Deck, deckLabel: String) {
        ShaderPickerPopup.ensureInlineSource("gen/$deckLabel", "Select Source for $deckLabel", applied = { deck.source.id }) { pick ->
            when (pick) {
                is ShaderPickerPopup.SourcePick.Id ->
                    DeckSourcePicker.applyPickedSourceId(session, parametersState, mixer, deck, deckLabel, pick.sourceId, ctx.deckPresetController)
                is ShaderPickerPopup.SourcePick.Saved ->
                    session.presetRepository.loadDeckPresetAsync(
                        pick.file,
                        isDeckA = deckLabel == "Deck A",
                        isDeckBG = deckLabel == "Deck BG",
                        isDeckPV = deckLabel == "Deck PV"
                    )
                ShaderPickerPopup.SourcePick.None -> {}
            }
        }
        drawGenBrowseSaveButton(session, mixer, deck, deckLabel)
        ShaderPickerPopup.drawInline(session)
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
        ImGui.spacing()
    }

    private fun drawTransitionBrowse(session: SessionContext, mixer: Mixer) {
        ShaderPickerPopup.ensureInline("transition", "Select Mixer Transition", ShaderPickerPopup.PickerType.MIXER_TRANSITION, applied = { mixer.transitionFilter?.id ?: "linear_crossfade" }) { id ->
            mixer.setTransition(id)
        }
        ShaderPickerPopup.drawInline(session)
    }

    /** [moduleId] is the canonical rack module (a deck, or MASTER) -- used only to look up the Chain/FX1/FX2/FX3 target chosen in the bay's tab row. */
    private fun drawFxChainBrowse(session: SessionContext, parametersState: ParametersState, moduleId: String, chain: FxChain, chainLabel: String) {
        val target = parametersState.browseTargetFor(moduleId) as? ParametersState.BrowseTarget.FxChain
        val activeSlot = target?.slotIndex

        if (activeSlot == null) {
            drawChainList(session, chain)
        } else {
            drawFxSlotPicker(session, chain, activeSlot, chainLabel)
        }
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
