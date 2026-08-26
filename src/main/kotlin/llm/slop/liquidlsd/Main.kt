package llm.slop.liquidlsd

import llm.slop.liquidlsd.rendering.FBO
import llm.slop.liquidlsd.rendering.Geometry
import llm.slop.liquidlsd.rendering.Shader
import llm.slop.liquidlsd.rendering.GLDebug
import llm.slop.liquidlsd.rendering.Renderer
import llm.slop.liquidlsd.rendering.Mandala
import llm.slop.liquidlsd.rendering.MandalaRatio
import llm.slop.liquidlsd.rendering.Deck
import llm.slop.liquidlsd.rendering.Mixer
import llm.slop.liquidlsd.ui.UIManager
import llm.slop.liquidlsd.audio.AudioEngine
import llm.slop.liquidlsd.ui.UITheme
import llm.slop.liquidlsd.cv.CVRegistry
import llm.slop.liquidlsd.notes.NotesManager
import llm.slop.liquidlsd.presets.PresetManager
import mu.KotlinLogging
import org.lwjgl.glfw.Callbacks.glfwFreeCallbacks
import org.lwjgl.glfw.GLFW.*
import org.lwjgl.opengl.GL
import org.lwjgl.opengl.GL33.*

private val logger = KotlinLogging.logger {}

