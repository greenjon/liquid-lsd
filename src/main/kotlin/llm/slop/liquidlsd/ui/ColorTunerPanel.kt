package llm.slop.liquidlsd.ui

import imgui.ImColor
import imgui.ImGui
import imgui.flag.ImGuiCol
import imgui.flag.ImGuiCond
import imgui.flag.ImGuiTableColumnFlags
import imgui.flag.ImGuiTableFlags
import imgui.flag.ImGuiWindowFlags
import imgui.type.ImBoolean
import imgui.type.ImInt
import llm.slop.liquidlsd.SessionContext
import mu.KotlinLogging

/**
 * Interactive developer tool to dial in theme color assignments live across palettes.
 * Rendered as a non-modal floating window without background dimming.
 */
object ColorTunerPanel {

    private val logger = KotlinLogging.logger {}

    var isOpen = false
    private val isOpenImBool = ImBoolean(false)

    data class Swatch(
        val id: String,
        val name: String,
        val r: Float,
        val g: Float,
        val b: Float,
        val hex: String
    ) {
        val u32: Int get() = ImColor.rgba(r, g, b, 1.0f)

        companion object {
            fun fromHex(id: String, name: String, hexCode: String): Swatch {
                val clean = hexCode.removePrefix("#")
                val num = clean.toInt(16)
                val r = ((num shr 16) and 0xFF) / 255f
                val g = ((num shr 8) and 0xFF) / 255f
                val b = (num and 0xFF) / 255f
                return Swatch(id, name, r, g, b, "#" + clean.lowercase())
            }

            fun fromRgb(id: String, name: String, r: Float, g: Float, b: Float): Swatch {
                val hex = String.format("#%02x%02x%02x", (r * 255).toInt().coerceIn(0, 255), (g * 255).toInt().coerceIn(0, 255), (b * 255).toInt().coerceIn(0, 255))
                return Swatch(id, name, r, g, b, hex)
            }
        }
    }

    data class Palette(
        val theme: UITheme.Theme,
        val name: String,
        val isLight: Boolean,
        val swatches: List<Swatch>,
        val defaultAssignments: Map<Int, Pair<String, Boolean>> // ImGuiCol -> (swatchId, respectsAlpha)
    )

    data class ElementDef(
        val colId: Int,
        val name: String,
        val category: String,
        val defaultAlphaSensitive: Boolean = false
    )

    private val ELEMENTS = listOf(
        // Surfaces & Chrome
        ElementDef(ImGuiCol.WindowBg, "WindowBg", "Surfaces & Chrome", true),
        ElementDef(ImGuiCol.PopupBg, "PopupBg", "Surfaces & Chrome", false),
        ElementDef(ImGuiCol.TitleBg, "TitleBg", "Surfaces & Chrome", true),
        ElementDef(ImGuiCol.TitleBgActive, "TitleBgActive", "Surfaces & Chrome", true),
        ElementDef(ImGuiCol.MenuBarBg, "MenuBarBg", "Surfaces & Chrome", true),

        // Frames & Inputs
        ElementDef(ImGuiCol.FrameBg, "FrameBg", "Frames & Inputs", false),
        ElementDef(ImGuiCol.FrameBgHovered, "FrameBgHovered", "Frames & Inputs", false),
        ElementDef(ImGuiCol.FrameBgActive, "FrameBgActive", "Frames & Inputs", false),

        // Buttons
        ElementDef(ImGuiCol.Button, "Button", "Buttons", false),
        ElementDef(ImGuiCol.ButtonHovered, "ButtonHovered", "Buttons", false),
        ElementDef(ImGuiCol.ButtonActive, "ButtonActive", "Buttons", false),

        // Sliders & Checkmarks
        ElementDef(ImGuiCol.SliderGrab, "SliderGrab", "Sliders & Checks", false),
        ElementDef(ImGuiCol.SliderGrabActive, "SliderGrabActive", "Sliders & Checks", false),
        ElementDef(ImGuiCol.CheckMark, "CheckMark", "Sliders & Checks", false),

        // Typography
        ElementDef(ImGuiCol.Text, "Text", "Typography", false),
        ElementDef(ImGuiCol.TextDisabled, "TextDisabled", "Typography", false),

        // Headers
        ElementDef(ImGuiCol.Header, "Header", "Headers", false),
        ElementDef(ImGuiCol.HeaderHovered, "HeaderHovered", "Headers", false),
        ElementDef(ImGuiCol.HeaderActive, "HeaderActive", "Headers", false)
    )

