package llm.slop.spirals.ui

import imgui.ImGui
import imgui.flag.ImGuiConfigFlags
import imgui.flag.ImGuiWindowFlags
import imgui.type.ImInt
import llm.slop.spirals.rendering.Deck
import llm.slop.spirals.rendering.Mandala
import llm.slop.spirals.rendering.MandalaRatio
import llm.slop.spirals.rendering.Mixer
import mu.KotlinLogging
import org.lwjgl.opengl.GL33.*
import imgui.gl3.ImGuiImplGl3
import imgui.glfw.ImGuiImplGlfw
import llm.slop.spirals.parameters.ModulatableParameter

/**
 * Manages the ImGui overlay for desktop control.
 */
class UIManager(private val windowHandle: Long) {
    private val logger = KotlinLogging.logger {}
    private val imguiGlfw = ImGuiImplGlfw()
    private val imguiGl3 = ImGuiImplGl3()

    // Tracks the base size we last passed to scaleAllSizes so we can compute
    // the correct delta ratio on subsequent changes.
    private var appliedBaseSize: Float = UITheme.baseSize

    // Font rebuild must happen between frames (atlas is locked during a frame).
    // Store the requested size here; it is consumed at the top of the next render().
    private var pendingFontSize: Float? = null

    // Set to true for one frame when the Settings menu item is clicked; consumed
    // immediately after endMainMenuBar so openPopup runs at root ID-stack level.
    private var pendingOpenSettings = false

    // Patch grid state shared between PatchGridPanel and CellConfigPanel
    private val patchState = PatchGridState()

    private val recipes = listOf(
        MandalaRatio("Recipe A", 26, 23, 14, 14),
        MandalaRatio("Recipe B", 32, 23, 11, 11),
        MandalaRatio("Recipe C", 28, 19, 16, 16),
        MandalaRatio("Recipe D", 31, 19, 19, 10),
        MandalaRatio("Recipe E", 35, 20, 11, 11),
        MandalaRatio("Recipe F", 38, 23, 14, 5)
    )
    private val recipeNames = arrayOf(
        "Recipe A (26,23,14,14)", "Recipe B (32,23,11,11)", "Recipe C (28,19,16,16)",
        "Recipe D (31,19,19,10)", "Recipe E (35,20,11,11)", "Recipe F (38,23,14,5)"
    )

    init {
        logger.info { "Initializing ImGui..." }
        ImGui.createContext()
        val io = ImGui.getIO()
        io.addConfigFlags(ImGuiConfigFlags.NavEnableKeyboard)

        // Load semantic fonts before the GL3 backend initialises so the atlas
        // is ready for the backend to upload on its first render call.
        UITheme.loadFonts(io)

        // Scale style sizes proportionally to the loaded baseSize relative to the baseline of 15f
        val startupScale = UITheme.baseSize / 15f
        if (startupScale != 1f) {
            ImGui.getStyle().scaleAllSizes(startupScale)
            logger.info { "Applied startup UI style scale: $startupScale (baseSize: ${UITheme.baseSize})" }
        }

        // Darken the modal backdrop for a more dramatic VJ-app feel.
        ImGui.getStyle().setColor(
            imgui.flag.ImGuiCol.ModalWindowDimBg,
            0f, 0f, 0f, 0.72f
        )

        imguiGlfw.init(windowHandle, true)
        imguiGl3.init("#version 150")
        logger.info { "UIManager initialized" }
    }

    fun render(mixer: Mixer, displayWidth: Float, displayHeight: Float) {
        // ── Between-frame work (atlas is unlocked here) ───────────────────────
        pendingFontSize?.let { newSize ->
            pendingFontSize = null
            val ratio = newSize / appliedBaseSize
            UITheme.baseSize = newSize
            UITheme.rebuildFonts(ImGui.getIO())
            imguiGl3.updateFontsTexture()
            ImGui.getStyle().scaleAllSizes(ratio)
            appliedBaseSize = newSize
            UITheme.saveSettings()
            logger.info { "Font size applied: ${newSize}px (ratio $ratio)" }
        }

        imguiGlfw.newFrame()
        ImGui.newFrame()

        drawMenuBar()
        // openPopup must be called at root ID-stack level — not inside the menu bar.
        if (pendingOpenSettings) {
            SettingsPanel.open()
            pendingOpenSettings = false
        }
        drawLayout(mixer, displayWidth, displayHeight)

        // Settings modal — drawn outside any docked window so it floats freely.
        SettingsPanel.draw(UITheme.baseSize, displayWidth, displayHeight) { newSize ->
            applyFontSize(newSize)
        }

        ImGui.render()
        imguiGl3.renderDrawData(ImGui.getDrawData())
    }

