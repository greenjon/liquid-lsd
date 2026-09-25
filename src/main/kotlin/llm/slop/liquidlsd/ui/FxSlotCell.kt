package llm.slop.liquidlsd.ui

import imgui.ImGui
import imgui.flag.ImGuiMouseButton
import llm.slop.liquidlsd.SessionContext
import llm.slop.liquidlsd.macro.FxMacroSync
import llm.slop.liquidlsd.models.ClipboardManager
import llm.slop.liquidlsd.presets.FxOps
import llm.slop.liquidlsd.presets.FxShortlist
import llm.slop.liquidlsd.rendering.FxChain
import llm.slop.liquidlsd.rendering.Mixer
import java.io.File

/**
 * One FX slot's "major switches", drawn as the caption line under that slot's knob on every
 * Performance FX row (deck rows and the Master row in FX mode):
 *
 *   `[●] [◀]  Effect Name  [▶]`
 *
 * - **●** turns the slot on/off (instantly).
 * - **◀ / ▶**, or the mouse wheel over the name, step through the [FxShortlist].
 * - **Name** click opens the shader picker (stock filters, ★ favorites, saved single FX).
 * - **Drag** a cell onto another cell to swap them (reorder within a chain, or trade between
 *   chains); hold Ctrl while dropping to copy instead. Library items drop onto a cell too:
 *   stock filters and saved `.lsdfx` replace the slot, a `.lsdfxchain` replaces the chain.
 * - **Right-click** for Replace, Save as FX Preset, Copy/Paste, Reset, Clear, favorite, Deep Edit.
 *
 * Every change goes through [FxOps], so it lands on the GL thread behind the swap fade and
 * re-syncs the row's knobs.
 */
object FxSlotCell {
    /** Drag payload for a slot cell: "<fx bank id>|<slot index>". */
    const val PAYLOAD_SLOT = "FX_SLOT"

    /** Drag payload for a stock ISF filter from the Library's FX browser: the filter id. */
    const val PAYLOAD_STOCK_FILTER = "ISF_FILTER"

    const val HEIGHT = 20f

    private const val ARROW_LEFT = "◀"
    private const val ARROW_RIGHT = "▶"
    private const val ELLIPSIS = "…"

    private const val PILL_W = 16f
    private const val ARROW_W = 16f

    private var wheelAccum = 0f

    /** Opens the FX shader picker for slot [slotIndex] of [chain], applying whatever is picked. */
    fun openPicker(session: SessionContext, chain: FxChain, slotIndex: Int, title: String) {
        ShaderPickerPopup.showFx(title, slotIndex) { pick ->
            when (pick) {
                is ShaderPickerPopup.FxPick.Stock -> FxOps.setSlotFilter(chain, slotIndex, pick.filterId)
                is ShaderPickerPopup.FxPick.Saved -> FxOps.loadSlot(session, pick.file, chain, slotIndex)
                ShaderPickerPopup.FxPick.None -> FxOps.clearSlot(chain, slotIndex)
            }
        }
    }

