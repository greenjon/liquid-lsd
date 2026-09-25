package llm.slop.liquidlsd.ui

import imgui.ImGui
import imgui.flag.ImGuiCol
import imgui.flag.ImGuiStyleVar
import llm.slop.liquidlsd.SessionContext
import llm.slop.liquidlsd.macro.FxMacroSync
import llm.slop.liquidlsd.models.ClipboardManager
import llm.slop.liquidlsd.presets.FxOps
import llm.slop.liquidlsd.rendering.FxChain
import llm.slop.liquidlsd.rendering.Mixer
import java.io.File

/**
 * Shared chain-level controls for every Performance FX row variant (ALL FX, LIVE CONSOLE, deck row in FX mode):
 *
 *   `[◀]  Chain Name •  [▶]  [Save] [⋮]`   ...   `[BYPASS]`
 *
 * - **◀ / ▶**: steps through .lsdfxchain files in the current chain's folder alphabetically.
 * - **Name**: click opens chain picker popup (with search filter). Drops of .lsdfxchain load here.
 * - **• (dirty dot)**: shows amber when the chain differs from its loaded baseline or has unsaved edits.
 * - **Save**: overwrites source file (or acts as Save As if untitled).
 * - **⋮ menu**: Save As, New, Revert, Clear, Copy / Paste chain, Resync knobs.
 * - **BYPASS**: top-level chain kill-switch.
 */
object FxChainHeader {

    private const val ARROW_W = 16f
    private const val MORE_BTN_W = 20f
    private const val SAVE_BTN_W = 42f

    private var cachedChains: List<AssetItem>? = null
    private var activePopupBankId: String? = null
    private val searchBuf = imgui.type.ImString(64)

    /** Steps [chain] to the previous (-1) or next (+1) chain file in its folder. */
    fun stepChain(session: SessionContext, chain: FxChain, dir: Int) {
        val folder = chain.sourceFile?.parentFile ?: FileSystemManager.getFxChainsRoot()
        val files = folder.listFiles { f -> f.isFile && f.extension.equals("lsdfxchain", ignoreCase = true) }
            ?.sortedBy { it.name.lowercase() }
            ?: return
        if (files.isEmpty()) return
        val currentPath = chain.sourceFile?.absolutePath
        val idx = files.indexOfFirst { it.absolutePath == currentPath }
        val nextIdx = when {
            idx < 0 -> if (dir >= 0) 0 else files.lastIndex
            else -> Math.floorMod(idx + dir, files.size)
        }
        FxOps.loadChain(session, files[nextIdx], chain)
    }

