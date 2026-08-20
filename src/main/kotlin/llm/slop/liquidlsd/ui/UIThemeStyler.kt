package llm.slop.liquidlsd.ui

import imgui.ImColor
import imgui.ImGui
import imgui.ImGuiStyle
import imgui.flag.ImGuiCol
import llm.slop.liquidlsd.SessionContext

/**
 * Handles ImGui theme color palettes, background gradients, and style size scaling.
 */
object UIThemeStyler {

    private var lastBgVideoEnabled: Boolean? = null
    private var lastTheme: UITheme.Theme? = null

    fun updateUiTransparency(session: SessionContext) {
        val enabled = session.uiTheme.backgroundVideoEnabled
        val theme = session.uiTheme.settings.theme
        if (enabled == lastBgVideoEnabled && theme == lastTheme) return
        lastBgVideoEnabled = enabled
        lastTheme = theme

        setupThemeColors(theme, enabled)
    }

    fun setupThemeColors(theme: UITheme.Theme, bgVideoEnabled: Boolean) {
        val style = ImGui.getStyle()
        val isLight = theme == UITheme.Theme.LIGHT_SOLARIZED || theme == UITheme.Theme.LIGHT_LUNARIZED

        if (isLight) {
            ImGui.styleColorsLight()
        } else {
            ImGui.styleColorsDark()
        }

        val alpha = if (bgVideoEnabled) 0.75f else 1.00f

        when (theme) {
            UITheme.Theme.BORING -> {
                if (bgVideoEnabled) {
                    style.setColor(ImGuiCol.WindowBg, 0.06f, 0.06f, 0.06f, 0.75f)
                    style.setColor(ImGuiCol.TitleBg, 0.04f, 0.04f, 0.04f, 0.75f)
                    style.setColor(ImGuiCol.TitleBgActive, 0.16f, 0.16f, 0.16f, 0.75f)
                    style.setColor(ImGuiCol.MenuBarBg, 0.14f, 0.14f, 0.14f, 0.75f)
                    style.setColor(ImGuiCol.PopupBg, 0.08f, 0.08f, 0.08f, 1.00f)
                } else {
                    style.setColor(ImGuiCol.WindowBg, 0.06f, 0.06f, 0.06f, 1.00f)
                    style.setColor(ImGuiCol.TitleBg, 0.04f, 0.04f, 0.04f, 1.00f)
                    style.setColor(ImGuiCol.TitleBgActive, 0.16f, 0.16f, 0.16f, 1.00f)
                    style.setColor(ImGuiCol.MenuBarBg, 0.14f, 0.14f, 0.14f, 1.00f)
                    style.setColor(ImGuiCol.PopupBg, 0.08f, 0.08f, 0.08f, 1.00f)
                }
            }
            UITheme.Theme.DARK_SOLARIZED -> {
                style.setColor(ImGuiCol.WindowBg, 0.00f, 0.17f, 0.21f, alpha)
                style.setColor(ImGuiCol.PopupBg, 0.03f, 0.21f, 0.26f, 1.00f)
                style.setColor(ImGuiCol.TitleBg, 0.03f, 0.21f, 0.26f, alpha)
                style.setColor(ImGuiCol.TitleBgActive, 0.00f, 0.17f, 0.21f, alpha)
                style.setColor(ImGuiCol.MenuBarBg, 0.03f, 0.21f, 0.26f, alpha)
                style.setColor(ImGuiCol.FrameBg, 0.03f, 0.21f, 0.26f, 1.00f)
                style.setColor(ImGuiCol.FrameBgHovered, 0.00f, 0.17f, 0.21f, 1.00f)
                style.setColor(ImGuiCol.FrameBgActive, 0.80f, 0.29f, 0.09f, 1.00f)
                style.setColor(ImGuiCol.Button, 0.03f, 0.21f, 0.26f, 1.00f)
                style.setColor(ImGuiCol.ButtonHovered, 0.35f, 0.43f, 0.46f, 1.00f)
                style.setColor(ImGuiCol.ButtonActive, 0.52f, 0.60f, 0.00f, 1.00f)
                style.setColor(ImGuiCol.SliderGrab, 0.80f, 0.29f, 0.09f, 1.00f)
                style.setColor(ImGuiCol.SliderGrabActive, 0.80f, 0.29f, 0.09f, 1.00f)
                style.setColor(ImGuiCol.CheckMark, 0.80f, 0.29f, 0.09f, 1.00f)
                style.setColor(ImGuiCol.Text, 0.51f, 0.58f, 0.59f, 1.00f)
                style.setColor(ImGuiCol.TextDisabled, 0.35f, 0.43f, 0.46f, 1.00f)
                style.setColor(ImGuiCol.Header, 0.03f, 0.21f, 0.26f, 1.00f)
                style.setColor(ImGuiCol.HeaderHovered, 0.35f, 0.43f, 0.46f, 1.00f)
                style.setColor(ImGuiCol.HeaderActive, 0.80f, 0.29f, 0.09f, 1.00f)
            }
            UITheme.Theme.LIGHT_SOLARIZED -> {
                style.setColor(ImGuiCol.WindowBg, 0.99f, 0.96f, 0.89f, alpha)
                style.setColor(ImGuiCol.PopupBg, 0.93f, 0.91f, 0.84f, 1.00f)
                style.setColor(ImGuiCol.TitleBg, 0.93f, 0.91f, 0.84f, alpha)
                style.setColor(ImGuiCol.TitleBgActive, 0.99f, 0.96f, 0.89f, alpha)
                style.setColor(ImGuiCol.MenuBarBg, 0.93f, 0.91f, 0.84f, alpha)

                style.setColor(ImGuiCol.FrameBg, 0.93f, 0.91f, 0.84f, 1.00f)
                style.setColor(ImGuiCol.FrameBgHovered, 0.99f, 0.96f, 0.89f, 1.00f)
                style.setColor(ImGuiCol.FrameBgActive, 0.17f, 0.63f, 0.60f, 1.00f)

                style.setColor(ImGuiCol.Button, 0.93f, 0.91f, 0.84f, 1.00f)
                style.setColor(ImGuiCol.ButtonHovered, 0.58f, 0.63f, 0.63f, 1.00f)
                style.setColor(ImGuiCol.ButtonActive, 0.83f, 0.21f, 0.51f, 1.00f)

                style.setColor(ImGuiCol.SliderGrab, 0.17f, 0.63f, 0.60f, 1.00f)
                style.setColor(ImGuiCol.SliderGrabActive, 0.83f, 0.21f, 0.51f, 1.00f)
                style.setColor(ImGuiCol.CheckMark, 0.52f, 0.60f, 0.00f, 1.00f)

                style.setColor(ImGuiCol.Text, 0.40f, 0.48f, 0.51f, 1.00f)
                style.setColor(ImGuiCol.TextDisabled, 0.58f, 0.63f, 0.63f, 1.00f)

                style.setColor(ImGuiCol.Header, 0.93f, 0.91f, 0.84f, 1.00f)
                style.setColor(ImGuiCol.HeaderHovered, 0.58f, 0.63f, 0.63f, 1.00f)
                style.setColor(ImGuiCol.HeaderActive, 0.17f, 0.63f, 0.60f, 1.00f)
            }
            UITheme.Theme.DARK_LUNARIZED -> {
                style.setColor(ImGuiCol.WindowBg, 0.21f, 0.04f, 0.00f, alpha)
                style.setColor(ImGuiCol.PopupBg, 0.28f, 0.07f, 0.00f, 1.00f)
                style.setColor(ImGuiCol.TitleBg, 0.28f, 0.07f, 0.00f, alpha)
                style.setColor(ImGuiCol.TitleBgActive, 0.21f, 0.04f, 0.00f, alpha)
                style.setColor(ImGuiCol.MenuBarBg, 0.28f, 0.07f, 0.00f, alpha)

                style.setColor(ImGuiCol.FrameBg, 0.28f, 0.07f, 0.00f, 1.00f)
                style.setColor(ImGuiCol.FrameBgHovered, 0.21f, 0.04f, 0.00f, 1.00f)
                style.setColor(ImGuiCol.FrameBgActive, 0.42f, 0.44f, 0.77f, 1.00f)

                style.setColor(ImGuiCol.Button, 0.28f, 0.07f, 0.00f, 1.00f)
                style.setColor(ImGuiCol.ButtonHovered, 0.37f, 0.16f, 0.08f, 1.00f)
                style.setColor(ImGuiCol.ButtonActive, 0.42f, 0.44f, 0.77f, 1.00f)

                style.setColor(ImGuiCol.SliderGrab, 0.42f, 0.44f, 0.77f, 1.00f)
                style.setColor(ImGuiCol.SliderGrabActive, 0.48f, 0.32f, 0.80f, 1.00f)
                style.setColor(ImGuiCol.CheckMark, 0.42f, 0.44f, 0.77f, 1.00f)

                style.setColor(ImGuiCol.Text, 0.97f, 0.91f, 0.88f, 1.00f)
                style.setColor(ImGuiCol.TextDisabled, 0.58f, 0.40f, 0.35f, 1.00f)

                style.setColor(ImGuiCol.Header, 0.28f, 0.07f, 0.00f, 1.00f)
                style.setColor(ImGuiCol.HeaderHovered, 0.37f, 0.16f, 0.08f, 1.00f)
                style.setColor(ImGuiCol.HeaderActive, 0.42f, 0.44f, 0.77f, 1.00f)
            }
            UITheme.Theme.LIGHT_LUNARIZED -> {
                style.setColor(ImGuiCol.WindowBg, 0.89f, 0.92f, 0.99f, alpha)
                style.setColor(ImGuiCol.PopupBg, 0.82f, 0.85f, 0.96f, 1.00f)
                style.setColor(ImGuiCol.TitleBg, 0.82f, 0.85f, 0.96f, alpha)
                style.setColor(ImGuiCol.TitleBgActive, 0.89f, 0.92f, 0.99f, alpha)
                style.setColor(ImGuiCol.MenuBarBg, 0.82f, 0.85f, 0.96f, alpha)

                style.setColor(ImGuiCol.FrameBg, 0.82f, 0.85f, 0.96f, 1.00f)
                style.setColor(ImGuiCol.FrameBgHovered, 0.89f, 0.92f, 0.99f, 1.00f)
                style.setColor(ImGuiCol.FrameBgActive, 0.11f, 0.37f, 0.89f, 1.00f)

                style.setColor(ImGuiCol.Button, 0.82f, 0.85f, 0.96f, 1.00f)
                style.setColor(ImGuiCol.ButtonHovered, 0.69f, 0.75f, 0.92f, 1.00f)
                style.setColor(ImGuiCol.ButtonActive, 0.11f, 0.37f, 0.89f, 1.00f)

                style.setColor(ImGuiCol.SliderGrab, 0.11f, 0.37f, 0.89f, 1.00f)
                style.setColor(ImGuiCol.SliderGrabActive, 0.00f, 0.64f, 0.80f, 1.00f)
                style.setColor(ImGuiCol.CheckMark, 0.00f, 0.64f, 0.80f, 1.00f)

                style.setColor(ImGuiCol.Text, 0.15f, 0.17f, 0.21f, 1.00f)
                style.setColor(ImGuiCol.TextDisabled, 0.47f, 0.50f, 0.61f, 1.00f)

                style.setColor(ImGuiCol.Header, 0.82f, 0.85f, 0.96f, 1.00f)
                style.setColor(ImGuiCol.HeaderHovered, 0.69f, 0.75f, 0.92f, 1.00f)
                style.setColor(ImGuiCol.HeaderActive, 0.11f, 0.37f, 0.89f, 1.00f)
            }
            UITheme.Theme.NEON -> {
                style.setColor(ImGuiCol.WindowBg, 0.00f, 0.00f, 0.00f, 0.00f)
                style.setColor(ImGuiCol.PopupBg, 0.05f, 0.01f, 0.08f, 1.00f)
                style.setColor(ImGuiCol.TitleBg, 0.04f, 0.04f, 0.10f, if (bgVideoEnabled) 0.65f else 0.90f)
                style.setColor(ImGuiCol.TitleBgActive, 0.08f, 0.00f, 0.14f, if (bgVideoEnabled) 0.65f else 0.90f)
                style.setColor(ImGuiCol.MenuBarBg, 0.04f, 0.04f, 0.10f, if (bgVideoEnabled) 0.65f else 0.90f)

                style.setColor(ImGuiCol.FrameBg, 0.11f, 0.05f, 0.16f, 1.00f)
                style.setColor(ImGuiCol.FrameBgHovered, 0.18f, 0.07f, 0.28f, 1.00f)
                style.setColor(ImGuiCol.FrameBgActive, 1.00f, 0.00f, 0.50f, 1.00f)

                style.setColor(ImGuiCol.Button, 0.13f, 0.02f, 0.20f, 1.00f)
                style.setColor(ImGuiCol.ButtonHovered, 1.00f, 0.00f, 0.50f, 1.00f)
                style.setColor(ImGuiCol.ButtonActive, 1.00f, 1.00f, 0.00f, 1.00f)

                style.setColor(ImGuiCol.SliderGrab, 1.00f, 0.00f, 0.50f, 1.00f)
                style.setColor(ImGuiCol.SliderGrabActive, 0.50f, 1.00f, 0.00f, 1.00f)
                style.setColor(ImGuiCol.CheckMark, 0.50f, 1.00f, 0.00f, 1.00f)

                style.setColor(ImGuiCol.Text, 1.00f, 1.00f, 1.00f, 1.00f)
                style.setColor(ImGuiCol.TextDisabled, 0.54f, 0.40f, 0.64f, 1.00f)

                style.setColor(ImGuiCol.Header, 0.13f, 0.02f, 0.20f, 1.00f)
                style.setColor(ImGuiCol.HeaderHovered, 1.00f, 0.00f, 0.50f, 1.00f)
                style.setColor(ImGuiCol.HeaderActive, 1.00f, 1.00f, 0.00f, 1.00f)
            }
        }

        style.setColor(ImGuiCol.ModalWindowDimBg, 0f, 0f, 0f, 0.72f)
    }

