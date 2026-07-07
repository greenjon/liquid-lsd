package llm.slop.spirals

import llm.slop.spirals.rendering.FBO
import llm.slop.spirals.rendering.Geometry
import llm.slop.spirals.rendering.Shader
import llm.slop.spirals.rendering.GLDebug
import llm.slop.spirals.rendering.Renderer
import llm.slop.spirals.rendering.Mandala
import llm.slop.spirals.rendering.MandalaRatio
import llm.slop.spirals.rendering.Deck
import llm.slop.spirals.rendering.Mixer
import llm.slop.spirals.ui.UIManager
import llm.slop.spirals.audio.AudioEngine
import llm.slop.spirals.ui.UITheme
import llm.slop.spirals.cv.CVRegistry
import llm.slop.spirals.patches.PatchManager
import mu.KotlinLogging
import org.lwjgl.glfw.GLFW.*
import org.lwjgl.opengl.GL
import org.lwjgl.opengl.GL33.*

private val logger = KotlinLogging.logger {}

fun main() {
    logger.info { "Starting Spirals Desktop..." }

    // Ensure preset directories exist
    java.io.File("presets/patches").mkdirs()
    java.io.File("presets/playlists").mkdirs()
    java.io.File("presets/midi").mkdirs()



    // Load active MIDI mapping profile
    llm.slop.spirals.midi.MidiMappingManager.loadProfile(UITheme.activeMidiProfile)

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
    glfwWindowHint(GLFW_OPENGL_DEBUG_CONTEXT, GLFW_TRUE)

    // Create window
    val window = glfwCreateWindow(1920, 1080, "Spirals Desktop - VJ Software", 0, 0)
        ?: throw RuntimeException("Failed to create GLFW window")

    glfwMakeContextCurrent(window)
    glfwSwapInterval(1) // Enable vsync

    // Initialize OpenGL
    GL.createCapabilities()
    GLDebug.setupDebugCallback()

    // Load dynamic visual sources
    llm.slop.spirals.rendering.VisualSourceRegistry.loadAll()

    logger.info { "OpenGL Version: ${glGetString(GL_VERSION)}" }
    logger.info { "OpenGL Renderer: ${glGetString(GL_RENDERER)}" }

    // Initialize UI Manager
    val uiManager = UIManager(window)

    glfwSetWindowCloseCallback(window) { win ->
        glfwSetWindowShouldClose(win, false)
        uiManager.triggerExitFlow()
    }

    logger.info { "Initialization complete" }

    // Initialize rendering components
    logger.info { "Initializing Decks and Mixer..." }
    val renderer = Renderer()

    val masterMandala = llm.slop.spirals.rendering.VisualSourceRegistry.availableSources.firstOrNull { it.id == "mandala" } as? Mandala
        ?: throw RuntimeException("Mandala source not loaded from presets/sources/mandala")

    // Create Deck A with a 4-petal recipe (yellow-ish theme default)
    val recipeA = MandalaRatio(
        id = "15001423042349762156",
        a = 26,
        b = 23,
        c = 14,
        d = 14
    )
    val mandalaA = masterMandala.clone()
    mandalaA.recipe = recipeA
    val deckA = Deck(mandalaA)

    // Create Deck B with a 3-petal recipe (shifted start hue)
    val recipeB = MandalaRatio(
        id = "3859966211554434234",
        a = 32,
        b = 23,
        c = 11,
        d = 11
    )
    val mandalaB = masterMandala.clone()
    mandalaB.recipe = recipeB
    mandalaB.parameters["Hue Offset"]?.set(0.5f) // starting color offset for distinction
    val deckB = Deck(mandalaB)

    // Create Deck C (for preview / live tweaking)
    val recipeC = MandalaRatio(
        id = "9999999999999999999", // generic ID
        a = 3,
        b = 3,
        c = 3,
        d = 3
    )
    val mandalaC = masterMandala.clone()
    mandalaC.recipe = recipeC
    val deckC = Deck(mandalaC)

    // Create Mixer
    val mixer = Mixer(deckA, deckB, deckC)
    PatchManager.initializeDefault(mixer)
    if (UITheme.startupBehavior == UITheme.StartupBehavior.EMPTY) {
        PatchManager.startEmpty(mixer)
    } else {
        PatchManager.loadSession(mixer)
    }
    GLDebug.checkErrors("Mixer and Decks initialization")

    logger.info { "Rendering components initialized" }

    // Set up GL state for 2D VJ rendering
    glDisable(GL_DEPTH_TEST)
    glDisable(GL_CULL_FACE)
    glEnable(GL_BLEND)
    glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA)

    logger.info { "GL state configured" }

    // Start Audio engine if enabled
    if (UITheme.audioEngineEnabled) {
        AudioEngine.start()
    }

    // Start background watchdogs for MIDI and JACK
    llm.slop.spirals.audio.MidiJackWatchdog.start()

    var secondaryWindow = 0L

    // Auto-detect and open secondary window on startup if external monitor is found
    val initialExternalMonitor = getExternalMonitor()
    if (initialExternalMonitor != null) {
        secondaryWindow = createSecondaryWindow(window)
    }

    // Setup key callback chaining to allow "f", "Spacebar", CTRL-, and CTRL= controls
    var imguiKeyCallback: org.lwjgl.glfw.GLFWKeyCallback? = null
    imguiKeyCallback = glfwSetKeyCallback(window) { win, key, scancode, action, mods ->
        val isFontSizeHotKey = (mods and GLFW_MOD_CONTROL) != 0 && (key == GLFW_KEY_MINUS || key == GLFW_KEY_EQUAL)
        val isHotKey = (key == GLFW_KEY_F || key == GLFW_KEY_SPACE || isFontSizeHotKey)
        if (action == GLFW_PRESS) {
            if (isFontSizeHotKey) {
                if (key == GLFW_KEY_MINUS) {
                    uiManager.adjustFontSize(-1f)
                } else if (key == GLFW_KEY_EQUAL) {
                    uiManager.adjustFontSize(1f)
                }
            } else if (key == GLFW_KEY_F) {
                UITheme.cleanModeEnabled = !UITheme.cleanModeEnabled
                logger.info { "Clean mode toggled: ${UITheme.cleanModeEnabled}" }
            } else if (key == GLFW_KEY_SPACE) {
                val io = imgui.ImGui.getIO()
                if (!io.wantCaptureKeyboard || UITheme.cleanModeEnabled) {
                    if (secondaryWindow == 0L) {
                        secondaryWindow = createSecondaryWindow(window)
                    } else {
                        destroySecondaryWindow(secondaryWindow)
                        secondaryWindow = 0L
                    }
                }
            }
        }
        if (!isHotKey) {
            imguiKeyCallback?.invoke(win, key, scancode, action, mods)
        }
    }

    // Main loop
    var frameCount = 0
    var lastTime = glfwGetTime()

    val w = IntArray(1)
    val h = IntArray(1)
    val sw = IntArray(1)
    val sh = IntArray(1)

    while (!glfwWindowShouldClose(window)) {
        val frameStartTime = glfwGetTime()
        glfwPollEvents()

        // Clean up secondary window if it was closed manually by the user
        if (secondaryWindow != 0L && glfwWindowShouldClose(secondaryWindow)) {
            destroySecondaryWindow(secondaryWindow)
            secondaryWindow = 0L
        }

        // Calculate FPS
        frameCount++
        val currentTime = glfwGetTime()
        if (currentTime - lastTime >= 1.0) {
            logger.debug { "FPS: $frameCount" }
            frameCount = 0
            lastTime = currentTime
        }

        // === RENDERING PHASE ===

        // Apply loaded patches from queues atomically on the main thread
        PatchManager.applyPendingPatches(mixer)

        // Update all global CV signals
        CVRegistry.updateAll()

        // Get framebuffer size
        glfwGetFramebufferSize(window, w, h)

        // 0. Update MIDI mappings
        llm.slop.spirals.midi.MidiMappingManager.update(mixer)

        // 1. Update and Render Deck A (renders source + applies feedback loop)
        deckA.update()
        renderer.renderDeck(deckA)

        // 2. Update and Render Deck B (renders source + applies feedback loop)
        deckB.update()
        renderer.renderDeck(deckB)

        // 3. Update and Render Deck C (preview)
        mixer.deckC.update()
        renderer.renderDeck(mixer.deckC)

        // 4. Update and composite Deck A & B in the Mixer
        mixer.update()
        renderer.renderMixer(mixer)

        // 4. Blit the Mixer's master FBO to the screen viewport if enabled
        glViewport(0, 0, w[0], h[0])
        glClearColor(0.1f, 0.1f, 0.1f, 1.0f)
        glClear(GL_COLOR_BUFFER_BIT)

        if (UITheme.backgroundVideoEnabled || UITheme.cleanModeEnabled) {
            glEnable(GL_BLEND)
            glBlendFunc(GL_ONE, GL_ONE_MINUS_SRC_ALPHA)

            renderer.blitShader.bind()
            glActiveTexture(GL_TEXTURE0)
            glBindTexture(GL_TEXTURE_2D, mixer.masterFBO.texture)
            renderer.blitShader.setUniform("uTexture", 0)
            Geometry.drawFullscreenQuad()
            renderer.blitShader.unbind()
        }
        glBindVertexArray(0) // Ensure VAO is unbound for ImGui overlay rendering

        // Check for errors (only first few frames to avoid spam)
        if (frameCount < 3) {
            GLDebug.checkErrors("Deck rendering and compositing")
        }

        // === UI PHASE ===
        uiManager.render(mixer, w[0].toFloat(), h[0].toFloat())

        glfwSwapBuffers(window)

        // === SECONDARY WINDOW RENDER PHASE ===
        if (secondaryWindow != 0L) {
            glfwMakeContextCurrent(secondaryWindow)

            glfwGetFramebufferSize(secondaryWindow, sw, sh)

            glViewport(0, 0, sw[0], sh[0])
            glClearColor(0.0f, 0.0f, 0.0f, 1.0f)
            glClear(GL_COLOR_BUFFER_BIT)

            glEnable(GL_BLEND)
            glBlendFunc(GL_ONE, GL_ONE_MINUS_SRC_ALPHA)

            renderer.blitShader.bind()
            glActiveTexture(GL_TEXTURE0)
            glBindTexture(GL_TEXTURE_2D, mixer.masterFBO.texture)
            renderer.blitShader.setUniform("uTexture", 0)
            Geometry.drawSecondaryFullscreenQuad()
            renderer.blitShader.unbind()

            glfwSwapBuffers(secondaryWindow)

            // Switch back to main context
            glfwMakeContextCurrent(window)
        }

        // Cap frame rate to UITheme.maxFps
        val targetFrameTime = 1.0 / UITheme.maxFps
        val elapsed = glfwGetTime() - frameStartTime
        val sleepTime = targetFrameTime - elapsed
        if (sleepTime > 0) {
            val sleepMs = (sleepTime * 1000).toLong()
            if (sleepMs > 0) {
                try {
                    Thread.sleep(sleepMs)
                } catch (e: InterruptedException) {
                    // Ignore
                }
            }
            while (glfwGetTime() - frameStartTime < targetFrameTime) {
                Thread.yield()
            }
        }
    }

    // Cleanup
    logger.info { "Shutting down..." }
    PatchManager.saveSession(mixer)
    llm.slop.spirals.audio.MidiJackWatchdog.stop()
    AudioEngine.stop()
    llm.slop.spirals.midi.MidiEngine.close()

    // Free key callbacks
    imguiKeyCallback?.free()

    // Dispose secondary window
    if (secondaryWindow != 0L) {
        destroySecondaryWindow(secondaryWindow)
    }

    // Dispose rendering resources
    renderer.dispose()
    deckA.dispose()
    deckB.dispose()
    deckC.dispose()
    mixer.dispose()
    llm.slop.spirals.rendering.VisualSourceRegistry.disposeAll()
    Geometry.dispose()

    // Dispose UI
    uiManager.dispose()

    // Dispose window
    GLDebug.disposeDebugCallback()
    glfwDestroyWindow(window)
    glfwTerminate()
}

