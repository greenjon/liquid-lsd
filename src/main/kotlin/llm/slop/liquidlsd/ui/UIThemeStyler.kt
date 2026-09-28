package llm.slop.liquidlsd.ui

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
        ImGui.styleColorsDark()

        val alpha = if (bgVideoEnabled) 0.75f else 1.00f

        style.setFrameBorderSize(1.0f)
        style.setFrameRounding(3.0f)
        style.setPopupBorderSize(1.0f)
        style.setPopupRounding(4.0f)

        val aluminium1 = TangoPalette.NEUTRAL_LIGHT
        val aluminium2 = TangoPalette.NEUTRAL_DARK
        val cyan = TangoPalette.SYNC

        when (theme) {
            UITheme.Theme.GREY_ACID -> {
                // Mixxx Tango-theme neutrals (Aluminium 1/2 charcoal/panel grays) with the Tango
                // sync/link cyan as the sole "acid" accent -- see TangoPalette.
                style.setColor(ImGuiCol.WindowBg, 0.10f, 0.10f, 0.10f, alpha) // #1A1A1A
                style.setColor(ImGuiCol.PopupBg, 0.17f, 0.17f, 0.17f, 1.00f) // #2B2B2B
                style.setColor(ImGuiCol.TitleBg, 0.07f, 0.07f, 0.07f, alpha) // #111111
                style.setColor(ImGuiCol.TitleBgActive, aluminium2.dark[0], aluminium2.dark[1], aluminium2.dark[2], alpha) // #2E3436
                style.setColor(ImGuiCol.MenuBarBg, 0.27f, 0.27f, 0.27f, alpha) // #444444

                style.setColor(ImGuiCol.FrameBg, 0.24f, 0.24f, 0.23f, 1.00f) // #3E3D3A
                style.setColor(ImGuiCol.FrameBgHovered, aluminium2.normal[0], aluminium2.normal[1], aluminium2.normal[2], 1.00f) // #555753
                style.setColor(ImGuiCol.FrameBgActive, cyan.normal[0], cyan.normal[1], cyan.normal[2], 1.00f) // #06AFDF

                style.setColor(ImGuiCol.Border, 0.09f, 0.09f, 0.09f, 0.90f) // #181818
                style.setColor(ImGuiCol.BorderShadow, 0.00f, 0.00f, 0.00f, 0.00f)

                style.setColor(ImGuiCol.Button, 0.24f, 0.24f, 0.23f, 1.00f) // #3E3D3A
                style.setColor(ImGuiCol.ButtonHovered, aluminium2.normal[0], aluminium2.normal[1], aluminium2.normal[2], 1.00f) // #555753
                style.setColor(ImGuiCol.ButtonActive, cyan.bright[0], cyan.bright[1], cyan.bright[2], 1.00f) // #34E2E2

                style.setColor(ImGuiCol.CheckMark, cyan.normal[0], cyan.normal[1], cyan.normal[2], 1.00f) // #06AFDF
                style.setColor(ImGuiCol.SliderGrab, cyan.normal[0], cyan.normal[1], cyan.normal[2], 1.00f) // #06AFDF
                style.setColor(ImGuiCol.SliderGrabActive, cyan.bright[0], cyan.bright[1], cyan.bright[2], 1.00f) // #34E2E2

                style.setColor(ImGuiCol.Header, 0.24f, 0.23f, 0.22f, 1.00f) // #3C3B37
                style.setColor(ImGuiCol.HeaderHovered, aluminium2.normal[0], aluminium2.normal[1], aluminium2.normal[2], 1.00f) // #555753
                style.setColor(ImGuiCol.HeaderActive, cyan.normal[0], cyan.normal[1], cyan.normal[2], 1.00f) // #06AFDF

                style.setColor(ImGuiCol.Text, aluminium1.normal[0], aluminium1.normal[1], aluminium1.normal[2], 1.00f) // #D3D7CF
                style.setColor(ImGuiCol.TextDisabled, aluminium2.light[0], aluminium2.light[1], aluminium2.light[2], 1.00f) // #888A85
            }
        }

        style.setColor(ImGuiCol.ModalWindowDimBg, 0f, 0f, 0f, 0.72f)

        // Capture the base text and border colours for this theme so tooltips can restore them
        // regardless of any per-widget ImGuiCol.Text / ImGuiCol.Border pushes active at call time
        // (e.g. BrowserDeckButtons pushes both Text and Border to deck accent colours).
        // Values mirror the colours set in the when(theme) block above.
        TooltipHelper.baseTextColor = when (theme) {
            UITheme.Theme.GREY_ACID -> TangoPalette.u32(aluminium1.normal)
        }
        TooltipHelper.baseBorderColor = when (theme) {
            UITheme.Theme.GREY_ACID -> ImGui.colorConvertFloat4ToU32(0.09f, 0.09f, 0.09f, 0.90f)
        }
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
        to.setColorButtonPosition(from.getColorButtonPosition())
        to.setButtonTextAlign(from.getButtonTextAlignX(), from.getButtonTextAlignY())
        to.setSelectableTextAlign(from.getSelectableTextAlignX(), from.getSelectableTextAlignY())
        to.setDisplayWindowPadding(from.getDisplayWindowPaddingX(), from.getDisplayWindowPaddingY())
        to.setDisplaySafeAreaPadding(from.getDisplaySafeAreaPaddingX(), from.getDisplaySafeAreaPaddingY())
        to.setMouseCursorScale(from.getMouseCursorScale())
        to.setSeparatorSize(from.getSeparatorSize().coerceAtLeast(1.0f))
        to.setSeparatorTextBorderSize(from.getSeparatorTextBorderSize().coerceAtLeast(1.0f))
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
        if (style.separatorSize <= 0.0f) {
            style.separatorSize = 1.0f
        }
        if (style.separatorTextBorderSize <= 0.0f) {
            style.separatorTextBorderSize = 1.0f
        }
    }
}