    /**
     * Rebuilds the font atlas at [newSize] and scales widget style proportionally.
     * [ImGui.getStyle().scaleAllSizes] is multiplicative, so we compute the delta
     * ratio from the last applied size each time.
     */
    private fun applyFontSize(newSize: Float) {
        if (newSize != appliedBaseSize) pendingFontSize = newSize
    }

    private fun drawMenuBar() {
        if (ImGui.beginMainMenuBar()) {
            if (ImGui.beginMenu("File")) {
                if (ImGui.menuItem("Exit")) { logger.info { "Exit clicked" } }
                ImGui.endMenu()
            }
            // Use menuItem (not beginMenu) so there's no dropdown — clicking
            // sets a flag that triggers openPopup after endMainMenuBar.
            if (ImGui.menuItem("Settings")) {
                pendingOpenSettings = true
            }
            ImGui.endMainMenuBar()
        }
    }

    private fun drawLayout(mixer: Mixer, displayWidth: Float, displayHeight: Float) {
        val menuBarH = 32f
        val contentH = displayHeight - menuBarH
        val noDecorate = ImGuiWindowFlags.NoResize or
                         ImGuiWindowFlags.NoMove or
                         ImGuiWindowFlags.NoCollapse

        // Left: Patch Grid (40% width, full content height)
        val leftW = displayWidth * 0.4f
        ImGui.setNextWindowPos(0f, menuBarH)
        ImGui.setNextWindowSize(leftW, contentH)
        if (ImGui.begin("Patch Grid", noDecorate)) {
            PatchGridPanel.draw(mixer, patchState)
        }
        ImGui.end()

        // Middle: Cell Config (30% width, full content height)
        val middleW = displayWidth * 0.3f
        ImGui.setNextWindowPos(leftW, menuBarH)
        ImGui.setNextWindowSize(middleW, contentH)
        if (ImGui.begin("Cell Config", noDecorate)) {
            CellConfigPanel.draw(patchState)
        }
        ImGui.end()

        // Right: Mixer / Monitor (30% width, full content height)
        val rightW = displayWidth - leftW - middleW
        ImGui.setNextWindowPos(leftW + middleW, menuBarH)
        ImGui.setNextWindowSize(rightW, contentH)
        val noTitleDecorate = noDecorate or ImGuiWindowFlags.NoTitleBar
        if (ImGui.begin("Mixer / Monitor", noTitleDecorate)) {
            drawMixerMonitor(mixer)
        }
        ImGui.end()
    }

