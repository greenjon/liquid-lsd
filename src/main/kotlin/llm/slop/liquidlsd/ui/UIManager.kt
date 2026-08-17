package llm.slop.liquidlsd.ui

import imgui.ImGui
import imgui.ImColor
import imgui.flag.ImGuiCol
import imgui.flag.ImGuiConfigFlags
import imgui.flag.ImGuiWindowFlags
import imgui.type.ImInt
import imgui.type.ImString
import java.io.File
import llm.slop.liquidlsd.rendering.Deck
import llm.slop.liquidlsd.rendering.Mandala
import llm.slop.liquidlsd.rendering.MandalaLibrary
import llm.slop.liquidlsd.rendering.MandalaRatio





import llm.slop.liquidlsd.rendering.Mixer
import llm.slop.liquidlsd.presets.PresetManager
import kotlin.math.roundToInt
import mu.KotlinLogging
import org.lwjgl.opengl.GL33.*
import imgui.gl3.ImGuiImplGl3
import imgui.glfw.ImGuiImplGlfw
import llm.slop.liquidlsd.parameters.ModulatableParameter
import llm.slop.liquidlsd.models.toDto
import llm.slop.liquidlsd.models.applyDto
import llm.slop.liquidlsd.presets.PlayQueueManager

/**
 * Manages the ImGui overlay for desktop control.
 */
class UIManager(private val windowHandle: Long, val session: llm.slop.liquidlsd.SessionContext) {
    private val logger = KotlinLogging.logger {}
    private val imguiGlfw = ImGuiImplGlfw()
    private val imguiGl3 = ImGuiImplGl3()


    // Clean default style to reset size attributes before scaling
    private lateinit var defaultStyle: imgui.ImGuiStyle

    // Font rebuild must happen between frames (atlas is locked during a frame).
    // Store the requested size here; it is consumed at the top of the next render().
    private var pendingFontSize: Float? = null

    // Set to true for one frame when the Settings menu item is clicked; consumed
    // immediately after endMainMenuBar so openPopup runs at root ID-stack level.
    private var pendingOpenSettings = false

    private var activeSplitterId: String? = null
    private var pendingOpenAudioEngineMonitor = false

    private var lastBgVideoEnabled: Boolean? = null
    private var lastTheme: UITheme.Theme? = null

