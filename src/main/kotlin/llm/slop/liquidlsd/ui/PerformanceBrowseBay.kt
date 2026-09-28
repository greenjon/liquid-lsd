package llm.slop.liquidlsd.ui

import imgui.ImGui
import imgui.flag.ImGuiCol
import llm.slop.liquidlsd.SessionContext
import llm.slop.liquidlsd.macro.MacroEngine
import llm.slop.liquidlsd.presets.FxOps
import llm.slop.liquidlsd.rendering.FxChain
import llm.slop.liquidlsd.rendering.Mixer
import java.io.File

/**
 * Inline "Browse" content for the Performance row bay: picking a deck's generator, a saved whole
 * FX chain, one FX chain slot's effect, or the active transition -- everything that used to be
 * [ShaderPickerPopup]'s modal popup or [FxChainHeader]'s small chain-browser popup. Lives beside
 * [PerformanceDeepEditBay]'s Params content as the other half of each row's Browse <-> Params
 * toggle: picking something applies it immediately (Ctrl+Z undoes it) and leaves the list open, so
 * trying several generators/effects/chains in a row doesn't mean reopening anything.
 */
internal class PerformanceBrowseBay(private val ctx: PerformanceUiContext) {

    private var cachedChains: List<AssetItem>? = null
    private var cachedChainsKey: String? = null
    private val chainSearchBuf = imgui.type.ImString(64)

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
        ShaderPickerPopup.ensureInlineSource("gen/$deckLabel", "Select Source for $deckLabel") { pick ->
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
        session.uiTheme.withFont(UITheme.FontLevel.BODY) {
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
        ShaderPickerPopup.ensureInline("transition", "Select Mixer Transition", ShaderPickerPopup.PickerType.MIXER_TRANSITION) { id ->
            mixer.setTransition(id)
        }
        ShaderPickerPopup.drawInline(session)
    }

    /** [moduleId] is the canonical rack module (a deck, or MASTER) -- used only to key the FX1/FX2/FX3-vs-Chain sub-tab choice. */
    private fun drawFxChainBrowse(session: SessionContext, parametersState: ParametersState, moduleId: String, chain: FxChain, chainLabel: String) {
        val target = parametersState.browseTargetFor(moduleId) as? ParametersState.BrowseTarget.FxChain
        val activeSlot = target?.slotIndex

        ImGui.beginGroup()
        for (i in -1 until FxChain.SLOT_COUNT) {
            if (i > -1) ImGui.sameLine()
            val isActive = activeSlot == (if (i == -1) null else i)
            val label = if (i == -1) "Chain" else "FX${i + 1}"
            if (isActive) {
                ImGui.pushStyleColor(ImGuiCol.Button, ImGui.colorConvertFloat4ToU32(0.20f, 0.45f, 0.70f, 1f))
            }
            if (ImGui.button("$label##browse_fxtab_${moduleId}_$i")) {
                parametersState.rackBrowseTarget[moduleId] = ParametersState.BrowseTarget.FxChain(if (i == -1) null else i)
            }
            if (isActive) {
                ImGui.popStyleColor()
            }
        }
        ImGui.endGroup()
        ImGui.spacing()
        ImGui.separator()
        ImGui.spacing()

        if (activeSlot == null) {
            drawChainList(session, chain)
        } else {
            drawFxSlotPicker(session, chain, activeSlot, chainLabel)
        }
    }

    private fun drawFxSlotPicker(session: SessionContext, chain: FxChain, slotIndex: Int, chainLabel: String) {
        val contextKey = "fxslot/${System.identityHashCode(chain)}/$slotIndex"
        ShaderPickerPopup.ensureInlineFx(contextKey, "Select FX Slot ${slotIndex + 1} for $chainLabel", slotIndex) { pick ->
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
        }
        ImGui.setNextItemWidth(240f)
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
        val filtered = if (query.isBlank()) chains else chains.filter { it.name.lowercase().contains(query) }

        ImGui.spacing()
        ImGui.separator()
        ImGui.spacing()

        if (ImGui.beginChild("##browse_chain_list", 0f, 0f, false)) {
            if (filtered.isEmpty()) {
                ImGui.textDisabled("No matching chains")
            } else {
                for (asset in filtered) {
                    val isCurrent = chain.sourceFile?.absolutePath == asset.path
                    if (selectableRow("${asset.name}##browse_chain_item_${asset.path.hashCode()}", isCurrent)) {
                        FxOps.loadChain(session, File(asset.path), chain)
                    }
                }
            }
        }
        ImGui.endChild()
    }
}
