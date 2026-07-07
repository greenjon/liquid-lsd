package llm.slop.spirals.ui

import imgui.ImGui
import imgui.flag.ImGuiCol
import imgui.flag.ImGuiWindowFlags
import imgui.type.ImBoolean
import llm.slop.spirals.config.ProjectConfig

object UiLabPanel {
    private val checkboxValue = ImBoolean(true)
    private val sliderValue = floatArrayOf(0.62f)
    private val meterValues = floatArrayOf(0.18f, 0.45f, 0.72f, 0.91f)

    fun draw(displayWidth: Float, displayHeight: Float) {
        val flags = ImGuiWindowFlags.NoResize or
            ImGuiWindowFlags.NoMove or
            ImGuiWindowFlags.NoCollapse or
            ImGuiWindowFlags.NoTitleBar

        ImGui.setNextWindowPos(0f, 0f)
        ImGui.setNextWindowSize(displayWidth, displayHeight)
        if (!ImGui.begin("UI Lab", flags)) {
            ImGui.end()
            return
        }

        drawHeader()
        ImGui.separator()
        ImGui.spacing()

        if (ImGui.beginTable("##ui_lab_layout", 3)) {
            ImGui.tableSetupColumn("Typography", imgui.flag.ImGuiTableColumnFlags.WidthStretch)
            ImGui.tableSetupColumn("Controls", imgui.flag.ImGuiTableColumnFlags.WidthStretch)
            ImGui.tableSetupColumn("Panels", imgui.flag.ImGuiTableColumnFlags.WidthStretch)
            ImGui.tableNextRow()

            ImGui.tableSetColumnIndex(0)
            drawTypographyColumn()

            ImGui.tableSetColumnIndex(1)
            drawControlsColumn()

            ImGui.tableSetColumnIndex(2)
            drawPanelColumn()

            ImGui.endTable()
        }

        ImGui.spacing()
        ImGui.separator()
        ImGui.spacing()
        drawFooterStrip()

        ImGui.end()
    }

    private fun drawHeader() {
        UITheme.h1(ProjectConfig.App.UI_LAB_TITLE)
        UITheme.caption("Deterministic composition for visual review, screenshot capture, and theme iteration.")
    }

    private fun drawTypographyColumn() {
        UITheme.h2("Type")
        ImGui.separator()
        UITheme.h1("H1 Performance Surface")
        UITheme.h2("H2 Mixer Monitor")
        UITheme.h3("H3 Deck Controls")
        UITheme.body("Body text should be legible without becoming bulky.")
        UITheme.caption("Caption text is for metadata, hints, and low-priority status.")
        UITheme.code("CODE  bpm=124.0  frame=016")

        ImGui.spacing()
        UITheme.h3("State Text")
        UITheme.bodyColored(0.25f, 0.9f, 0.45f, 1f, "ACTIVE")
        UITheme.bodyColored(1.0f, 0.65f, 0.18f, 1f, "WAITING")
        UITheme.bodyColored(1.0f, 0.32f, 0.32f, 1f, "DIRTY")
    }

    private fun drawControlsColumn() {
        UITheme.h2("Controls")
        ImGui.separator()

        if (ImGui.button("${Icons.PLAY} Play")) {
            sliderValue[0] = 0.75f
        }
        ImGui.sameLine()
        ImGui.button("${Icons.SAVE} Save")
        ImGui.sameLine()
        ImGui.button("${Icons.SHUFFLE} Shuffle")

        ImGui.spacing()
        ImGui.checkbox("Audio reactive", checkboxValue)
        ImGui.sliderFloat("Intensity", sliderValue, 0f, 1f, "%.2f")

        ImGui.spacing()
        UITheme.h3("Color Swatches")
        drawSwatch("Amplitude", 0.2f, 0.8f, 1.0f)
        drawSwatch("Bass", 1.0f, 0.3f, 0.6f)
        drawSwatch("Mid", 1.0f, 0.6f, 0.1f)
        drawSwatch("High", 0.1f, 0.9f, 0.8f)
    }

    private fun drawPanelColumn() {
        UITheme.h2("Panels")
        ImGui.separator()

        drawMiniPanel("Deck A", 0.2f, 0.75f, 0.95f, meterValues[0])
        drawMiniPanel("Deck B", 0.95f, 0.35f, 0.65f, meterValues[1])
        drawMiniPanel("Deck C", 0.95f, 0.8f, 0.25f, meterValues[2])

        ImGui.spacing()
        UITheme.h3("Queue")
        drawQueueRow("01", "aurora_fold.lsd", true)
        drawQueueRow("02", "glass_feedback.lsd", false)
        drawQueueRow("03", "night_drive.lsd", false)
    }

    private fun drawSwatch(label: String, r: Float, g: Float, b: Float) {
        val drawList = ImGui.getWindowDrawList()
        val x = ImGui.getCursorScreenPosX()
        val y = ImGui.getCursorScreenPosY()
        val size = ImGui.getFrameHeight()
        val color = ImGui.colorConvertFloat4ToU32(r, g, b, 1f)
        drawList.addRectFilled(x, y, x + size, y + size, color, 3f)
        ImGui.dummy(size, size)
        ImGui.sameLine()
        UITheme.body(label)
    }

    private fun drawMiniPanel(label: String, r: Float, g: Float, b: Float, value: Float) {
        ImGui.pushStyleColor(ImGuiCol.FrameBg, r * 0.18f, g * 0.18f, b * 0.18f, 1f)
        ImGui.pushStyleColor(ImGuiCol.PlotHistogram, r, g, b, 1f)
        UITheme.h3(label)
        ImGui.progressBar(value, ImGui.getContentRegionAvailX(), 18f)
        ImGui.popStyleColor(2)
        ImGui.spacing()
    }

    private fun drawQueueRow(index: String, name: String, active: Boolean) {
        if (active) {
            ImGui.pushStyleColor(ImGuiCol.Text, 0.3f, 0.95f, 0.55f, 1f)
        }
        UITheme.body("$index  $name")
        if (active) {
            ImGui.popStyleColor()
        }
    }

    private fun drawFooterStrip() {
        UITheme.caption("Run: ./gradlew run --args=\"--ui-lab --window=1440x900 --screenshot-ui=build/ui-lab.png\"")
    }
}
