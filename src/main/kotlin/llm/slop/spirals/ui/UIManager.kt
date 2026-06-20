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
        imguiGlfw.init(windowHandle, true)
        imguiGl3.init("#version 150")
        logger.info { "UIManager initialized" }
    }

    fun render(mixer: Mixer, displayWidth: Float, displayHeight: Float) {
        imguiGlfw.newFrame()
        ImGui.newFrame()

        drawMenuBar()
        drawLayout(mixer, displayWidth, displayHeight)

        ImGui.render()
        imguiGl3.renderDrawData(ImGui.getDrawData())
    }

    private fun drawMenuBar() {
        if (ImGui.beginMainMenuBar()) {
            if (ImGui.beginMenu("File")) {
                if (ImGui.menuItem("Exit")) { logger.info { "Exit clicked" } }
                ImGui.endMenu()
            }
            ImGui.endMainMenuBar()
        }
    }

    private fun drawLayout(mixer: Mixer, displayWidth: Float, displayHeight: Float) {
        val menuBarH = 25f
        val contentH = displayHeight - menuBarH
        val noDecorate = ImGuiWindowFlags.NoResize or
                         ImGuiWindowFlags.NoMove or
                         ImGuiWindowFlags.NoCollapse

        // Left: Patch Grid (2/3 width, full content height)
        val leftW = displayWidth * (2f / 3f)
        ImGui.setNextWindowPos(0f, menuBarH)
        ImGui.setNextWindowSize(leftW, contentH)
        if (ImGui.begin("Patch Grid", noDecorate)) {
            PatchGridPanel.draw(mixer, patchState)
        }
        ImGui.end()

        val rightW = displayWidth - leftW
        val rightTopH = contentH * 0.4f     // Cell Config
        val rightBotH = contentH - rightTopH // Mixer / Monitor

        // Right Top: Cell Config
        ImGui.setNextWindowPos(leftW, menuBarH)
        ImGui.setNextWindowSize(rightW, rightTopH)
        if (ImGui.begin("Cell Config", noDecorate)) {
            CellConfigPanel.draw(patchState)
        }
        ImGui.end()

        // Right Bottom: Mixer / Monitor
        ImGui.setNextWindowPos(leftW, menuBarH + rightTopH)
        ImGui.setNextWindowSize(rightW, rightBotH)
        if (ImGui.begin("Mixer / Monitor", noDecorate)) {
            drawMixerMonitor(mixer)
        }
        ImGui.end()
    }

    // ── Mixer / Monitor ───────────────────────────────────────────────────────

    private fun drawMixerMonitor(mixer: Mixer) {
        val availW = ImGui.getContentRegionAvailX()
        val masterH = availW * (9f / 16f)

        ImGui.text("Master Output")
        ImGui.image(mixer.masterFBO.texture, availW, masterH, 0f, 1f, 1f, 0f)
        ImGui.spacing()

        val subW = (availW - 8f) * 0.5f
        val subH = subW * (9f / 16f)

        ImGui.columns(2, "subMonitors", false)
        ImGui.setColumnWidth(0, availW * 0.5f)
        ImGui.text("Deck A")
        ImGui.image(mixer.deckA.getCurrentHistoryFBO().texture, subW, subH, 0f, 1f, 1f, 0f)
        ImGui.nextColumn()
        ImGui.text("Deck B")
        ImGui.image(mixer.deckB.getCurrentHistoryFBO().texture, subW, subH, 0f, 1f, 1f, 0f)
        ImGui.columns(1)
        ImGui.spacing()

        // Crossfader
        ImGui.text("Crossfader")
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
        ImGui.text("Blend Mode")
        ImGui.pushItemWidth(-1f)
        if (ImGui.combo("##blendmode", modeIdx, modes)) { mixer.mode.set(modeIdx.get().toFloat()) }
        ImGui.popItemWidth()

        val masterAlpha = floatArrayOf(mixer.masterAlpha.baseValue)
        ImGui.text("Master Alpha")
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
        ImGui.text(label)
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
            ImGui.text(lbl)
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

        ImGui.popID()
    }

    fun dispose() {
        imguiGl3.dispose()
        imguiGlfw.dispose()
        ImGui.destroyContext()
    }
}
