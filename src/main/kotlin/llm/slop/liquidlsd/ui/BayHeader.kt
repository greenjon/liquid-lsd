package llm.slop.liquidlsd.ui

import imgui.ImGui
import imgui.flag.ImGuiCol
import llm.slop.liquidlsd.SessionContext
import llm.slop.liquidlsd.presets.QueueNextUp
import llm.slop.liquidlsd.rendering.Mixer

/**
 * The header line at the top of the bay, drawn the same way in the pair view and in the Edit view so switching between them moves
 * nothing: `[Back] [Browse | Params]  ● <target> [row buttons]`, with what the queue feeding the pair plays next right-aligned.
 * The pair view's tab strip and list, or the Edit view's editor, follow under it. Its height never depends on what it shows.
 */
internal object BayHeader {
    /** What the header is about: [label] with its [accent] dot, a hover [tooltip], and row buttons ([actions], e.g. Save, Clear Slot). [paused] greys it. */
    class Chip(val label: String, val accent: FloatArray?, val tooltip: String, val actions: (() -> Unit)? = null, val paused: Boolean = false)

    /**
     * Draws the line and the separator under it. [browsing] says which half of the Browse|Params toggle is lit; [onToggle] switches to the
     * other view, null when it has none (the toggle's other half is then disabled, with [toggleUnavailable] as its tooltip). [queueDeck]
     * is the deck label whose queue line to show ("Deck BG": the BG queue; "Deck PV": none; anything else, or null: the A/B play queue).
     */
    fun draw(
        session: SessionContext,
        parametersState: ParametersState,
        mixer: Mixer,
        browsing: Boolean,
        chip: Chip,
        queueDeck: String?,
        onToggle: (() -> Unit)?,
        toggleTooltip: String,
        toggleUnavailable: String = toggleTooltip
    ) {
        val y0 = ImGui.getCursorPosY()
        var lineH = 0f
        // The row buttons (Save, Clear Slot) draw in this font too, so the whole line reads as one.
        session.uiTheme.withFont(UITheme.FontLevel.TOOLTIP) {
            lineH = ImGui.getFrameHeight()
            if (ButtonChrome.button("Back##bay_back", 0f, lineH)) BackNavigation.back(parametersState, mixer)
            itemTooltip(if (browsing) "Back to the Perform view (Esc)." else "Close the editor, back to the Perform view (Esc).")
            ImGui.sameLine(0f, 10f)
            ImGui.beginGroup()
            segment("Browse##bay_browse", lit = browsing, enabled = browsing || onToggle != null, lineH) { onToggle?.invoke() }
            itemTooltip(
                if (browsing) "Showing the list for ${chip.label}: a click applies a row." else if (onToggle != null) toggleTooltip else toggleUnavailable,
                allowWhenDisabled = true
            )
            ImGui.sameLine(0f, 2f)
            segment("Params##bay_params", lit = !browsing, enabled = !browsing || onToggle != null, lineH) { onToggle?.invoke() }
            itemTooltip(
                if (!browsing) "Showing the parameters of ${chip.label}." else if (onToggle != null) toggleTooltip else toggleUnavailable,
                allowWhenDisabled = true
            )
            ImGui.endGroup()
            ImGui.sameLine(0f, 14f)
            drawChip(chip, lineH)
            drawQueueNextUp(session, queueDeck)
        }
        ImGui.setCursorPosY(y0 + lineH)
        ImGui.spacing()
        ImGui.separator()
        ImGui.spacing()
    }

    /** One half of the Browse|Params toggle, lit like a Library tab when it is the current view. */
    private inline fun segment(label: String, lit: Boolean, enabled: Boolean, h: Float, onClick: () -> Unit) {
        ButtonChrome.pushColor(if (lit) TangoPalette.MODE_ACTIVE.u32() else TangoPalette.MODE_INACTIVE.u32())
        ImGui.pushStyleColor(ImGuiCol.Text, if (lit) TangoPalette.MODE_ACTIVE_TEXT.u32() else ImGui.getColorU32(ImGuiCol.Text))
        if (!enabled) ImGui.beginDisabled(true)
        if (ButtonChrome.button(label, 64f, h) && !lit) onClick()
        if (!enabled) ImGui.endDisabled()
        ImGui.popStyleColor(4)
    }

    /**
     * `● label [row buttons]`. Greyed, without its buttons, while [Chip.paused], so the binding reads as paused, not gone. Also drawn on
     * the Perform dock's tab line while it is bound in place ([LibraryPanel.draw]), so the two places look the same; [showActions] false
     * leaves the row buttons out where the line has no room for them.
     */
    fun drawChip(chip: Chip, lineH: Float, showActions: Boolean = true) {
        ImGui.beginGroup()
        val dot = if (!chip.paused && chip.accent != null) TangoPalette.u32(chip.accent) else ImGui.getColorU32(ImGuiCol.TextDisabled)
        val cx = ImGui.getCursorScreenPosX()
        val cy = ImGui.getCursorScreenPosY() + lineH * 0.5f
        ImGui.getWindowDrawList().addCircleFilled(cx + 5f, cy, 4f, dot)
        ImGui.dummy(14f, lineH)
        ImGui.sameLine(0f, 0f)
        ImGui.alignTextToFramePadding()
        if (chip.paused) ImGui.textDisabled("${chip.label} (paused)") else ImGui.text(chip.label)
        ImGui.endGroup()
        itemTooltip(chip.tooltip)
        if (!chip.paused && showActions) chip.actions?.let { ImGui.sameLine(0f, 10f); it() }
    }

    /**
     * Right-aligned and ellipsized to the room left on the line: what the queue feeding this pair plays next. Decks A/B, Master and the
     * transition show the play queue, BG its own queue; PV has none. The queue columns themselves are on the Browse view's Queues tab.
     */
    private fun drawQueueNextUp(session: SessionContext, deckLabel: String?) {
        val text = when (deckLabel) {
            "Deck PV" -> return
            "Deck BG" -> session.bgQueueManager.let {
                QueueNextUp.describe(it.queue, it.activeIndex, it.isShuffleEnabled, it.isRepeatEnabled)
            }
            else -> session.playQueueManager.let {
                val staged = when {
                    it.stagedDeckA -> "A"
                    it.stagedDeckB -> "B"
                    else -> null
                }
                QueueNextUp.describe(it.queue, it.activeIndex, it.isShuffleEnabled, it.isRepeatEnabled, staged)
            }
        }
        val left = ImGui.getItemRectMaxX() - ImGui.getWindowPosX() + 16f
        val right = ImGui.getWindowWidth() - ImGui.getStyle().windowPaddingX
        if (right - left < 60f) return
        val fitted = TextFit.ellipsize(text, right - left)
        ImGui.sameLine()
        ImGui.setCursorPosX((right - ImGui.calcTextSize(fitted).x).coerceAtLeast(left))
        ImGui.alignTextToFramePadding()
        ImGui.textDisabled(fitted)
        itemTooltip("$text\nThe queues themselves are on the Browse view's Queues tab.")
    }
}