private fun getExternalMonitor(): Long? {
    val monitors = glfwGetMonitors() ?: return null
    val primary = glfwGetPrimaryMonitor()
    for (i in 0 until monitors.limit()) {
        val m = monitors.get(i)
        if (m != primary) {
            return m
        }
    }
    return null
}

private fun createSecondaryWindow(primaryWindow: Long): Long {
    // Save current window hints, then reset them to default
    glfwDefaultWindowHints()
    glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3)
    glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3)
    glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE)
    glfwWindowHint(GLFW_OPENGL_FORWARD_COMPAT, GLFW_TRUE)
    glfwWindowHint(GLFW_OPENGL_DEBUG_CONTEXT, GLFW_TRUE)

    val externalMonitor = getExternalMonitor()
    if (externalMonitor != null) {
        val mode = glfwGetVideoMode(externalMonitor) ?: return 0L
        glfwWindowHint(GLFW_AUTO_ICONIFY, GLFW_FALSE)
        glfwWindowHint(GLFW_DECORATED, GLFW_FALSE)
        val win = glfwCreateWindow(mode.width(), mode.height(), "Spirals Output", externalMonitor, primaryWindow)
        if (win != 0L) {
            logger.info { "Created secondary window fullscreen on external monitor (width: ${mode.width()}, height: ${mode.height()})" }
            return win
        }
    } else {
        glfwWindowHint(GLFW_DECORATED, GLFW_TRUE)
        glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE)
        val win = glfwCreateWindow(1280, 720, "Spirals Output Preview", 0, primaryWindow)
        if (win != 0L) {
            logger.info { "Created secondary preview window (no external monitor found)" }
            return win
        }
    }
    return 0L
}

private fun destroySecondaryWindow(win: Long) {
    if (win != 0L) {
        val mainContext = glfwGetCurrentContext()
        glfwMakeContextCurrent(win)
        Geometry.deleteSecondaryVAO()
        glfwMakeContextCurrent(mainContext)

        glfwDestroyWindow(win)
        logger.info { "Destroyed secondary window" }
    }
}