    fun drawNeonBackgroundIfNeeded(session: SessionContext, posX: Float, posY: Float, panelW: Float, panelH: Float, displayWidth: Float) {
        if (session.uiTheme.settings.theme != UITheme.Theme.NEON || displayWidth <= 0f) return
        val dl = ImGui.getWindowDrawList()

        fun getNeonBgColor(t: Float): Int {
            val r: Float
            val g: Float = 0.0f
            val b: Float
            if (t < 0.5f) {
                val fraction = t * 2f
                r = 0.01f + (0.85f - 0.01f) * fraction
                b = 0.14f + (0.42f - 0.14f) * fraction
            } else {
                val fraction = (t - 0.5f) * 2f
                r = 0.85f + (0.01f - 0.85f) * fraction
                b = 0.42f + (0.14f - 0.42f) * fraction
            }
            val alpha = if (session.uiTheme.backgroundVideoEnabled) 0.65f else 0.90f
            return ImColor.rgba(r, g, b, alpha)
        }

        val leftCol = getNeonBgColor((posX / displayWidth).coerceIn(0f, 1f)).toLong() and 0xFFFFFFFFL
        val rightCol = getNeonBgColor(((posX + panelW) / displayWidth).coerceIn(0f, 1f)).toLong() and 0xFFFFFFFFL

        dl.addRectFilledMultiColor(posX, posY, posX + panelW, posY + panelH, leftCol, rightCol, rightCol, leftCol)
    }