    private fun setupThemeColors(theme: UITheme.Theme, bgVideoEnabled: Boolean) {
        val style = ImGui.getStyle()
        val isLight = theme == UITheme.Theme.LIGHT_SOLARIZED || theme == UITheme.Theme.LIGHT_LUNARIZED

        if (isLight) {
            ImGui.styleColorsLight()
        } else {
            ImGui.styleColorsDark()
        }

        val alpha = if (bgVideoEnabled) 0.75f else 1.00f

        when (theme) {
            UITheme.Theme.BORING -> {
                if (bgVideoEnabled) {
                    style.setColor(ImGuiCol.WindowBg, 0.06f, 0.06f, 0.06f, 0.75f)
                    style.setColor(ImGuiCol.TitleBg, 0.04f, 0.04f, 0.04f, 0.75f)
                    style.setColor(ImGuiCol.TitleBgActive, 0.16f, 0.16f, 0.16f, 0.75f)
                    style.setColor(ImGuiCol.MenuBarBg, 0.14f, 0.14f, 0.14f, 0.75f)
                    style.setColor(ImGuiCol.PopupBg, 0.08f, 0.08f, 0.08f, 1.00f)
                } else {
                    style.setColor(ImGuiCol.WindowBg, 0.06f, 0.06f, 0.06f, 1.00f)
                    style.setColor(ImGuiCol.TitleBg, 0.04f, 0.04f, 0.04f, 1.00f)
                    style.setColor(ImGuiCol.TitleBgActive, 0.16f, 0.16f, 0.16f, 1.00f)
                    style.setColor(ImGuiCol.MenuBarBg, 0.14f, 0.14f, 0.14f, 1.00f)
                    style.setColor(ImGuiCol.PopupBg, 0.08f, 0.08f, 0.08f, 1.00f)
                }
            }
            UITheme.Theme.DARK_SOLARIZED -> {
                style.setColor(ImGuiCol.WindowBg, 0.00f, 0.17f, 0.21f, alpha)
                style.setColor(ImGuiCol.PopupBg, 0.03f, 0.21f, 0.26f, 1.00f)
                style.setColor(ImGuiCol.TitleBg, 0.03f, 0.21f, 0.26f, alpha)
                style.setColor(ImGuiCol.TitleBgActive, 0.00f, 0.17f, 0.21f, alpha)
                style.setColor(ImGuiCol.MenuBarBg, 0.03f, 0.21f, 0.26f, alpha)
                
                style.setColor(ImGuiCol.FrameBg, 0.03f, 0.21f, 0.26f, 1.00f)
                style.setColor(ImGuiCol.FrameBgHovered, 0.00f, 0.17f, 0.21f, 1.00f)
                style.setColor(ImGuiCol.FrameBgActive, 0.80f, 0.29f, 0.09f, 1.00f)
                
                style.setColor(ImGuiCol.Button, 0.03f, 0.21f, 0.26f, 1.00f)
                style.setColor(ImGuiCol.ButtonHovered, 0.35f, 0.43f, 0.46f, 1.00f)
                style.setColor(ImGuiCol.ButtonActive, 0.80f, 0.29f, 0.09f, 1.00f)
                
                style.setColor(ImGuiCol.SliderGrab, 0.80f, 0.29f, 0.09f, 1.00f)
                style.setColor(ImGuiCol.SliderGrabActive, 0.80f, 0.29f, 0.09f, 1.00f)
                style.setColor(ImGuiCol.CheckMark, 0.80f, 0.29f, 0.09f, 1.00f)
                
                style.setColor(ImGuiCol.Text, 0.51f, 0.58f, 0.59f, 1.00f)
                style.setColor(ImGuiCol.TextDisabled, 0.35f, 0.43f, 0.46f, 1.00f)
                
                style.setColor(ImGuiCol.Header, 0.03f, 0.21f, 0.26f, 1.00f)
                style.setColor(ImGuiCol.HeaderHovered, 0.35f, 0.43f, 0.46f, 1.00f)
                style.setColor(ImGuiCol.HeaderActive, 0.80f, 0.29f, 0.09f, 1.00f)
            }
            UITheme.Theme.LIGHT_SOLARIZED -> {
                style.setColor(ImGuiCol.WindowBg, 0.99f, 0.96f, 0.89f, alpha)
                style.setColor(ImGuiCol.PopupBg, 0.93f, 0.91f, 0.84f, 1.00f)
                style.setColor(ImGuiCol.TitleBg, 0.93f, 0.91f, 0.84f, alpha)
                style.setColor(ImGuiCol.TitleBgActive, 0.99f, 0.96f, 0.89f, alpha)
                style.setColor(ImGuiCol.MenuBarBg, 0.93f, 0.91f, 0.84f, alpha)
                
                style.setColor(ImGuiCol.FrameBg, 0.93f, 0.91f, 0.84f, 1.00f)
                style.setColor(ImGuiCol.FrameBgHovered, 0.99f, 0.96f, 0.89f, 1.00f)
                style.setColor(ImGuiCol.FrameBgActive, 0.17f, 0.63f, 0.60f, 1.00f)
                
                style.setColor(ImGuiCol.Button, 0.93f, 0.91f, 0.84f, 1.00f)
                style.setColor(ImGuiCol.ButtonHovered, 0.58f, 0.63f, 0.63f, 1.00f)
                style.setColor(ImGuiCol.ButtonActive, 0.83f, 0.21f, 0.51f, 1.00f)
                
                style.setColor(ImGuiCol.SliderGrab, 0.17f, 0.63f, 0.60f, 1.00f)
                style.setColor(ImGuiCol.SliderGrabActive, 0.83f, 0.21f, 0.51f, 1.00f)
                style.setColor(ImGuiCol.CheckMark, 0.52f, 0.60f, 0.00f, 1.00f)
                
                style.setColor(ImGuiCol.Text, 0.40f, 0.48f, 0.51f, 1.00f)
                style.setColor(ImGuiCol.TextDisabled, 0.58f, 0.63f, 0.63f, 1.00f)
                
                style.setColor(ImGuiCol.Header, 0.93f, 0.91f, 0.84f, 1.00f)
                style.setColor(ImGuiCol.HeaderHovered, 0.58f, 0.63f, 0.63f, 1.00f)
                style.setColor(ImGuiCol.HeaderActive, 0.17f, 0.63f, 0.60f, 1.00f)
            }
            UITheme.Theme.DARK_LUNARIZED -> {
                style.setColor(ImGuiCol.WindowBg, 0.21f, 0.04f, 0.00f, alpha)
                style.setColor(ImGuiCol.PopupBg, 0.28f, 0.07f, 0.00f, 1.00f)
                style.setColor(ImGuiCol.TitleBg, 0.28f, 0.07f, 0.00f, alpha)
                style.setColor(ImGuiCol.TitleBgActive, 0.21f, 0.04f, 0.00f, alpha)
                style.setColor(ImGuiCol.MenuBarBg, 0.28f, 0.07f, 0.00f, alpha)
                
                style.setColor(ImGuiCol.FrameBg, 0.28f, 0.07f, 0.00f, 1.00f)
                style.setColor(ImGuiCol.FrameBgHovered, 0.21f, 0.04f, 0.00f, 1.00f)
                style.setColor(ImGuiCol.FrameBgActive, 0.42f, 0.44f, 0.77f, 1.00f)
                
                style.setColor(ImGuiCol.Button, 0.28f, 0.07f, 0.00f, 1.00f)
                style.setColor(ImGuiCol.ButtonHovered, 0.37f, 0.16f, 0.08f, 1.00f)
                style.setColor(ImGuiCol.ButtonActive, 0.42f, 0.44f, 0.77f, 1.00f)
                
                style.setColor(ImGuiCol.SliderGrab, 0.42f, 0.44f, 0.77f, 1.00f)
                style.setColor(ImGuiCol.SliderGrabActive, 0.48f, 0.32f, 0.80f, 1.00f)
                style.setColor(ImGuiCol.CheckMark, 0.42f, 0.44f, 0.77f, 1.00f)
                
                style.setColor(ImGuiCol.Text, 0.97f, 0.91f, 0.88f, 1.00f)
                style.setColor(ImGuiCol.TextDisabled, 0.58f, 0.40f, 0.35f, 1.00f)
                
                style.setColor(ImGuiCol.Header, 0.28f, 0.07f, 0.00f, 1.00f)
                style.setColor(ImGuiCol.HeaderHovered, 0.37f, 0.16f, 0.08f, 1.00f)
                style.setColor(ImGuiCol.HeaderActive, 0.42f, 0.44f, 0.77f, 1.00f)
            }
            UITheme.Theme.LIGHT_LUNARIZED -> {
                style.setColor(ImGuiCol.WindowBg, 0.89f, 0.92f, 0.99f, alpha)
                style.setColor(ImGuiCol.PopupBg, 0.82f, 0.85f, 0.96f, 1.00f)
                style.setColor(ImGuiCol.TitleBg, 0.82f, 0.85f, 0.96f, alpha)
                style.setColor(ImGuiCol.TitleBgActive, 0.89f, 0.92f, 0.99f, alpha)
                style.setColor(ImGuiCol.MenuBarBg, 0.82f, 0.85f, 0.96f, alpha)
                
                style.setColor(ImGuiCol.FrameBg, 0.82f, 0.85f, 0.96f, 1.00f)
                style.setColor(ImGuiCol.FrameBgHovered, 0.89f, 0.92f, 0.99f, 1.00f)
                style.setColor(ImGuiCol.FrameBgActive, 0.11f, 0.37f, 0.89f, 1.00f)
                
                style.setColor(ImGuiCol.Button, 0.82f, 0.85f, 0.96f, 1.00f)
                style.setColor(ImGuiCol.ButtonHovered, 0.69f, 0.75f, 0.92f, 1.00f)
                style.setColor(ImGuiCol.ButtonActive, 0.11f, 0.37f, 0.89f, 1.00f)
                
                style.setColor(ImGuiCol.SliderGrab, 0.11f, 0.37f, 0.89f, 1.00f)
                style.setColor(ImGuiCol.SliderGrabActive, 0.00f, 0.64f, 0.80f, 1.00f)
                style.setColor(ImGuiCol.CheckMark, 0.00f, 0.64f, 0.80f, 1.00f)
                
                style.setColor(ImGuiCol.Text, 0.15f, 0.17f, 0.21f, 1.00f)
                style.setColor(ImGuiCol.TextDisabled, 0.47f, 0.50f, 0.61f, 1.00f)
                
                style.setColor(ImGuiCol.Header, 0.82f, 0.85f, 0.96f, 1.00f)
                style.setColor(ImGuiCol.HeaderHovered, 0.69f, 0.75f, 0.92f, 1.00f)
                style.setColor(ImGuiCol.HeaderActive, 0.11f, 0.37f, 0.89f, 1.00f)
            }
            UITheme.Theme.NEON -> {
                style.setColor(ImGuiCol.WindowBg, 0.00f, 0.00f, 0.00f, 0.00f)
                style.setColor(ImGuiCol.PopupBg, 0.05f, 0.01f, 0.08f, 1.00f)
                style.setColor(ImGuiCol.TitleBg, 0.04f, 0.04f, 0.10f, if (bgVideoEnabled) 0.65f else 0.90f)
                style.setColor(ImGuiCol.TitleBgActive, 0.08f, 0.00f, 0.14f, if (bgVideoEnabled) 0.65f else 0.90f)
                style.setColor(ImGuiCol.MenuBarBg, 0.04f, 0.04f, 0.10f, if (bgVideoEnabled) 0.65f else 0.90f)
                
                style.setColor(ImGuiCol.FrameBg, 0.11f, 0.05f, 0.16f, 1.00f)
                style.setColor(ImGuiCol.FrameBgHovered, 0.18f, 0.07f, 0.28f, 1.00f)
                style.setColor(ImGuiCol.FrameBgActive, 1.00f, 0.00f, 0.50f, 1.00f)
                
                style.setColor(ImGuiCol.Button, 0.13f, 0.02f, 0.20f, 1.00f)
                style.setColor(ImGuiCol.ButtonHovered, 1.00f, 0.00f, 0.50f, 1.00f)
                style.setColor(ImGuiCol.ButtonActive, 1.00f, 1.00f, 0.00f, 1.00f)
                
                style.setColor(ImGuiCol.SliderGrab, 1.00f, 0.00f, 0.50f, 1.00f)
                style.setColor(ImGuiCol.SliderGrabActive, 0.50f, 1.00f, 0.00f, 1.00f)
                style.setColor(ImGuiCol.CheckMark, 0.50f, 1.00f, 0.00f, 1.00f)
                
                style.setColor(ImGuiCol.Text, 1.00f, 1.00f, 1.00f, 1.00f)
                style.setColor(ImGuiCol.TextDisabled, 0.54f, 0.40f, 0.64f, 1.00f)
                
                style.setColor(ImGuiCol.Header, 0.13f, 0.02f, 0.20f, 1.00f)
                style.setColor(ImGuiCol.HeaderHovered, 1.00f, 0.00f, 0.50f, 1.00f)
                style.setColor(ImGuiCol.HeaderActive, 1.00f, 1.00f, 0.00f, 1.00f)
            }
        }

        style.setColor(imgui.flag.ImGuiCol.ModalWindowDimBg, 0f, 0f, 0f, 0.72f)
    }

