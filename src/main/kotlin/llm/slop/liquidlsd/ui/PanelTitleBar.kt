package llm.slop.liquidlsd.ui

import imgui.ImGui
import imgui.flag.ImGuiStyleVar
import llm.slop.liquidlsd.SessionContext

/**
 * Shared title bar helper for primary Suite C workspace panels (Parameters, Properties).
 * Ensures panel menu bars maintain synchronized 1.5x height, frame padding, typography,
 * and optical vertical text centering.
 */
object PanelTitleBar {

    /**
     * Sizing factor scaling the panel title bar relative to base frame height.
     */
    const val HEIGHT_SCALE = 1.5f

    /**
     * Optical vertical offset applied to H3 title text to compensate for font internal ascender
     * headroom and baseline positioning, keeping space above and below text visually balanced.
     */
    const val TEXT_Y_OPTICAL_OFFSET = 3.0f

    /**
     * Calculates the standard 1.5x scaled title bar height.
     */
    fun calculateHeight(session: SessionContext): Float {
        val baseH = session.uiTheme.withFont(UITheme.FontLevel.BODY) { ImGui.getFrameHeight() }
        return baseH * HEIGHT_SCALE
    }

    /**
     * Calculates the frame padding Y required before [ImGui.begin] with [imgui.flag.ImGuiWindowFlags.MenuBar]
     * to size the window menu bar to [calculateHeight].
     */
    fun calculateFramePaddingY(session: SessionContext): Float {
        val targetH = calculateHeight(session)
        val fontSize = session.uiTheme.withFont(UITheme.FontLevel.BODY) { ImGui.getFontSize() }
        return ((targetH - fontSize) * 0.5f).coerceAtLeast(0f)
    }

    /**
     * Executes [block] with the panel title bar [ImGuiStyleVar.FramePadding] pushed,
     * ensuring it is always popped safely.
     */
    inline fun withFramePadding(session: SessionContext, block: () -> Unit) {
        val padY = calculateFramePaddingY(session)
        ImGui.pushStyleVar(ImGuiStyleVar.FramePadding, ImGui.getStyle().getFramePaddingX(), padY)
        try {
            block()
        } finally {
            ImGui.popStyleVar()
        }
    }

    /**
     * Calculates the vertical cursor position for H3 title text inside the menu bar.
     */
    fun calculateTextYOffset(session: SessionContext, menuBarH: Float): Float {
        var txtH = 0f
        session.uiTheme.withFont(UITheme.FontLevel.H3) { txtH = ImGui.getTextLineHeight() }
        return (((menuBarH - txtH) * 0.5f) - TEXT_Y_OPTICAL_OFFSET).coerceAtLeast(0f)
    }

    /**
     * Draws a synchronized panel title bar inside a window with [imgui.flag.ImGuiWindowFlags.MenuBar].
     *
     * @param session The active session context.
     * @param title The panel title string (rendered in [UITheme.FontLevel.H3]).
     * @param extraSpacing Horizontal spacing before any trailing elements.
     * @param drawExtra Optional trailing content lambda (e.g., video source selector tab in Parameters).
     */
    fun draw(
        session: SessionContext,
        title: String,
        extraSpacing: Float = 24f,
        drawExtra: ((menuBarH: Float, btnH: Float, btnYOffset: Float) -> Unit)? = null
    ) {
        if (ImGui.beginMenuBar()) {
            val menuBarH = session.uiTheme.withFont(UITheme.FontLevel.BODY) { ImGui.getFrameHeight() }
            val btnH = (menuBarH - 6f).coerceAtLeast(22f)
            val btnYOffset = ((menuBarH - btnH) * 0.5f).coerceAtLeast(0f)

            val textYOffset = calculateTextYOffset(session, menuBarH)
            ImGui.setCursorPosY(textYOffset)
            session.uiTheme.h3(title)

            if (drawExtra != null) {
                ImGui.sameLine(0f, extraSpacing)
                ImGui.setCursorPosY(btnYOffset)
                drawExtra(menuBarH, btnH, btnYOffset)
            }

            ImGui.endMenuBar()
        }
    }
}
