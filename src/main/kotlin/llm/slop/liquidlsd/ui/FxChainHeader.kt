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
 * Shared chain-level controls for every Performance FX chain row (deck rows and the Master row):
 *
 *   `[◀]  Chain Name •  [▶]  [Save] [⋮]`   ...   `[BYPASS]`
 *
 * - **◀ / ▶**: steps through .lsdfxchain files in the current chain's folder alphabetically.
 * - **Name**: click opens that row's Browse content on the whole-chain list (search filter). Drops of .lsdfxchain load here.
 * - **• (dirty dot)**: shows amber when the chain differs from its loaded baseline or has unsaved edits.
 * - **Save**: overwrites source file (or acts as Save As if untitled).
 * - **⋮ menu**: Save As, New, Revert, Clear, Copy / Paste chain, Resync knobs.
 * - **BYPASS**: top-level chain kill-switch.
 */
object FxChainHeader {

    private const val ARROW_W = 16f
    private const val MORE_BTN_W = 20f
    private const val SAVE_BTN_W = 42f

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
     *
     * [onOpenChainBrowse] opens that row's Browse content on the whole-chain list (clicking the
     * chain name), replacing what used to be a small popup here.
     */
    fun drawControls(
        session: SessionContext,
        mixer: Mixer,
        chain: FxChain,
        bankId: String,
        chainLabel: String,
        ctrlH: Float,
        maxW: Float = 220f,
        onOpenChainBrowse: () -> Unit
    ) {
        val gap = 3f
        val isDirty = chain.isDirty()
        val isFocused = chain.isFocused()
        val menuId = "##fx_chain_more_$bankId"

        ImGui.pushStyleVar(ImGuiStyleVar.ItemSpacing, gap, 0f)

        if (isFocused) {
            // -- FOCUS MODE HEADER ----------------------------------------------------------------
            val focusedSlot = chain.focusedSlot!!
            val totalPages = chain.totalParamPages(focusedSlot)

            // 1. [◀ CHAIN] Exit Focus Mode button
            val backCol = TangoPalette.u32(TangoPalette.SYNC.normal, 0.90f)
            ImGui.pushStyleColor(ImGuiCol.Button, backCol)
            if (ImGui.button("◀ CHAIN##exit_focus_$bankId", 58f, ctrlH)) {
                FxMacroSync.focusSlot(bankId, mixer, null)
            }
            ImGui.popStyleColor()
            itemTooltip("Exit Focus Mode and return to 3-slot chain view.")

            ImGui.sameLine()

            // 2. Slot pills [1] [2] [3]
            drawSlotPills(session, mixer, chain, bankId, ctrlH, focusedSlot)

            // 3. Parameter page stepper [◀ P1/2 ▶] (if totalPages > 1)
            if (totalPages > 1) {
                ImGui.sameLine()
                if (ImGui.button("◀##focus_prev_page_$bankId", ARROW_W, ctrlH)) {
                    FxMacroSync.stepParamPage(bankId, mixer, -1)
                }
                itemTooltip("Previous parameter page.")

                ImGui.sameLine()
                session.uiTheme.withFont(UITheme.FontLevel.CAPTION) {
                    val pageText = "P${chain.focusParamPage + 1}/$totalPages"
                    val ptw = ImGui.calcTextSize(pageText).x
                    val curX = ImGui.getCursorScreenPosX()
                    val curY = ImGui.getCursorScreenPosY()
                    ImGui.dummy(ptw + 4f, ctrlH)
                    val textY = curY + (ctrlH - ImGui.getTextLineHeight()) * 0.5f
                    ImGui.getWindowDrawList().addText(curX + 2f, textY, ImGui.colorConvertFloat4ToU32(0.9f, 0.9f, 0.95f, 1f), pageText)
                }
                itemTooltip("Parameter page ${chain.focusParamPage + 1} of $totalPages.")

                ImGui.sameLine()
                if (ImGui.button("▶##focus_next_page_$bankId", ARROW_W, ctrlH)) {
                    FxMacroSync.stepParamPage(bankId, mixer, 1)
                }
                itemTooltip("Next parameter page.")
            }

            ImGui.sameLine()

            // 4. [Save] button
            drawSaveButton(session, chain, bankId, ctrlH, isDirty)

            ImGui.sameLine()

            // 5. [⋮] More actions menu
            drawMoreButton(session, mixer, chain, bankId, chainLabel, ctrlH, menuId)
        } else {
            // -- GROUP MODE HEADER ----------------------------------------------------------------
            // 1. [◀] Prev chain
            if (ImGui.button("◀##prev_chain_$bankId", ARROW_W, ctrlH)) {
                stepChain(session, chain, -1)
            }
            itemTooltip("Previous FX chain in folder.")

            ImGui.sameLine()

            // 2. Chain name button
            val slotPillsW = 20f * FxChain.SLOT_COUNT + gap * (FxChain.SLOT_COUNT - 1)
            val nameW = (maxW - (ARROW_W * 2f + SAVE_BTN_W + MORE_BTN_W + slotPillsW + gap * 6f)).coerceAtLeast(48f)
            drawChainNameButton(session, chain, bankId, ctrlH, nameW, isDirty, onOpenChainBrowse)

            ImGui.sameLine()

            // 3. [▶] Next chain
            if (ImGui.button("▶##next_chain_$bankId", ARROW_W, ctrlH)) {
                stepChain(session, chain, 1)
            }
            itemTooltip("Next FX chain in folder.")

            ImGui.sameLine()

            // 4. Slot focus pills [1] [2] [3]
            drawSlotPills(session, mixer, chain, bankId, ctrlH, null)

            ImGui.sameLine()

            // 5. [Save] button
            drawSaveButton(session, chain, bankId, ctrlH, isDirty)

            ImGui.sameLine()

            // 6. [⋮] More actions menu
            drawMoreButton(session, mixer, chain, bankId, chainLabel, ctrlH, menuId)
        }

        ImGui.popStyleVar()
    }

    private fun drawSlotPills(
        session: SessionContext,
        mixer: Mixer,
        chain: FxChain,
        bankId: String,
        ctrlH: Float,
        focusedSlot: Int?
    ) {
        val pillW = 20f
        for (i in 0 until FxChain.SLOT_COUNT) {
            if (i > 0) ImGui.sameLine()
            val isFocused = focusedSlot == i
            val slot = chain.slots.getOrNull(i)
            val slotNum = i + 1
            val btnLabel = if (isFocused) "●$slotNum" else "$slotNum"

            val isLight = session.uiTheme.theme == UITheme.Theme.ORANGE_SUNSHINE
            val activeCol = if (isLight) TangoPalette.u32(TangoPalette.ORANGE.normal) else TangoPalette.u32(TangoPalette.SYNC.normal, 0.95f)
            val inactiveCol = when {
                isLight && slot != null -> ImGui.getColorU32(ImGuiCol.Button)
                isLight -> ImGui.colorConvertFloat4ToU32(0.95f, 0.96f, 0.97f, 1f)
                slot != null -> ImGui.colorConvertFloat4ToU32(0.20f, 0.22f, 0.26f, 0.9f)
                else -> ImGui.colorConvertFloat4ToU32(0.14f, 0.15f, 0.18f, 0.6f)
            }
            val textCol = when {
                isFocused -> ImGui.colorConvertFloat4ToU32(1f, 1f, 1f, 1f)
                isLight -> if (slot != null) ImGui.getColorU32(ImGuiCol.Text) else ImGui.getColorU32(ImGuiCol.TextDisabled)
                else -> if (slot != null) ImGui.colorConvertFloat4ToU32(0.90f, 0.92f, 0.95f, 1f) else ImGui.colorConvertFloat4ToU32(0.55f, 0.58f, 0.62f, 1f)
            }

            ImGui.pushStyleColor(ImGuiCol.Button, if (isFocused) activeCol else inactiveCol)
            ImGui.pushStyleColor(ImGuiCol.Text, textCol)
            if (ImGui.button("$btnLabel##slot_focus_${bankId}_$i", pillW, ctrlH)) {
                if (isFocused) {
                    FxMacroSync.focusSlot(bankId, mixer, null)
                } else {
                    FxMacroSync.focusSlot(bankId, mixer, i)
                }
            }
            ImGui.popStyleColor(2)

            itemTooltip(
                when {
                    isFocused -> "Slot $slotNum (${slot?.displayName ?: "empty"}) is focused.\nClick to exit Focus Mode."
                    slot != null -> "Focus Slot $slotNum (${slot.displayName}).\nKnob 1 = Dry/Wet, Knobs 2-4 = top parameters."
                    else -> "Focus Slot $slotNum (empty).\nClick to focus and edit."
                }
            )
        }
    }

    private fun drawChainNameButton(
        session: SessionContext,
        chain: FxChain,
        bankId: String,
        ctrlH: Float,
        nameW: Float,
        isDirty: Boolean,
        onOpenChainBrowse: () -> Unit
    ) {
        val displayName = if (chain.name.isBlank()) "Untitled" else chain.name
        val dirtyMarker = if (isDirty) " •" else ""
        val fullLabel = "$displayName$dirtyMarker ${Icons.CHEVRON_DOWN}"

        val isLight = session.uiTheme.theme == UITheme.Theme.ORANGE_SUNSHINE
        if (isDirty) {
            val dirtyTextCol = if (isLight) TangoPalette.ALERT.dark else TangoPalette.ALERT.light
            ImGui.pushStyleColor(ImGuiCol.Text, dirtyTextCol[0], dirtyTextCol[1], dirtyTextCol[2], 1.0f)
        }
        if (ImGui.button("$fullLabel##fx_chain_name_$bankId", nameW, ctrlH)) {
            onOpenChainBrowse()
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
    }

    private fun drawSaveButton(session: SessionContext, chain: FxChain, bankId: String, ctrlH: Float, isDirty: Boolean) {
        val canOverwrite = chain.sourceFile != null
        val isLight = session.uiTheme.theme == UITheme.Theme.ORANGE_SUNSHINE
        val saveColRgb = when {
            isDirty -> TangoPalette.ALERT.dark
            isLight -> floatArrayOf(0.90f, 0.91f, 0.92f)
            else -> floatArrayOf(0.18f, 0.20f, 0.24f)
        }
        val saveCol = when {
            isDirty -> TangoPalette.u32(TangoPalette.ALERT.dark)
            isLight -> ImGui.getColorU32(ImGuiCol.Button)
            else -> ImGui.colorConvertFloat4ToU32(0.18f, 0.20f, 0.24f, 0.8f)
        }
        val ink = if (isLight && !isDirty) floatArrayOf(0.06f, 0.07f, 0.08f) else TangoPalette.inkFor(saveColRgb)
        ImGui.pushStyleColor(ImGuiCol.Button, saveCol)
        ImGui.pushStyleColor(ImGuiCol.Text, ink[0], ink[1], ink[2], 1.0f)
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
        ImGui.popStyleColor(2)
        itemTooltip(if (canOverwrite) "Save changes to ${chain.sourceFile?.name}." else "Save as new FX chain (.lsdfxchain).")
    }

    private fun drawMoreButton(
        session: SessionContext,
        mixer: Mixer,
        chain: FxChain,
        bankId: String,
        chainLabel: String,
        ctrlH: Float,
        menuId: String
    ) {
        if (ImGui.button("${Icons.MORE_VERTICAL}##more_btn_$bankId", MORE_BTN_W, ctrlH)) {
            ImGui.openPopup(menuId)
        }
        itemTooltip("Chain operations (Save As, New, Revert, Clear, Copy/Paste, Focus, Resync).")

        pushOpenDropdownPadding()
        if (ImGui.beginPopup(menuId)) {
            pushOpenDropdownFont()
            ImGui.textDisabled("$chainLabel FX Chain")
            ImGui.separator()

            if (chain.isFocused()) {
                if (ImGui.menuItem("Exit Focus Mode")) {
                    FxMacroSync.focusSlot(bankId, mixer, null)
                }
            } else {
                if (ImGui.beginMenu("Focus Slot…")) {
                    for (i in 0 until FxChain.SLOT_COUNT) {
                        val slot = chain.slots.getOrNull(i)
                        val label = "Slot ${i + 1}" + (slot?.displayName?.let { " ($it)" } ?: " (empty)")
                        if (ImGui.menuItem(label)) {
                            FxMacroSync.focusSlot(bankId, mixer, i)
                        }
                    }
                    ImGui.endMenu()
                }
            }
            ImGui.separator()

            if (ImGui.menuItem("Save As…")) {
                openSaveAsModal(session, chain)
            }
            ImGui.separator()

            if (ImGui.menuItem("New Chain")) {
                FxOps.newChain(chain)
            }
            val canRevert = chain.isDirty() && chain.baselineDto != null
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
            popOpenDropdownFont()
            ImGui.endPopup()
        }
        popOpenDropdownPadding()
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
     * Draws the top-level chain kill-switch for [chain]. Static "BYPASS" label (state shown by
     * color, not text) so it reads as a signal-chain switch, distinct from the [SRC]/[FX]
     * knob-assign pill this sits beside -- the pill picks *what the knobs show*, this picks
     * *whether the chain processes audio/video at all*.
     */
    fun drawBypassButton(session: SessionContext, chain: FxChain, id: String, ctrlH: Float, width: Float = 64f) {
        val isActive = chain.enabled
        val label = "BYPASS"

        PerformanceColors.pushActiveToggleStyle(isActive)
        session.uiTheme.withFont(UITheme.FontLevel.CAPTION) {
            if (ImGui.button("$label##bypass_$id", width, ctrlH)) {
                chain.enabled = !chain.enabled
            }
        }
        PerformanceColors.popActiveToggleStyle()
        itemTooltip(if (isActive) "FX chain is active. Click to bypass." else "FX chain is bypassed. Click to enable.")
    }
}