    /**
     * Draws the chain selection and management controls:
     * `[◀]  Chain Name •  [▶]  [Save] [⋮]`
     */
    fun drawControls(
        session: SessionContext,
        mixer: Mixer,
        chain: FxChain,
        bankId: String,
        chainLabel: String,
        ctrlH: Float,
        maxW: Float = 220f
    ) {
        val gap = 3f
        val isDirty = chain.isDirty()
        val popupId = "##fx_chain_picker_$bankId"
        val menuId = "##fx_chain_more_$bankId"

        ImGui.pushStyleVar(ImGuiStyleVar.ItemSpacing, gap, 0f)

        // 1. [◀] Prev chain
        if (ImGui.button("◀##prev_chain_$bankId", ARROW_W, ctrlH)) {
            stepChain(session, chain, -1)
        }
        itemTooltip("Previous FX chain in folder.")

        ImGui.sameLine()

        // 2. Chain name button (click to open picker popup, drop .lsdfxchain here)
        val nameW = (maxW - (ARROW_W * 2f + SAVE_BTN_W + MORE_BTN_W + gap * 5f)).coerceAtLeast(60f)
        val displayName = if (chain.name.isBlank()) "Untitled" else chain.name
        val dirtyMarker = if (isDirty) " •" else ""
        val fullLabel = "$displayName$dirtyMarker ${Icons.CHEVRON_DOWN}"

        if (isDirty) {
            ImGui.pushStyleColor(ImGuiCol.Text, ImGui.colorConvertFloat4ToU32(1.0f, 0.75f, 0.25f, 1f))
        }
        if (ImGui.button("$fullLabel$popupId", nameW, ctrlH)) {
            cachedChains = FileSystemManager.scanAllFxChains()
            activePopupBankId = bankId
            searchBuf.set("")
            ImGui.openPopup(popupId)
        }
        if (isDirty) {
            ImGui.popStyleColor()
        }
        itemTooltip(
            "${chain.name.ifBlank { "Untitled" }}${if (isDirty) " (Modified)" else ""}\n" +
            "Source: ${chain.sourceFile?.name ?: "Unsaved"}\n" +
            "Click to browse saved chains, or drop a .lsdfxchain here."
        )

        // Drag & drop receiver for chain name button
        if (ImGui.beginDragDropTarget()) {
            ImGui.acceptDragDropPayload<String>("ASSET_ITEM")?.let { path ->
                val file = File(path)
                if (file.extension.equals("lsdfxchain", ignoreCase = true)) {
                    FxOps.loadChain(session, file, chain)
                }
            }
            ImGui.endDragDropTarget()
        }

        // Chain Browser Popup
        if (ImGui.beginPopup(popupId)) {
            ImGui.textDisabled("$chainLabel FX Chains")
            ImGui.separator()
            ImGui.setNextItemWidth(180f)
            ImGui.inputTextWithHint("##chain_search_$bankId", "Search chains...", searchBuf)
            val query = searchBuf.get().trim().lowercase()

            val chains = cachedChains ?: FileSystemManager.scanAllFxChains().also { cachedChains = it }
            val filtered = if (query.isBlank()) chains else chains.filter { it.name.lowercase().contains(query) }

            if (filtered.isEmpty()) {
                ImGui.textDisabled("No matching chains")
            } else {
                for (asset in filtered) {
                    val isCurrent = chain.sourceFile?.absolutePath == asset.path
                    if (ImGui.selectable("${asset.name}##item_${asset.path.hashCode()}", isCurrent)) {
                        val file = File(asset.path)
                        FxOps.loadChain(session, file, chain)
                    }
                }
            }
            ImGui.separator()
            if (ImGui.menuItem("${Icons.TRASH} Clear Chain")) {
                FxOps.clearChain(chain)
            }
            ImGui.endPopup()
        }

        ImGui.sameLine()

        // 3. [▶] Next chain
        if (ImGui.button("▶##next_chain_$bankId", ARROW_W, ctrlH)) {
            stepChain(session, chain, 1)
        }
        itemTooltip("Next FX chain in folder.")

        ImGui.sameLine()

        // 4. [Save] button
        val canOverwrite = chain.sourceFile != null
        val saveCol = if (isDirty) ImGui.colorConvertFloat4ToU32(0.25f, 0.65f, 0.45f, 1f) else ImGui.colorConvertFloat4ToU32(0.18f, 0.20f, 0.24f, 0.8f)
        ImGui.pushStyleColor(ImGuiCol.Button, saveCol)
        if (ImGui.button("Save##save_$bankId", SAVE_BTN_W, ctrlH)) {
            if (canOverwrite) {
                val file = chain.sourceFile!!
                val dto = chain.toFxChainDto(chain.name)
                session.presetRepository.saveFxChainAsync(file, chain.name, dto)
                chain.markClean(file)
            } else {
                openSaveAsModal(session, chain)
            }
        }
        ImGui.popStyleColor()
        itemTooltip(if (canOverwrite) "Save changes to ${chain.sourceFile?.name}." else "Save as new FX chain (.lsdfxchain).")

        ImGui.sameLine()

        // 5. [⋮] More actions menu
        if (ImGui.button("⋮##more_btn_$bankId", MORE_BTN_W, ctrlH)) {
            ImGui.openPopup(menuId)
        }
        itemTooltip("Chain operations (Save As, New, Revert, Clear, Copy/Paste, Resync).")

        if (ImGui.beginPopup(menuId)) {
            ImGui.textDisabled("$chainLabel FX Chain")
            ImGui.separator()

            if (ImGui.menuItem("Save As…")) {
                openSaveAsModal(session, chain)
            }
            ImGui.separator()

            if (ImGui.menuItem("New Chain")) {
                FxOps.newChain(chain)
            }
            val canRevert = isDirty && chain.baselineDto != null
            if (ImGui.menuItem("Revert to Saved", "", false, canRevert)) {
                FxOps.revertChain(chain)
            }
            if (ImGui.menuItem("Clear All Slots")) {
                FxOps.clearChain(chain)
            }
            ImGui.separator()

            if (ImGui.menuItem("Copy Chain")) {
                ClipboardManager.copyFxChain(chain.toFxChainDto())
            }
            val canPaste = ClipboardManager.fxChainClipboard != null
            if (ImGui.menuItem("Paste Chain", "", false, canPaste)) {
                ClipboardManager.fxChainClipboard?.let { FxOps.applyChain(chain, it) }
            }
            ImGui.separator()

            if (ImGui.menuItem("Resync Knobs")) {
                FxMacroSync.syncFor(bankId, mixer, forceResync = true)
            }
            ImGui.endPopup()
        }

        ImGui.popStyleVar()
    }

    private fun openSaveAsModal(session: SessionContext, chain: FxChain) {
        SavePresetModal.request(
            title = "Save FX Chain As",
            confirmLabel = "Save",
            defaultName = chain.name.ifBlank { "fx_chain" },
            targetDir = FileSystemManager.getFxChainsRoot(),
            extension = "lsdfxchain"
        ) { name, tags ->
            val file = File(FileSystemManager.getFxChainsRoot(), "$name.lsdfxchain")
            val dto = chain.toFxChainDto(name, tags)
            session.presetRepository.saveFxChainAsync(file, name, dto, tags)
            chain.markClean(file)
        }
    }

    /**
     * Draws the top-level [BYPASS] button for [chain].
     */
    fun drawBypassButton(chain: FxChain, id: String, ctrlH: Float, width: Float = 64f) {
        val isBypassed = !chain.enabled
        val btnCol = if (isBypassed) {
            ImGui.colorConvertFloat4ToU32(0.70f, 0.18f, 0.18f, 1f)
        } else {
            ImGui.colorConvertFloat4ToU32(0.16f, 0.18f, 0.22f, 1f)
        }
        val label = if (isBypassed) "BYPASS" else "FX ON"

        ImGui.pushStyleColor(ImGuiCol.Button, btnCol)
        if (ImGui.button("$label##bypass_$id", width, ctrlH)) {
            chain.enabled = !chain.enabled
        }
        ImGui.popStyleColor()
        itemTooltip(if (isBypassed) "Chain is bypassed. Click to enable FX." else "Chain is active. Click to bypass FX.")
    }
}