    private fun updateUiTransparency() {
        val enabled = session.uiTheme.backgroundVideoEnabled
        val theme = session.uiTheme.settings.theme
        if (enabled == lastBgVideoEnabled && theme == lastTheme) return
        lastBgVideoEnabled = enabled
        lastTheme = theme

        setupThemeColors(theme, enabled)
    }

    private fun drawNeonBackgroundIfNeeded(posX: Float, posY: Float, panelW: Float, panelH: Float, displayWidth: Float) {
        if (session.uiTheme.settings.theme != UITheme.Theme.NEON) return
        val dl = ImGui.getWindowDrawList()
        
        fun getNeonBgColor(t: Float): Int {
            val r: Float
            val g: Float = 0.0f
            val b: Float
            if (t < 0.5f) {
                val fraction = t * 2f
                r = 0.01f + (0.85f - 0.01f) * fraction
                b = 0.14f + (0.42f - 0.14f) * fraction
            } else {
                val fraction = (t - 0.5f) * 2f
                r = 0.85f + (0.01f - 0.85f) * fraction
                b = 0.42f + (0.14f - 0.42f) * fraction
            }
            val alpha = if (session.uiTheme.backgroundVideoEnabled) 0.65f else 0.90f
            return ImColor.rgba(r, g, b, alpha)
        }

        val leftCol = getNeonBgColor((posX / displayWidth).coerceIn(0f, 1f)).toLong() and 0xFFFFFFFFL
        val rightCol = getNeonBgColor(((posX + panelW) / displayWidth).coerceIn(0f, 1f)).toLong() and 0xFFFFFFFFL
        
        dl.addRectFilledMultiColor(posX, posY, posX + panelW, posY + panelH, leftCol, rightCol, rightCol, leftCol)
    }

    private val presetState = PresetGridState()

    private val popupManager: PopupManager = PopupManager(
        onTriggerExit = { org.lwjgl.glfw.GLFW.glfwSetWindowShouldClose(windowHandle, true) },
        onSaveDeck = { name, deck, isDeckA -> saveDeckPreset(name, deck, isDeckA) },
        onExecuteDeckAction = { deck, isDeckA, action, targetPreset ->
            when (action) {
                PopupManager.PendingDeckAction.NEW -> {
                    deck.reset()
                    when {
                        deck === currentMixer?.deckA -> {
                            session.presetManager.activePresetA = null
                            session.presetManager.cachedDtoA = null
                        }
                        deck === currentMixer?.deckB -> {
                            session.presetManager.activePresetB = null
                            session.presetManager.cachedDtoB = null
                        }
                        deck === currentMixer?.deckC -> {
                            session.presetManager.activePresetC = null
                            session.presetManager.cachedDtoC = null
                        }
                    }
                }
                PopupManager.PendingDeckAction.LOAD_FILE -> {
                    performLoadDeckPreset(isDeckA)
                }
                PopupManager.PendingDeckAction.LOAD_PRESET -> {
                    if (targetPreset != null) {
                        if (targetPreset == "None") {
                            when {
                                deck === currentMixer?.deckA -> {
                                    session.presetManager.activePresetA = null
                                    session.presetManager.cachedDtoA = null
                                }
                                deck === currentMixer?.deckB -> {
                                    session.presetManager.activePresetB = null
                                    session.presetManager.cachedDtoB = null
                                }
                                deck === currentMixer?.deckC -> {
                                    session.presetManager.activePresetC = null
                                    session.presetManager.cachedDtoC = null
                                }
                            }
                        } else {
                            loadDeckPreset(targetPreset, deck, deck === currentMixer?.deckA)
                        }
                    }
                }
                else -> {}
            }
        }
    )

    private val menuBar = MenuBar(
        popupManager = popupManager,
        presetState = presetState,
        onTriggerExitFlow = { triggerExitFlow() },
        onOpenSettings = { pendingOpenSettings = true },
        onOpenAudioEngineMonitor = { pendingOpenAudioEngineMonitor = true }
    )

    // Phase 2 -- Deck preset browsers (replaces flat ImGui.combo)
    private val deckABrowser = DeckPresetBrowser("A")
    private val deckBBrowser = DeckPresetBrowser("B")

    private val missingItemsPanel = MissingItemsPanel()


    private var lastNextMidiCcHigh = false
    private var lastPrevMidiCcHigh = false

    private var currentMixer: Mixer? = null

    private var lastWindowTitle: String? = null


    init {
        instance = this
        logger.info { "Initializing ImGui..." }
        ImGui.createContext()
        val io = ImGui.getIO()
        io.addConfigFlags(ImGuiConfigFlags.NavEnableKeyboard)

        // Load semantic fonts before the GL3 backend initialises so the atlas
        // is ready for the backend to upload on its first render call.
        session.uiTheme.loadFonts(io)

        // Save the default style right after context initialization so we can revert sizes
        defaultStyle = imgui.ImGuiStyle()

        // Scale style sizes proportionally to the loaded baseSize relative to the baseline of 15f
        scaleStyleFromDefault(session.uiTheme.baseSize)

        // Darken the modal backdrop for a more dramatic VJ-app feel.
        ImGui.getStyle().setColor(
            imgui.flag.ImGuiCol.ModalWindowDimBg,
            0f, 0f, 0f, 0.72f
        )

        imguiGlfw.init(windowHandle, true)
        imguiGl3.init("#version 150")
        // MIDI learn events arrive via MidiEngine.receivedCcEvents (a ConcurrentLinkedQueue)
        // and are processed each frame at the top of render(). No direct callback hook needed.

        logger.info { "UIManager initialized" }
    }