    // Palette Definitions
    private val SOLARIZED_SWATCHES = listOf(
        Swatch.fromHex("base03", "Base03", "#002b36"),
        Swatch.fromHex("base02", "Base02", "#073642"),
        Swatch.fromHex("base01", "Base01", "#586e75"),
        Swatch.fromHex("base00", "Base00", "#657b83"),
        Swatch.fromHex("base0",  "Base0",  "#839496"),
        Swatch.fromHex("base1",  "Base1",  "#93a1a1"),
        Swatch.fromHex("base2",  "Base2",  "#eee8d5"),
        Swatch.fromHex("base3",  "Base3",  "#fdf6e3"),
        Swatch.fromHex("red",    "Red",    "#dc322f"),
        Swatch.fromHex("orange", "Orange", "#cb4b16"),
        Swatch.fromHex("yellow", "Yellow", "#b58900"),
        Swatch.fromHex("green",  "Green",  "#859900"),
        Swatch.fromHex("cyan",   "Cyan",   "#2aa198"),
        Swatch.fromHex("blue",   "Blue",   "#268bd2"),
        Swatch.fromHex("violet", "Violet", "#6c71c4"),
        Swatch.fromHex("magenta","Magenta","#d33682")
    )

    private val LUNARIZED_SWATCHES = listOf(
        Swatch.fromHex("base03", "Base03", "#360b00"),
        Swatch.fromHex("base02", "Base02", "#421307"),
        Swatch.fromHex("base01", "Base01", "#755f58"),
        Swatch.fromHex("base00", "Base00", "#836d65"),
        Swatch.fromHex("base0",  "Base0",  "#968583"),
        Swatch.fromHex("base1",  "Base1",  "#a19393"),
        Swatch.fromHex("base2",  "Base2",  "#d5dbee"),
        Swatch.fromHex("base3",  "Base3",  "#e3eafd"),
        Swatch.fromHex("red",    "Red",    "#d55e67"),
        Swatch.fromHex("orange", "Orange", "#d9742d"),
        Swatch.fromHex("yellow", "Yellow", "#938e3b"),
        Swatch.fromHex("green",  "Green",  "#2cc97d"),
        Swatch.fromHex("cyan",   "Cyan",   "#23cdd0"),
        Swatch.fromHex("blue",   "Blue",   "#34b4e9"),
        Swatch.fromHex("indigo", "Indigo", "#4a76ff"),
        Swatch.fromHex("violet", "Violet", "#7a66ff")
    )

    private val NEON_SWATCHES = listOf(
        Swatch.fromRgb("transparent","Transparent",0.00f, 0.00f, 0.00f),
        Swatch.fromRgb("indigoDark", "IndigoDark", 0.04f, 0.04f, 0.10f),
        Swatch.fromRgb("indigoMid",  "IndigoMid",  0.08f, 0.00f, 0.14f),
        Swatch.fromRgb("purpleDeep", "PurpleDeep", 0.13f, 0.02f, 0.20f),
        Swatch.fromRgb("frameDark",  "FrameDark",  0.11f, 0.05f, 0.16f),
        Swatch.fromRgb("frameHover", "FrameHover", 0.18f, 0.07f, 0.28f),
        Swatch.fromRgb("popupDark",  "PopupDark",  0.05f, 0.01f, 0.08f),
        Swatch.fromRgb("hotPink",    "HotPink",    1.00f, 0.00f, 0.50f),
        Swatch.fromRgb("neonYellow", "NeonYellow", 1.00f, 1.00f, 0.00f),
        Swatch.fromRgb("neonGreen",  "NeonGreen",  0.50f, 1.00f, 0.00f),
        Swatch.fromRgb("pureWhite",  "PureWhite",  1.00f, 1.00f, 1.00f),
        Swatch.fromRgb("mutedViolet","MutedViolet",0.54f, 0.40f, 0.64f)
    )