fun main() {
    logger.info { "Starting Liquid LSD..." }

    // Ensure library directories exist
    java.io.File("library/presets").mkdirs()
    java.io.File("library/playlists").mkdirs()
    java.io.File("library/midi").mkdirs()



    // Load active MIDI mapping profile
    llm.slop.liquidlsd.midi.MidiMappingManager.loadProfile(llm.slop.liquidlsd.ui.UITheme.activeMidiProfile)

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

    glfwWindowHintString(GLFW_X11_CLASS_NAME, "Liquid LSD")
    glfwWindowHintString(GLFW_X11_INSTANCE_NAME, "liquid-lsd")

    // Create window
    val window = glfwCreateWindow(1920, 1080, "Liquid LSD - Libre Shader Decks", 0, 0)
        ?: throw RuntimeException("Failed to create GLFW window")

    // Enforce minimum window size to prevent desktop layout compression
    glfwSetWindowSizeLimits(window, 800, 600, GLFW_DONT_CARE, GLFW_DONT_CARE)

    // Query OS DPI / HiDPI content scale so controls are legible on 4K screens.
    // Only applied when no user settings file exists yet (first run / fresh install).
    // On standard 1080p monitors xScale ≈ 1.0; on 4K HiDPI monitors it can be 1.5–2.0+.
    if (!java.io.File("lsd-settings.properties").exists()) {
        val xScaleBuf = FloatArray(1)
        val yScaleBuf = FloatArray(1)
        glfwGetWindowContentScale(window, xScaleBuf, yScaleBuf)
        val dpiScale = xScaleBuf[0].coerceAtLeast(1.0f)
        if (dpiScale > 1.05f) {
            // BASE_PX=15f; snap to nearest 5 % step, clamp 75 %–200 %
            val BASE_PX   = 15f
            val STEP_PCT  = 5
            val rawPct    = (dpiScale * 100f).toInt()
            val snappedPct = (rawPct / STEP_PCT * STEP_PCT).coerceIn(75, 200)
            UITheme.baseSize = snappedPct / 100f * BASE_PX
            logger.info { "HiDPI scale detected: ${dpiScale}x → GUI scale ${snappedPct}%% (baseSize=${UITheme.baseSize}px)" }
        }
    }

    glfwMakeContextCurrent(window)
    glfwSwapInterval(1) // Enable vsync

    // Initialize OpenGL
    GL.createCapabilities()
    GLDebug.setupDebugCallback()

    val queryIds = IntArray(2)
    org.lwjgl.opengl.GL15.glGenQueries(queryIds)

    // Load dynamic visual sources
    llm.slop.liquidlsd.rendering.VisualSourceRegistry.loadAll()

    logger.info { "OpenGL Version: ${glGetString(GL_VERSION)}" }
    logger.info { "OpenGL Renderer: ${glGetString(GL_RENDERER)}" }

    // Initialize Session Context
    val session = llm.slop.liquidlsd.SessionContext()

    var secondaryWindow = 0L

    // Initialize UI Manager
    val uiManager = UIManager(
        windowHandle = window,
        session = session,
        onToggleOutputWindow = {
            if (secondaryWindow == 0L) {
                secondaryWindow = createSecondaryWindow(window)
            } else {
                destroySecondaryWindow(secondaryWindow)
                secondaryWindow = 0L
            }
        },
        isOutputWindowOpen = { secondaryWindow != 0L }
    )

    glfwSetWindowCloseCallback(window) { win ->
        glfwSetWindowShouldClose(win, false)
        uiManager.triggerExitFlow()
    }

    logger.info { "Initialization complete" }

    // Initialize rendering components
    logger.info { "Initializing Decks and Mixer..." }
    val renderer = Renderer()

    val masterMandala = llm.slop.liquidlsd.rendering.VisualSourceRegistry.availableSources.firstOrNull { it.id == "mandala" } as? Mandala
        ?: throw RuntimeException("Mandala source not loaded from presets/sources/mandala")

    // Create Deck A with a 4-petal recipe (yellow-ish theme default)
    val recipeA = MandalaRatio(
        id = "15001423042349762156",
        a = 26,
        b = 23,
        c = 14,
        d = 14
    )
    val initialWidth = UITheme.renderWidth
    val initialHeight = UITheme.renderHeight

    val mandalaA = masterMandala.clone()
    mandalaA.recipe = recipeA
    val deckA = Deck(mandalaA, initialWidth, initialHeight)

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
    val deckB = Deck(mandalaB, initialWidth, initialHeight)

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
    val deckC = Deck(mandalaC, initialWidth, initialHeight)

    // Create Mixer
    val mixer = Mixer(deckA, deckB, deckC, initialWidth, initialHeight)
    if (UITheme.startupBehavior == UITheme.StartupBehavior.EMPTY) {
        PresetManager.startEmpty(mixer)
    } else {
        PresetManager.loadSession(mixer)
    }
    NotesManager.loadSourceNotes()
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
    llm.slop.liquidlsd.audio.MidiJackWatchdog.start()

    // Auto-detect and open secondary window on startup ONLY if an external monitor is found
    val initialExternalMonitor = getExternalMonitor()
    if (initialExternalMonitor != null) {
        secondaryWindow = createSecondaryWindow(window)
    }

    // Setup key callback chaining to allow "f", "b", CTRL-, and CTRL= controls
    var imguiKeyCallback: org.lwjgl.glfw.GLFWKeyCallback? = null
    imguiKeyCallback = glfwSetKeyCallback(window) { win, key, scancode, action, mods ->
        val io = imgui.ImGui.getIO()
        val isFontSizeHotKey = (mods and GLFW_MOD_CONTROL) != 0 && (key == GLFW_KEY_MINUS || key == GLFW_KEY_EQUAL)
        val isShortcutAllowed = !io.wantTextInput || UITheme.cleanModeEnabled
        val isHotKey = ((key == GLFW_KEY_F || key == GLFW_KEY_B) && isShortcutAllowed) || isFontSizeHotKey

        if (action == GLFW_PRESS) {
            if (isFontSizeHotKey) {
                if (key == GLFW_KEY_MINUS) {
                    uiManager.adjustFontSize(-1f)
                } else if (key == GLFW_KEY_EQUAL) {
                    uiManager.adjustFontSize(1f)
                }
            } else if (key == GLFW_KEY_F && isShortcutAllowed) {
                UITheme.cleanModeEnabled = !UITheme.cleanModeEnabled
                logger.info { "Clean mode toggled: ${UITheme.cleanModeEnabled}" }
            } else if (key == GLFW_KEY_B && isShortcutAllowed) {
                UITheme.backgroundVideoEnabled = !UITheme.backgroundVideoEnabled
                UITheme.saveSettings()
                logger.info { "Background video toggled: ${UITheme.backgroundVideoEnabled}" }
            }
        }
        if (!isHotKey) {
            imguiKeyCallback?.invoke(win, key, scancode, action, mods)
        }
    }

    // Main loop
    var frameCount = 0
    var frameIndex = 0
    var lastTime = glfwGetTime()

    val w = IntArray(1)
    val h = IntArray(1)
    val windowW = IntArray(1)
    val windowH = IntArray(1)
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

        org.lwjgl.opengl.GL15.glBeginQuery(org.lwjgl.opengl.GL33.GL_TIME_ELAPSED, queryIds[frameIndex % 2])

        // === RENDERING PHASE ===
        val targetRenderW = UITheme.renderWidth
        val targetRenderH = UITheme.renderHeight
        if (mixer.width != targetRenderW || mixer.height != targetRenderH) {
            logger.info { "Resizing render pipeline from ${mixer.width}x${mixer.height} to ${targetRenderW}x${targetRenderH}" }
            mixer.resize(targetRenderW, targetRenderH)
        }

        // Apply loaded presets from queues atomically on the main thread
        PresetManager.applyPendingPresets(mixer)

        // Update all global CV signals
        CVRegistry.updateAll()

        // Get framebuffer size for OpenGL and window size for ImGui layout.
        // ImGui GLFW coordinates are logical window units, not framebuffer pixels.
        glfwGetFramebufferSize(window, w, h)
        glfwGetWindowSize(window, windowW, windowH)

        // 0. Update MIDI mappings
        llm.slop.liquidlsd.midi.MidiMappingManager.update(mixer)

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

        // 4. Blit the Mixer's master FBO to the screen viewport if enabled and window is visible
        val fbW = w[0]
        val fbH = h[0]
        val winW = windowW[0]
        val winH = windowH[0]

        if (fbW > 0 && fbH > 0 && winW > 0 && winH > 0) {
            glViewport(0, 0, fbW, fbH)
            glClearColor(0.0f, 0.0f, 0.0f, 1.0f)
            glClear(GL_COLOR_BUFFER_BIT)

            if (UITheme.backgroundVideoEnabled || UITheme.cleanModeEnabled) {
                val vp = llm.slop.liquidlsd.rendering.ViewportHelper.computeViewport(
                    fbW, fbH,
                    mixer.width, mixer.height,
                    UITheme.outputScaleMode
                )
                glViewport(vp.x, vp.y, vp.width, vp.height)

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
            uiManager.render(mixer, winW.toFloat(), winH.toFloat())
        }

        org.lwjgl.opengl.GL15.glEndQuery(org.lwjgl.opengl.GL33.GL_TIME_ELAPSED)
        if (frameIndex > 0) {
            val frameNanos = org.lwjgl.opengl.GL33.glGetQueryObjecti64(queryIds[(frameIndex + 1) % 2], org.lwjgl.opengl.GL15.GL_QUERY_RESULT)
            llm.slop.liquidlsd.ui.PerformanceStats.frameTimeNanos.set(frameNanos)
        }
        frameIndex++

        glfwSwapBuffers(window)

        // === SECONDARY WINDOW RENDER PHASE ===
        if (secondaryWindow != 0L) {
            glfwMakeContextCurrent(secondaryWindow)

            glfwGetFramebufferSize(secondaryWindow, sw, sh)

            if (sw[0] > 0 && sh[0] > 0) {
                glViewport(0, 0, sw[0], sh[0])
                glClearColor(0.0f, 0.0f, 0.0f, 1.0f)
                glClear(GL_COLOR_BUFFER_BIT)

                val vp = llm.slop.liquidlsd.rendering.ViewportHelper.computeViewport(
                    sw[0], sh[0],
                    mixer.width, mixer.height,
                    UITheme.outputScaleMode
                )
                glViewport(vp.x, vp.y, vp.width, vp.height)

                glEnable(GL_BLEND)
                glBlendFunc(GL_ONE, GL_ONE_MINUS_SRC_ALPHA)

                renderer.blitShader.bind()
                glActiveTexture(GL_TEXTURE0)
                glBindTexture(GL_TEXTURE_2D, mixer.masterFBO.texture)
                renderer.blitShader.setUniform("uTexture", 0)
                Geometry.drawSecondaryFullscreenQuad()
                renderer.blitShader.unbind()

                glfwSwapBuffers(secondaryWindow)
            }

            // Switch back to main context
            glfwMakeContextCurrent(window)
        }

        // Cap frame rate to session.uiTheme.maxFps
        val targetFrameTime = 1.0 / session.uiTheme.maxFps
        val elapsed = glfwGetTime() - frameStartTime
        var remaining = targetFrameTime - elapsed
        if (remaining > 0.0) {
            // First sleep with a 1 ms (1,000,000 ns) safety margin to avoid oversleeping.
            // A 1ms margin is safe for 30/60 FPS desktop apps, giving the OS scheduler 
            // plenty of leeway without wasting CPU in a spin loop.
            val sleep1Ms = ((remaining * 1000.0) - 1.0).toLong()
            if (sleep1Ms > 0) {
                try {
                    Thread.sleep(sleep1Ms)
                } catch (e: InterruptedException) {
                    // Ignore
                }
            }

            // Second sleep with a smaller 0.2 ms (200,000 ns) margin to get closer to the target.
            // No spin loop is used (Thread.yield() removed) to conserve CPU.
            remaining = targetFrameTime - (glfwGetTime() - frameStartTime)
            if (remaining > 0.0002) {
                val sleep2Ns = ((remaining - 0.0002) * 1_000_000_000.0).toLong()
                val ms = sleep2Ns / 1_000_000L
                val ns = (sleep2Ns % 1_000_000L).toInt()
                try {
                    Thread.sleep(ms, ns)
                } catch (e: InterruptedException) {
                    // Ignore
                }
            }
        }
    }

    // Cleanup
    logger.info { "Shutting down..." }
    PresetManager.saveSession(mixer)
    llm.slop.liquidlsd.audio.MidiJackWatchdog.stop()
    AudioEngine.stop()
    llm.slop.liquidlsd.midi.MidiEngine.close()

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
    llm.slop.liquidlsd.rendering.VisualSourceRegistry.disposeAll()
    Geometry.dispose()

    // Dispose UI
    uiManager.dispose()

    llm.slop.liquidlsd.rendering.GLResourceTracker.assertNoLeaks()

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
        val win = glfwCreateWindow(mode.width(), mode.height(), "Liquid LSD Output", externalMonitor, primaryWindow)
        if (win != 0L) {
            logger.info { "Created secondary window fullscreen on external monitor (width: ${mode.width()}, height: ${mode.height()})" }
            return win
        }
    } else {
        glfwWindowHint(GLFW_DECORATED, GLFW_TRUE)
        glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE)
        val win = glfwCreateWindow(1280, 720, "Liquid LSD Output Preview", 0, primaryWindow)
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