    fun copyStyleSizes(from: ImGuiStyle, to: ImGuiStyle) {
        to.setAlpha(from.getAlpha())
        to.setDisabledAlpha(from.getDisabledAlpha())
        to.setWindowPadding(from.getWindowPaddingX(), from.getWindowPaddingY())
        to.setWindowRounding(from.getWindowRounding())
        to.setWindowBorderSize(from.getWindowBorderSize())
        to.setWindowMinSize(from.getWindowMinSizeX(), from.getWindowMinSizeY())
        to.setWindowTitleAlign(from.getWindowTitleAlignX(), from.getWindowTitleAlignY())
        to.setWindowMenuButtonPosition(from.getWindowMenuButtonPosition())
        to.setChildRounding(from.getChildRounding())
        to.setChildBorderSize(from.getChildBorderSize())
        to.setPopupRounding(from.getPopupRounding())
        to.setPopupBorderSize(from.getPopupBorderSize())
        to.setFramePadding(from.getFramePaddingX(), from.getFramePaddingY())
        to.setFrameRounding(from.getFrameRounding())
        to.setFrameBorderSize(from.getFrameBorderSize())
        to.setItemSpacing(from.getItemSpacingX(), from.getItemSpacingY())
        to.setItemInnerSpacing(from.getItemInnerSpacingX(), from.getItemInnerSpacingY())
        to.setCellPadding(from.getCellPaddingX(), from.getCellPaddingY())
        to.setTouchExtraPadding(from.getTouchExtraPaddingX(), from.getTouchExtraPaddingY())
        to.setIndentSpacing(from.getIndentSpacing())
        to.setColumnsMinSpacing(from.getColumnsMinSpacing())
        to.setScrollbarSize(from.getScrollbarSize())
        to.setScrollbarRounding(from.getScrollbarRounding())
        to.setGrabMinSize(from.getGrabMinSize())
        to.setGrabRounding(from.getGrabRounding())
        to.setLogSliderDeadzone(from.getLogSliderDeadzone())
        to.setTabRounding(from.getTabRounding())
        to.setTabBorderSize(from.getTabBorderSize())
        to.setTabMinWidthForCloseButton(from.getTabMinWidthForCloseButton())
        to.setColorButtonPosition(from.getColorButtonPosition())
        to.setButtonTextAlign(from.getButtonTextAlignX(), from.getButtonTextAlignY())
        to.setSelectableTextAlign(from.getSelectableTextAlignX(), from.getSelectableTextAlignY())
        to.setDisplayWindowPadding(from.getDisplayWindowPaddingX(), from.getDisplayWindowPaddingY())
        to.setDisplaySafeAreaPadding(from.getDisplaySafeAreaPaddingX(), from.getDisplaySafeAreaPaddingY())
        to.setMouseCursorScale(from.getMouseCursorScale())
    }

    fun scaleStyleFromDefault(defaultStyle: ImGuiStyle, newSize: Float) {
        val style = ImGui.getStyle()
        copyStyleSizes(defaultStyle, style)
        val scale = newSize / 15f
        if (scale != 1f) {
            style.scaleAllSizes(scale)
        }
        // Safety guard: ensure critical sizes never underflow to or below 0.0f
        if (style.scrollbarSize <= 0.0f) {
            style.scrollbarSize = 1.0f
        }
        if (style.grabMinSize <= 0.0f) {
            style.grabMinSize = 1.0f
        }
    }
}