    /**
     * Draws the cell for slot [slotIndex] of the chain behind FX bank [bankId] at ([x], [y]),
     * [w] wide. [chainLabel] is the chain's display name ("Deck A", "Master") for titles/tooltips;
     * [onEditInDeepEdit] opens that row's Deep Edit.
     */
    fun draw(
        session: SessionContext,
        mixer: Mixer,
        bankId: String,
        chainLabel: String,
        slotIndex: Int,
        x: Float,
        y: Float,
        w: Float,
        accent: FloatArray,
        onEditInDeepEdit: () -> Unit
    ) {
        val chain = FxMacroSync.chainFor(bankId, mixer) ?: return
        val fx = chain.slots[slotIndex]
        val isOn = fx != null && fx.enabled
        val slotNum = slotIndex + 1
        val idBase = "fxcell_${bankId}_$slotIndex"
        val dl = ImGui.getWindowDrawList()
        val h = HEIGHT

        val bgAlpha = if (fx == null) 0.25f else 0.45f
        dl.addRectFilled(x, y, x + w, y + h, ImGui.colorConvertFloat4ToU32(0.08f, 0.09f, 0.11f, bgAlpha), 4f)

        // -- ● on/off -------------------------------------------------------------------------
        ImGui.setCursorScreenPos(x, y)
        if (ImGui.invisibleButton("##pill_$idBase", PILL_W, h) && fx != null) {
            FxOps.setSlotEnabled(chain, slotIndex, !fx.enabled)
        }
        val pillHovered = ImGui.isItemHovered()
        itemTooltip(
            when {
                fx == null -> "Slot $slotNum is empty."
                isOn -> "Slot $slotNum (${fx.displayName}) is on. Click to bypass just this effect."
                else -> "Slot $slotNum (${fx.displayName}) is bypassed. Click to turn it back on."
            }
        )
        val pcx = x + PILL_W / 2f + 1f
        val pcy = y + h / 2f
        val pillR = 4.5f
        val accentCol = ImGui.colorConvertFloat4ToU32(accent[0], accent[1], accent[2], if (pillHovered) 1f else 0.9f)
        when {
            fx == null -> dl.addCircle(pcx, pcy, pillR, ImGui.colorConvertFloat4ToU32(0.4f, 0.4f, 0.45f, 0.5f), 12, 1f)
            isOn -> dl.addCircleFilled(pcx, pcy, pillR, accentCol, 12)
            else -> dl.addCircle(pcx, pcy, pillR, ImGui.colorConvertFloat4ToU32(0.85f, 0.3f, 0.3f, 0.95f), 12, 1.5f)
        }

        // -- ◀ -----------------------------------------------------------------------------
        val prevX = x + PILL_W
        drawArrow(session, "##prev_$idBase", ARROW_LEFT, prevX, y, h) { FxOps.stepSlot(chain, slotIndex, -1) }
        itemTooltip("Previous effect in the FX shortlist (★ favorites, or this effect's category).")

        // -- name ---------------------------------------------------------------------------
        val nameX = prevX + ARROW_W
        val nameW = (w - (nameX - x) - ARROW_W).coerceAtLeast(8f)
        ImGui.setCursorScreenPos(nameX, y)
        ImGui.invisibleButton("##name_$idBase", nameW, h)
        val nameHovered = ImGui.isItemHovered()
        if (nameHovered && ImGui.isMouseDoubleClicked(ImGuiMouseButton.Left)) {
            val targetFocus = if (chain.focusedSlot == slotIndex) null else slotIndex
            FxMacroSync.focusSlot(bankId, mixer, targetFocus)
        } else if (ImGui.isItemClicked(ImGuiMouseButton.Left)) {
            openPicker(session, chain, slotIndex, "Select FX Slot $slotNum for $chainLabel FX")
        }
        if (ImGui.isItemClicked(ImGuiMouseButton.Right)) {
            ImGui.openPopup("##menu_$idBase")
        }
        if (nameHovered) {
            val io = ImGui.getIO()
            if (io.mouseWheel != 0f) {
                wheelAccum += io.mouseWheel
                io.mouseWheel = 0f
                while (wheelAccum >= 1f) { FxOps.stepSlot(chain, slotIndex, -1); wheelAccum -= 1f }
                while (wheelAccum <= -1f) { FxOps.stepSlot(chain, slotIndex, 1); wheelAccum += 1f }
            }
        }
        val isThisSlotFocused = chain.focusedSlot == slotIndex
        itemTooltip(
            if (fx == null) "Slot $slotNum is empty.\nClick to pick an effect, double-click to focus, scroll to step through shortlist, or drop an effect here. Right-click for more."
            else "${fx.displayName}${fx.categories.firstOrNull()?.let { "  ($it)" } ?: ""}\n" +
                 (if (isThisSlotFocused) "● FOCUSED: Knob 1 = Dry/Wet, Knobs 2-4 = Parameters.\n" else "") +
                 "Double-click to ${if (isThisSlotFocused) "exit Focus Mode" else "focus on this effect"}.\n" +
                 "Click to pick another effect, scroll to step through shortlist.\n" +
                 "Drag onto another slot to swap (Ctrl: copy). Right-click for more."
        )
        drawDragAndDrop(session, mixer, bankId, chain, slotIndex, fx?.displayName)

        val nameText = fx?.displayName ?: "— empty —"
        session.uiTheme.withFont(UITheme.FontLevel.CAPTION) {
            val shown = truncate(nameText, nameW - 4f)
            val tw = ImGui.calcTextSize(shown).x
            val th = ImGui.getTextLineHeight()
            val textCol = when {
                fx == null -> ImGui.colorConvertFloat4ToU32(0.5f, 0.5f, 0.55f, 0.8f)
                !fx.enabled -> ImGui.colorConvertFloat4ToU32(0.55f, 0.55f, 0.6f, 0.75f)
                isThisSlotFocused -> ImGui.colorConvertFloat4ToU32(1f, 0.85f, 0.45f, 1f)
                nameHovered -> ImGui.colorConvertFloat4ToU32(1f, 1f, 1f, 1f)
                else -> ImGui.colorConvertFloat4ToU32(0.85f, 0.85f, 0.88f, 0.95f)
            }
            dl.addText(nameX + (nameW - tw) / 2f, y + (h - th) / 2f, textCol, shown)
        }

        // -- ▶ -----------------------------------------------------------------------------
        drawArrow(session, "##next_$idBase", ARROW_RIGHT, nameX + nameW, y, h) { FxOps.stepSlot(chain, slotIndex, 1) }
        itemTooltip("Next effect in the FX shortlist (★ favorites, or this effect's category).")

        drawContextMenu(session, mixer, bankId, chain, chainLabel, slotIndex, "##menu_$idBase", onEditInDeepEdit)
    }