    private val deckControlPanel = DeckControlPanel(
        deckABrowser = deckABrowser,
        deckBBrowser = deckBBrowser,
        onNewDeck = { isDeckA, isDirty ->
            if (isDirty) {
                if (isDeckA) popupManager.pendingDeckActionA = PopupManager.PendingDeckAction.NEW
                else         popupManager.pendingDeckActionB = PopupManager.PendingDeckAction.NEW
            } else {
                if (isDeckA) {
                    currentMixer?.deckA?.reset()
                    session.presetManager.activePresetA = null
                    session.presetManager.cachedDtoA = null
                } else {
                    currentMixer?.deckB?.reset()
                    session.presetManager.activePresetB = null
                    session.presetManager.cachedDtoB = null
                }
            }
        },
        onLoadDeck = { isDeckA, isDirty ->
            if (isDirty) {
                if (isDeckA) popupManager.pendingDeckActionA = PopupManager.PendingDeckAction.LOAD_FILE
                else         popupManager.pendingDeckActionB = PopupManager.PendingDeckAction.LOAD_FILE
            } else {
                performLoadDeckPreset(isDeckA)
            }
        },
        onSaveDeck = { name, deck, isDeckA -> saveDeckPreset(name, deck, isDeckA) },
        onDeleteDeck = { isDeckA ->
            if (isDeckA) {
                currentMixer?.deckA?.reset()
                session.presetManager.activePresetA = null
                session.presetManager.cachedDtoA = null
            } else {
                currentMixer?.deckB?.reset()
                session.presetManager.activePresetB = null
                session.presetManager.cachedDtoB = null
            }
        }
    )

    private val deckUtilityAction = { mode: Int, from: Deck, to: Deck ->
        val mixer = currentMixer
        if (mixer != null) {
            val isDirty = session.presetManager.isDeckDirty(to, mixer)
            if (!isDirty) {
                when (mode) {
                    0 -> session.presetManager.moveDeck(mixer, from, to)
                    1 -> session.presetManager.copyDeck(mixer, from, to)
                    2 -> session.presetManager.swapDecks(mixer, from, to)
                }
            } else {
                when (to) {
                    mixer.deckA -> {
                        popupManager.pendingDeckActionA = when(mode) { 0 -> PopupManager.PendingDeckAction.MOVE; 1 -> PopupManager.PendingDeckAction.COPY; else -> PopupManager.PendingDeckAction.SWAP }
                        popupManager.pendingDeckUtilitySourceA = from
                    }
                    mixer.deckB -> {
                        popupManager.pendingDeckActionB = when(mode) { 0 -> PopupManager.PendingDeckAction.MOVE; 1 -> PopupManager.PendingDeckAction.COPY; else -> PopupManager.PendingDeckAction.SWAP }
                        popupManager.pendingDeckUtilitySourceB = from
                    }
                    mixer.deckC -> {
                        popupManager.pendingDeckActionC = when(mode) { 0 -> PopupManager.PendingDeckAction.MOVE; 1 -> PopupManager.PendingDeckAction.COPY; else -> PopupManager.PendingDeckAction.SWAP }
                        popupManager.pendingDeckUtilitySourceC = from
                    }
                }
            }
        }
    }

    private val monitorSaveDeck = { deck: Deck, isDeckA: Boolean, isSaveAs: Boolean ->
        val activeName = when {
            deck === currentMixer?.deckA -> session.presetManager.activePresetA
            deck === currentMixer?.deckB -> session.presetManager.activePresetB
            deck === currentMixer?.deckC -> session.presetManager.activePresetC
            else -> null
        }
        if (activeName != null && !isSaveAs) {
            saveDeckPreset(activeName, deck, isDeckA)
        } else {
            if (deck === currentMixer?.deckA) deckABrowser.open()
            else if (deck === currentMixer?.deckB) deckBBrowser.open()
        }
    }

    private val monitorEjectDeck = { deck: Deck, isDeckA: Boolean, isDeckC: Boolean ->
        ejectDeck(deck, isDeckA, isDeckC)
    }

    private val mixerMonitorPanel = MixerMonitorPanel(
        presetState = presetState,
        drawDeckControls = { mixer, label, deck, width, height, isDeckA ->
            deckControlPanel.drawDeckControls(session, mixer, label, deck, width, height, isDeckA, deckUtilityAction, monitorSaveDeck, monitorEjectDeck)
        },
        onUtilityAction = deckUtilityAction,
        onSaveDeck = monitorSaveDeck,
        onEjectDeck = monitorEjectDeck
    )

