package llm.slop.liquidlsd

import llm.slop.liquidlsd.rendering.FBO
import llm.slop.liquidlsd.rendering.Geometry
import llm.slop.liquidlsd.rendering.Shader
import llm.slop.liquidlsd.rendering.GLDebug
import llm.slop.liquidlsd.rendering.Renderer
import llm.slop.liquidlsd.rendering.Mandala
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
import org.lwjgl.glfw.GLFWImage
import org.lwjgl.stb.STBImage.*
import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryUtil
import org.lwjgl.opengl.GL
import org.lwjgl.opengl.GL33.*

private val logger = KotlinLogging.logger {}

private fun getAppVersion(): String {
    return llm.slop.liquidlsd.update.AppVersion.CURRENT
}

private fun printVersion() {
    val version = getAppVersion()
    val osName = System.getProperty("os.name") ?: "Unknown"
    val osArch = System.getProperty("os.arch") ?: "Unknown"
    val jvmVersion = System.getProperty("java.version") ?: "Unknown"
    val jvmVendor = System.getProperty("java.vendor") ?: "Unknown"
    val jvmHome = System.getProperty("java.home") ?: "Unknown"

    println("Liquid LSD v$version")
    println("OS:       $osName ($osArch)")
    println("JVM:      $jvmVersion ($jvmVendor)")
    println("JVM Home: $jvmHome")
}

private fun printHelp() {
    val version = getAppVersion()
    println("""
Liquid LSD - Libre Shader Decks v$version
Usage: liquid-lsd [OPTIONS]

Options:
  --version, -v      Print version and runtime platform details, then exit
  --smoke-test       Run headless multi-component smoke test and exit (0 on success)
  --help, -h         Display this help message and exit
""".trimIndent())
}

private fun runSmokeTest(): Int {
    println("==================================================")
    println("   Liquid LSD Headless Binary Diagnostic Test     ")
    println("==================================================")
    val osName = System.getProperty("os.name") ?: "Unknown"
    val osArch = System.getProperty("os.arch") ?: "Unknown"
    val jvmVersion = System.getProperty("java.version") ?: "Unknown"
    val jvmVendor = System.getProperty("java.vendor") ?: "Unknown"
    println("[1/5] Runtime Environment: OS=$osName, Arch=$osArch, JVM=$jvmVersion ($jvmVendor)")

    print("[2/5] Testing LWJGL native library linkage... ")
    try {
        val lwjglVer = org.lwjgl.Version.getVersion()
        println("OK (LWJGL $lwjglVer)")
    } catch (t: Throwable) {
        println("FAILED!")
        System.err.println("CRITICAL: Failed to load LWJGL native library for $osName/$osArch: ${t.message}")
        t.printStackTrace()
        return 1
    }

    print("[3/5] Testing Dear ImGui native bindings & JNI context... ")
    try {
        val context = imgui.ImGui.createContext()
        val imguiVer = imgui.ImGui.getVersion()
        imgui.ImGui.destroyContext(context)
        println("OK (Dear ImGui $imguiVer)")
    } catch (t: Throwable) {
        println("FAILED!")
        System.err.println("CRITICAL: Failed to load Dear ImGui native library for $osName/$osArch: ${t.message}")
        t.printStackTrace()
        return 2
    }

    print("[4/5] Testing classpath resource packaging (shaders/presets)... ")
    try {
        val requiredResources = listOf(
            "/shaders/blit.frag",
            "/shaders/blit.vert",
            "/shaders/mixer.frag",
            "/presets/default.json"
        )
        for (resPath in requiredResources) {
            val stream = object {}.javaClass.getResourceAsStream(resPath)
                ?: throw IllegalStateException("Required classpath resource '$resPath' was not found in bundle.")
            stream.close()
        }
        println("OK (${requiredResources.size} core assets verified)")
    } catch (t: Throwable) {
        println("FAILED!")
        System.err.println("CRITICAL: Resource verification failed: ${t.message}")
        t.printStackTrace()
        return 3
    }

    print("[5/5] Testing Audio Engine backend initialization & fallback... ")
    try {
        val backendName = llm.slop.liquidlsd.audio.AudioEngine.getActiveBackendName()
        println("OK (Initial Backend: $backendName)")
    } catch (t: Throwable) {
        println("FAILED!")
        System.err.println("CRITICAL: AudioEngine backend initialization failed: ${t.message}")
        t.printStackTrace()
        return 4
    }

    println("==================================================")
    println("✅ Smoke Test PASSED: Bundle, JVM, and JNI healthy!")
    println("==================================================")
    return 0
}

