package llm.slop.liquidlsd.ui.browser

import imgui.ImGui
import imgui.flag.ImGuiCol
import llm.slop.liquidlsd.SessionContext
import llm.slop.liquidlsd.ui.ButtonChrome
import llm.slop.liquidlsd.ui.Icons
import llm.slop.liquidlsd.ui.UITheme
import llm.slop.liquidlsd.ui.itemTooltip

/**
 * Shared header of every Library queue column: a small caption naming the queue, then ONE row of
 * icon buttons: `◀ [auto] ▶  repeat shuffle export clear`. All buttons share one width so the row
 * reads as a strip. The Auto button is optional (preset/transition queues have one, FX queues don't).
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
        session.uiTheme.withFont(UITheme.FontLevel.BODY) {
            val textW = ImGui.calcTextSize(s.caption).x
            val x = ImGui.getCursorPosX() + (ImGui.getContentRegionAvailX() - textW) / 2f
            if (x > ImGui.getCursorPosX()) ImGui.setCursorPosX(x)
            ImGui.text(s.caption)
        }

        session.uiTheme.withFont(UITheme.FontLevel.BODY) {
            val glyphs = listOf("◀", "▶", Icons.PLAY, Icons.PAUSE, Icons.REPEAT, Icons.SHUFFLE, Icons.DOWNLOAD, Icons.TRASH)
            val btnW = glyphs.maxOf { ImGui.calcTextSize(it).x } + ImGui.getStyle().getFramePaddingX() * 2f
            // Buttons keep their natural width (glyphs stay centered); a narrow column trims the gaps instead.
            val count = if (s.auto != null) 7 else 6
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
            if (s.auto != null) {
                ImGui.sameLine(0f, gap)
                val icon = if (s.auto.active) Icons.PAUSE else Icons.PLAY
                button("$icon##${s.id}Auto", s.auto.tooltip, s.auto.lit, s.auto.onClick)
            }
            ImGui.sameLine(0f, gap)
            button("▶##${s.id}Next", s.nextTooltip, onClick = s.onNext)

            ImGui.sameLine(0f, gap)
            button("${Icons.REPEAT}##${s.id}Repeat", s.repeat.tooltip, s.repeat.active, s.repeat.onClick)
            ImGui.sameLine(0f, gap)
            button("${Icons.SHUFFLE}##${s.id}Shuffle", s.shuffle.tooltip, s.shuffle.active, s.shuffle.onClick)
            ImGui.sameLine(0f, gap)
            button("${Icons.DOWNLOAD}##${s.id}Export", s.exportTooltip, onClick = s.onExport)
            ImGui.sameLine(0f, gap)
            button("${Icons.TRASH}##${s.id}Clear", s.clearTooltip, onClick = s.onClear)

            s.trailing()
        }

        ImGui.separator()
        ImGui.spacing()
    }
}