    private fun drawArrow(session: SessionContext, id: String, glyph: String, ax: Float, ay: Float, h: Float, onClick: () -> Unit) {
        ImGui.setCursorScreenPos(ax, ay)
        if (ImGui.invisibleButton(id, ARROW_W, h)) onClick()
        val hovered = ImGui.isItemHovered()
        val col = if (hovered) ImGui.colorConvertFloat4ToU32(1f, 1f, 1f, 1f) else ImGui.colorConvertFloat4ToU32(0.6f, 0.62f, 0.68f, 0.85f)
        session.uiTheme.withFont(UITheme.FontLevel.CAPTION) {
            val sz = ImGui.calcTextSize(glyph)
            ImGui.getWindowDrawList().addText(ax + (ARROW_W - sz.x) / 2f, ay + (h - sz.y) / 2f, col, glyph)
        }
    }

    /** Shortens [text] with an ellipsis until it fits [maxW] in the current font. */
    private fun truncate(text: String, maxW: Float): String {
        if (ImGui.calcTextSize(text).x <= maxW) return text
        var end = text.length
        while (end > 1 && ImGui.calcTextSize(text.substring(0, end) + ELLIPSIS).x > maxW) end--
        return text.substring(0, end) + ELLIPSIS
    }

    private fun drawDragAndDrop(session: SessionContext, mixer: Mixer, bankId: String, chain: FxChain, slotIndex: Int, fxName: String?) {
        if (fxName != null && ImGui.beginDragDropSource()) {
            ImGui.setDragDropPayload(PAYLOAD_SLOT, "$bankId|$slotIndex" as Any)
            ImGui.textUnformatted("$fxName  (Ctrl: copy)")
            ImGui.endDragDropSource()
        }
        if (!ImGui.beginDragDropTarget()) return
        ImGui.acceptDragDropPayload<String>(PAYLOAD_SLOT)?.let { payload ->
            val sourceBankId = payload.substringBefore('|')
            val sourceSlot = payload.substringAfter('|').toIntOrNull()
            val sourceChain = FxMacroSync.chainFor(sourceBankId, mixer)
            if (sourceChain != null && sourceSlot != null) {
                FxOps.swapSlots(sourceChain, sourceSlot, chain, slotIndex, copy = ImGui.getIO().keyCtrl)
            }
        }
        ImGui.acceptDragDropPayload<String>(PAYLOAD_STOCK_FILTER)?.let { filterId ->
            FxOps.setSlotFilter(chain, slotIndex, filterId)
        }
        ImGui.acceptDragDropPayload<String>("ASSET_ITEM")?.let { path ->
            val file = File(path)
            when (file.extension.lowercase()) {
                "lsdfx" -> FxOps.loadSlot(session, file, chain, slotIndex)
                "lsdfxchain" -> FxOps.loadChain(session, file, chain)
            }
        }
        ImGui.endDragDropTarget()
    }