fun main(args: Array<String>) {
    if (args.contains("--help") || args.contains("-h")) {
        printHelp()
        kotlin.system.exitProcess(0)
    }

    if (args.contains("--version") || args.contains("-v")) {
        printVersion()
        kotlin.system.exitProcess(0)
    }

    if (args.contains("--smoke-test")) {
        val code = runSmokeTest()
        kotlin.system.exitProcess(code)
    }

    logger.info { "Starting Liquid LSD..." }

    // Ensure library directories exist
    java.io.File("library/presets").mkdirs()
    java.io.File("library/playlists").mkdirs()
    java.io.File("library/midi").mkdirs()
    llm.slop.liquidlsd.ui.FileSystemManager.ensureDefaultLibrary()



    // Load active MIDI mapping profile
    if (llm.slop.liquidlsd.ui.UITheme.midiEnabled) {
        llm.slop.liquidlsd.midi.MidiMappingManager.loadProfile(llm.slop.liquidlsd.ui.UITheme.activeMidiProfile)
    }

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
    glfwWindowHintString(GLFW_WAYLAND_APP_ID, "liquid-lsd")

    // Configure window decorations based on user setting (Frameless CSD vs Native)
    val isFrameless = UITheme.framelessWindow
    glfwWindowHint(GLFW_DECORATED, if (isFrameless) GLFW_FALSE else GLFW_TRUE)

    // Create window
    val window = glfwCreateWindow(1920, 1080, "Liquid LSD - Libre Shader Decks", 0, 0)
    if (window == 0L) throw RuntimeException("Failed to create GLFW window")
    setWindowAppIcons(window)
    ensureLinuxDesktopEntry()

    // Enforce minimum window size to prevent desktop layout compression
    glfwSetWindowSizeLimits(window, 1280, 720, GLFW_DONT_CARE, GLFW_DONT_CARE)

    // Log detected OS display content scale for diagnostics.
    // With imgui-java 1.86.12+, the ImGui GLFW/GL3 backends handle framebuffer content scaling automatically.
    val xScaleBuf = FloatArray(1)
    val yScaleBuf = FloatArray(1)
    glfwGetWindowContentScale(window, xScaleBuf, yScaleBuf)
    val startupScale = xScaleBuf[0].coerceAtLeast(1.0f)
    logger.info { "Display content scale detected: ${startupScale}x → UI fixed at 95% (Cap 12, Body 14, H3 15, H2 18, H1 22 px), PresetName=${UITheme.presetNameScalePercent}%" }

    glfwMakeContextCurrent(window)
    glfwSwapInterval(1) // Enable vsync

    // Initialize OpenGL
    GL.createCapabilities()
    GLDebug.setupDebugCallback()

    val queryIds = IntArray(2)
    org.lwjgl.opengl.GL15.glGenQueries(queryIds)

    // Load dynamic visual sources and ISF filters
    llm.slop.liquidlsd.rendering.VisualSourceRegistry.loadAll(async = true)
    llm.slop.liquidlsd.rendering.isf.ISFFilterRegistry.loadAll()
    llm.slop.liquidlsd.rendering.isf.ISFTransitionRegistry.loadAll()
    llm.slop.liquidlsd.rendering.ExternalVideoDiscovery.startPolling()

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

    glfwSetWindowContentScaleCallback(window) { _, xScale, _ ->
        val newDpi = xScale.coerceAtLeast(1.0f)
        uiManager.onContentScaleChanged(newDpi)
    }

    logger.info { "Initialization complete" }

    // Initialize rendering components
    logger.info { "Initializing Decks and Mixer..." }
    val renderer = Renderer()

    val masterMandala = llm.slop.liquidlsd.rendering.VisualSourceRegistry.availableSources.firstOrNull { it.id == "mandala" } as? Mandala
        ?: throw RuntimeException("Mandala source not loaded from library/sources/mandala")

    val initialWidth = UITheme.renderWidth
    val initialHeight = UITheme.renderHeight

    val deckA = Deck(masterMandala.clone(), initialWidth, initialHeight)
    val deckB = Deck(masterMandala.clone(), initialWidth, initialHeight)
    val deckBG = Deck(masterMandala.clone(), initialWidth, initialHeight)
    val deckPV = Deck(masterMandala.clone(), initialWidth, initialHeight)

    // Create Mixer
    val mixer = Mixer(deckA, deckB, deckBG, deckPV, initialWidth, initialHeight)
    PresetManager.startEmpty(mixer)
    if (UITheme.startupBehavior != UITheme.StartupBehavior.EMPTY) {
        PresetManager.loadSession(mixer)
    }
    NotesManager.loadSourceNotes()
    session.touchConsoleController.initialize(window, mixer)
    glfwSetWindowFocusCallback(window) { _, focused ->
        if (!focused) {
            session.touchConsoleController.onFocusLost()
        }
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
    llm.slop.liquidlsd.audio.MidiJackWatchdog.start()

    // Auto-detect and open secondary window on startup ONLY if an external monitor is found
    val initialExternalMonitor = getExternalMonitor()
    if (initialExternalMonitor != null) {
        secondaryWindow = createSecondaryWindow(window)
    }

    // Setup key callback chaining to allow "f", "b", ESC, CTRL-, CTRL=, and CTRL-R controls
    var imguiKeyCallback: org.lwjgl.glfw.GLFWKeyCallback? = null
    imguiKeyCallback = glfwSetKeyCallback(window) { win, key, scancode, action, mods ->
        val io = imgui.ImGui.getIO()
        val isMinus = key == GLFW_KEY_MINUS || key == GLFW_KEY_KP_SUBTRACT
        val isEqual = key == GLFW_KEY_EQUAL || key == GLFW_KEY_KP_ADD
        val isFontSizeHotKey = (mods and GLFW_MOD_CONTROL) != 0 && (isMinus || isEqual)
        val isRecordHotKey = (mods and GLFW_MOD_CONTROL) != 0 && key == GLFW_KEY_R
        val isEscapeFullscreen = key == GLFW_KEY_ESCAPE && UITheme.cleanModeEnabled
        val isShortcutAllowed = !io.wantTextInput || UITheme.cleanModeEnabled
        val isPlainFOrB = (mods == 0) && (key == GLFW_KEY_F || key == GLFW_KEY_B) && isShortcutAllowed
        val isCapsLock = key == GLFW_KEY_CAPS_LOCK
        val isTapTempoKey = (mods == 0) && isShortcutAllowed && key == GLFW_KEY_T
        val isHotKey = isPlainFOrB || isFontSizeHotKey || isRecordHotKey || isEscapeFullscreen || isCapsLock || isTapTempoKey

        if (action == GLFW_PRESS) {
            if (isTapTempoKey) {
                session.tapTempoController.tap()
            } else if (isCapsLock) {
                session.touchConsoleController.toggleActive()
            } else if (isFontSizeHotKey) {
                if (isMinus) {
                    uiManager.adjustPresetNameScale(-1f)
                } else if (isEqual) {
                    uiManager.adjustPresetNameScale(1f)
                }
            } else if (isRecordHotKey) {
                if (llm.slop.liquidlsd.export.RealtimeRecorder.isRecording) {
                    llm.slop.liquidlsd.export.RealtimeRecorder.stopRecording()
                } else {
                    val dateStr = java.text.SimpleDateFormat("yyyyMMdd_HHmmss").format(java.util.Date())
                    val recDir = UITheme.getDefaultVideosDirectory()
                    val outFile = java.io.File(recDir, "liquid_lsd_$dateStr.mp4")
                    llm.slop.liquidlsd.export.RealtimeRecorder.startRecording(
                        outputFile = outFile,
                        width = mixer.width,
                        height = mixer.height,
                        fps = UITheme.recordingFps,
                        bitrateMbps = UITheme.recordingBitrateMbps,
                        includeAudio = UITheme.recordingIncludeAudio
                    )
                }
            } else if (isPlainFOrB && key == GLFW_KEY_F) {
                UITheme.cleanModeEnabled = !UITheme.cleanModeEnabled
                logger.info { "Clean mode toggled: ${UITheme.cleanModeEnabled}" }
            } else if (key == GLFW_KEY_ESCAPE && UITheme.cleanModeEnabled) {
                UITheme.cleanModeEnabled = false
                logger.info { "Clean mode exited via ESC: ${UITheme.cleanModeEnabled}" }
            } else if (isPlainFOrB && key == GLFW_KEY_B) {
                UITheme.backgroundVideoEnabled = !UITheme.backgroundVideoEnabled
                UITheme.saveSettings()
                logger.info { "Background video toggled: ${UITheme.backgroundVideoEnabled}" }
            }
        }
        if (!isHotKey) {
            imguiKeyCallback?.invoke(win, key, scancode, action, mods)
        }
    }

    // Start broadcast relay if autoConnect is enabled
    if (llm.slop.liquidlsd.broadcast.BroadcastSettings.autoConnect) {
        llm.slop.liquidlsd.broadcast.BroadcastEngine.startBroadcast(mixer)
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
        session.touchConsoleController.processPendingEvents()

        // Query window and framebuffer dimensions
        glfwGetFramebufferSize(window, w, h)
        glfwGetWindowSize(window, windowW, windowH)

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
        if (llm.slop.liquidlsd.export.OfflineRenderStudio.isRendering) {
            llm.slop.liquidlsd.export.OfflineRenderStudio.step(mixer, renderer)
        } else {
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

            // 0. Update MIDI mappings
            if (llm.slop.liquidlsd.ui.UITheme.midiEnabled) {
                llm.slop.liquidlsd.midi.MidiMappingManager.update(mixer)
            }

            // 1. Update and Render Deck A (renders source + applies feedback loop)
            deckA.update()
            renderer.renderDeck(deckA)

            // 2. Update and Render Deck B (renders source + applies feedback loop)
            deckB.update()
            renderer.renderDeck(deckB)

            // 3. Update and Render Deck BG (background layer)
            mixer.deckBG.update()
            renderer.renderDeck(mixer.deckBG)

            // 4. Update and Render Deck PV (preview)
            mixer.deckPV.update()
            renderer.renderDeck(mixer.deckPV)

            // 5. Update and composite Decks in the Mixer
            mixer.update()
            renderer.renderMixer(mixer)

            // 6. External Video Sharing & Capture
            llm.slop.liquidlsd.rendering.TextureStreamerManager.update(llm.slop.liquidlsd.rendering.VideoOutputEndpoint.DECK_A, deckA.getOutputTexture(), mixer.width, mixer.height, renderer)
            llm.slop.liquidlsd.rendering.TextureStreamerManager.update(llm.slop.liquidlsd.rendering.VideoOutputEndpoint.DECK_B, deckB.getOutputTexture(), mixer.width, mixer.height, renderer)
            llm.slop.liquidlsd.rendering.TextureStreamerManager.update(llm.slop.liquidlsd.rendering.VideoOutputEndpoint.DECK_BG, mixer.deckBG.getOutputTexture(), mixer.width, mixer.height, renderer)
            llm.slop.liquidlsd.rendering.TextureStreamerManager.update(llm.slop.liquidlsd.rendering.VideoOutputEndpoint.DECK_PV, mixer.deckPV.getOutputTexture(), mixer.width, mixer.height, renderer)
            llm.slop.liquidlsd.rendering.TextureStreamerManager.update(llm.slop.liquidlsd.rendering.VideoOutputEndpoint.MASTER, mixer.masterFBO.texture, mixer.width, mixer.height, renderer)

            llm.slop.liquidlsd.export.RealtimeRecorder.captureFrame(mixer.masterFBO.framebufferId)
            llm.slop.liquidlsd.broadcast.BroadcastEngine.tick(mixer)
        }

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
            uiManager.render(mixer, renderer, winW.toFloat(), winH.toFloat())
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
    llm.slop.liquidlsd.broadcast.BroadcastEngine.shutdown()
    llm.slop.liquidlsd.export.RealtimeRecorder.stopRecording()
    llm.slop.liquidlsd.rendering.TextureStreamerManager.shutdown()
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
    deckBG.dispose()
    deckPV.dispose()
    mixer.dispose()
    llm.slop.liquidlsd.rendering.VisualSourceRegistry.disposeAll()
    llm.slop.liquidlsd.rendering.isf.ISFFilterRegistry.disposeAll()
    llm.slop.liquidlsd.rendering.isf.ISFTransitionRegistry.disposeAll()
    Geometry.dispose()

    // Dispose UI and input
    session.touchConsoleController.shutdown()
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
    glfwWindowHintString(GLFW_X11_CLASS_NAME, "Liquid LSD Output")
    glfwWindowHintString(GLFW_X11_INSTANCE_NAME, "liquid-lsd")
    glfwWindowHintString(GLFW_WAYLAND_APP_ID, "liquid-lsd")

    val externalMonitor = getExternalMonitor()
    if (externalMonitor != null) {
        val mode = glfwGetVideoMode(externalMonitor) ?: return 0L
        glfwWindowHint(GLFW_AUTO_ICONIFY, GLFW_FALSE)
        glfwWindowHint(GLFW_DECORATED, GLFW_FALSE)
        val win = glfwCreateWindow(mode.width(), mode.height(), "Liquid LSD Output", externalMonitor, primaryWindow)
        if (win != 0L) {
            setWindowAppIcons(win)
            logger.info { "Created secondary window fullscreen on external monitor (width: ${mode.width()}, height: ${mode.height()})" }
            return win
        }
    } else {
        glfwWindowHint(GLFW_DECORATED, GLFW_TRUE)
        glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE)
        val win = glfwCreateWindow(1280, 720, "Liquid LSD Output Preview", 0, primaryWindow)
        if (win != 0L) {
            setWindowAppIcons(win)
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

private fun setWindowAppIcons(window: Long) {
    if (window == 0L) return
    val iconSizes = listOf(16, 32, 48, 64, 128, 256)
    val directBuffers = mutableListOf<java.nio.ByteBuffer>()
    val pixelBuffers = mutableListOf<java.nio.ByteBuffer>()

    try {
        MemoryStack.stackPush().use { stack ->
            val w = stack.mallocInt(1)
            val h = stack.mallocInt(1)
            val comp = stack.mallocInt(1)

            val loadedIcons = mutableListOf<Triple<Int, Int, java.nio.ByteBuffer>>()

            for (size in iconSizes) {
                val res = "/icons/icon-$size.png"
                val stream = object {}.javaClass.getResourceAsStream(res) ?: continue
                val bytes = stream.use { it.readBytes() }
                val byteBuffer = MemoryUtil.memAlloc(bytes.size)
                directBuffers.add(byteBuffer)
                byteBuffer.put(bytes).flip()

                w.clear(); h.clear(); comp.clear()
                val pixels = stbi_load_from_memory(byteBuffer, w, h, comp, 4)
                if (pixels != null) {
                    pixelBuffers.add(pixels)
                    loadedIcons.add(Triple(w.get(0), h.get(0), pixels))
                }
            }

            if (loadedIcons.isNotEmpty()) {
                val buffer = GLFWImage.malloc(loadedIcons.size, stack)
                for (i in loadedIcons.indices) {
                    val (iconW, iconH, pixels) = loadedIcons[i]
                    buffer.get(i).set(iconW, iconH, pixels)
                }
                glfwSetWindowIcon(window, buffer)
                logger.info { "Configured GLFW window icon with ${loadedIcons.size} resolution mipmaps (16x16 to 256x256)." }
            }
        }
    } catch (e: Exception) {
        logger.warn(e) { "Failed to set GLFW window icon" }
    } finally {
        for (pixels in pixelBuffers) {
            stbi_image_free(pixels)
        }
        for (buf in directBuffers) {
            MemoryUtil.memFree(buf)
        }
    }
}

/**
 * On Linux desktops, Wayland and X11 compositors require a registered `.desktop` file
 * and FreeDesktop hicolor icon to associate the running window (app_id: "liquid-lsd")
 * with the correct application icon in the dock/taskbar and Alt-Tab switcher.
 */
private fun ensureLinuxDesktopEntry() {
    if (!System.getProperty("os.name", "").lowercase().contains("linux")) return
    try {
        val userHome = System.getProperty("user.home") ?: return
        val xdgData = System.getenv("XDG_DATA_HOME")?.takeIf { it.isNotBlank() }
            ?: "$userHome/.local/share"

        val appsDir = java.io.File(xdgData, "applications")
        val iconsDir = java.io.File(xdgData, "icons/hicolor/512x512/apps")
        val targetIcon = java.io.File(iconsDir, "liquid-lsd.png")
        val targetDesktop = java.io.File(appsDir, "liquid-lsd.desktop")

        if (!targetIcon.exists()) {
            iconsDir.mkdirs()
            val stream = object {}.javaClass.getResourceAsStream("/icons/icon-512.png")
            if (stream != null) {
                stream.use { input ->
                    java.nio.file.Files.copy(input, targetIcon.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
                }
                logger.info { "Registered local desktop application icon: ${targetIcon.absolutePath}" }
            }
        }

        if (!targetDesktop.exists()) {
            appsDir.mkdirs()
            val desktopContent = """
                [Desktop Entry]
                Version=1.0
                Type=Application
                Name=Liquid LSD
                GenericName=Audio-Reactive Visual Synthesizer
                Comment=Libre Shader Decks - Real-time audio-reactive graphics workstation
                Icon=liquid-lsd
                Terminal=false
                Categories=AudioVideo;Graphics;Audio;
                StartupNotify=true
                StartupWMClass=liquid-lsd
            """.trimIndent()
            targetDesktop.writeText(desktopContent)
            logger.info { "Registered local desktop entry: ${targetDesktop.absolutePath}" }
        }
    } catch (t: Throwable) {
        logger.debug(t) { "Unable to auto-register desktop entry (harmless in sandboxed or read-only environments)" }
    }
}
