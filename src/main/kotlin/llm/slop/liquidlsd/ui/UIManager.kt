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
    private val isOutputWindowOpen: () -> Boolean = { false }
) {
    private val logger = KotlinLogging.logger {}
    private val imguiGlfw = ImGuiImplGlfw()
    private val imguiGl3 = ImGuiImplGl3()


    // Clean default style to reset size attributes before scaling
    private var defaultStyle: imgui.ImGuiStyle

    // Font rebuild must happen between frames (atlas is locked during a frame).
    // Store rebuild request flag here; it is consumed at the top of the next render().
    private var pendingFontRebuild = false

    // Set to true for one frame when the Settings menu item is clicked; consumed
    // immediately after endMainMenuBar so openPopup runs at root ID-stack level.
    private var pendingOpenSettings = false
    private var pendingOpenSettingsCategory: SettingsPanel.Category? = null

    private val splitterManager = SplitterManager()

    private val presetState = PresetGridState()

    private val popupManager: PopupManager = PopupManager(
        onTriggerExit = { org.lwjgl.glfw.GLFW.glfwSetWindowShouldClose(windowHandle, true) },
        onSaveDeck = { name, deck, isDeckA -> currentMixer?.let { deckPresetController.saveDeckPreset(it, name, deck, isDeckA) } }
    )

    val deckPresetController = DeckPresetController(session, popupManager)

    private val menuBar = MenuBar(
        popupManager = popupManager,
        presetState = presetState,
        onTriggerExitFlow = { triggerExitFlow() },
        onOpenSettings = {
            pendingOpenSettings = true
            pendingOpenSettingsCategory = null
        },
        onOpenAudioEngineMonitor = {
            pendingOpenSettings = true
            pendingOpenSettingsCategory = SettingsPanel.Category.AUDIO_ENGINE
        },
        onToggleOutputWindow = onToggleOutputWindow,
        isOutputWindowOpen = isOutputWindowOpen
    )

    private val missingItemsPanel = MissingItemsPanel()


    private var lastNextMidiCcHigh = false
    private var lastPrevMidiCcHigh = false
    private var lastBgNextMidiCcHigh = false
    private var lastBgPrevMidiCcHigh = false

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
        // MIDI learn events arrive via MidiEngine.receivedCcEvents (a ConcurrentLinkedQueue)
        // and are processed each frame at the top of render(). No direct callback hook needed.

        logger.info { "UIManager initialized" }
    }

    private val deckControlPanel = DeckControlPanel(presetState)

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

    private val mixerMonitorPanel = MixerMonitorPanel(
        presetState = presetState,
        drawDeckControls = { mixer, label, deck, width, height, isDeckA ->
            deckControlPanel.drawDeckControls(session, mixer, label, deck, width, height, isDeckA, deckUtilityAction, monitorSaveDeck, monitorEjectDeck)
        },
        onUtilityAction = deckUtilityAction,
        onSaveDeck = monitorSaveDeck,
        onEjectDeck = monitorEjectDeck
    )

    fun render(mixer: Mixer, renderer: Renderer, displayWidth: Float, displayHeight: Float) {
        currentMixer = mixer

        // Update window title dynamically
        val title = "Liquid LSD - Libre Shader Decks"
        if (title != lastWindowTitle) {
            org.lwjgl.glfw.GLFW.glfwSetWindowTitle(windowHandle, title)
            lastWindowTitle = title
        }

        // Drain all MIDI events queued by the MIDI receiver thread.
        var midiCcDelta = 0
        var bgMidiCcDelta = 0
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
                val bgNextCc = session.midiMappingManager.getCcForSpecial("Global/bgQueueNext")
                val bgNextCh = session.midiMappingManager.getChannelForSpecial("Global/bgQueueNext")
                if (bgNextCc != -1 && cc == bgNextCc && channel == bgNextCh) {
                    val valNow = llm.slop.liquidlsd.midi.MidiEngine.getCcValue(channel, cc)
                    val isHigh = valNow > 0.5f
                    if (isHigh && !lastBgNextMidiCcHigh) {
                        bgMidiCcDelta += 1
                    }
                    lastBgNextMidiCcHigh = isHigh
                }
                val bgPrevCc = session.midiMappingManager.getCcForSpecial("Global/bgQueuePrev")
                val bgPrevCh = session.midiMappingManager.getChannelForSpecial("Global/bgQueuePrev")
                if (bgPrevCc != -1 && cc == bgPrevCc && channel == bgPrevCh) {
                    val valNow = llm.slop.liquidlsd.midi.MidiEngine.getCcValue(channel, cc)
                    val isHigh = valNow > 0.5f
                    if (isHigh && !lastBgPrevMidiCcHigh) {
                        bgMidiCcDelta -= 1
                    }
                    lastBgPrevMidiCcHigh = isHigh
                }
            }
        }

        val cvDelta = if (session.playQueueManager.isAutoVJEnabled) mixer.pollQueueAdvance() else { mixer.pollQueueAdvance(); 0 }
        var keyDelta = 0
        if (!ImGui.getIO().wantTextInput) {
            val isCtrlF = ImGui.getIO().keyCtrl && ImGui.isKeyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_F, false)
            val isSlash = ImGui.isKeyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_SLASH, false)
            if (isCtrlF || isSlash) {
                if (session.uiTheme.libraryMode == UITheme.LibraryMode.HIDE) {
                    session.uiTheme.libraryMode = UITheme.LibraryMode.HALF
                    LibraryPanel.isLibraryExpanding = true
                    session.uiTheme.saveSettings()
                }
                llm.slop.liquidlsd.ui.browser.PresetListPanel.shouldFocusSearch = true
            }

            if (session.uiTheme.queueKeyTrigger != UITheme.QueueKeyTrigger.SPACE_BACKSPACE) {
                if (ImGui.isKeyPressed(ImGui.getKeyIndex(imgui.flag.ImGuiKey.Space))) {
                    LibraryPanel.cycleMode(session)
                }
            }
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
            val currentBaseSize = session.uiTheme.baseSize
            session.uiTheme.rebuildFonts(ImGui.getIO())
            imguiGl3.updateFontsTexture()
            UIThemeStyler.scaleStyleFromDefault(defaultStyle, currentBaseSize)
            logger.info { "Font size applied: ${currentBaseSize}px (guiScale=${session.uiTheme.guiScalePercent}%, dpiScale=${session.uiTheme.systemDpiScale}x)" }
        }

        imguiGlfw.newFrame()
        ImGui.newFrame()
        UIThemeStyler.updateUiTransparency(session)

        if (!session.uiTheme.cleanModeEnabled) {
            menuBar.draw(session, mixer)
            if (pendingOpenSettings) {
                SettingsPanel.open(pendingOpenSettingsCategory)
                pendingOpenSettings = false
                pendingOpenSettingsCategory = null
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

            SettingsPanel.draw(session, session.uiTheme.baseSize, displayWidth, displayHeight, mixer) { newPct ->
                applyGuiScalePercent(newPct)
            }

            VideoExportModal.draw(session, mixer, renderer, displayWidth, displayHeight)

            popupManager.drawExitPopup(mixer, displayWidth, displayHeight)
            popupManager.drawDeckConfirmPopups(session, mixer)
            popupManager.drawMidiWarningPopup(displayWidth, displayHeight)

            NoteEditorModal.draw()
            SavePresetModal.draw(session)

            missingItemsPanel.draw(session)

            ColorTunerPanel.draw(session, displayWidth, displayHeight)

            deckPresetController.drawFileBrowsers()
        }

        ImGui.render()
        imguiGl3.renderDrawData(ImGui.getDrawData())
    }

    fun onContentScaleChanged(newScale: Float) {
        val clamped = newScale.coerceAtLeast(1.0f)
        if (session.uiTheme.systemDpiScale != clamped) {
            session.uiTheme.systemDpiScale = clamped
            pendingFontRebuild = true
            logger.info { "System DPI scale changed to: ${clamped}x, scheduling font rebuild (effective baseSize=${session.uiTheme.baseSize}px)" }
        }
    }

    fun applyGuiScalePercent(newPct: Int) {
        val clamped = newPct.coerceIn(75, 200)
        if (clamped != session.uiTheme.guiScalePercent) {
            session.uiTheme.guiScalePercent = clamped
            pendingFontRebuild = true
            session.uiTheme.saveSettings()
            logger.info { "User GUI scale changed to: ${clamped}%, scheduling font rebuild (effective baseSize=${session.uiTheme.baseSize}px)" }
        }
    }

    fun adjustFontSize(delta: Float) {
        val step = 5
        val currentPct = session.uiTheme.guiScalePercent
        val targetPct = if (delta > 0) currentPct + step else currentPct - step
        applyGuiScalePercent(targetPct)
    }

    fun triggerExitFlow() {
        session.uiTheme.cleanModeEnabled = false
        popupManager.pendingOpenExitPopup = true
    }

    companion object {
        private var instance: UIManager? = null

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

    private fun drawLayout(mixer: Mixer, displayWidth: Float, displayHeight: Float) {
        val safeW = displayWidth.coerceAtLeast(100f)
        val safeH = displayHeight.coerceAtLeast(100f)
        val menuBarH = session.uiTheme.withFont(UITheme.FontLevel.BODY) { ImGui.getFrameHeight() }.coerceAtLeast(32f)
        val contentH = (safeH - menuBarH).coerceAtLeast(50f)
        val noDecorate = ImGuiWindowFlags.NoResize or
                         ImGuiWindowFlags.NoMove or
                         ImGuiWindowFlags.NoCollapse or
                         ImGuiWindowFlags.NoBringToFrontOnFocus

        drawAssetManagementLayout(safeW, safeH, menuBarH, contentH, noDecorate)
    }

    private fun drawAssetManagementLayout(displayWidth: Float, displayHeight: Float, menuBarH: Float, contentH: Float, noDecorate: Int) {
        val theme = session.uiTheme
        val minRatio = 0.15f

        val sliderWasHovered = CustomRangeSlider.isAnySliderHovered
        CustomRangeSlider.isAnySliderHovered = false

        // Column 1 (Left Panel / Preset Grid): Auto-calculated based on active columns & font scale
        val reqCol1W = currentMixer?.let { PresetGridPanel.calculateRequiredWidth(session, it, presetState) } ?: (displayWidth * 0.30f)
        val maxCol1W = (displayWidth * 0.50f).coerceAtMost(displayWidth - 200f).coerceAtLeast(displayWidth * minRatio)
        val minCol1W = (displayWidth * minRatio).coerceAtMost(maxCol1W)
        val col1W = reqCol1W.coerceIn(minCol1W, maxCol1W)

        // Column 3 (Right Panel / Mixer Monitor): Sized strictly to max allowed width based on height & aspect ratio
        val style = ImGui.getStyle()
        val availHForMixer = (contentH - (style.getWindowPaddingY() * 2f)).coerceAtLeast(1f)
        val maxRightW = MixerMonitorLayoutCalculator.calculateMaxAllowedWindowWidth(
            availableHeight = availHForMixer,
            windowPaddingX = style.getWindowPaddingX(),
            textLineHeightWithSpacing = ImGui.getTextLineHeightWithSpacing(),
            frameHeightWithSpacing = ImGui.getFrameHeightWithSpacing(),
            itemSpacingY = style.getItemSpacingY(),
            aspectRatio = theme.renderAspectRatio
        )
        val maxAllowedRightW = (displayWidth - col1W - 50f).coerceAtLeast(100f)
        val rightW = maxRightW.coerceIn(100f, maxAllowedRightW)

        // Column 2 (Middle Panel / Cell Config) and Library (Spans Col 1 + Col 2)
        val libraryW = (displayWidth - rightW).coerceAtLeast(100f)
        val col2W = (libraryW - col1W).coerceAtLeast(20f)

        val libraryH = when (theme.libraryMode) {
            UITheme.LibraryMode.FULL -> contentH
            UITheme.LibraryMode.HIDE -> 38f.coerceAtMost(contentH)
            UITheme.LibraryMode.HALF -> (contentH * theme.libraryRatio.coerceIn(minRatio, 0.85f)).coerceIn(38f.coerceAtMost(contentH), contentH)
        }

        if (theme.libraryMode != UITheme.LibraryMode.FULL) {
            val topH = (contentH - libraryH).coerceAtLeast(1f)

            // Column 1: Preset Grid
            ImGui.setNextWindowPos(0f, menuBarH)
            ImGui.setNextWindowSize(col1W.coerceAtLeast(1f), topH)
            if (ImGui.begin("Preset Grid", noDecorate or ImGuiWindowFlags.NoScrollbar)) {
                UIThemeStyler.drawNeonBackgroundIfNeeded(session, ImGui.getWindowPosX(), ImGui.getWindowPosY(), ImGui.getWindowWidth(), ImGui.getWindowHeight(), displayWidth)
                PresetGridPanel.draw(session, currentMixer!!, presetState)
            }
            ImGui.end()

            // Column 2: Cell Config
            ImGui.setNextWindowPos(col1W, menuBarH)
            ImGui.setNextWindowSize(col2W.coerceAtLeast(1f), topH)
            val cellConfigFlags = if (sliderWasHovered) {
                noDecorate or ImGuiWindowFlags.NoScrollWithMouse or ImGuiWindowFlags.NoScrollbar
            } else {
                noDecorate or ImGuiWindowFlags.NoScrollbar
            }
            if (ImGui.begin("Cell Config", cellConfigFlags)) {
                UIThemeStyler.drawNeonBackgroundIfNeeded(session, ImGui.getWindowPosX(), ImGui.getWindowPosY(), ImGui.getWindowWidth(), ImGui.getWindowHeight(), displayWidth)
                CellConfigPanel.draw(session, presetState, currentMixer!!)

                // Static divider line between Preset Grid & Cell Config
                val dividerColor = ImGui.getColorU32(imgui.flag.ImGuiCol.Separator)
                ImGui.getWindowDrawList().addLine(col1W, menuBarH, col1W, menuBarH + topH, dividerColor, 1.5f)
            }
            ImGui.end()
        }

        // Horizontal Splitter / Title bar drag region (above Library when not FULL)
        val libraryPosH = if (theme.libraryMode == UITheme.LibraryMode.FULL) menuBarH else (menuBarH + contentH - libraryH)

        // Library (Bottom or Full)
        ImGui.setNextWindowPos(0f, libraryPosH)
        ImGui.setNextWindowSize(libraryW.coerceAtLeast(1f), libraryH.coerceAtLeast(1f))
        val flags = (if (theme.libraryMode == UITheme.LibraryMode.HIDE) noDecorate or ImGuiWindowFlags.NoScrollbar else noDecorate) or
                ImGuiWindowFlags.NoTitleBar or ImGuiWindowFlags.MenuBar
        ImGui.pushStyleVar(imgui.flag.ImGuiStyleVar.FramePadding, ImGui.getStyle().getFramePaddingX(), 10.5f)
        if (ImGui.begin("Library", flags)) {
            UIThemeStyler.drawNeonBackgroundIfNeeded(session, ImGui.getWindowPosX(), ImGui.getWindowPosY(), ImGui.getWindowWidth(), ImGui.getWindowHeight(), displayWidth)
            LibraryPanel.draw(session, libraryW.coerceAtLeast(1f), libraryH.coerceAtLeast(1f), currentMixer!!, presetState)

            if (theme.libraryMode != UITheme.LibraryMode.FULL) {
                val titleBarH = session.uiTheme.withFont(UITheme.FontLevel.BODY) { ImGui.getFrameHeight() }.coerceAtLeast(32f)
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
                                theme.saveSettings()
                            }
                        } else {
                            val deltaR = if (contentH > 0f) -deltaY / contentH else 0f
                            val targetRatio = theme.libraryRatio + deltaR
                            val targetPixelH = contentH * targetRatio
                            if (targetPixelH < 60f || targetRatio < 0.10f) {
                                theme.lastCustomLibraryRatio = theme.libraryRatio
                                theme.libraryMode = UITheme.LibraryMode.HIDE
                                LibraryPanel.isLibraryExpanding = true
                            } else {
                                val newR = targetRatio.coerceIn(minRatio, 0.85f)
                                theme.libraryRatio = newR
                                theme.lastCustomLibraryRatio = newR
                            }
                            theme.saveSettings()
                        }
                    },
                    onDoubleClick = {
                        theme.libraryMode = UITheme.LibraryMode.HALF
                        LibraryPanel.isLibraryExpanding = true
                        theme.libraryRatio = 0.50f
                        theme.lastCustomLibraryRatio = 0.50f
                        theme.saveSettings()
                    }
                )
            }
        }
        ImGui.end()
        ImGui.popStyleVar()

        // Column 3: Mixer / Monitor
        ImGui.setNextWindowPos(libraryW, menuBarH)
        ImGui.setNextWindowSize(rightW.coerceAtLeast(1f), contentH.coerceAtLeast(1f))
        val noTitleDecorate = noDecorate or ImGuiWindowFlags.NoTitleBar
        if (ImGui.begin("Mixer / Monitor", noTitleDecorate)) {
            UIThemeStyler.drawNeonBackgroundIfNeeded(session, ImGui.getWindowPosX(), ImGui.getWindowPosY(), ImGui.getWindowWidth(), ImGui.getWindowHeight(), displayWidth)
            drawMixerMonitor(currentMixer!!)

            // Static divider line between Center Column/Library and Mixer/Monitor
            val dividerColor = ImGui.getColorU32(imgui.flag.ImGuiCol.Separator)
            ImGui.getWindowDrawList().addLine(libraryW, menuBarH, libraryW, menuBarH + contentH, dividerColor, 1.5f)
        }
        ImGui.end()
    }

    private fun drawMixerMonitor(mixer: Mixer) {
        mixerMonitorPanel.draw(session, mixer)
    }

    fun dispose() {
        defaultStyle.destroy()
        imguiGl3.dispose()
        imguiGlfw.dispose()
        ImGui.destroyContext()
    }
}