    private val BORING_SWATCHES = listOf(
        Swatch.fromRgb("black04",   "Black04",   0.04f, 0.04f, 0.04f),
        Swatch.fromRgb("black06",   "Black06",   0.06f, 0.06f, 0.06f),
        Swatch.fromRgb("black08",   "Black08",   0.08f, 0.08f, 0.08f),
        Swatch.fromRgb("gray14",    "Gray14",    0.14f, 0.14f, 0.14f),
        Swatch.fromRgb("gray16",    "Gray16",    0.16f, 0.16f, 0.16f),
        Swatch.fromRgb("gray30",    "Gray30",    0.30f, 0.30f, 0.30f),
        Swatch.fromRgb("gray50",    "Gray50",    0.50f, 0.50f, 0.50f),
        Swatch.fromRgb("gray80",    "Gray80",    0.80f, 0.80f, 0.80f),
        Swatch.fromRgb("white",     "White",     1.00f, 1.00f, 1.00f),
        Swatch.fromRgb("blueAccent","BlueAccent",0.26f, 0.59f, 0.98f)
    )

    val PALETTES = listOf(
        Palette(
            theme = UITheme.Theme.DARK_SOLARIZED,
            name = "Dark Solarized",
            isLight = false,
            swatches = SOLARIZED_SWATCHES,
            defaultAssignments = mapOf(
                ImGuiCol.WindowBg to Pair("base03", true),
                ImGuiCol.PopupBg to Pair("base02", false),
                ImGuiCol.TitleBg to Pair("base02", true),
                ImGuiCol.TitleBgActive to Pair("base03", true),
                ImGuiCol.MenuBarBg to Pair("base02", true),
                ImGuiCol.FrameBg to Pair("base02", false),
                ImGuiCol.FrameBgHovered to Pair("base03", false),
                ImGuiCol.FrameBgActive to Pair("orange", false),
                ImGuiCol.Button to Pair("base02", false),
                ImGuiCol.ButtonHovered to Pair("base01", false),
                ImGuiCol.ButtonActive to Pair("orange", false),
                ImGuiCol.SliderGrab to Pair("orange", false),
                ImGuiCol.SliderGrabActive to Pair("orange", false),
                ImGuiCol.CheckMark to Pair("orange", false),
                ImGuiCol.Text to Pair("base0", false),
                ImGuiCol.TextDisabled to Pair("base01", false),
                ImGuiCol.Header to Pair("base02", false),
                ImGuiCol.HeaderHovered to Pair("base01", false),
                ImGuiCol.HeaderActive to Pair("orange", false)
            )
        ),
        Palette(
            theme = UITheme.Theme.LIGHT_SOLARIZED,
            name = "Light Solarized",
            isLight = true,
            swatches = SOLARIZED_SWATCHES,
            defaultAssignments = mapOf(
                ImGuiCol.WindowBg to Pair("base3", true),
                ImGuiCol.PopupBg to Pair("base2", false),
                ImGuiCol.TitleBg to Pair("base2", true),
                ImGuiCol.TitleBgActive to Pair("base3", true),
                ImGuiCol.MenuBarBg to Pair("base2", true),
                ImGuiCol.FrameBg to Pair("base2", false),
                ImGuiCol.FrameBgHovered to Pair("base3", false),
                ImGuiCol.FrameBgActive to Pair("cyan", false),
                ImGuiCol.Button to Pair("base2", false),
                ImGuiCol.ButtonHovered to Pair("base1", false),
                ImGuiCol.ButtonActive to Pair("magenta", false),
                ImGuiCol.SliderGrab to Pair("cyan", false),
                ImGuiCol.SliderGrabActive to Pair("magenta", false),
                ImGuiCol.CheckMark to Pair("green", false),
                ImGuiCol.Text to Pair("base00", false),
                ImGuiCol.TextDisabled to Pair("base1", false),
                ImGuiCol.Header to Pair("base2", false),
                ImGuiCol.HeaderHovered to Pair("base1", false),
                ImGuiCol.HeaderActive to Pair("cyan", false)
            )
        ),
        Palette(
            theme = UITheme.Theme.DARK_LUNARIZED,
            name = "Dark Lunarized",
            isLight = false,
            swatches = LUNARIZED_SWATCHES,
            defaultAssignments = mapOf(
                ImGuiCol.WindowBg to Pair("base03", true),
                ImGuiCol.PopupBg to Pair("base02", false),
                ImGuiCol.TitleBg to Pair("base02", true),
                ImGuiCol.TitleBgActive to Pair("base03", true),
                ImGuiCol.MenuBarBg to Pair("base02", true),
                ImGuiCol.FrameBg to Pair("base02", false),
                ImGuiCol.FrameBgHovered to Pair("base03", false),
                ImGuiCol.FrameBgActive to Pair("indigo", false),
                ImGuiCol.Button to Pair("base02", false),
                ImGuiCol.ButtonHovered to Pair("base01", false),
                ImGuiCol.ButtonActive to Pair("indigo", false),
                ImGuiCol.SliderGrab to Pair("indigo", false),
                ImGuiCol.SliderGrabActive to Pair("violet", false),
                ImGuiCol.CheckMark to Pair("indigo", false),
                ImGuiCol.Text to Pair("base0", false),
                ImGuiCol.TextDisabled to Pair("base01", false),
                ImGuiCol.Header to Pair("base02", false),
                ImGuiCol.HeaderHovered to Pair("base01", false),
                ImGuiCol.HeaderActive to Pair("indigo", false)
            )
        ),
        Palette(
            theme = UITheme.Theme.LIGHT_LUNARIZED,
            name = "Light Lunarized",
            isLight = true,
            swatches = LUNARIZED_SWATCHES,
            defaultAssignments = mapOf(
                ImGuiCol.WindowBg to Pair("base3", true),
                ImGuiCol.PopupBg to Pair("base2", false),
                ImGuiCol.TitleBg to Pair("base2", true),
                ImGuiCol.TitleBgActive to Pair("base3", true),
                ImGuiCol.MenuBarBg to Pair("base2", true),
                ImGuiCol.FrameBg to Pair("base2", false),
                ImGuiCol.FrameBgHovered to Pair("base3", false),
                ImGuiCol.FrameBgActive to Pair("indigo", false),
                ImGuiCol.Button to Pair("base2", false),
                ImGuiCol.ButtonHovered to Pair("base1", false),
                ImGuiCol.ButtonActive to Pair("indigo", false),
                ImGuiCol.SliderGrab to Pair("indigo", false),
                ImGuiCol.SliderGrabActive to Pair("cyan", false),
                ImGuiCol.CheckMark to Pair("cyan", false),
                ImGuiCol.Text to Pair("base00", false),
                ImGuiCol.TextDisabled to Pair("base1", false),
                ImGuiCol.Header to Pair("base2", false),
                ImGuiCol.HeaderHovered to Pair("base1", false),
                ImGuiCol.HeaderActive to Pair("indigo", false)
            )
        ),
        Palette(
            theme = UITheme.Theme.NEON,
            name = "Neon",
            isLight = false,
            swatches = NEON_SWATCHES,
            defaultAssignments = mapOf(
                ImGuiCol.WindowBg to Pair("transparent", false),
                ImGuiCol.PopupBg to Pair("popupDark", false),
                ImGuiCol.TitleBg to Pair("indigoDark", true),
                ImGuiCol.TitleBgActive to Pair("indigoMid", true),
                ImGuiCol.MenuBarBg to Pair("indigoDark", true),
                ImGuiCol.FrameBg to Pair("frameDark", false),
                ImGuiCol.FrameBgHovered to Pair("frameHover", false),
                ImGuiCol.FrameBgActive to Pair("hotPink", false),
                ImGuiCol.Button to Pair("purpleDeep", false),
                ImGuiCol.ButtonHovered to Pair("hotPink", false),
                ImGuiCol.ButtonActive to Pair("neonYellow", false),
                ImGuiCol.SliderGrab to Pair("hotPink", false),
                ImGuiCol.SliderGrabActive to Pair("neonGreen", false),
                ImGuiCol.CheckMark to Pair("neonGreen", false),
                ImGuiCol.Text to Pair("pureWhite", false),
                ImGuiCol.TextDisabled to Pair("mutedViolet", false),
                ImGuiCol.Header to Pair("purpleDeep", false),
                ImGuiCol.HeaderHovered to Pair("hotPink", false),
                ImGuiCol.HeaderActive to Pair("neonYellow", false)
            )
        ),
        Palette(
            theme = UITheme.Theme.BORING,
            name = "Boring (Grayscale)",
            isLight = false,
            swatches = BORING_SWATCHES,
            defaultAssignments = mapOf(
                ImGuiCol.WindowBg to Pair("black06", true),
                ImGuiCol.PopupBg to Pair("black08", false),
                ImGuiCol.TitleBg to Pair("black04", true),
                ImGuiCol.TitleBgActive to Pair("gray16", true),
                ImGuiCol.MenuBarBg to Pair("gray14", true),
                ImGuiCol.FrameBg to Pair("gray14", false),
                ImGuiCol.FrameBgHovered to Pair("gray30", false),
                ImGuiCol.FrameBgActive to Pair("blueAccent", false),
                ImGuiCol.Button to Pair("gray14", false),
                ImGuiCol.ButtonHovered to Pair("gray30", false),
                ImGuiCol.ButtonActive to Pair("blueAccent", false),
                ImGuiCol.SliderGrab to Pair("blueAccent", false),
                ImGuiCol.SliderGrabActive to Pair("blueAccent", false),
                ImGuiCol.CheckMark to Pair("blueAccent", false),
                ImGuiCol.Text to Pair("white", false),
                ImGuiCol.TextDisabled to Pair("gray50", false),
                ImGuiCol.Header to Pair("gray14", false),
                ImGuiCol.HeaderHovered to Pair("gray30", false),
                ImGuiCol.HeaderActive to Pair("blueAccent", false)
            )
        )
    )

