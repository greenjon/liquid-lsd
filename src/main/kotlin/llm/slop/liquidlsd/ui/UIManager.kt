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
class UIManager(private val windowHandle: Long, val session: llm.slop.liquidlsd.SessionContext) {
    private val logger = KotlinLogging.logger {}
    private val imguiGlfw = ImGuiImplGlfw()
    private val imguiGl3 = ImGuiImplGl3()


    // Clean default style to reset size attributes before scaling
    private var defaultStyle: imgui.ImGuiStyle

    // Font rebuild must happen between frames (atlas is locked during a frame).
    // Store the requested size here; it is consumed at the top of the next render().
    private var pendingFontSize: Float? = null

    // Set to true for one frame when the Settings menu item is clicked; consumed
    // immediately after endMainMenuBar so openPopup runs at root ID-stack level.
    private var pendingOpenSettings = false

    private val splitterManager = SplitterManager()
    private var pendingOpenAudioEngineMonitor = false

    private val presetState = PresetGridState()

    private val popupManager: PopupManager = PopupManager(
        onTriggerExit = { org.lwjgl.glfw.GLFW.glfwSetWindowShouldClose(windowHandle, true) },
        onSaveDeck = { name, deck, isDeckA -> currentMixer?.let { deckPresetController.saveDeckPreset(it, name, deck, isDeckA) } },
        onExecuteDeckAction = { deck, isDeckA, action, targetPreset ->
            currentMixer?.let { deckPresetController.onExecuteDeckAction(it, deck, isDeckA, action, targetPreset) }
        }
    )

    val deckPresetController = DeckPresetController(session, popupManager)

    private val menuBar = MenuBar(
        popupManager = popupManager,
        presetState = presetState,
        onTriggerExitFlow = { triggerExitFlow() },
        onOpenSettings = { pendingOpenSettings = true },
        onOpenAudioEngineMonitor = { pendingOpenAudioEngineMonitor = true }
    )

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

    private val monitorEjectDeck = { deck: Deck, isDeckA: Boolean, isDeckC: Boolean ->
        val mixer = currentMixer
        if (mixer != null) {
            deckPresetController.handleEjectDeck(mixer, deck, isDeckA, isDeckC)
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
            UIThemeStyler.scaleStyleFromDefault(defaultStyle, newSize)
            session.uiTheme.saveSettings()
            logger.info { "Font size applied: ${newSize}px" }
        }

        imguiGlfw.newFrame()
        ImGui.newFrame()
        UIThemeStyler.updateUiTransparency(session)

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

            NoteEditorModal.draw()
            SavePresetModal.draw(session)

            missingItemsPanel.draw(session)

            ColorTunerPanel.draw(session, displayWidth, displayHeight)

            deckPresetController.drawFileBrowsers()
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
            instance?.deckPresetController?.triggerDeckDragDrop(file, deck, isDeckA, mixer)
        }

        fun triggerDeckEject(deck: Deck, isDeckA: Boolean, isDeckC: Boolean = false) {
            val ui = instance ?: return
            val mixer = ui.currentMixer ?: return
            ui.deckPresetController.handleEjectDeck(mixer, deck, isDeckA, isDeckC)
        }
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

        val libraryH = when (theme.libraryMode) {
            UITheme.LibraryMode.FULL -> contentH
            UITheme.LibraryMode.HIDE -> 38f
            UITheme.LibraryMode.HALF -> contentH * theme.libraryRatio.coerceIn(minRatio, 0.85f)
        }

