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
        if (ImGui.begin("Mixer / Monitor", noDecorate)) {
            drawMixerMonitor(mixer)
        }
        ImGui.end()
    }

    // ── Mixer / Monitor ───────────────────────────────────────────────────────

    private fun drawMixerMonitor(mixer: Mixer) {
        val availW = ImGui.getContentRegionAvailX()
        val masterH = availW * (9f / 16f)

        UITheme.h2("Master Output")
        ImGui.image(mixer.masterFBO.texture, availW, masterH, 0f, 1f, 1f, 0f)
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

        // Crossfader
        UITheme.body("Crossfader")
        val xfade = floatArrayOf(mixer.crossfade.baseValue)
        ImGui.pushItemWidth(-1f)
        if (ImGui.sliderFloat("##xfade", xfade, 0f, 1f, "A <-- %.2f --> B")) {
            mixer.crossfade.set(xfade[0])
        }
        ImGui.popItemWidth()
        ImGui.spacing()

        // Blend mode
        val modes = arrayOf("ADD", "SCREEN", "MULT", "MAX", "XFADE")
        val modeIdx = ImInt(mixer.mode.baseValue.toInt())
        UITheme.body("Blend Mode")
        ImGui.pushItemWidth(-1f)
        if (ImGui.combo("##blendmode", modeIdx, modes)) { mixer.mode.set(modeIdx.get().toFloat()) }
        ImGui.popItemWidth()

        val masterAlpha = floatArrayOf(mixer.masterAlpha.baseValue)
        UITheme.body("Master Alpha")
        ImGui.pushItemWidth(-1f)
        if (ImGui.sliderFloat("##alpha", masterAlpha, 0f, 1f)) { mixer.masterAlpha.set(masterAlpha[0]) }
        ImGui.popItemWidth()
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

        // Recipe
        val mandala = deck.source as? Mandala
        if (mandala != null) {
            val idx = recipes.indexOfFirst {
                it.a == mandala.recipe.a && it.b == mandala.recipe.b &&
                it.c == mandala.recipe.c && it.d == mandala.recipe.d
            }.coerceAtLeast(0)
            val combo = ImInt(idx)
            ImGui.pushItemWidth(-1f)
            if (ImGui.combo("##recipe", combo, recipeNames)) {
                mandala.recipe = recipes[combo.get()]
            }
            ImGui.popItemWidth()
        }

        fun slider(lbl: String, param: llm.slop.spirals.parameters.ModulatableParameter,
                   min: Float, max: Float, fmt: String = "%.3f") {
            UITheme.body(lbl)
            val v = floatArrayOf(param.baseValue)
            ImGui.pushItemWidth(-1f)
            if (ImGui.sliderFloat("##$lbl", v, min, max, fmt)) param.set(v[0])
            ImGui.popItemWidth()
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

    fun dispose() {
        imguiGl3.dispose()
        imguiGlfw.dispose()
        ImGui.destroyContext()
    }
}
