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
        val b: Float
    ) {
        val u32: Int get() = ImColor.rgba(r, g, b, 1.0f)
        val hex: String get() = String.format("#%02X%02X%02X", (r * 255).toInt().coerceIn(0, 255), (g * 255).toInt().coerceIn(0, 255), (b * 255).toInt().coerceIn(0, 255))
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
        Swatch("base03", "Base03", 0.00f, 0.17f, 0.21f),
        Swatch("base02", "Base02", 0.03f, 0.21f, 0.26f),
        Swatch("base01", "Base01", 0.35f, 0.43f, 0.46f),
        Swatch("base00", "Base00", 0.40f, 0.48f, 0.51f),
        Swatch("base0",  "Base0",  0.51f, 0.58f, 0.59f),
        Swatch("base1",  "Base1",  0.58f, 0.63f, 0.63f),
        Swatch("base2",  "Base2",  0.93f, 0.91f, 0.84f),
        Swatch("base3",  "Base3",  0.99f, 0.96f, 0.89f),
        Swatch("yellow", "Yellow", 0.71f, 0.54f, 0.00f),
        Swatch("orange", "Orange", 0.80f, 0.29f, 0.09f),
        Swatch("red",    "Red",    0.86f, 0.20f, 0.18f),
        Swatch("magenta","Magenta",0.83f, 0.21f, 0.51f),
        Swatch("violet", "Violet", 0.42f, 0.44f, 0.77f),
        Swatch("blue",   "Blue",   0.15f, 0.55f, 0.82f),
        Swatch("cyan",   "Cyan",   0.17f, 0.63f, 0.60f),
        Swatch("green",  "Green",  0.52f, 0.60f, 0.00f)
    )

    private val LUNARIZED_SWATCHES = listOf(
        Swatch("darkBase03", "DarkBase03", 0.21f, 0.04f, 0.00f),
        Swatch("darkBase02", "DarkBase02", 0.28f, 0.07f, 0.00f),
        Swatch("darkBase01", "DarkBase01", 0.37f, 0.16f, 0.08f),
        Swatch("darkBase00", "DarkBase00", 0.58f, 0.40f, 0.35f),
        Swatch("darkBase0",  "DarkBase0",  0.97f, 0.91f, 0.88f),
        Swatch("lightBase3", "LightBase3", 0.89f, 0.92f, 0.99f),
        Swatch("lightBase2", "LightBase2", 0.82f, 0.85f, 0.96f),
        Swatch("lightBase1", "LightBase1", 0.69f, 0.75f, 0.92f),
        Swatch("lightBase0", "LightBase0", 0.47f, 0.50f, 0.61f),
        Swatch("lightBase00","LightBase00",0.15f, 0.17f, 0.21f),
        Swatch("periwinkle", "Periwinkle", 0.42f, 0.44f, 0.77f),
        Swatch("purple",     "Purple",     0.48f, 0.32f, 0.80f),
        Swatch("royalBlue",  "RoyalBlue",  0.11f, 0.37f, 0.89f),
        Swatch("cyan",       "Cyan",       0.00f, 0.64f, 0.80f)
    )

    private val NEON_SWATCHES = listOf(
        Swatch("transparent","Transparent",0.00f, 0.00f, 0.00f),
        Swatch("indigoDark", "IndigoDark", 0.04f, 0.04f, 0.10f),
        Swatch("indigoMid",  "IndigoMid",  0.08f, 0.00f, 0.14f),
        Swatch("purpleDeep", "PurpleDeep", 0.13f, 0.02f, 0.20f),
        Swatch("frameDark",  "FrameDark",  0.11f, 0.05f, 0.16f),
        Swatch("frameHover", "FrameHover", 0.18f, 0.07f, 0.28f),
        Swatch("popupDark",  "PopupDark",  0.05f, 0.01f, 0.08f),
        Swatch("hotPink",    "HotPink",    1.00f, 0.00f, 0.50f),
        Swatch("neonYellow", "NeonYellow", 1.00f, 1.00f, 0.00f),
        Swatch("neonGreen",  "NeonGreen",  0.50f, 1.00f, 0.00f),
        Swatch("pureWhite",  "PureWhite",  1.00f, 1.00f, 1.00f),
        Swatch("mutedViolet","MutedViolet",0.54f, 0.40f, 0.64f)
    )

    private val BORING_SWATCHES = listOf(
        Swatch("black04",   "Black04",   0.04f, 0.04f, 0.04f),
        Swatch("black06",   "Black06",   0.06f, 0.06f, 0.06f),
        Swatch("black08",   "Black08",   0.08f, 0.08f, 0.08f),
        Swatch("gray14",    "Gray14",    0.14f, 0.14f, 0.14f),
        Swatch("gray16",    "Gray16",    0.16f, 0.16f, 0.16f),
        Swatch("gray30",    "Gray30",    0.30f, 0.30f, 0.30f),
        Swatch("gray50",    "Gray50",    0.50f, 0.50f, 0.50f),
        Swatch("gray80",    "Gray80",    0.80f, 0.80f, 0.80f),
        Swatch("white",     "White",     1.00f, 1.00f, 1.00f),
        Swatch("blueAccent","BlueAccent",0.26f, 0.59f, 0.98f)
    )

    private val PALETTES = listOf(
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
                ImGuiCol.WindowBg to Pair("darkBase03", true),
                ImGuiCol.PopupBg to Pair("darkBase02", false),
                ImGuiCol.TitleBg to Pair("darkBase02", true),
                ImGuiCol.TitleBgActive to Pair("darkBase03", true),
                ImGuiCol.MenuBarBg to Pair("darkBase02", true),
                ImGuiCol.FrameBg to Pair("darkBase02", false),
                ImGuiCol.FrameBgHovered to Pair("darkBase03", false),
                ImGuiCol.FrameBgActive to Pair("periwinkle", false),
                ImGuiCol.Button to Pair("darkBase02", false),
                ImGuiCol.ButtonHovered to Pair("darkBase01", false),
                ImGuiCol.ButtonActive to Pair("periwinkle", false),
                ImGuiCol.SliderGrab to Pair("periwinkle", false),
                ImGuiCol.SliderGrabActive to Pair("purple", false),
                ImGuiCol.CheckMark to Pair("periwinkle", false),
                ImGuiCol.Text to Pair("darkBase0", false),
                ImGuiCol.TextDisabled to Pair("darkBase00", false),
                ImGuiCol.Header to Pair("darkBase02", false),
                ImGuiCol.HeaderHovered to Pair("darkBase01", false),
                ImGuiCol.HeaderActive to Pair("periwinkle", false)
            )
        ),
        Palette(
            theme = UITheme.Theme.LIGHT_LUNARIZED,
            name = "Light Lunarized",
            isLight = true,
            swatches = LUNARIZED_SWATCHES,
            defaultAssignments = mapOf(
                ImGuiCol.WindowBg to Pair("lightBase3", true),
                ImGuiCol.PopupBg to Pair("lightBase2", false),
                ImGuiCol.TitleBg to Pair("lightBase2", true),
                ImGuiCol.TitleBgActive to Pair("lightBase3", true),
                ImGuiCol.MenuBarBg to Pair("lightBase2", true),
                ImGuiCol.FrameBg to Pair("lightBase2", false),
                ImGuiCol.FrameBgHovered to Pair("lightBase3", false),
                ImGuiCol.FrameBgActive to Pair("royalBlue", false),
                ImGuiCol.Button to Pair("lightBase2", false),
                ImGuiCol.ButtonHovered to Pair("lightBase1", false),
                ImGuiCol.ButtonActive to Pair("royalBlue", false),
                ImGuiCol.SliderGrab to Pair("royalBlue", false),
                ImGuiCol.SliderGrabActive to Pair("cyan", false),
                ImGuiCol.CheckMark to Pair("cyan", false),
                ImGuiCol.Text to Pair("lightBase00", false),
                ImGuiCol.TextDisabled to Pair("lightBase0", false),
                ImGuiCol.Header to Pair("lightBase2", false),
                ImGuiCol.HeaderHovered to Pair("lightBase1", false),
                ImGuiCol.HeaderActive to Pair("royalBlue", false)
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
    }

    fun toggle() {
        isOpen = !isOpen
    }

    fun draw(session: SessionContext, displayW: Float, displayH: Float) {
        if (!isOpen) return

        isOpenImBool.set(isOpen)
        val targetW = 680f.coerceAtMost(displayW * 0.90f)
        val targetH = 620f.coerceAtMost(displayH * 0.90f)

        ImGui.setNextWindowSize(targetW, targetH, ImGuiCond.FirstUseEver)
        ImGui.setNextWindowPos(displayW * 0.5f - targetW * 0.5f, displayH * 0.5f - targetH * 0.5f, ImGuiCond.FirstUseEver)

        val windowFlags = ImGuiWindowFlags.None
        if (ImGui.begin("Color Tuner (Live)###ColorTunerTool", isOpenImBool, windowFlags)) {
            isOpen = isOpenImBool.get()

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
                val chipSize = 22f
                val dl = ImGui.getWindowDrawList()
                val availW = ImGui.getContentRegionAvailX()
                var curX = 0f

                currentPalette.swatches.forEach { swatch ->
                    val label = swatch.name
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
            val swatchNames = currentPalette.swatches.map { it.name }.toTypedArray()

            if (ImGui.beginChild("##elementsList", 0f, -48f, true)) {
                categories.forEach { (catName, elementDefs) ->
                    if (ImGui.collapsingHeader(catName, imgui.flag.ImGuiTreeNodeFlags.DefaultOpen)) {
                        if (ImGui.beginTable("##table_$catName", 4, ImGuiTableFlags.BordersInnerH or ImGuiTableFlags.RowBg)) {
                            ImGui.tableSetupColumn("Element", ImGuiTableColumnFlags.WidthStretch, 0.35f)
                            ImGui.tableSetupColumn("Color", ImGuiTableColumnFlags.WidthFixed, 36f)
                            ImGui.tableSetupColumn("Assigned Swatch", ImGuiTableColumnFlags.WidthStretch, 0.45f)
                            ImGui.tableSetupColumn("Alpha?", ImGuiTableColumnFlags.WidthFixed, 60f)
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
                                val currentIdx = currentPalette.swatches.indexOfFirst { it.id == currentSwatch.id }.coerceAtLeast(0)
                                val comboIdx = ImInt(currentIdx)
                                ImGui.pushItemWidth(-1f)
                                if (ImGui.combo("##combo_${elem.colId}", comboIdx, swatchNames)) {
                                    val selectedSwatch = currentPalette.swatches[comboIdx.get()]
                                    currentAssignments[elem.colId] = selectedSwatch.id
                                    applySingleElementToImGui(session, elem.colId, selectedSwatch, currentRespectAlpha[elem.colId] ?: false)
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
                isOpen = false
            }
        }
        ImGui.end()
        isOpen = isOpenImBool.get()
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
