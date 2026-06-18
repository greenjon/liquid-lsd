package llm.slop.spirals

import llm.slop.spirals.rendering.FBO
import llm.slop.spirals.rendering.Geometry
import llm.slop.spirals.rendering.Shader
import llm.slop.spirals.rendering.GLDebug
import llm.slop.spirals.ui.UIManager
import mu.KotlinLogging
import org.lwjgl.glfw.GLFW.*
import org.lwjgl.opengl.GL
import org.lwjgl.opengl.GL33.*

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

    // Initialize UI Manager
    val uiManager = UIManager(window)

    logger.info { "Initialization complete" }

    // Initialize rendering components
    logger.info { "Creating FBO..." }
    val testFBO = FBO(1920, 1080)
    GLDebug.checkErrors("FBO creation")

    logger.info { "Loading shaders..." }
    val blitShader = Shader.fromResources("shaders/blit.vert", "shaders/blit.frag")
    GLDebug.checkErrors("Blit shader creation")

    val testShader = Shader.fromResources("shaders/blit.vert", "shaders/test_gradient.frag")
    GLDebug.checkErrors("Test shader creation")

    logger.info { "Rendering components initialized" }

    // Set up GL state for 2D rendering
    glDisable(GL_DEPTH_TEST)
    glDisable(GL_CULL_FACE)
    glEnable(GL_BLEND)
    glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA)

    logger.info { "GL state configured" }

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

        // === RENDERING PHASE ===

        // Get framebuffer size
        val w = IntArray(1)
        val h = IntArray(1)
        glfwGetFramebufferSize(window, w, h)

        // TODO: Handle FBO resizing if w[0]/h[0] differs from testFBO dimensions

        // 1. Render content to FBO
        testFBO.bind()
        glClearColor(0f, 0f, 0f, 1f)
        glClear(GL_COLOR_BUFFER_BIT)

        testShader.bind()
        testShader.setUniform("uTime", glfwGetTime().toFloat())
        Geometry.drawFullscreenQuad()
        testShader.unbind()
        testFBO.unbind()

        // 2. Blit FBO to screen
        glViewport(0, 0, w[0], h[0])
        glClearColor(0.1f, 0.1f, 0.1f, 1.0f)
        glClear(GL_COLOR_BUFFER_BIT)

        blitShader.bind()
        glActiveTexture(GL_TEXTURE0)
        glBindTexture(GL_TEXTURE_2D, testFBO.texture)
        blitShader.setUniform("uTexture", 0)
        Geometry.drawFullscreenQuad()
        blitShader.unbind()
        glBindVertexArray(0) // Ensure VAO is unbound for ImGui

        // Check for errors (only first 3 frames to avoid spam)
        if (frameCount < 3) {
            GLDebug.checkErrors("FBO Render and Blit")
        }

        // === UI PHASE ===
        uiManager.render(testFBO)

        glfwSwapBuffers(window)
    }

    // Cleanup
    logger.info { "Shutting down..." }

    // Dispose rendering resources
    testShader.dispose()
    blitShader.dispose()
    testFBO.dispose()
    Geometry.dispose()

    // Dispose UI
    uiManager.dispose()

    // Dispose window
    glfwDestroyWindow(window)
    glfwTerminate()
}