    // Current State
    private var selectedPaletteIdx = ImInt(0)
    private val currentAssignments = mutableMapOf<Int, String>() // ImGuiCol -> swatchId
    private val currentRespectAlpha = mutableMapOf<Int, Boolean>() // ImGuiCol -> respectsAlpha
    private val paletteNames = PALETTES.map { it.name }.toTypedArray()

    init {
        loadPaletteDefaults(0)
    }

    private fun loadPaletteDefaults(paletteIdx: Int) {
        val palette = PALETTES[paletteIdx]
        currentAssignments.clear()
        currentRespectAlpha.clear()
        palette.defaultAssignments.forEach { (col, pair) ->
            currentAssignments[col] = pair.first
            currentRespectAlpha[col] = pair.second
        }
    }

    fun open() {
        isOpen = true
        isOpenImBool.set(true)
    }

    fun close() {
        isOpen = false
        isOpenImBool.set(false)
    }

    fun toggle() {
        isOpen = !isOpen
        isOpenImBool.set(isOpen)
    }

    fun draw(session: SessionContext, displayW: Float, displayH: Float) {
        if (!isOpen) return

        isOpenImBool.set(isOpen)
        val targetW = 740f.coerceAtMost(displayW * 0.90f)
        val targetH = 640f.coerceAtMost(displayH * 0.90f)

        ImGui.setNextWindowSize(targetW, targetH, ImGuiCond.FirstUseEver)
        ImGui.setNextWindowPos(displayW * 0.5f - targetW * 0.5f, displayH * 0.5f - targetH * 0.5f, ImGuiCond.FirstUseEver)

        val windowFlags = ImGuiWindowFlags.NoScrollbar
        if (ImGui.begin("Color Tuner (Live)###ColorTunerTool", isOpenImBool, windowFlags)) {
            if (!isOpenImBool.get()) {
                close()
            }

            session.uiTheme.withFont(UITheme.FontLevel.H2) {
                ImGui.text("Theme Color Tuner")
            }
            ImGui.sameLine()
            session.uiTheme.caption(" (Live interactive tuning - no background dimming)")
            ImGui.separator()
            ImGui.spacing()

            // Palette Selector
            val prevIdx = selectedPaletteIdx.get()
            if (ImGui.combo("Active Palette", selectedPaletteIdx, paletteNames)) {
                val newIdx = selectedPaletteIdx.get()
                if (newIdx != prevIdx) {
                    loadPaletteDefaults(newIdx)
                    applyCurrentToImGui(session)
                }
            }

            val currentPalette = PALETTES[selectedPaletteIdx.get()]

            ImGui.sameLine()
            if (ImGui.button("Reset Palette Defaults")) {
                loadPaletteDefaults(selectedPaletteIdx.get())
                applyCurrentToImGui(session)
            }

            ImGui.spacing()

            // Swatch Bank Display
            if (ImGui.collapsingHeader("Palette Swatches (${currentPalette.swatches.size})", imgui.flag.ImGuiTreeNodeFlags.DefaultOpen)) {
                val itemSpacing = 6f
                val chipSize = 20f
                val dl = ImGui.getWindowDrawList()
                val availW = ImGui.getContentRegionAvailX()
                var curX = 0f

                currentPalette.swatches.forEach { swatch ->
                    val label = "${swatch.name} (${swatch.hex})"
                    val labelW = ImGui.calcTextSize(label).x
                    val totalW = chipSize + 6f + labelW + 12f

                    if (curX + totalW > availW && curX > 0f) {
                        curX = 0f
                        ImGui.newLine()
                    } else if (curX > 0f) {
                        ImGui.sameLine(0f, itemSpacing)
                    }

                    val pMinX = ImGui.getCursorScreenPosX()
                    val pMinY = ImGui.getCursorScreenPosY()
                    val pMaxX = pMinX + chipSize
                    val pMaxY = pMinY + chipSize

                    dl.addRectFilled(pMinX, pMinY, pMaxX, pMaxY, swatch.u32, 3f)
                    dl.addRect(pMinX, pMinY, pMaxX, pMaxY, ImColor.rgba(0.5f, 0.5f, 0.5f, 0.5f), 3f)

                    ImGui.dummy(chipSize, chipSize)
                    ImGui.sameLine(0f, 4f)
                    session.uiTheme.caption(label)
                    if (ImGui.isItemHovered() && session.uiTheme.tooltipsEnabled) {
                        ImGui.setTooltip("${swatch.name}\n${swatch.hex}\nRGB(${swatch.r}, ${swatch.g}, ${swatch.b})")
                    }

                    curX += totalW + itemSpacing
                }
                ImGui.newLine()
            }

            ImGui.spacing()
            ImGui.separator()
            ImGui.spacing()

            // Table of UI Elements and their swatch assignments
            val categories = ELEMENTS.groupBy { it.category }
            val footerH = ImGui.getFrameHeightWithSpacing() + ImGui.getStyle().getItemSpacingY() + 8f

            if (ImGui.beginChild("##elementsList", 0f, -footerH, true)) {
                categories.forEach { (catName, elementDefs) ->
                    if (ImGui.collapsingHeader(catName, imgui.flag.ImGuiTreeNodeFlags.DefaultOpen)) {
                        if (ImGui.beginTable("##table_$catName", 4, ImGuiTableFlags.BordersInnerH or ImGuiTableFlags.RowBg)) {
                            ImGui.tableSetupColumn("Element", ImGuiTableColumnFlags.WidthStretch, 0.32f)
                            ImGui.tableSetupColumn("Color", ImGuiTableColumnFlags.WidthFixed, 36f)
                            ImGui.tableSetupColumn("Assigned Swatch", ImGuiTableColumnFlags.WidthStretch, 0.52f)
                            ImGui.tableSetupColumn("Alpha", ImGuiTableColumnFlags.WidthFixed, 75f)
                            ImGui.tableHeadersRow()

                            elementDefs.forEach { elem ->
                                ImGui.tableNextRow()
                                ImGui.tableNextColumn()
                                session.uiTheme.body(elem.name)

                                val currentSwatchId = currentAssignments[elem.colId] ?: currentPalette.swatches.first().id
                                val currentSwatch = currentPalette.swatches.find { it.id == currentSwatchId } ?: currentPalette.swatches.first()

                                ImGui.tableNextColumn()
                                val dl = ImGui.getWindowDrawList()
                                val pMinX = ImGui.getCursorScreenPosX()
                                val pMinY = ImGui.getCursorScreenPosY() + 2f
                                val chipSize = 18f
                                dl.addRectFilled(pMinX, pMinY, pMinX + chipSize, pMinY + chipSize, currentSwatch.u32, 2f)
                                dl.addRect(pMinX, pMinY, pMinX + chipSize, pMinY + chipSize, ImColor.rgba(0.5f, 0.5f, 0.5f, 0.8f), 2f)
                                ImGui.dummy(chipSize, chipSize)

                                ImGui.tableNextColumn()
                                ImGui.pushItemWidth(-1f)
                                val previewLabel = "${currentSwatch.name}  (${currentSwatch.hex})"
                                if (ImGui.beginCombo("##combo_${elem.colId}", previewLabel)) {
                                    val comboDl = ImGui.getWindowDrawList()
                                    val comboChipSize = 13f

                                    currentPalette.swatches.forEach { swatch ->
                                        val isSelected = swatch.id == currentSwatch.id
                                        val itemText = "       ${swatch.name}  (${swatch.hex})##opt_${elem.colId}_${swatch.id}"

                                        val itemMinX = ImGui.getCursorScreenPosX()
                                        val itemMinY = ImGui.getCursorScreenPosY()

                                        if (ImGui.selectable(itemText, isSelected)) {
                                            currentAssignments[elem.colId] = swatch.id
                                            applySingleElementToImGui(session, elem.colId, swatch, currentRespectAlpha[elem.colId] ?: false)
                                        }

                                        // Draw colored square swatch inside the dropdown item row
                                        val itemChipY = itemMinY + (ImGui.getFrameHeight() - comboChipSize) * 0.5f - 1f
                                        val itemChipX = itemMinX + 4f
                                        comboDl.addRectFilled(itemChipX, itemChipY, itemChipX + comboChipSize, itemChipY + comboChipSize, swatch.u32, 2f)
                                        comboDl.addRect(itemChipX, itemChipY, itemChipX + comboChipSize, itemChipY + comboChipSize, ImColor.rgba(0.5f, 0.5f, 0.5f, 0.7f), 1f)

                                        if (isSelected) {
                                            ImGui.setItemDefaultFocus()
                                        }
                                    }
                                    ImGui.endCombo()
                                }
                                ImGui.popItemWidth()

                                ImGui.tableNextColumn()
                                if (elem.defaultAlphaSensitive) {
                                    val isAlpha = ImBoolean(currentRespectAlpha[elem.colId] ?: true)
                                    if (ImGui.checkbox("##alpha_${elem.colId}", isAlpha)) {
                                        currentRespectAlpha[elem.colId] = isAlpha.get()
                                        applySingleElementToImGui(session, elem.colId, currentSwatch, isAlpha.get())
                                    }
                                    if (ImGui.isItemHovered() && session.uiTheme.tooltipsEnabled) {
                                        ImGui.setTooltip("Respect background video transparency (alpha = 0.75)")
                                    }
                                } else {
                                    session.uiTheme.caption("-")
                                }
                            }
                            ImGui.endTable()
                        }
                    }
                }
            }
            ImGui.endChild()

            ImGui.separator()
            ImGui.spacing()

            // Action Buttons
            if (ImGui.button("Copy Kotlin Code to Clipboard", 260f, 0f)) {
                val code = generateKotlinCode(currentPalette, session.uiTheme.backgroundVideoEnabled)
                ImGui.setClipboardText(code)
                logger.info { "Theme Kotlin code copied to clipboard" }
            }
            ImGui.sameLine()
            if (ImGui.button("Apply to App", 120f, 0f)) {
                applyCurrentToImGui(session)
            }
            ImGui.sameLine()
            if (ImGui.button("Close", 100f, 0f)) {
                close()
            }
        }
        ImGui.end()
        if (!isOpenImBool.get()) {
            isOpen = false
        }
    }