    private fun drawMixerMonitor(mixer: Mixer) {
        val availW = ImGui.getContentRegionAvailX()
        val masterH = availW * (9f / 16f)

        val imgScreenX = ImGui.getCursorScreenPosX()
        val imgScreenY = ImGui.getCursorScreenPosY()

        ImGui.image(mixer.masterFBO.texture, availW, masterH, 0f, 1f, 1f, 0f)

        // Save the cursor position below the image
        val nextY = ImGui.getCursorPosY()

        // Draw overlay text on top of the master output image
        ImGui.setCursorScreenPos(imgScreenX + 10f, imgScreenY + 10f)
        UITheme.h2Colored(1.0f, 1.0f, 1.0f, 0.8f, "Master Output")

        // Restore Y cursor position
        ImGui.setCursorPosY(nextY)
        ImGui.spacing()

        val subW = (availW - 8f) * 0.5f
        val subH = subW * (9f / 16f)

        ImGui.columns(2, "subMonitors", false)
        ImGui.setColumnWidth(0, availW * 0.5f)
        UITheme.h3("Deck A")
        ImGui.image(mixer.deckA.getCurrentHistoryFBO().texture, subW, subH, 0f, 1f, 1f, 0f)
        ImGui.nextColumn()
        UITheme.h3("Deck B")
        ImGui.image(mixer.deckB.getCurrentHistoryFBO().texture, subW, subH, 0f, 1f, 1f, 0f)
        ImGui.columns(1)
        ImGui.spacing()

        // Crossfader (mapped display value from -1.0 to 1.0)
        drawFlatSlider("Crossfader", mixer.crossfade, 0f, 1f, 100f, -1f, 1f) {
            "A <-- %.2f --> B".format(it)
        }

        // Blend mode inline combo
        val startLocalX = ImGui.getCursorPosX()
        val startLocalY = ImGui.getCursorPosY()
        val textH = ImGui.getTextLineHeight()
        val comboH = 26f
        val rowH = maxOf(textH, comboH)

        ImGui.setCursorPosX(startLocalX)
        ImGui.setCursorPosY(startLocalY + (rowH - textH) * 0.5f)
        UITheme.body("Blend Mode")

        ImGui.setCursorPosX(startLocalX + 100f)
        ImGui.setCursorPosY(startLocalY + (rowH - comboH) * 0.5f)
        ImGui.pushItemWidth(ImGui.getContentRegionAvailX() - 5f)
        val modes = arrayOf("ADD", "SCREEN", "MULT", "MAX", "XFADE")
        val modeIdx = ImInt(mixer.mode.baseValue.toInt())
        if (ImGui.combo("##blendmode", modeIdx, modes)) {
            mixer.mode.set(modeIdx.get().toFloat())
        }
        ImGui.popItemWidth()

        ImGui.setCursorPosX(startLocalX)
        ImGui.setCursorPosY(startLocalY + rowH + 6f)

        // Alpha (renamed from "Master Alpha")
        drawFlatSlider("Alpha", mixer.masterAlpha, 0f, 1f, 100f)
        ImGui.spacing()

        ImGui.columns(2, "deckCtrls", true)
        drawDeckControls("Deck A", mixer.deckA)
        ImGui.nextColumn()
        drawDeckControls("Deck B", mixer.deckB)
        ImGui.columns(1)
    }

    private fun drawDeckControls(label: String, deck: Deck) {
        ImGui.pushID(label)
        UITheme.h3(label)
        ImGui.separator()

        // Recipe inline combo
        val mandala = deck.source as? Mandala
        if (mandala != null) {
            val idx = recipes.indexOfFirst {
                it.a == mandala.recipe.a && it.b == mandala.recipe.b &&
                it.c == mandala.recipe.c && it.d == mandala.recipe.d
            }.coerceAtLeast(0)
            val combo = ImInt(idx)

            val startLocalX = ImGui.getCursorPosX()
            val startLocalY = ImGui.getCursorPosY()
            val textH = ImGui.getTextLineHeight()
            val comboH = 26f
            val rowH = maxOf(textH, comboH)

            ImGui.setCursorPosX(startLocalX)
            ImGui.setCursorPosY(startLocalY + (rowH - textH) * 0.5f)
            UITheme.body("Recipe")

            ImGui.setCursorPosX(startLocalX + 80f)
            ImGui.setCursorPosY(startLocalY + (rowH - comboH) * 0.5f)
            ImGui.pushItemWidth(ImGui.getContentRegionAvailX() - 5f)
            if (ImGui.combo("##recipe", combo, recipeNames)) {
                mandala.recipe = recipes[combo.get()]
            }
            ImGui.popItemWidth()

            ImGui.setCursorPosX(startLocalX)
            ImGui.setCursorPosY(startLocalY + rowH + 6f)
        }

        fun slider(lbl: String, param: ModulatableParameter,
                   min: Float, max: Float, fmt: String = "%.3f") {
            drawFlatSlider(lbl, param, min, max, 80f) { fmt.format(it) }
        }

        slider("Gain",      deck.source.globalAlpha, 0f, 1f)
        slider("FB Decay",  deck.fbDecay,   0f, 0.2f)
        slider("FB Gain",   deck.fbGain,    0.9f, 1.1f)
        slider("FB Zoom",   deck.fbZoom,   -0.1f, 0.1f)
        slider("FB Rotate", deck.fbRotate, -0.1f, 0.1f)
        slider("FB Hue",    deck.fbHueShift,-0.1f, 0.1f)
        slider("FB Blur",   deck.fbBlur,    0f, 0.2f)

        ImGui.spacing()
        if (ImGui.button("🎲 Randomize Modulators", ImGui.getContentRegionAvailX(), 30f)) {
            deck.randomizeModulators()
            val cell = patchState.selectedCell
            val param = patchState.selectedParam
            if (cell != null && param != null && cell.paramKey.startsWith(label)) {
                patchState.editingModulator = param.modulators.find { it.sourceId == cell.cvSourceId }
            }
        }

        ImGui.popID()
    }

