package llm.slop.liquidlsd.ui

import imgui.ImGui
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
import llm.slop.liquidlsd.rendering.Renderer
import llm.slop.liquidlsd.presets.PresetManager
import kotlin.math.roundToInt
import mu.KotlinLogging
import org.lwjgl.opengl.GL33.*
import imgui.gl3.ImGuiImplGl3
import imgui.glfw.ImGuiImplGlfw
import llm.slop.liquidlsd.parameters.ModulatableParameter
import llm.slop.liquidlsd.presets.PlayQueueManager

/**
 * Manages the ImGui overlay for desktop control.
 */
class UIManager(
    private val windowHandle: Long,
    val session: llm.slop.liquidlsd.SessionContext,
    private val onToggleOutputWindow: () -> Unit = {},
    private val isOutputWindowOpen: () -> Boolean = { false },
    var isUiLabMode: Boolean = false
) {
    private val logger = KotlinLogging.logger {}
    private val uiLabPanel = UiLabPanel()
    private val imguiGlfw = ImGuiImplGlfw()
    private val imguiGl3 = ImGuiImplGl3()

    // Chained GLFW mouse button callback and single-frame latch to prevent fast clicks
    // (e.g. ThinkPad trackpoint middle hardware button tap) from being missed.
    private val pendingMousePress = BooleanArray(5)
    private var prevMouseButtonCallback: org.lwjgl.glfw.GLFWMouseButtonCallback? = null

    // Clean default style to reset size attributes before scaling
    private var defaultStyle: imgui.ImGuiStyle

    // Font rebuild must happen between frames (atlas is locked during a frame).
    // Store rebuild request flag here; it is consumed at the top of the next render().
    private var pendingFontRebuild = false

    // Set to true for one frame when the Preferences menu item is clicked; consumed
    // immediately after endMainMenuBar so openPopup runs at root ID-stack level.
    private var pendingOpenPreferences = false
    private var pendingOpenPreferencesCategory: PreferencesPanel.Category? = null

    fun openPreferences(category: PreferencesPanel.Category? = null) {
        pendingOpenPreferences = true
        pendingOpenPreferencesCategory = category
    }

    private val splitterManager = SplitterManager()

    private val parametersState = ParametersState()

    private val popupManager: PopupManager = PopupManager(
        onTriggerExit = { org.lwjgl.glfw.GLFW.glfwSetWindowShouldClose(windowHandle, true) },
        onSaveDeck = { name, deck, isDeckA -> currentMixer?.let { deckPresetController.saveDeckPreset(it, name, deck, isDeckA) } }
    )

    val deckPresetController = DeckPresetController(session, popupManager)

    val windowFrameController = WindowFrameController(windowHandle)

    private val menuBar = MenuBar(
        popupManager = popupManager,
        parametersState = parametersState,
        onTriggerExitFlow = { triggerExitFlow() },
        onOpenPreferences = {
            openPreferences(null)
        },
        onOpenAudioEngineMonitor = {
            openPreferences(PreferencesPanel.Category.AUDIO_ENGINE)
        },
        onToggleOutputWindow = onToggleOutputWindow,
        isOutputWindowOpen = isOutputWindowOpen,
        windowFrameController = windowFrameController,
        onFlipRack = { rackPanel.isRearView = !rackPanel.isRearView }
    )

    private val missingItemsPanel = MissingItemsPanel()

    init {
        if (session.uiTheme.checkUpdatesOnStartup && !llm.slop.liquidlsd.update.UpdateChecker.hasCheckedOnStartup) {
            llm.slop.liquidlsd.update.UpdateChecker.hasCheckedOnStartup = true
            llm.slop.liquidlsd.update.UpdateChecker.checkForUpdatesAsync(isManualCheck = false) { result ->
                if (result is llm.slop.liquidlsd.update.UpdateCheckResult.UpdateAvailable) {
                    if (result.latestRelease.tagName != session.uiTheme.ignoredUpdateVersion) {
                        UpdatePromptModal.request(result.latestRelease, result.currentVersion)
                    }
                }
            }
        }
    }

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
        defaultStyle = imgui.ImGuiStyle().apply {
            setFrameBorderSize(1.0f)
            setFrameRounding(3.0f)
            setPopupBorderSize(1.0f)
            setPopupRounding(4.0f)
            setScrollbarSize(10.0f)
            setScrollbarRounding(5.0f)
            setSeparatorSize(1.0f)
            setSeparatorTextBorderSize(1.0f)
        }

        // Scale style sizes proportionally to the loaded baseSize relative to the baseline of 15f
        UIThemeStyler.scaleStyleFromDefault(defaultStyle, session.uiTheme.baseSize)

        // Darken the modal backdrop for a more dramatic VJ-app feel.
        ImGui.getStyle().setColor(
            imgui.flag.ImGuiCol.ModalWindowDimBg,
            0f, 0f, 0f, 0.72f
        )

        imguiGlfw.init(windowHandle, true)
        imguiGl3.init("#version 150")

        // Intercept GLFW mouse button events so short click-and-release taps
        // (such as TrackPoint middle hardware buttons on Linux / libinput)
        // are never swallowed if a release event arrives before the next ImGui frame.
        prevMouseButtonCallback = org.lwjgl.glfw.GLFW.glfwSetMouseButtonCallback(windowHandle) { win, button, action, mods ->
            if (button in 0..4 && action == org.lwjgl.glfw.GLFW.GLFW_PRESS) {
                pendingMousePress[button] = true
            }
            prevMouseButtonCallback?.invoke(win, button, action, mods)
        }

        // MIDI learn events arrive via MidiEngine.receivedCcEvents (a ConcurrentLinkedQueue)
        // and are processed each frame at the top of render(). No direct callback hook needed.

        logger.info { "UIManager initialized" }
    }

    private val deckControlPanel = DeckControlPanel(parametersState)

    private val deckUtilityAction = { mode: Int, from: Deck, to: Deck ->
        val mixer = currentMixer
        if (mixer != null) {
            deckPresetController.handleUtilityAction(mixer, mode, from, to)
        }
    }

    private val monitorSaveDeck = { deck: Deck, isDeckA: Boolean, isSaveAs: Boolean ->
        val mixer = currentMixer
        if (mixer != null) {
            deckPresetController.handleSaveDeck(mixer, deck, isDeckA, isSaveAs)
        }
    }

    private val monitorEjectDeck = { deck: Deck, isDeckA: Boolean, isDeckPV: Boolean ->
        val mixer = currentMixer
        if (mixer != null) {
            deckPresetController.handleEjectDeck(mixer, deck, isDeckA, isDeckPV)
        }
    }

    private val mixerPanel = MixerPanel(
        parametersState = parametersState,
        drawDeckControls = { mixer, label, deck, width, height, isDeckA ->
            deckControlPanel.drawDeckControls(session, mixer, label, deck, width, height, isDeckA, deckUtilityAction, monitorSaveDeck, monitorEjectDeck)
        },
        onUtilityAction = deckUtilityAction,
        onSaveDeck = monitorSaveDeck,
        onEjectDeck = monitorEjectDeck
    )

    private val macroPanel = MacroPanel(parametersState = parametersState)
    val rackPanel = llm.slop.liquidlsd.rack.ui.RackPanel()

    fun render(mixer: Mixer, renderer: Renderer, displayWidth: Float, displayHeight: Float) {
        currentMixer = mixer

        // Update window title dynamically
        val title = "Liquid LSD - Libre Shader Decks"
        if (title != lastWindowTitle) {
            org.lwjgl.glfw.GLFW.glfwSetWindowTitle(windowHandle, title)
            lastWindowTitle = title
        }

        // Drain all MIDI events queued by the MIDI receiver thread and dispatch MIDI-learn /
        // global actions (queue next/prev, bg-queue next/prev, tap tempo) / parameter bindings.
        val (midiCcDelta, bgMidiCcDelta) = session.midiMappingManager.processGlobalMidiEvents(
            midiEnabled = session.uiTheme.midiEnabled,
            parametersState = parametersState,
            mixer = mixer,
            onTapTempo = { session.tapTempoController.tap() }
        )

        val cvDelta = if (session.playQueueManager.isAutoVJEnabled) mixer.pollQueueAdvance() else { mixer.pollQueueAdvance(); 0 }
        if (mixer.pollTapTempo()) {
            session.tapTempoController.tap()
        }
        val keyDelta = processQueueKeyboardShortcuts()
        val totalDelta = midiCcDelta + cvDelta + keyDelta
        if (totalDelta != 0) {
            if (totalDelta > 0) {
                session.playQueueManager.triggerNext(mixer)
            } else {
                session.playQueueManager.triggerPrevious(mixer)
            }
        }

        val bgCvDelta = if (session.bgQueueManager.isAutoBGEnabled) mixer.pollBgQueueAdvance() else { mixer.pollBgQueueAdvance(); 0 }
        val totalBgDelta = bgMidiCcDelta + bgCvDelta
        if (totalBgDelta != 0) {
            if (totalBgDelta > 0) {
                session.bgQueueManager.triggerNext(mixer)
            } else {
                session.bgQueueManager.triggerPrevious(mixer)
            }
        }

        if (pendingFontRebuild) {
            pendingFontRebuild = false
            session.uiTheme.rebuildFonts(ImGui.getIO())
            imguiGl3.destroyFontsTexture()
            imguiGl3.createFontsTexture()
            logger.info { "Preset font size applied (presetNameScalePercent=${session.uiTheme.presetNameScalePercent}%)" }
        }

        imguiGlfw.newFrame()
        imguiGl3.newFrame()
        for (i in 0..4) {
            if (pendingMousePress[i]) {
                ImGui.getIO().setMouseDown(i, true)
                pendingMousePress[i] = false
            }
        }
        ImGui.getIO().mouseWheelH = 0f // Horizontal scroll wheel is disabled globally across all UI panels
        ImGui.newFrame()
        UIThemeStyler.updateUiTransparency(session)
        windowFrameController.update()

        if (!session.uiTheme.cleanModeEnabled) {
            menuBar.draw(session, mixer)
            if (pendingOpenPreferences) {
                PreferencesPanel.open(pendingOpenPreferencesCategory)
                pendingOpenPreferences = false
                pendingOpenPreferencesCategory = null
            }

            if (popupManager.pendingOpenExitPopup) {
                ImGui.openPopup("Exit Liquid LSD?##confirm")
                popupManager.pendingOpenExitPopup = false
            }
            if (popupManager.pendingOpenMidiWarningPopup || PopupManager.globalPendingMidiWarning) {
                ImGui.openPopup("No MIDI Devices Connected##midi_warning")
                popupManager.pendingOpenMidiWarningPopup = false
                PopupManager.globalPendingMidiWarning = false
            }

            drawLayout(mixer, renderer, displayWidth, displayHeight)

            PreferencesPanel.draw(
                session = session,
                currentSize = session.uiTheme.baseSize,
                displayW = displayWidth,
                displayH = displayHeight,
                mixer = mixer,
                onPresetScaleChanged = { newPct -> applyPresetNameScale(newPct) },
                parametersState = parametersState
            )

            VideoExportModal.draw(session, mixer, renderer, displayWidth, displayHeight)

            popupManager.drawExitPopup(mixer, displayWidth, displayHeight)
            popupManager.drawDeckConfirmPopups(session, mixer)
            popupManager.drawSourceChangeConfirmPopup(session, mixer)
            popupManager.drawMidiWarningPopup(displayWidth, displayHeight)
            popupManager.drawRestoreDefaultsPopup()

            NoteEditorModal.draw()
            SavePresetModal.draw(session)
            UpdatePromptModal.draw(session)
            AboutModal.draw(session)
            ShaderPickerPopup.draw(session)

            missingItemsPanel.draw(session)

            ColorTunerPanel.draw(session, displayWidth, displayHeight)

            deckPresetController.drawFileBrowsers()
        }

        ImGui.render()
        imguiGl3.renderDrawData(ImGui.getDrawData())
    }

    /**
     * Handles the fixed set of keyboard shortcuts for driving the play queue and library
     * search, honoring the user's configured [UITheme.QueueKeyTrigger] binding. Returns the
     * net queue-navigation delta (-1/0/+1) produced by this frame's key presses; search-focus
     * and library-mode side effects are applied directly.
     */
    private fun processQueueKeyboardShortcuts(): Int {
        var keyDelta = 0
        if (ImGui.isKeyPressed(imgui.flag.ImGuiKey.F4, false)) {
            session.uiTheme.workspaceMode = if (session.uiTheme.workspaceMode == UITheme.WorkspaceMode.RACK) {
                UITheme.WorkspaceMode.CLASSIC
            } else {
                UITheme.WorkspaceMode.RACK
            }
            AppPreferencesStore.savePreferences()
        }

        val isCtrlF = ImGui.getIO().keyCtrl && ImGui.isKeyPressed(imgui.flag.ImGuiKey.F, false)
        val isSlash = ImGui.isKeyPressed(imgui.flag.ImGuiKey.Slash, false)
        if (isCtrlF || isSlash) {
            if (session.uiTheme.libraryMode == UITheme.LibraryMode.HIDE) {
                session.uiTheme.libraryMode = UITheme.LibraryMode.HALF
                LibraryPanel.isLibraryExpanding = true
                AppPreferencesStore.savePreferences()
            }
            llm.slop.liquidlsd.ui.browser.PresetListPanel.shouldFocusSearch = true
        }

        if (session.uiTheme.queueKeyTrigger != UITheme.QueueKeyTrigger.SPACE_BACKSPACE) {
            if (ImGui.isKeyPressed(imgui.flag.ImGuiKey.Space)) {
                LibraryPanel.cycleMode(session)
            }
        }
        when (session.uiTheme.queueKeyTrigger) {
            UITheme.QueueKeyTrigger.ARROWS -> {
                if (ImGui.isKeyPressed(imgui.flag.ImGuiKey.LeftArrow)) keyDelta -= 1
                if (ImGui.isKeyPressed(imgui.flag.ImGuiKey.RightArrow)) keyDelta += 1
            }
            UITheme.QueueKeyTrigger.PAGE_UP_DOWN -> {
                if (ImGui.isKeyPressed(imgui.flag.ImGuiKey.PageUp)) keyDelta -= 1
                if (ImGui.isKeyPressed(imgui.flag.ImGuiKey.PageDown)) keyDelta += 1
            }
            UITheme.QueueKeyTrigger.SPACE_BACKSPACE -> {
                if (ImGui.isKeyPressed(imgui.flag.ImGuiKey.Backspace)) keyDelta -= 1
                if (ImGui.isKeyPressed(imgui.flag.ImGuiKey.Space)) keyDelta += 1
            }
            else -> {}
        }
        return keyDelta
    }

    fun onContentScaleChanged(newScale: Float) {
        val clamped = newScale.coerceAtLeast(1.0f)
        logger.info { "Window content scale changed: ${clamped}x" }
    }

    fun applyPresetNameScale(newPct: Int) {
        val clamped = (kotlin.math.round(newPct / 10f) * 10).toInt().coerceIn(80, 120)
        if (clamped != session.uiTheme.presetNameScalePercent) {
            session.uiTheme.presetNameScalePercent = clamped
            pendingFontRebuild = true
            if (PreferencesPanel.isOpen) {
                pendingOpenPreferences = true
                pendingOpenPreferencesCategory = PreferencesPanel.activeCategory
            }
            AppPreferencesStore.savePreferences()
            logger.info { "User preset name scale changed to: $clamped%, scheduling font rebuild" }
        }
    }

    fun adjustPresetNameScale(delta: Float) {
        val step = 10
        val currentPct = session.uiTheme.presetNameScalePercent
        val targetPct = (if (delta > 0) currentPct + step else currentPct - step).coerceIn(80, 120)
        applyPresetNameScale(targetPct)
    }

    fun triggerExitFlow() {
        session.uiTheme.cleanModeEnabled = false
        popupManager.pendingOpenExitPopup = true
    }

    companion object {
        private var instance: UIManager? = null

        /**
         * Vertical gap in pixels between the bottom edge of the top title/menu bar
         * and the top edge of the workspace panels (Parameters, Properties, Mixer).
         * Provides a subtle visual separator between the application header and performance panels.
         */
        const val TITLE_BAR_PANEL_GAP = 2.0f

        fun triggerDeckDragDrop(file: File, deck: Deck, isDeckA: Boolean, mixer: Mixer) {
            val ui = instance ?: return
            ui.deckPresetController.loadDeckPresetSafely(mixer, deck, file)
        }

        fun triggerDeckEject(deck: Deck, isDeckA: Boolean = false, isDeckPV: Boolean = false) {
            val ui = instance ?: return
            val mixer = ui.currentMixer ?: return
            ui.deckPresetController.handleEjectDeck(mixer, deck, isDeckA, isDeckPV)
        }

        fun loadDeckPresetSafely(mixer: Mixer, deck: Deck, file: File) {
            val ui = instance ?: return
            ui.deckPresetController.loadDeckPresetSafely(mixer, deck, file)
        }

        fun newPresetSafely(mixer: Mixer, deck: Deck) {
            val ui = instance ?: return
            ui.deckPresetController.newPresetSafely(mixer, deck)
        }
    }

    private fun drawLayout(mixer: Mixer, renderer: Renderer, displayWidth: Float, displayHeight: Float) {
        val safeW = displayWidth.coerceAtLeast(100f)
        val safeH = displayHeight.coerceAtLeast(100f)
        val titleBarH = MenuBar.calculateHeight(session)
        val menuBarH = titleBarH + TITLE_BAR_PANEL_GAP
        val contentH = (safeH - menuBarH).coerceAtLeast(50f)
        val noDecorate = ImGuiWindowFlags.NoResize or
                         ImGuiWindowFlags.NoMove or
                         ImGuiWindowFlags.NoCollapse or
                         ImGuiWindowFlags.NoBringToFrontOnFocus

        if (isUiLabMode) {
            ImGui.setNextWindowPos(0f, menuBarH)
            ImGui.setNextWindowSize(safeW, contentH)
            uiLabPanel.render(safeW, contentH)
            return
        }

        drawAssetManagementLayout(renderer, safeW, safeH, menuBarH, contentH, noDecorate)
    }

    private fun drawAssetManagementLayout(renderer: Renderer, displayWidth: Float, displayHeight: Float, menuBarH: Float, contentH: Float, noDecorate: Int) {
        val theme = session.uiTheme
        if (theme.workspaceMode == UITheme.WorkspaceMode.RACK) {
            ImGui.setNextWindowPos(0f, menuBarH)
            ImGui.setNextWindowSize(displayWidth.coerceAtLeast(1f), contentH.coerceAtLeast(1f))
            val rackWindowFlags = noDecorate or ImGuiWindowFlags.NoScrollbar or ImGuiWindowFlags.NoTitleBar
            if (ImGui.begin("ModularVideoRack", rackWindowFlags)) {
                UIThemeStyler.drawNeonBackgroundIfNeeded(session, ImGui.getWindowPosX(), ImGui.getWindowPosY(), ImGui.getWindowWidth(), ImGui.getWindowHeight(), displayWidth)
                currentMixer?.let { rackPanel.draw(session, it, renderer, displayWidth, contentH) }
            }
            ImGui.end()
            return
        }

        val minRatio = 0.15f

        val sliderWasHovered = CustomRangeSlider.isAnySliderHovered
        CustomRangeSlider.isAnySliderHovered = false

        // Column 1 (Left Panel / Parameters): Auto-calculated based on active columns & font scale
        val reqCol1W = currentMixer?.let { ParametersPanel.calculateRequiredWidth(session, it, parametersState) } ?: (displayWidth * 0.30f)
        val maxCol1W = (displayWidth * 0.50f).coerceAtMost(displayWidth - 200f).coerceAtLeast(displayWidth * minRatio)
        val minCol1W = (displayWidth * minRatio).coerceAtMost(maxCol1W)
        val col1W = reqCol1W.coerceIn(minCol1W, maxCol1W)

        // Column 3 (Right Panel / Mixer): Sized strictly to max allowed width based on height & aspect ratio
        val style = ImGui.getStyle()
        val availHForMixer = (contentH - (style.getWindowPaddingY() * 2f)).coerceAtLeast(1f)
        val maxRightW = MixerLayoutCalculator.calculateMaxAllowedWindowWidth(
            availableHeight = availHForMixer,
            windowPaddingX = style.getWindowPaddingX(),
            textLineHeightWithSpacing = ImGui.getTextLineHeightWithSpacing(),
            frameHeightWithSpacing = ImGui.getFrameHeightWithSpacing(),
            itemSpacingY = style.getItemSpacingY(),
            aspectRatio = theme.renderAspectRatio
        )
        val maxAllowedRightW = (displayWidth - col1W - 450f).coerceAtLeast(100f)
        val rightW = maxRightW.coerceIn(100f, maxAllowedRightW)

        // Column 2 (Middle Panel / Properties) and Library (Spans Col 1 + Col 2)
        val libraryW = (displayWidth - rightW).coerceAtLeast(100f)
        val col2W = (libraryW - col1W).coerceAtLeast(450f)

        val libTitleBarH = session.uiTheme.withFont(UITheme.FontLevel.BODY) {
            (ImGui.getTextLineHeight() + 12f + (style.getWindowBorderSize() * 2f)).coerceAtLeast(32f)
        }

        val libraryH = when (theme.libraryMode) {
            UITheme.LibraryMode.FULL -> contentH
            UITheme.LibraryMode.HIDE -> libTitleBarH.coerceAtMost(contentH)
            UITheme.LibraryMode.HALF -> (contentH * theme.libraryRatio.coerceIn(minRatio, 0.85f)).coerceIn(libTitleBarH.coerceAtMost(contentH), contentH)
        }

        if (theme.libraryMode != UITheme.LibraryMode.FULL) {
            val topH = (contentH - libraryH).coerceAtLeast(1f)

            // Column 1: Parameters
            ImGui.setNextWindowPos(0f, menuBarH)
            ImGui.setNextWindowSize(col1W.coerceAtLeast(1f), topH)
            val parametersFlags = noDecorate or ImGuiWindowFlags.NoScrollbar or ImGuiWindowFlags.NoTitleBar or ImGuiWindowFlags.MenuBar
            PanelTitleBar.withFramePadding(session) {
                if (ImGui.begin("Parameters", parametersFlags)) {
                    UIThemeStyler.drawNeonBackgroundIfNeeded(session, ImGui.getWindowPosX(), ImGui.getWindowPosY(), ImGui.getWindowWidth(), ImGui.getWindowHeight(), displayWidth)
                    ParametersPanel.draw(session, currentMixer!!, parametersState, deckPresetController)
                }
                ImGui.end()
            }

            // Column 2: Properties
            ImGui.setNextWindowPos(col1W, menuBarH)
            ImGui.setNextWindowSize(col2W.coerceAtLeast(1f), topH)
            val propertiesFlags = if (sliderWasHovered) {
                noDecorate or ImGuiWindowFlags.NoScrollWithMouse or ImGuiWindowFlags.NoScrollbar or ImGuiWindowFlags.NoTitleBar or ImGuiWindowFlags.MenuBar
            } else {
                noDecorate or ImGuiWindowFlags.NoScrollbar or ImGuiWindowFlags.NoTitleBar or ImGuiWindowFlags.MenuBar
            }
            PanelTitleBar.withFramePadding(session) {
                if (ImGui.begin("Properties", propertiesFlags)) {
                    UIThemeStyler.drawNeonBackgroundIfNeeded(session, ImGui.getWindowPosX(), ImGui.getWindowPosY(), ImGui.getWindowWidth(), ImGui.getWindowHeight(), displayWidth)
                    PropertiesPanel.draw(session, parametersState, currentMixer!!)

                    // Static divider line between Parameters & Properties
                    val dividerColor = ImGui.getColorU32(imgui.flag.ImGuiCol.Separator)
                    ImGui.getWindowDrawList().addLine(col1W, menuBarH, col1W, menuBarH + topH, dividerColor, 1.5f)
                }
                ImGui.end()
            }
        }

        // Horizontal Splitter / Title bar drag region (above Library when not FULL)
        val libraryPosH = if (theme.libraryMode == UITheme.LibraryMode.FULL) menuBarH else (menuBarH + contentH - libraryH)

        // Library (Bottom or Full)
        ImGui.setNextWindowPos(0f, libraryPosH)
        ImGui.setNextWindowSize(libraryW.coerceAtLeast(1f), libraryH.coerceAtLeast(1f))
        val flags = noDecorate or ImGuiWindowFlags.NoScrollbar or ImGuiWindowFlags.NoTitleBar or ImGuiWindowFlags.MenuBar
        ImGui.pushStyleVar(imgui.flag.ImGuiStyleVar.FramePadding, ImGui.getStyle().getFramePaddingX(), 6.0f)
        if (ImGui.begin("Library", flags)) {
            UIThemeStyler.drawNeonBackgroundIfNeeded(session, ImGui.getWindowPosX(), ImGui.getWindowPosY(), ImGui.getWindowWidth(), ImGui.getWindowHeight(), displayWidth)
            LibraryPanel.draw(session, libraryW.coerceAtLeast(1f), libraryH.coerceAtLeast(1f), currentMixer!!, parametersState)

            if (theme.libraryMode != UITheme.LibraryMode.FULL) {
                val titleBarH = libTitleBarH
                splitterManager.drawHorizontalSplitter(
                    id = "##hsplit",
                    posX = 0f,
                    posY = libraryPosH,
                    width = libraryW.coerceAtLeast(1f),
                    height = titleBarH,
                    displayHeight = displayHeight,
                    drawList = ImGui.getWindowDrawList(),
                    onDrag = { deltaY ->
                        if (theme.libraryMode == UITheme.LibraryMode.HIDE) {
                            if (deltaY < 0f) { // Dragging upward
                                theme.libraryMode = UITheme.LibraryMode.HALF
                                LibraryPanel.isLibraryExpanding = true
                                theme.libraryRatio = theme.lastCustomLibraryRatio.coerceIn(minRatio, 0.85f)
                                AppPreferencesStore.savePreferences()
                            }
                        } else {
                            val deltaR = if (contentH > 0f) -deltaY / contentH else 0f
                            val targetRatio = theme.libraryRatio + deltaR
                            val targetPixelH = contentH * targetRatio
                            if (targetPixelH < (libTitleBarH * 1.3f) || targetRatio < 0.10f) {
                                theme.lastCustomLibraryRatio = theme.libraryRatio
                                theme.libraryMode = UITheme.LibraryMode.HIDE
                                LibraryPanel.isLibraryExpanding = true
                            } else {
                                val newR = targetRatio.coerceIn(minRatio, 0.85f)
                                theme.libraryRatio = newR
                                theme.lastCustomLibraryRatio = newR
                            }
                            AppPreferencesStore.savePreferences()
                        }
                    },
                    onDoubleClick = {
                        theme.libraryMode = UITheme.LibraryMode.HALF
                        LibraryPanel.isLibraryExpanding = true
                        theme.libraryRatio = 0.50f
                        theme.lastCustomLibraryRatio = 0.50f
                        AppPreferencesStore.savePreferences()
                    }
                )
            }
        }
        ImGui.end()
        ImGui.popStyleVar()

        // Column 3: Mixer
        ImGui.setNextWindowPos(libraryW, menuBarH)
        ImGui.setNextWindowSize(rightW.coerceAtLeast(1f), contentH.coerceAtLeast(1f))
        val noTitleDecorate = noDecorate or ImGuiWindowFlags.NoTitleBar or ImGuiWindowFlags.NoScrollbar
        if (ImGui.begin("Mixer", noTitleDecorate)) {
            UIThemeStyler.drawNeonBackgroundIfNeeded(session, ImGui.getWindowPosX(), ImGui.getWindowPosY(), ImGui.getWindowWidth(), ImGui.getWindowHeight(), displayWidth)
            drawMixer(currentMixer!!)

            // Static divider line between Center Column/Library and Mixer
            val dividerColor = ImGui.getColorU32(imgui.flag.ImGuiCol.Separator)
            ImGui.getWindowDrawList().addLine(libraryW, menuBarH, libraryW, menuBarH + contentH, dividerColor, 1.5f)
        }
        ImGui.end()
    }

    private fun drawMixer(mixer: Mixer) {
        Column3HeaderToggle.draw(session)
        ImGui.spacing()
        when (session.uiTheme.column3Mode) {
            UITheme.Column3Mode.MIXER -> mixerPanel.draw(session, mixer)
            UITheme.Column3Mode.MACROS -> macroPanel.draw(session, mixer)
        }
    }

    fun dispose() {
        prevMouseButtonCallback?.free()
        windowFrameController.destroy()
        defaultStyle.destroy()
        imguiGl3.shutdown()
        imguiGlfw.shutdown()
        ImGui.destroyContext()
    }
}
