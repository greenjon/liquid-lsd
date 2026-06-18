package llm.slop.spirals

import mu.KotlinLogging
import org.lwjgl.glfw.GLFW.*
import org.lwjgl.opengl.GL
import org.lwjgl.opengl.GL33.*
import imgui.ImGui
import imgui.flag.ImGuiConfigFlags
import imgui.gl3.ImGuiImplGl3
import imgui.glfw.ImGuiImplGlfw

private val logger = KotlinLogging.logger {}

fun main() {
    logger.info { "Starting Spirals Desktop..." }

    // Initialize GLFW
    if (!glfwInit()) {
        throw RuntimeException("Failed to initialize GLFW")
    }

    // Configure GLFW
    glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3)
    glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3)
    glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE)
    glfwWindowHint(GLFW_OPENGL_FORWARD_COMPAT, GLFW_TRUE)
    glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE)

    // Create window
    val window = glfwCreateWindow(1920, 1080, "Spirals Desktop - VJ Software", 0, 0)
        ?: throw RuntimeException("Failed to create GLFW window")

    glfwMakeContextCurrent(window)
    glfwSwapInterval(1) // Enable vsync

    // Initialize OpenGL
    GL.createCapabilities()

    logger.info { "OpenGL Version: ${glGetString(GL_VERSION)}" }
    logger.info { "OpenGL Renderer: ${glGetString(GL_RENDERER)}" }

    // Initialize ImGui
    ImGui.createContext()
    val io = ImGui.getIO()
    io.addConfigFlags(ImGuiConfigFlags.NavEnableKeyboard)
    io.iniFilename = null // Disable imgui.ini

    val imguiGlfw = ImGuiImplGlfw()
    val imguiGl3 = ImGuiImplGl3()
    imguiGlfw.init(window, true)
    imguiGl3.init("#version 330 core")

    logger.info { "Initialization complete" }

    // Main loop
    var frameCount = 0
    var lastTime = glfwGetTime()

    while (!glfwWindowShouldClose(window)) {
        glfwPollEvents()

        // Calculate FPS
        frameCount++
        val currentTime = glfwGetTime()
        if (currentTime - lastTime >= 1.0) {
            logger.debug { "FPS: $frameCount" }
            frameCount = 0
            lastTime = currentTime
        }

        // Start ImGui frame
        imguiGlfw.newFrame()
        ImGui.newFrame()

        // Render UI
        renderUI()

        // Render OpenGL background
        glClearColor(0.1f, 0.1f, 0.12f, 1.0f)
        glClear(GL_COLOR_BUFFER_BIT)

        // TODO: Render mandalas here

        // Render ImGui
        ImGui.render()
        imguiGl3.renderDrawData(ImGui.getDrawData())

        glfwSwapBuffers(window)
    }

    // Cleanup
    logger.info { "Shutting down..." }
    imguiGl3.dispose()
    imguiGlfw.dispose()
    ImGui.destroyContext()
    glfwDestroyWindow(window)
    glfwTerminate()
}

fun renderUI() {
    // Main menu bar
    if (ImGui.beginMainMenuBar()) {
        if (ImGui.beginMenu("File")) {
            if (ImGui.menuItem("New Patch")) {
                logger.info { "New patch" }
            }
            if (ImGui.menuItem("Open Patch...")) {
                logger.info { "Open patch" }
            }
            if (ImGui.menuItem("Save Patch")) {
                logger.info { "Save patch" }
            }
            ImGui.separator()
            if (ImGui.menuItem("Exit")) {
                logger.info { "Exit requested" }
            }
            ImGui.endMenu()
        }
        if (ImGui.beginMenu("View")) {
            if (ImGui.menuItem("CV Sources")) {
                logger.info { "Toggle CV sources" }
            }
            if (ImGui.menuItem("Parameter Grid")) {
                logger.info { "Toggle parameter grid" }
            }
            ImGui.endMenu()
        }
        if (ImGui.beginMenu("Audio")) {
            if (ImGui.menuItem("Settings...")) {
                logger.info { "Audio settings" }
            }
            ImGui.endMenu()
        }
        if (ImGui.beginMenu("Help")) {
            if (ImGui.menuItem("About")) {
                logger.info { "About" }
            }
            ImGui.endMenu()
        }
        ImGui.endMainMenuBar()
    }

    // Demo window for testing
    ImGui.showDemoWindow()

    // Main UI window
    ImGui.begin("Spirals Control")
    ImGui.text("Welcome to Spirals Desktop!")
    ImGui.separator()

    if (ImGui.collapsingHeader("Status")) {
        ImGui.text("OpenGL: ${glGetString(GL_VERSION)}")
        ImGui.text("Renderer: ${glGetString(GL_RENDERER)}")
        ImGui.text("FPS: %.1f".format(ImGui.getIO().framerate))
    }

    if (ImGui.collapsingHeader("Mixer")) {
        ImGui.text("Deck A / Deck B")
        val crossfade = floatArrayOf(0.5f)
        if (ImGui.sliderFloat("Crossfade", crossfade, 0f, 1f)) {
            logger.debug { "Crossfade: ${crossfade[0]}" }
        }
    }

    ImGui.end()
}