    fun render(mixer: Mixer, displayWidth: Float, displayHeight: Float) {
        currentMixer = mixer

        // Update window title dynamically with project name and dirty status
        val title = "Liquid LSD"
        if (title != lastWindowTitle) {
            org.lwjgl.glfw.GLFW.glfwSetWindowTitle(windowHandle, title)
            lastWindowTitle = title
        }

        // Drain all MIDI events queued by the MIDI receiver thread.
        var midiCcDelta = 0
        while (true) {
            val event = llm.slop.liquidlsd.midi.MidiEngine.receivedCcEvents.poll() ?: break
            val (channel, cc) = event
            val target = presetState.midiLearnTarget
            if (target != null) {
                val midiId = "midi_cc_${channel}_${cc}"
                when (target) {
                    is MidiLearnTarget.BaseValueSlider -> {
                        session.midiMappingManager.addMapping(target.paramKey, cc, channel, target.min, target.max)
                        session.midiMappingManager.saveActiveProfile()
                    }
                    is MidiLearnTarget.GridCell -> {
                        val existingMods = target.param.modulators.filter { it.sourceId.startsWith("midi_cc_") }
                        target.param.modulators.removeAll(existingMods)
                        val exists = target.param.modulators.any { it.sourceId == midiId }
                        if (!exists) {
                            target.param.modulators.add(
                                llm.slop.liquidlsd.parameters.CvModulator(
                                    sourceId = midiId,
                                    depth = 1.0f,
                                    operator = llm.slop.liquidlsd.parameters.ModulationOperator.ADD
                                )
                            )
                        }
                    }
                }
                presetState.midiLearnTarget = null
            } else {
                val nextCc = session.midiMappingManager.getCcForSpecial("Global/queueNext")
                val nextCh = session.midiMappingManager.getChannelForSpecial("Global/queueNext")
                if (nextCc != -1 && cc == nextCc && channel == nextCh) {
                    val valNow = llm.slop.liquidlsd.midi.MidiEngine.getCcValue(channel, cc)
                    val isHigh = valNow > 0.5f
                    if (isHigh && !lastNextMidiCcHigh) {
                        midiCcDelta += 1
                    }
                    lastNextMidiCcHigh = isHigh
                }
                val prevCc = session.midiMappingManager.getCcForSpecial("Global/queuePrev")
                val prevCh = session.midiMappingManager.getChannelForSpecial("Global/queuePrev")
                if (prevCc != -1 && cc == prevCc && channel == prevCh) {
                    val valNow = llm.slop.liquidlsd.midi.MidiEngine.getCcValue(channel, cc)
                    val isHigh = valNow > 0.5f
                    if (isHigh && !lastPrevMidiCcHigh) {
                        midiCcDelta -= 1
                    }
                    lastPrevMidiCcHigh = isHigh
                }
            }
        }

        val cvDelta = if (session.playQueueManager.isAutoVJEnabled) mixer.pollQueueAdvance() else { mixer.pollQueueAdvance(); 0 }
        var keyDelta = 0
        if (!ImGui.getIO().wantTextInput) {
            when (session.uiTheme.queueKeyTrigger) {
                UITheme.QueueKeyTrigger.ARROWS -> {
                    if (ImGui.isKeyPressed(ImGui.getKeyIndex(imgui.flag.ImGuiKey.LeftArrow))) keyDelta -= 1
                    if (ImGui.isKeyPressed(ImGui.getKeyIndex(imgui.flag.ImGuiKey.RightArrow))) keyDelta += 1
                }
                UITheme.QueueKeyTrigger.PAGE_UP_DOWN -> {
                    if (ImGui.isKeyPressed(ImGui.getKeyIndex(imgui.flag.ImGuiKey.PageUp))) keyDelta -= 1
                    if (ImGui.isKeyPressed(ImGui.getKeyIndex(imgui.flag.ImGuiKey.PageDown))) keyDelta += 1
                }
                UITheme.QueueKeyTrigger.SPACE_BACKSPACE -> {
                    if (ImGui.isKeyPressed(ImGui.getKeyIndex(imgui.flag.ImGuiKey.Backspace))) keyDelta -= 1
                    if (ImGui.isKeyPressed(ImGui.getKeyIndex(imgui.flag.ImGuiKey.Space))) keyDelta += 1
                }
                else -> {}
            }
        }
        val totalDelta = midiCcDelta + cvDelta + keyDelta
        if (totalDelta != 0) {
            if (totalDelta > 0) {
                session.playQueueManager.triggerNext(mixer)
            } else {
                session.playQueueManager.triggerPrevious(mixer)
            }
        }

        pendingFontSize?.let { newSize ->
            pendingFontSize = null
            session.uiTheme.baseSize = newSize
            session.uiTheme.rebuildFonts(ImGui.getIO())
            imguiGl3.updateFontsTexture()
            scaleStyleFromDefault(newSize)
            session.uiTheme.saveSettings()
            logger.info { "Font size applied: ${newSize}px" }
        }

        imguiGlfw.newFrame()
        ImGui.newFrame()
        updateUiTransparency()

        if (!session.uiTheme.cleanModeEnabled) {
            menuBar.draw(session, mixer)
            if (pendingOpenSettings) {
                SettingsPanel.open()
                pendingOpenSettings = false
            }
            if (pendingOpenAudioEngineMonitor) {
                AudioEnginePanel.open()
                pendingOpenAudioEngineMonitor = false
            }

            if (popupManager.pendingOpenExitPopup) {
                ImGui.openPopup("Exit Liquid LSD?##confirm")
                popupManager.pendingOpenExitPopup = false
            }
            if (popupManager.pendingOpenMidiWarningPopup) {
                ImGui.openPopup("No MIDI Devices Connected##midi_warning")
                popupManager.pendingOpenMidiWarningPopup = false
            }

            drawLayout(mixer, displayWidth, displayHeight)

            SettingsPanel.draw(session, session.uiTheme.baseSize, displayWidth, displayHeight) { newSize ->
                applyFontSize(newSize)
            }

            AudioEnginePanel.draw(session, displayWidth, displayHeight)

            popupManager.drawExitPopup(mixer, displayWidth, displayHeight)
            popupManager.drawDeckConfirmPopups(session, mixer)
            popupManager.drawMidiWarningPopup(displayWidth, displayHeight)

            // Note editor modal (opened by NoteEditorModal.request() from anywhere in the UI)
            NoteEditorModal.draw()

            missingItemsPanel.draw(session)


            deckABrowser.draw(session,
                activePresetName = session.presetManager.activePresetA,
                isDirty          = session.presetManager.isDeckDirty(mixer.deckA, mixer),
                onSelect         = { name ->
                    if (name == null) {
                        session.presetManager.activePresetA = null
                        session.presetManager.cachedDtoA = null
                    } else {
                        loadDeckPreset(name, mixer.deckA, true)
                    }
                },
                onSaveAs = { name, tags -> saveDeckPreset(name, mixer.deckA, true, tags) }
            )
            deckBBrowser.draw(session,
                activePresetName = session.presetManager.activePresetB,
                isDirty          = session.presetManager.isDeckDirty(mixer.deckB, mixer),
                onSelect         = { name ->
                    if (name == null) {
                        session.presetManager.activePresetB = null
                        session.presetManager.cachedDtoB = null
                    } else {
                        loadDeckPreset(name, mixer.deckB, false)
                    }
                },
                onSaveAs = { name, tags -> saveDeckPreset(name, mixer.deckB, false, tags) }
            )

            deckAFileBrowser.draw { file ->
                session.presetManager.loadDeckPresetAsync(file, true)
            }
            deckBFileBrowser.draw { file ->
                session.presetManager.loadDeckPresetAsync(file, false)
            }
        }

        ImGui.render()
        imguiGl3.renderDrawData(ImGui.getDrawData())
    }

    /**
     * Rebuilds the font atlas at [newSize] and scales widget style proportionally.
     * Scale is computed relative to the baseline of 15f from a clean default style.
     */
    private fun applyFontSize(newSize: Float) {
        if (newSize != session.uiTheme.baseSize) pendingFontSize = newSize
    }

    fun adjustFontSize(delta: Float) {
        val currentSize = session.uiTheme.baseSize
        val targetSize = currentSize + delta
        val constrainedSize = targetSize.coerceIn(10f, 36f)
        applyFontSize(constrainedSize)
    }

    fun triggerExitFlow() {
        session.uiTheme.cleanModeEnabled = false
        popupManager.pendingOpenExitPopup = true
    }