    private fun applySingleElementToImGui(session: SessionContext, colId: Int, swatch: Swatch, respectsAlpha: Boolean) {
        val style = ImGui.getStyle()
        val bgVideo = session.uiTheme.backgroundVideoEnabled
        val alpha = if (respectsAlpha && bgVideo) 0.75f else 1.00f
        style.setColor(colId, swatch.r, swatch.g, swatch.b, alpha)
    }

    private fun applyCurrentToImGui(session: SessionContext) {
        val style = ImGui.getStyle()
        val currentPalette = PALETTES[selectedPaletteIdx.get()]
        if (currentPalette.isLight) {
            ImGui.styleColorsLight()
        } else {
            ImGui.styleColorsDark()
        }

        val bgVideo = session.uiTheme.backgroundVideoEnabled

        ELEMENTS.forEach { elem ->
            val swatchId = currentAssignments[elem.colId] ?: return@forEach
            val swatch = currentPalette.swatches.find { it.id == swatchId } ?: return@forEach
            val respectsAlpha = currentRespectAlpha[elem.colId] ?: false
            val alpha = if (respectsAlpha && bgVideo) 0.75f else 1.00f
            style.setColor(elem.colId, swatch.r, swatch.g, swatch.b, alpha)
        }
    }

    private fun generateKotlinCode(palette: Palette, bgVideoEnabled: Boolean): String {
        val sb = StringBuilder()
        sb.appendLine("// Theme: ${palette.name}")
        sb.appendLine("UITheme.Theme.${palette.theme.name} -> {")
        ELEMENTS.forEach { elem ->
            val swatchId = currentAssignments[elem.colId]
            val swatch = palette.swatches.find { it.id == swatchId }
            if (swatch != null) {
                val respectsAlpha = currentRespectAlpha[elem.colId] ?: false
                val alphaStr = if (respectsAlpha) "alpha" else "1.00f"
                sb.appendLine("    style.setColor(ImGuiCol.${elem.name}, %.2ff, %.2ff, %.2ff, %s)".format(swatch.r, swatch.g, swatch.b, alphaStr))
            }
        }
        sb.appendLine("}")
        return sb.toString()
    }
}