    private fun drawContextMenu(
        session: SessionContext,
        mixer: Mixer,
        bankId: String,
        chain: FxChain,
        chainLabel: String,
        slotIndex: Int,
        popupId: String,
        onEditInDeepEdit: () -> Unit
    ) {
        if (!ImGui.beginPopup(popupId)) return
        val fx = chain.slots[slotIndex]
        val slotNum = slotIndex + 1
        val isFocused = chain.focusedSlot == slotIndex
        ImGui.textDisabled("$chainLabel FX — Slot $slotNum" + (if (isFocused) " (Focused)" else ""))
        ImGui.separator()
        if (ImGui.menuItem(if (isFocused) "Exit Focus Mode" else "Focus Mode (Edit Parameters)")) {
            FxMacroSync.focusSlot(bankId, mixer, if (isFocused) null else slotIndex)
        }
        ImGui.separator()
        if (ImGui.menuItem("Replace…")) {
            openPicker(session, chain, slotIndex, "Select FX Slot $slotNum for $chainLabel FX")
        }
        if (ImGui.menuItem("Save as FX Preset…", "", false, fx != null)) {
            chain.toFxSlotDto(slotIndex)?.let { slotDto ->
                SavePresetModal.request(
                    title = "Save FX Slot Preset As",
                    confirmLabel = "Save",
                    defaultName = fx?.displayName?.lowercase()?.replace(" ", "_")?.ifBlank { null } ?: "fx_preset",
                    targetDir = FileSystemManager.getFxPresetsRoot(),
                    extension = "lsdfx"
                ) { name, tags ->
                    val file = File(FileSystemManager.getFxPresetsRoot(), "$name.lsdfx")
                    session.presetRepository.saveFxPresetAsync(file, name, slotDto, tags)
                }
            }
        }
        ImGui.separator()
        if (ImGui.menuItem("Copy Slot", "", false, fx != null)) {
            chain.toFxSlotDto(slotIndex)?.let { ClipboardManager.copyFxSlot(it) }
        }
        if (ImGui.menuItem("Paste Slot", "", false, ClipboardManager.fxSlotClipboard != null)) {
            ClipboardManager.fxSlotClipboard?.let { FxOps.applySlot(chain, slotIndex, it) }
        }
        if (ImGui.menuItem("Reset Parameters", "", false, fx != null)) {
            FxOps.resetSlot(chain, slotIndex)
        }
        if (ImGui.menuItem("Clear Slot", "", false, fx != null)) {
            FxOps.clearSlot(chain, slotIndex)
        }
        if (fx != null) {
            ImGui.separator()
            val starred = FxShortlist.isFavorite(fx.id)
            if (ImGui.menuItem(if (starred) "★ Remove from FX Shortlist" else "☆ Add to FX Shortlist")) {
                FxShortlist.toggle(fx.id)
            }
        }
        ImGui.separator()
        if (ImGui.menuItem("Edit in Deep Edit")) onEditInDeepEdit()
        ImGui.endPopup()
    }
}
