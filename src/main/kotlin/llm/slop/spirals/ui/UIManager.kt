package llm.slop.spirals.ui

import imgui.ImGui
import imgui.flag.ImGuiConfigFlags
import imgui.gl3.ImGuiImplGl3
import imgui.glfw.ImGuiImplGlfw
import llm.slop.spirals.rendering.FBO
import mu.KotlinLogging
import org.lwjgl.opengl.GL33.*

private val logger = KotlinLogging.logger {}

class UIManager(private val windowHandle: Long) {
    private val imguiGlfw = ImGuiImplGlfw()
    private val imguiGl3 = ImGuiImplGl3()

    // Persistent state for UI controls
    private val crossfade = floatArrayOf(0.5f)

    init {
        ImGui.createContext()
        val io = ImGui.getIO()
        io.addConfigFlags(ImGuiConfigFlags.NavEnableKeyboard)
        io.iniFilename = null // Disable imgui.ini

        imguiGlfw.init(windowHandle, true)
        imguiGl3.init("#version 330 core")

        logger.info { "UIManager initialized" }
    }

    fun render(testFBO: FBO) {
        imguiGlfw.newFrame()
        ImGui.newFrame()

        // Setup UI Windows
        drawMenuBar()
        ImGui.showDemoWindow()
        drawControlWindow(testFBO)

        // Rendering
        ImGui.render()
        imguiGl3.renderDrawData(ImGui.getDrawData())
    }

    private fun drawMenuBar() {
        if (ImGui.beginMainMenuBar()) {
            if (ImGui.beginMenu("File")) {
                if (ImGui.menuItem("New Patch")) { logger.info { "New patch" } }
                if (ImGui.menuItem("Open Patch...")) { logger.info { "Open patch" } }
                if (ImGui.menuItem("Save Patch")) { logger.info { "Save patch" } }
                ImGui.separator()
                if (ImGui.menuItem("Exit")) {
                    // We'll handle exit in Main
                }
                ImGui.endMenu()
            }
            if (ImGui.beginMenu("View")) {
                if (ImGui.menuItem("CV Sources")) { }
                if (ImGui.menuItem("Parameter Grid")) { }
                ImGui.endMenu()
            }
            ImGui.endMainMenuBar()
        }
    }

    private fun drawControlWindow(testFBO: FBO) {
        ImGui.begin("Spirals Control")
        ImGui.text("Welcome to Spirals Desktop!")
        ImGui.separator()

        if (ImGui.collapsingHeader("Status")) {
            ImGui.text("OpenGL: ${glGetString(GL_VERSION)}")
            ImGui.text("Renderer: ${glGetString(GL_RENDERER)}")
            ImGui.text("FPS: %.1f".format(ImGui.getIO().framerate))
            ImGui.separator()
            ImGui.text("FBO Test:")
            ImGui.text("  ID: ${testFBO.framebufferId}")
            ImGui.text("  Size: ${testFBO.width}x${testFBO.height}")
            ImGui.text("  Texture: ${testFBO.texture}")
        }

        if (ImGui.collapsingHeader("Mixer")) {
            ImGui.text("Deck A / Deck B")
            if (ImGui.sliderFloat("Crossfade", crossfade, 0f, 1f)) {
                logger.debug { "Crossfade: ${crossfade[0]}" }
            }
        }

        ImGui.end()
    }

    fun dispose() {
        imguiGl3.dispose()
        imguiGlfw.dispose()
        ImGui.destroyContext()
        logger.info { "UIManager disposed" }
    }
}