    companion object {
        private var instance: UIManager? = null

        fun triggerDeckDragDrop(file: File, deck: Deck, isDeckA: Boolean, mixer: Mixer) {
            val ui = instance ?: return
            val isDeckC = deck === mixer.deckC
            if (isDeckC) {
                ui.popupManager.pendingDeckActionC = PopupManager.PendingDeckAction.DRAG_DROP
                ui.popupManager.pendingDeckSourceFileC = file
            } else if (isDeckA) {
                ui.popupManager.pendingDeckActionA = PopupManager.PendingDeckAction.DRAG_DROP
                ui.popupManager.pendingDeckSourceFileA = file
            } else {
                ui.popupManager.pendingDeckActionB = PopupManager.PendingDeckAction.DRAG_DROP
                ui.popupManager.pendingDeckSourceFileB = file
            }
        }
    }



    /**
     * Phase 2: deck preset "Load File..." now opens the ImGui file browser
     * pointed at `library/presets/` instead of `java.awt.FileDialog`.
     *
     * The browser is shared with the global project browser but uses a
     * separate instance per deck so both decks can have independent state.
     */
    private val deckAFileBrowser = ImGuiFileBrowser("deckAFileBrowser")
    private val deckBFileBrowser = ImGuiFileBrowser("deckBFileBrowser")

    private fun performLoadDeckPreset(isDeckA: Boolean) {
        val browser = if (isDeckA) deckAFileBrowser else deckBFileBrowser
        val dir = File("library/presets")
        browser.open(
            ImGuiFileBrowser.Mode.LOAD,
            startDir = dir.canonicalFile
        )
    }

    private fun loadDeckPreset(presetName: String, deck: Deck, isDeckA: Boolean, isDeckC: Boolean = (deck === currentMixer?.deckC)) {
        if (presetName == "None") return
        val cleanName = presetName.removeSuffix(".lsd").trim()
        val file = File("library/presets/$cleanName.lsd")
        if (file.exists()) {
            session.presetManager.loadDeckPresetAsync(file, isDeckA, isDeckC)
        }
    }

    private fun ejectDeck(deck: Deck, isDeckA: Boolean, isDeckC: Boolean = false) {
        val mixer = currentMixer ?: return
        val isDirty = session.presetManager.isDeckDirty(deck, mixer)
        if (!isDirty) {
            performEjectDeck(deck)
        } else {
            when (session.uiTheme.autoVjDirtyBehavior) {
                UITheme.AutoVjDirtyBehavior.AUTO_SAVE -> {
                    val activeName = when {
                        deck === mixer.deckC -> session.presetManager.activePresetC
                        deck === mixer.deckA -> session.presetManager.activePresetA
                        else -> session.presetManager.activePresetB
                    }
                    if (activeName != null && activeName != "None") {
                        saveDeckPreset(activeName, deck, isDeckA)
                    }
                    performEjectDeck(deck)
                }
                UITheme.AutoVjDirtyBehavior.AUTO_DISCARD -> {
                    performEjectDeck(deck)
                }
                UITheme.AutoVjDirtyBehavior.SKIP -> {
                    if (deck === mixer.deckC) {
                        popupManager.pendingDeckActionC = PopupManager.PendingDeckAction.NEW
                    } else if (deck === mixer.deckA) {
                        popupManager.pendingDeckActionA = PopupManager.PendingDeckAction.NEW
                    } else {
                        popupManager.pendingDeckActionB = PopupManager.PendingDeckAction.NEW
                    }
                }
            }
        }
    }

    private fun performEjectDeck(deck: Deck) {
        val mixer = currentMixer ?: return
        deck.reset()
        when {
            deck === mixer.deckA -> {
                session.presetManager.activePresetA = null
                session.presetManager.cachedDtoA = null
            }
            deck === mixer.deckB -> {
                session.presetManager.activePresetB = null
                session.presetManager.cachedDtoB = null
            }
            deck === mixer.deckC -> {
                session.presetManager.activePresetC = null
                session.presetManager.cachedDtoC = null
            }
        }
    }

    /**
     * Save a deck preset. [tags] are stored in `DeckPresetDto.tags` (Phase 2c).
     * Existing callers that don't supply tags preserve the current tag list by
     * reading it from the cached DTO, so an overwrite never silently strips tags.
     */
    private fun saveDeckPreset(name: String, deck: Deck, isDeckA: Boolean, tags: List<String>? = null) {
        val cleanName = name.removeSuffix(".lsd").trim()
        if (cleanName.isBlank()) return

        // Restore existing tags when overwriting unless the caller explicitly supplies new ones
        val resolvedTags = tags ?: run {
            val cached = when {
                deck === currentMixer?.deckA -> session.presetManager.cachedDtoA
                deck === currentMixer?.deckB -> session.presetManager.cachedDtoB
                deck === currentMixer?.deckC -> session.presetManager.cachedDtoC
                else -> null
            }
            cached?.tags ?: emptyList()
        }

        val dto = deck.toDto(cleanName, resolvedTags)
        when {
            deck === currentMixer?.deckA -> {
                session.presetManager.activePresetA = cleanName
                session.presetManager.cachedDtoA = dto
            }
            deck === currentMixer?.deckB -> {
                session.presetManager.activePresetB = cleanName
                session.presetManager.cachedDtoB = dto
            }
            deck === currentMixer?.deckC -> {
                session.presetManager.activePresetC = cleanName
                session.presetManager.cachedDtoC = dto
            }
        }
        val file = File("library/presets/$cleanName.lsd")

        val deckIndex = when {
            deck === currentMixer?.deckA -> 0
            deck === currentMixer?.deckB -> 1
            deck === currentMixer?.deckC -> 2
            else -> -1
        }
        session.presetManager.saveDeckPresetAsync(file, deck, cleanName, resolvedTags, deckIndex)
    }

    private fun drawLayout(mixer: Mixer, displayWidth: Float, displayHeight: Float) {
        val menuBarH = session.uiTheme.withFont(UITheme.FontLevel.BODY) { ImGui.getFrameHeight() }.coerceAtLeast(32f)
        val contentH = displayHeight - menuBarH
        val noDecorate = ImGuiWindowFlags.NoResize or
                         ImGuiWindowFlags.NoMove or
                         ImGuiWindowFlags.NoCollapse or
                         ImGuiWindowFlags.NoBringToFrontOnFocus

        drawAssetManagementLayout(displayWidth, displayHeight, menuBarH, contentH, noDecorate)
    }

