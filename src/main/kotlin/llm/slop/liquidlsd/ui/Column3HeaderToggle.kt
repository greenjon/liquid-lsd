package llm.slop.liquidlsd.ui

import imgui.ImGui

/**
 * Header mode toggle at the top of Column 3: `[ MIXER | MACROS ]`.
 *
 * Toggles [UITheme.column3Mode] between [UITheme.Column3Mode.MIXER] and
 * [UITheme.Column3Mode.MACROS]. Saved to app preferences upon user click.
 */
object Column3HeaderToggle {
    fun draw(session: llm.slop.liquidlsd.SessionContext) {
        val availW = ImGui.getContentRegionAvailX().coerceAtLeast(2f)
        val btnH = session.uiTheme.withFont(UITheme.FontLevel.H3) { ImGui.getTextLineHeight() + 12f }.coerceAtLeast(28f)
        val gap = 3f
        val segW = ((availW - gap) / 2f).coerceAtLeast(1f)

        val startX = ImGui.getCursorScreenPosX()
        val startY = ImGui.getCursorScreenPosY()
        val dl = ImGui.getWindowDrawList()
        val current = session.uiTheme.column3Mode

        fun segmentColor(isSelected: Boolean, isActiveItem: Boolean, isHovered: Boolean): Int = when {
            isSelected -> ImGui.colorConvertFloat4ToU32(0.10f, 0.52f, 0.72f, 1f)
            isActiveItem -> ImGui.colorConvertFloat4ToU32(0.32f, 0.32f, 0.32f, 1f)
            isHovered -> ImGui.colorConvertFloat4ToU32(0.24f, 0.24f, 0.24f, 1f)
            else -> ImGui.colorConvertFloat4ToU32(0.14f, 0.14f, 0.14f, 1f)
        }

        // -- MIXER segment (left, rounded on the outer/left edge) --
        run {
            val pMinX = startX
            val pMaxX = startX + segW
            ImGui.setCursorScreenPos(pMinX, startY)
            ImGui.invisibleButton("##col3_mode_mixer", segW, btnH)
            val isHovered = ImGui.isItemHovered()
            val isActiveItem = ImGui.isItemActive()
            if (ImGui.isItemClicked(0) && current != UITheme.Column3Mode.MIXER) {
                session.uiTheme.column3Mode = UITheme.Column3Mode.MIXER
                AppPreferencesStore.savePreferences()
            }
            val isSelected = current == UITheme.Column3Mode.MIXER
            val bgCol = segmentColor(isSelected, isActiveItem, isHovered)
            dl.addRectFilled(pMinX, startY, pMaxX, startY + btnH, bgCol, 4f)
            dl.addRectFilled(pMaxX - 6f, startY, pMaxX, startY + btnH, bgCol, 0f)
            drawSegmentLabel(session, "MIXER", pMinX, startY, segW, btnH, isSelected)
            itemTooltip("4-deck crossfader mixer view.")
        }

        // -- MACROS segment (right, rounded on the outer/right edge) --
        run {
            val pMinX = startX + segW + gap
            val pMaxX = pMinX + segW
            ImGui.setCursorScreenPos(pMinX, startY)
            ImGui.invisibleButton("##col3_mode_macros", segW, btnH)
            val isHovered = ImGui.isItemHovered()
            val isActiveItem = ImGui.isItemActive()
            if (ImGui.isItemClicked(0) && current != UITheme.Column3Mode.MACROS) {
                session.uiTheme.column3Mode = UITheme.Column3Mode.MACROS
                AppPreferencesStore.savePreferences()
            }
            val isSelected = current == UITheme.Column3Mode.MACROS
            val bgCol = segmentColor(isSelected, isActiveItem, isHovered)
            dl.addRectFilled(pMinX, startY, pMaxX, startY + btnH, bgCol, 4f)
            dl.addRectFilled(pMinX, startY, pMinX + 6f, startY + btnH, bgCol, 0f)
            drawSegmentLabel(session, "MACROS", pMinX, startY, segW, btnH, isSelected)
            itemTooltip("Macro Knobs, Switches, and single-deck preview.")
        }

        ImGui.setCursorScreenPos(startX, startY + btnH)
        ImGui.dummy(0f, 0f)
    }

    private fun drawSegmentLabel(
        session: llm.slop.liquidlsd.SessionContext,
        text: String,
        x: Float,
        y: Float,
        w: Float,
        h: Float,
        isSelected: Boolean
    ) {
        var tw = 0f
        var th = 0f
        session.uiTheme.withFont(UITheme.FontLevel.H3) {
            val sz = ImGui.calcTextSize(text)
            tw = sz.x
            th = sz.y
        }
        val textX = x + (w - tw) / 2f
        val textY = y + (h - th) / 2f
        val col = ImGui.colorConvertFloat4ToU32(1f, 1f, 1f, if (isSelected) 1f else 0.8f)
        session.uiTheme.withFont(UITheme.FontLevel.H3) {
            ImGui.getWindowDrawList().addText(textX, textY, col, text)
        }
    }
}