    private fun drawFlatSlider(
        label: String,
        param: ModulatableParameter,
        min: Float,
        max: Float,
        labelW: Float = 100f,
        displayMin: Float = min,
        displayMax: Float = max,
        formatValue: (Float) -> String = { "%.3f".format(it) }
    ) {
        ImGui.pushID(label)

        val startLocalX = ImGui.getCursorPosX()
        val startLocalY = ImGui.getCursorPosY()
        val startScreenX = ImGui.getCursorScreenPosX()
        val startScreenY = ImGui.getCursorScreenPosY()

        val barH = 14f
        val textH = ImGui.getTextLineHeight()
        val rowH = maxOf(barH, textH)

        // Draw label (centered vertically in rowH)
        ImGui.setCursorPosY(startLocalY + (rowH - textH) * 0.5f)
        UITheme.body(label)

        // Calculate bar position and width
        val barLocalStartX = startLocalX + labelW
        val barScreenX = startScreenX + labelW
        val barScreenY = startScreenY + (rowH - barH) * 0.5f

        // Draw interactive invisible button
        ImGui.setCursorPosX(barLocalStartX)
        ImGui.setCursorPosY(startLocalY + (rowH - barH) * 0.5f)
        val barW = ImGui.getContentRegionAvailX() - 5f
        ImGui.invisibleButton("##slider", barW, barH)

        // Process mouse dragging
        val mousePressed = ImGui.isItemActive() || (ImGui.isItemHovered() && ImGui.isMouseDown(0))
        val valueRange = max - min
        val displayRange = displayMax - displayMin

        if (mousePressed) {
            val io = ImGui.getIO()
            val pct = ((io.mousePos.x - barScreenX) / barW).coerceIn(0f, 1f)
            val nextDisplayVal = displayMin + pct * displayRange
            val nextInternalVal = min + if (displayRange > 0f) ((nextDisplayVal - displayMin) / displayRange) * valueRange else 0f
            param.set(nextInternalVal)
        }

        // Draw the flat bar visual using DrawList
        val dl = ImGui.getWindowDrawList()
        dl.addRectFilled(
            barScreenX, barScreenY,
            barScreenX + barW, barScreenY + barH,
            ImGui.colorConvertFloat4ToU32(0.15f, 0.15f, 0.15f, 1f),
            3f
        )

        val currentDisplayVal = displayMin + if (valueRange > 0f) ((param.baseValue - min) / valueRange) * displayRange else 0f
        val fillCol = ImGui.colorConvertFloat4ToU32(0.8f, 0.6f, 0.2f, 1f)

        if (displayMin < 0f && displayMax > 0f) {
            val pctCenter = (0f - displayMin) / displayRange
            val pctVal = (currentDisplayVal - displayMin) / displayRange
            val centerX = barScreenX + barW * pctCenter
            val valX = barScreenX + barW * pctVal

            dl.addRectFilled(
                minOf(centerX, valX), barScreenY,
                maxOf(centerX, valX), barScreenY + barH,
                fillCol,
                3f
            )
        } else {
            val fillPct = if (valueRange > 0f) ((param.baseValue - min) / valueRange).coerceIn(0f, 1f) else 0f
            if (fillPct > 0f) {
                dl.addRectFilled(
                    barScreenX, barScreenY,
                    barScreenX + barW * fillPct, barScreenY + barH,
                    fillCol,
                    3f
                )
            }
        }

        // Value text overlay
        val valStr = formatValue(currentDisplayVal)
        val textW = ImGui.calcTextSize(valStr).x
        val valTextH = ImGui.calcTextSize(valStr).y

        ImGui.setCursorPosX(barLocalStartX + barW - textW - 5f)
        ImGui.setCursorPosY(startLocalY + (rowH - valTextH) * 0.5f)
        UITheme.captionColored(0.9f, 0.9f, 0.9f, 0.8f, valStr)

        // Set cursor position to the next line
        ImGui.setCursorPosX(startLocalX)
        ImGui.setCursorPosY(startLocalY + rowH + 6f)
        ImGui.popID()
    }

    fun dispose() {
        imguiGl3.dispose()
        imguiGlfw.dispose()
        ImGui.destroyContext()
    }
}
