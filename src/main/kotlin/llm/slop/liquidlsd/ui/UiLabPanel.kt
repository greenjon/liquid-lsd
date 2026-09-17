package llm.slop.liquidlsd.ui

import imgui.ImGui
import imgui.flag.ImGuiCol
import imgui.flag.ImGuiWindowFlags

/**
 * Isolated UI Lab component gallery sandbox for verifying theme styling, icons,
 * sliders, color swatches, and custom controls without requiring audio/GL hardware.
 */
class UiLabPanel {

    private var sampleSliderVal = 0.5f
    private var sampleBeatVal = 4
    private var sampleWaveShape = WaveShape.SINE

    fun render(width: Float, height: Float) {
        ImGui.setNextWindowPos(0f, 0f)
        ImGui.setNextWindowSize(width, height)

        val windowFlags = ImGuiWindowFlags.NoTitleBar or
                ImGuiWindowFlags.NoResize or
                ImGuiWindowFlags.NoMove or
                ImGuiWindowFlags.NoCollapse or
                ImGuiWindowFlags.NoBringToFrontOnFocus

        if (ImGui.begin("##UiLabWindow", windowFlags)) {
            ImGui.textColored(1.0f, 0.8f, 0.2f, 1.0f, "Liquid LSD UI Lab - Component Gallery & Theme Sandbox")
            ImGui.separator()
            ImGui.spacing()

            if (ImGui.beginChild("##UiLabScrollArea", 0f, 0f, true)) {

                // --- SECTION 1: THEME & COLOR SWATCHES ---
                if (ImGui.collapsingHeader("1. Theme Color Swatches & Metrics", imgui.flag.ImGuiTreeNodeFlags.DefaultOpen)) {
                    ImGui.text("Primary & Surface Colors:")
                    renderColorSwatch("WindowBg", ImGuiCol.WindowBg)
                    ImGui.sameLine()
                    renderColorSwatch("ChildBg", ImGuiCol.ChildBg)
                    ImGui.sameLine()
                    renderColorSwatch("PopupBg", ImGuiCol.PopupBg)
                    ImGui.sameLine()
                    renderColorSwatch("FrameBg", ImGuiCol.FrameBg)
                    ImGui.sameLine()
                    renderColorSwatch("TitleBgActive", ImGuiCol.TitleBgActive)

                    ImGui.spacing()
                    ImGui.text("Interactions & Accents:")
                    renderColorSwatch("Button", ImGuiCol.Button)
                    ImGui.sameLine()
                    renderColorSwatch("ButtonHovered", ImGuiCol.ButtonHovered)
                    ImGui.sameLine()
                    renderColorSwatch("ButtonActive", ImGuiCol.ButtonActive)
                    ImGui.sameLine()
                    renderColorSwatch("Header", ImGuiCol.Header)
                    ImGui.sameLine()
                    renderColorSwatch("HeaderHovered", ImGuiCol.HeaderHovered)

                    ImGui.spacing()
                    ImGui.text("Active UI Scaling: ${UITheme.presetNameScalePercent}% | Render Dimensions: ${UITheme.renderWidth}x${UITheme.renderHeight}")
                }

                ImGui.spacing()

                // --- SECTION 2: ICON CATALOG ---
                if (ImGui.collapsingHeader("2. Lucide & Typography Icon Catalog", imgui.flag.ImGuiTreeNodeFlags.DefaultOpen)) {
                    val icons = listOf(
                        "SETTINGS" to Icons.SETTINGS,
                        "POWER" to Icons.POWER,
                        "TRASH" to Icons.TRASH,
                        "DICES" to Icons.DICES,
                        "FOLDER" to Icons.FOLDER,
                        "FILE" to Icons.FILE,
                        "ACTIVITY" to Icons.ACTIVITY,
                        "ZAP" to Icons.ZAP,
                        "SEARCH" to Icons.SEARCH,
                        "REFRESH" to Icons.REFRESH,
                        "PLUS" to Icons.PLUS,
                        "MINUS" to Icons.MINUS,
                        "PLAY" to Icons.PLAY,
                        "PAUSE" to Icons.PAUSE,
                        "ALERT" to Icons.ALERT,
                        "INFO" to Icons.INFO,
                        "SAVE" to Icons.SAVE,
                        "DOWNLOAD" to Icons.DOWNLOAD,
                        "DISC" to Icons.DISC,
                        "VOLUME" to Icons.VOLUME,
                        "BOT" to Icons.BOT,
                        "LOCK" to Icons.LOCK,
                        "NOTE" to Icons.NOTE
                    )

                    var col = 0
                    for ((name, symbol) in icons) {
                        ImGui.text("$symbol $name")
                        col++
                        if (col % 4 != 0) {
                            ImGui.sameLine(col * 160f)
                        } else {
                            col = 0
                        }
                    }
                    if (col != 0) ImGui.newLine()
                }

                ImGui.spacing()

                // --- SECTION 3: CUSTOM WIDGETS & WAVEFORMS ---
                if (ImGui.collapsingHeader("3. Waveform Buttons & Custom Sliders", imgui.flag.ImGuiTreeNodeFlags.DefaultOpen)) {
                    ImGui.text("Waveform Selectors:")
                    val shapes = WaveShape.entries
                    for (shape in shapes) {
                        val selected = (sampleWaveShape == shape)
                        val themeCol = imgui.ImColor.rgba(0.2f, 0.8f, 1.0f, 1.0f)
                        if (CustomIconButton.drawWaveformButton("##wave_${shape.name}", shape, selected, themeCol, 36f, 28f)) {
                            sampleWaveShape = shape
                        }
                        ImGui.sameLine()
                    }
                    ImGui.newLine()
                    ImGui.text("Active Shape: ${sampleWaveShape.name}")

                    ImGui.spacing()
                    ImGui.text("Standard & Custom Range Sliders:")
                    val arr = floatArrayOf(sampleSliderVal)
                    if (ImGui.sliderFloat("##sample_slider", arr, 0f, 1f, "Value: %.2f")) {
                        sampleSliderVal = arr[0]
                    }

                    ImGui.spacing()
                    ImGui.text("Beat Division Selector:")
                    val divisions = intArrayOf(1, 2, 4, 8, 16, 32)
                    for (div in divisions) {
                        val active = (sampleBeatVal == div)
                        if (active) {
                            ImGui.pushStyleColor(ImGuiCol.Button, 0.2f, 0.6f, 0.9f, 1.0f)
                        }
                        if (ImGui.button("1/$div", 50f, 24f)) {
                            sampleBeatVal = div
                        }
                        if (active) {
                            ImGui.popStyleColor()
                        }
                        ImGui.sameLine()
                    }
                    ImGui.newLine()
                }

                ImGui.spacing()

                // --- SECTION 4: STATUS METERS & CARDS ---
                if (ImGui.collapsingHeader("4. Status Meters & Indicators", imgui.flag.ImGuiTreeNodeFlags.DefaultOpen)) {
                    ImGui.text("Sample VU / Level Meters:")
                    ImGui.progressBar(0.72f, 200f, 18f, "L: -3.2 dB")
                    ImGui.sameLine()
                    ImGui.progressBar(0.65f, 200f, 18f, "R: -4.8 dB")

                    ImGui.spacing()
                    ImGui.text("Status Badges:")
                    ImGui.button("${Icons.ZAP} AUDIO ONLINE", 130f, 24f)
                    ImGui.sameLine()
                    ImGui.button("${Icons.ACTIVITY} LINK SYNCED", 130f, 24f)
                    ImGui.sameLine()
                    ImGui.button("${Icons.DISC} RECORDING", 120f, 24f)
                }

            }
            ImGui.endChild()
        }
        ImGui.end()
    }

    private fun renderColorSwatch(label: String, colorIdx: Int) {
        val color = ImGui.getStyle().getColor(colorIdx)
        val rgba = floatArrayOf(color.x, color.y, color.z, color.w)
        ImGui.colorButton("##swatch_$label", rgba, imgui.flag.ImGuiColorEditFlags.NoTooltip, 20f, 20f)
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(label)
        }
    }
}