    private fun drawAssetManagementLayout(displayWidth: Float, displayHeight: Float, menuBarH: Float, contentH: Float, noDecorate: Int) {
        val theme = session.uiTheme
        val minRatio = 0.15f

        val sliderWasHovered = CustomRangeSlider.isAnySliderHovered
        CustomRangeSlider.isAnySliderHovered = false

        // Auto-calculate Column 1 (Left Panel / Preset Grid) width based on active columns & font scale
        val reqCol1W = currentMixer?.let { PresetGridPanel.calculateRequiredWidth(session, it, presetState) } ?: (displayWidth * 0.30f)
        val maxCol1W = (displayWidth * 0.50f).coerceAtMost((displayWidth * (1.0f - minRatio)) - 250f)
        val col1W = reqCol1W.coerceIn(displayWidth * minRatio, maxCol1W)
        val col1R = col1W / displayWidth

        // Column 2 (Middle Panel / Cell Config) and Column 3 (Right Panel / Mixer Monitor)
        var col2R = theme.col2Ratio.coerceIn(minRatio, (1.0f - minRatio - col1R).coerceAtLeast(minRatio))
        val col2W = displayWidth * col2R
        val libraryW = col1W + col2W
        val rightW = displayWidth - libraryW

        val assetBrowserH = when (theme.assetBrowserMode) {
            UITheme.AssetBrowserMode.FULL -> contentH
            UITheme.AssetBrowserMode.HIDE -> 38f
            UITheme.AssetBrowserMode.HALF -> contentH * theme.assetBrowserRatio.coerceIn(minRatio, 0.85f)
        }

        if (theme.assetBrowserMode != UITheme.AssetBrowserMode.FULL) {
            val topH = contentH - assetBrowserH

            // Column 1: Preset Grid
            ImGui.setNextWindowPos(0f, menuBarH)
            ImGui.setNextWindowSize(col1W, topH)
            if (ImGui.begin("Preset Grid", noDecorate or ImGuiWindowFlags.NoScrollbar)) {
                drawNeonBackgroundIfNeeded(ImGui.getWindowPosX(), ImGui.getWindowPosY(), ImGui.getWindowWidth(), ImGui.getWindowHeight(), displayWidth)
                PresetGridPanel.draw(session, currentMixer!!, presetState)
            }
            ImGui.end()

            // Column 2: Cell Config
            ImGui.setNextWindowPos(col1W, menuBarH)
            ImGui.setNextWindowSize(col2W, topH)
            val cellConfigFlags = if (sliderWasHovered) {
                noDecorate or ImGuiWindowFlags.NoScrollWithMouse
            } else {
                noDecorate
            }
            if (ImGui.begin("Cell Config", cellConfigFlags)) {
                drawNeonBackgroundIfNeeded(ImGui.getWindowPosX(), ImGui.getWindowPosY(), ImGui.getWindowWidth(), ImGui.getWindowHeight(), displayWidth)
                CellConfigPanel.draw(session, presetState, currentMixer!!)
            }
            ImGui.end()

            // Vertical Splitter 1 (Static divider line between Preset Grid & Cell Config)
            val drawList = ImGui.getForegroundDrawList()
            val dividerColor = ImGui.getColorU32(imgui.flag.ImGuiCol.Separator)
            drawList.addLine(col1W, menuBarH, col1W, menuBarH + topH, dividerColor, 1.5f)
        }

        // Horizontal Splitter (above Asset Browser when not FULL)
        val assetBrowserPosH = if (theme.assetBrowserMode == UITheme.AssetBrowserMode.FULL) menuBarH else (menuBarH + contentH - assetBrowserH)
        if (theme.assetBrowserMode != UITheme.AssetBrowserMode.FULL) {
            drawHorizontalSplitter(
                id = "##hsplit",
                posX = 0f,
                posY = assetBrowserPosH,
                width = libraryW,
                height = 8f,
                displayHeight = displayHeight,
                onDrag = { deltaY ->
                    if (theme.assetBrowserMode == UITheme.AssetBrowserMode.HIDE) {
                        if (deltaY < 0f) { // Dragging upward
                            theme.assetBrowserMode = UITheme.AssetBrowserMode.HALF
                            theme.assetBrowserRatio = theme.lastCustomAssetBrowserRatio.coerceIn(minRatio, 0.85f)
                            theme.saveSettings()
                        }
                    } else {
                        val deltaR = -deltaY / contentH
                        val targetRatio = theme.assetBrowserRatio + deltaR
                        val targetPixelH = contentH * targetRatio
                        if (targetPixelH < 60f || targetRatio < 0.10f) {
                            theme.lastCustomAssetBrowserRatio = theme.assetBrowserRatio
                            theme.assetBrowserMode = UITheme.AssetBrowserMode.HIDE
                        } else {
                            val newR = targetRatio.coerceIn(minRatio, 0.85f)
                            theme.assetBrowserRatio = newR
                            theme.lastCustomAssetBrowserRatio = newR
                        }
                        theme.saveSettings()
                    }
                },
                onDoubleClick = {
                    theme.assetBrowserMode = UITheme.AssetBrowserMode.HALF
                    theme.assetBrowserRatio = 0.50f
                    theme.lastCustomAssetBrowserRatio = 0.50f
                    theme.saveSettings()
                }
            )
        }

        // Asset Browser (Bottom or Full)
        ImGui.setNextWindowPos(0f, assetBrowserPosH)
        ImGui.setNextWindowSize(libraryW, assetBrowserH)
        val flags = (if (theme.assetBrowserMode == UITheme.AssetBrowserMode.HIDE) noDecorate or ImGuiWindowFlags.NoScrollbar else noDecorate) or
                ImGuiWindowFlags.NoTitleBar or ImGuiWindowFlags.MenuBar
        if (ImGui.begin("Asset Browser", flags)) {
            drawNeonBackgroundIfNeeded(ImGui.getWindowPosX(), ImGui.getWindowPosY(), ImGui.getWindowWidth(), ImGui.getWindowHeight(), displayWidth)
            AssetBrowserPanel.draw(session, libraryW, assetBrowserH, currentMixer!!)
        }
        ImGui.end()

        // Column 3: Mixer / Monitor
        ImGui.setNextWindowPos(libraryW, menuBarH)
        ImGui.setNextWindowSize(rightW, contentH)
        val noTitleDecorate = noDecorate or ImGuiWindowFlags.NoTitleBar
        if (ImGui.begin("Mixer / Monitor", noTitleDecorate)) {
            drawNeonBackgroundIfNeeded(ImGui.getWindowPosX(), ImGui.getWindowPosY(), ImGui.getWindowWidth(), ImGui.getWindowHeight(), displayWidth)
            drawMixerMonitor(currentMixer!!)
        }
        ImGui.end()

        // Vertical Splitter 2 (between Cell Config/Asset Browser and Mixer/Monitor)
        drawVerticalSplitter(
            id = "##vsplit2",
            posX = libraryW,
            posY = menuBarH,
            width = 8f,
            height = contentH,
            displayWidth = displayWidth,
            onDrag = { deltaX ->
                val deltaR = deltaX / displayWidth
                val maxC2 = (1.0f - minRatio - col1R).coerceAtLeast(minRatio)
                val newC2 = (theme.col2Ratio + deltaR).coerceIn(minRatio, maxC2)
                theme.col2Ratio = newC2
                theme.saveSettings()
            },
            onDoubleClick = {
                theme.col2Ratio = 0.40f
                theme.saveSettings()
            }
        )
    }