        if (theme.libraryMode != UITheme.LibraryMode.FULL) {
            val topH = contentH - libraryH

            // Column 1: Preset Grid
            ImGui.setNextWindowPos(0f, menuBarH)
            ImGui.setNextWindowSize(col1W, topH)
            if (ImGui.begin("Preset Grid", noDecorate or ImGuiWindowFlags.NoScrollbar)) {
                UIThemeStyler.drawNeonBackgroundIfNeeded(session, ImGui.getWindowPosX(), ImGui.getWindowPosY(), ImGui.getWindowWidth(), ImGui.getWindowHeight(), displayWidth)
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
                UIThemeStyler.drawNeonBackgroundIfNeeded(session, ImGui.getWindowPosX(), ImGui.getWindowPosY(), ImGui.getWindowWidth(), ImGui.getWindowHeight(), displayWidth)
                CellConfigPanel.draw(session, presetState, currentMixer!!)
            }
            ImGui.end()

            // Vertical Splitter 1 (Static divider line between Preset Grid & Cell Config)
            val drawList = ImGui.getForegroundDrawList()
            val dividerColor = ImGui.getColorU32(imgui.flag.ImGuiCol.Separator)
            drawList.addLine(col1W, menuBarH, col1W, menuBarH + topH, dividerColor, 1.5f)
        }

        // Horizontal Splitter (above Library when not FULL)
        val libraryPosH = if (theme.libraryMode == UITheme.LibraryMode.FULL) menuBarH else (menuBarH + contentH - libraryH)
        if (theme.libraryMode != UITheme.LibraryMode.FULL) {
            splitterManager.drawHorizontalSplitter(
                id = "##hsplit",
                posX = 0f,
                posY = libraryPosH,
                width = libraryW,
                height = 8f,
                displayHeight = displayHeight,
                onDrag = { deltaY ->
                    if (theme.libraryMode == UITheme.LibraryMode.HIDE) {
                        if (deltaY < 0f) { // Dragging upward
                            theme.libraryMode = UITheme.LibraryMode.HALF
                            theme.libraryRatio = theme.lastCustomLibraryRatio.coerceIn(minRatio, 0.85f)
                            theme.saveSettings()
                        }
                    } else {
                        val deltaR = -deltaY / contentH
                        val targetRatio = theme.libraryRatio + deltaR
                        val targetPixelH = contentH * targetRatio
                        if (targetPixelH < 60f || targetRatio < 0.10f) {
                            theme.lastCustomLibraryRatio = theme.libraryRatio
                            theme.libraryMode = UITheme.LibraryMode.HIDE
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
                    theme.libraryRatio = 0.50f
                    theme.lastCustomLibraryRatio = 0.50f
                    theme.saveSettings()
                }
            )
        }

        // Library (Bottom or Full)
        ImGui.setNextWindowPos(0f, libraryPosH)
        ImGui.setNextWindowSize(libraryW, libraryH)
        val flags = (if (theme.libraryMode == UITheme.LibraryMode.HIDE) noDecorate or ImGuiWindowFlags.NoScrollbar else noDecorate) or
                ImGuiWindowFlags.NoTitleBar or ImGuiWindowFlags.MenuBar
        if (ImGui.begin("Library", flags)) {
            UIThemeStyler.drawNeonBackgroundIfNeeded(session, ImGui.getWindowPosX(), ImGui.getWindowPosY(), ImGui.getWindowWidth(), ImGui.getWindowHeight(), displayWidth)
            LibraryPanel.draw(session, libraryW, libraryH, currentMixer!!, presetState)
        }
        ImGui.end()

        // Column 3: Mixer / Monitor
        ImGui.setNextWindowPos(libraryW, menuBarH)
        ImGui.setNextWindowSize(rightW, contentH)
        val noTitleDecorate = noDecorate or ImGuiWindowFlags.NoTitleBar
        if (ImGui.begin("Mixer / Monitor", noTitleDecorate)) {
            UIThemeStyler.drawNeonBackgroundIfNeeded(session, ImGui.getWindowPosX(), ImGui.getWindowPosY(), ImGui.getWindowWidth(), ImGui.getWindowHeight(), displayWidth)
            drawMixerMonitor(currentMixer!!)
        }
        ImGui.end()

        // Vertical Splitter 2 (between Cell Config/Library and Mixer/Monitor)
        splitterManager.drawVerticalSplitter(
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
