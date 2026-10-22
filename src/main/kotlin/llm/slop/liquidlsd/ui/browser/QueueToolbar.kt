package llm.slop.liquidlsd.ui.browser

import imgui.ImGui
import imgui.flag.ImGuiCol
import llm.slop.liquidlsd.SessionContext
import llm.slop.liquidlsd.ui.ButtonChrome
import llm.slop.liquidlsd.ui.Icons
import llm.slop.liquidlsd.ui.UITheme
import llm.slop.liquidlsd.ui.itemTooltip
import llm.slop.liquidlsd.ui.pushOpenDropdownPadding
import llm.slop.liquidlsd.ui.popOpenDropdownPadding
import llm.slop.liquidlsd.ui.pushOpenDropdownFont
import llm.slop.liquidlsd.ui.popOpenDropdownFont
import llm.slop.liquidlsd.ui.shortcuts.ShortcutManager

/**
 * Shared header of every Library queue column: a small caption naming the queue, then ONE row of
 * icon buttons: `◀ [auto] ▶  repeat shuffle`. All buttons share one width so the row reads as a strip. The Auto
 * button is optional (preset/transition queues have one, FX queues don't and omit the slot). A kebab at the right of the
 * caption row holds Export and Clear.
 */
object QueueToolbar {
    /** Mint-green lit toggle, one definition for the whole strip. */
    private const val MINT_R = 0.4f
    private const val MINT_G = 1.0f
    private const val MINT_B = 0.8f

    class Toggle(val active: Boolean, val tooltip: String, val onClick: () -> Unit)

    /** The play/pause Auto button; [lit] tints it mint like the other toggles. */
    class Auto(val active: Boolean, val tooltip: String, val lit: Boolean = false, val onClick: () -> Unit)

    class Spec(
        val caption: String,
        /** Unique suffix for ImGui ids, e.g. "fxBg". */
        val id: String,
        val prevTooltip: String,
        val onPrev: () -> Unit,
        val nextTooltip: String,
        val onNext: () -> Unit,
        val auto: Auto? = null,
        val repeat: Toggle,
        val shuffle: Toggle,
        val exportTooltip: String,
        val onExport: () -> Unit,
        val clearTooltip: String,
        val onClear: () -> Unit,
        /** Runs inside the toolbar's font scope after the buttons, e.g. to draw a modal opened by [onExport]. */
        val trailing: () -> Unit = {}
    )

    fun draw(session: SessionContext, s: Spec) {
        ImGui.alignTextToFramePadding()
        var exportRequested = false
        val popupId = "##${s.id}QueueMenu"
        session.uiTheme.withFont(UITheme.FontLevel.BODY) {
            val startX = ImGui.getCursorPosX()
            val availW = ImGui.getContentRegionAvailX()
            val kebabW = ImGui.getFrameHeight()
            val textW = ImGui.calcTextSize(s.caption).x
            val x = startX + (availW - textW) / 2f
            if (x > startX) ImGui.setCursorPosX(x)
            ImGui.text(s.caption)

            // Export and Clear are rare (and Clear destructive), so they live in a menu rather than on the transport strip.
            ImGui.sameLine()
            ImGui.setCursorPosX(startX + availW - kebabW)
            BrowserRowMoreButton.draw(popupId, true, false, "${s.id}Queue", kebabW, ImGui.getFrameHeight(), "Queue options...")
            pushOpenDropdownPadding()
            if (ImGui.beginPopup(popupId)) {
                pushOpenDropdownFont()
                if (ImGui.menuItem("Export as playlist...")) exportRequested = true
                if (ImGui.menuItem("Clear queue")) s.onClear()
                popOpenDropdownFont()
                ImGui.endPopup()
            }
            popOpenDropdownPadding()
        }

        session.uiTheme.withFont(UITheme.FontLevel.BODY) {
            // Opened here, not inside the menu item: openPopup from within a popup lands on the wrong ID-stack level.
            if (exportRequested) s.onExport()

            val glyphs = listOf("◀", "▶", Icons.PLAY, Icons.PAUSE, Icons.REPEAT, Icons.SHUFFLE)
            val btnW = glyphs.maxOf { ImGui.calcTextSize(it).x } + ImGui.getStyle().getFramePaddingX() * 2f
            // Buttons keep their natural width (glyphs stay centered); a narrow column trims the gaps instead.
            val count = if (s.auto != null) 5 else 4
            val gap = ((ImGui.getContentRegionAvailX() - btnW * count) / (count - 1))
                .coerceIn(0f, ImGui.getStyle().getItemSpacingX())

            fun button(label: String, tooltip: String, lit: Boolean = false, onClick: () -> Unit) {
                if (lit) {
                    ImGui.pushStyleColor(ImGuiCol.Text, MINT_R, MINT_G, MINT_B, 1.0f)
                    ButtonChrome.pushColor(0.1f, 0.4f, 0.3f, 1.0f)
                }
                if (ButtonChrome.button(label, btnW, 0f)) onClick()
                if (lit) ImGui.popStyleColor(4)
                itemTooltip(tooltip)
            }

            button("◀##${s.id}Prev", s.prevTooltip, onClick = s.onPrev)
            ImGui.sameLine(0f, gap)
            if (s.auto != null) {
                val icon = if (s.auto.active) Icons.PAUSE else Icons.PLAY
                button("$icon##${s.id}Auto", s.auto.tooltip, s.auto.lit, s.auto.onClick)
                ImGui.sameLine(0f, gap)
            }
            button("▶##${s.id}Next", s.nextTooltip, onClick = s.onNext)

            ImGui.sameLine(0f, gap)
            button("${Icons.REPEAT}##${s.id}Repeat", s.repeat.tooltip, s.repeat.active, s.repeat.onClick)
            ImGui.sameLine(0f, gap)
            button("${Icons.SHUFFLE}##${s.id}Shuffle", s.shuffle.tooltip, s.shuffle.active, s.shuffle.onClick)

            s.trailing()
        }

        ImGui.separator()
        ImGui.spacing()
    }

    /**
     * The muted hint an empty queue column shows instead of a blank list, naming the enqueue key ([bg]: the BG queue's). Call at
     * the top of the column's item list.
     */
    fun drawEmptyHint(isEmpty: Boolean, bg: Boolean = false) {
        if (!isEmpty) return
        val key = ShortcutManager.getAction(if (bg) "library.queue_bg" else "library.queue_ab")?.currentKey
            ?.takeIf { !it.isEmpty }?.toDisplayString()
        val press = if (key != null) ", or select one and press $key" else ""
        ImGui.pushStyleColor(ImGuiCol.Text, ImGui.getColorU32(ImGuiCol.TextDisabled))
        ImGui.textWrapped("Empty. Drag items here$press.")
        ImGui.popStyleColor()
    }
}