    private fun drawVerticalSplitter(
        id: String,
        posX: Float,
        posY: Float,
        width: Float,
        height: Float,
        displayWidth: Float,
        onDrag: (Float) -> Unit,
        onDoubleClick: () -> Unit
    ) {
        val io = ImGui.getIO()
        val mouseX = io.mousePosX
        val mouseY = io.mousePosY
        val halfW = width / 2f
        val inBounds = mouseX >= (posX - halfW) && mouseX <= (posX + halfW) && mouseY >= posY && mouseY <= (posY + height)

        val isActive = activeSplitterId == id
        val isHovered = isActive || (activeSplitterId == null && inBounds)

        if (isActive) {
            ImGui.setMouseCursor(imgui.flag.ImGuiMouseCursor.ResizeEW)
            val deltaX = io.mouseDeltaX
            if (deltaX != 0f) {
                onDrag(deltaX)
            }
            if (!ImGui.isMouseDown(0)) {
                activeSplitterId = null
            }
        } else if (activeSplitterId == null && inBounds) {
            ImGui.setMouseCursor(imgui.flag.ImGuiMouseCursor.ResizeEW)
            if (ImGui.isMouseDoubleClicked(0)) {
                onDoubleClick()
            } else if (ImGui.isMouseClicked(0)) {
                activeSplitterId = id
            }
        }

        val color = when {
            isActive -> ImGui.getColorU32(ImGuiCol.SeparatorActive)
            isHovered -> ImGui.getColorU32(ImGuiCol.SeparatorHovered)
            else -> ImGui.getColorU32(ImGuiCol.Separator)
        }
        val drawList = ImGui.getForegroundDrawList()
        drawList.addLine(posX, posY, posX, posY + height, color, if (isActive || isHovered) 2.5f else 1.5f)
    }

    private fun drawHorizontalSplitter(
        id: String,
        posX: Float,
        posY: Float,
        width: Float,
        height: Float,
        displayHeight: Float,
        onDrag: (Float) -> Unit,
        onDoubleClick: () -> Unit
    ) {
        val io = ImGui.getIO()
        val mouseX = io.mousePosX
        val mouseY = io.mousePosY
        val halfH = height / 2f
        val inBounds = mouseX >= posX && mouseX <= (posX + width) && mouseY >= (posY - halfH) && mouseY <= (posY + halfH)

        val isActive = activeSplitterId == id
        val isHovered = isActive || (activeSplitterId == null && inBounds)

        if (isActive) {
            ImGui.setMouseCursor(imgui.flag.ImGuiMouseCursor.ResizeNS)
            val deltaY = io.mouseDeltaY
            if (deltaY != 0f) {
                onDrag(deltaY)
            }
            if (!ImGui.isMouseDown(0)) {
                activeSplitterId = null
            }
        } else if (activeSplitterId == null && inBounds) {
            ImGui.setMouseCursor(imgui.flag.ImGuiMouseCursor.ResizeNS)
            if (ImGui.isMouseDoubleClicked(0)) {
                onDoubleClick()
            } else if (ImGui.isMouseClicked(0)) {
                activeSplitterId = id
            }
        }

        val color = when {
            isActive -> ImGui.getColorU32(ImGuiCol.SeparatorActive)
            isHovered -> ImGui.getColorU32(ImGuiCol.SeparatorHovered)
            else -> ImGui.getColorU32(ImGuiCol.Separator)
        }
        val drawList = ImGui.getForegroundDrawList()
        drawList.addLine(posX, posY, posX + width, posY, color, if (isActive || isHovered) 2.5f else 1.5f)
    }

    private fun drawMixerMonitor(mixer: Mixer) {
        mixerMonitorPanel.draw(session, mixer)
    }

    private fun copyStyleSizes(from: imgui.ImGuiStyle, to: imgui.ImGuiStyle) {
        to.setAlpha(from.getAlpha())
        to.setDisabledAlpha(from.getDisabledAlpha())
        to.setWindowPadding(from.getWindowPaddingX(), from.getWindowPaddingY())
        to.setWindowRounding(from.getWindowRounding())
        to.setWindowBorderSize(from.getWindowBorderSize())
        to.setWindowMinSize(from.getWindowMinSizeX(), from.getWindowMinSizeY())
        to.setWindowTitleAlign(from.getWindowTitleAlignX(), from.getWindowTitleAlignY())
        to.setWindowMenuButtonPosition(from.getWindowMenuButtonPosition())
        to.setChildRounding(from.getChildRounding())
        to.setChildBorderSize(from.getChildBorderSize())
        to.setPopupRounding(from.getPopupRounding())
        to.setPopupBorderSize(from.getPopupBorderSize())
        to.setFramePadding(from.getFramePaddingX(), from.getFramePaddingY())
        to.setFrameRounding(from.getFrameRounding())
        to.setFrameBorderSize(from.getFrameBorderSize())
        to.setItemSpacing(from.getItemSpacingX(), from.getItemSpacingY())
        to.setItemInnerSpacing(from.getItemInnerSpacingX(), from.getItemInnerSpacingY())
        to.setCellPadding(from.getCellPaddingX(), from.getCellPaddingY())
        to.setTouchExtraPadding(from.getTouchExtraPaddingX(), from.getTouchExtraPaddingY())
        to.setIndentSpacing(from.getIndentSpacing())
        to.setColumnsMinSpacing(from.getColumnsMinSpacing())
        to.setScrollbarSize(from.getScrollbarSize())
        to.setScrollbarRounding(from.getScrollbarRounding())
        to.setGrabMinSize(from.getGrabMinSize())
        to.setGrabRounding(from.getGrabRounding())
        to.setLogSliderDeadzone(from.getLogSliderDeadzone())
        to.setTabRounding(from.getTabRounding())
        to.setTabBorderSize(from.getTabBorderSize())
        to.setTabMinWidthForCloseButton(from.getTabMinWidthForCloseButton())
        to.setColorButtonPosition(from.getColorButtonPosition())
        to.setButtonTextAlign(from.getButtonTextAlignX(), from.getButtonTextAlignY())
        to.setSelectableTextAlign(from.getSelectableTextAlignX(), from.getSelectableTextAlignY())
        to.setDisplayWindowPadding(from.getDisplayWindowPaddingX(), from.getDisplayWindowPaddingY())
        to.setDisplaySafeAreaPadding(from.getDisplaySafeAreaPaddingX(), from.getDisplaySafeAreaPaddingY())
        to.setMouseCursorScale(from.getMouseCursorScale())
    }

    private fun scaleStyleFromDefault(newSize: Float) {
        val style = ImGui.getStyle()
        copyStyleSizes(defaultStyle, style)
        val scale = newSize / 15f
        if (scale != 1f) {
            style.scaleAllSizes(scale)
        }
        // Safety guard: ensure critical sizes never underflow to or below 0.0f
        if (style.scrollbarSize <= 0.0f) {
            style.scrollbarSize = 1.0f
        }
        if (style.grabMinSize <= 0.0f) {
            style.grabMinSize = 1.0f
        }
    }

    fun dispose() {
        defaultStyle.destroy()
        imguiGl3.dispose()
        imguiGlfw.dispose()
        ImGui.destroyContext()
    }
}
