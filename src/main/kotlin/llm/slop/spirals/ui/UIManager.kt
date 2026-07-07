package llm.slop.spirals.ui

import imgui.ImGui
import imgui.flag.ImGuiCol
import imgui.flag.ImGuiConfigFlags
import imgui.flag.ImGuiWindowFlags
import imgui.type.ImInt
import imgui.type.ImString
import llm.slop.spirals.config.ProjectConfig
import java.io.File
import llm.slop.spirals.rendering.Deck
import llm.slop.spirals.rendering.Mandala
import llm.slop.spirals.rendering.MandalaLibrary
import llm.slop.spirals.rendering.MandalaRatio





import llm.slop.spirals.rendering.Mixer
import llm.slop.spirals.patches.PatchManager
import kotlin.math.roundToInt
import mu.KotlinLogging
import org.lwjgl.opengl.GL33.*
import imgui.gl3.ImGuiImplGl3
import imgui.glfw.ImGuiImplGlfw
import llm.slop.spirals.parameters.ModulatableParameter
import llm.slop.spirals.models.toDto
import llm.slop.spirals.models.applyDto
import llm.slop.spirals.patches.PlayQueueManager

/**
 * Manages the ImGui overlay for desktop control.
 */
class UIManager(
    private val windowHandle: Long,
    private val uiMode: UiMode = UiMode.APP
) {
    enum class UiMode { APP, LAB }

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
    private var pendingOpenAudioEngineMonitor = false

    private var lastBgVideoEnabled: Boolean? = null

    private fun updateUiTransparency() {
        val enabled = UITheme.backgroundVideoEnabled
        if (enabled == lastBgVideoEnabled) return
        lastBgVideoEnabled = enabled
        UITheme.applyStyleColors(enabled)
    }

    private val patchState = PatchGridState()

    private val popupManager: PopupManager = PopupManager(
        onTriggerExit = { org.lwjgl.glfw.GLFW.glfwSetWindowShouldClose(windowHandle, true) },
        onSaveDeck = { name, deck, isDeckA -> saveDeckPreset(name, deck, isDeckA) },
        onExecuteDeckAction = { deck, isDeckA, action, targetPreset ->
            when (action) {
                PopupManager.PendingDeckAction.NEW -> {
                    deck.reset()
                    if (isDeckA) {
                        llm.slop.spirals.patches.PatchManager.activePresetA = null
                        llm.slop.spirals.patches.PatchManager.cachedDtoA = null
                    } else {
                        llm.slop.spirals.patches.PatchManager.activePresetB = null
                        llm.slop.spirals.patches.PatchManager.cachedDtoB = null
                    }
                }
                PopupManager.PendingDeckAction.LOAD_FILE -> {
                    performLoadDeckPreset(isDeckA)
                }
                PopupManager.PendingDeckAction.LOAD_PRESET -> {
                    if (targetPreset != null) {
                        if (targetPreset == "None") {
                            if (isDeckA) {
                                llm.slop.spirals.patches.PatchManager.activePresetA = null
                                llm.slop.spirals.patches.PatchManager.cachedDtoA = null
                            } else {
                                llm.slop.spirals.patches.PatchManager.activePresetB = null
                                llm.slop.spirals.patches.PatchManager.cachedDtoB = null
                            }
                        } else {
                            loadDeckPreset(targetPreset, deck, isDeckA)
                        }
                    }
                }
                else -> {}
            }
        }
    )

    private val menuBar = MenuBar(
        popupManager = popupManager,
        patchState = patchState,
        onTriggerExitFlow = { triggerExitFlow() },
        onOpenSettings = { pendingOpenSettings = true },
        onOpenAudioEngineMonitor = { pendingOpenAudioEngineMonitor = true }
    )

    // Phase 2 -- Deck preset browsers (replaces flat ImGui.combo)
    private val deckABrowser = DeckPresetBrowser("A")
    private val deckBBrowser = DeckPresetBrowser("B")


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
        UITheme.loadFonts(io)

        // Save the default style right after context initialization so we can revert sizes
        defaultStyle = imgui.ImGuiStyle()

        // Scale style sizes proportionally to the loaded baseSize relative to the baseline of 15f
        scaleStyleFromDefault(UITheme.baseSize)
        UITheme.applyStyleColors(UITheme.backgroundVideoEnabled)

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
                    llm.slop.spirals.patches.PatchManager.activePresetA = null
                    llm.slop.spirals.patches.PatchManager.cachedDtoA = null
                } else {
                    currentMixer?.deckB?.reset()
                    llm.slop.spirals.patches.PatchManager.activePresetB = null
                    llm.slop.spirals.patches.PatchManager.cachedDtoB = null
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
                llm.slop.spirals.patches.PatchManager.activePresetA = null
                llm.slop.spirals.patches.PatchManager.cachedDtoA = null
            } else {
                currentMixer?.deckB?.reset()
                llm.slop.spirals.patches.PatchManager.activePresetB = null
                llm.slop.spirals.patches.PatchManager.cachedDtoB = null
            }
        }
    )

    private val deckUtilityAction = { mode: Int, from: Deck, to: Deck ->
        val mixer = currentMixer
        if (mixer != null) {
            val isDirty = PatchManager.isDeckDirty(to, mixer)
            if (!isDirty) {
                when (mode) {
                    0 -> PatchManager.moveDeck(mixer, from, to)
                    1 -> PatchManager.copyDeck(mixer, from, to)
                    2 -> PatchManager.swapDecks(mixer, from, to)
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

    private val mixerMonitorPanel = MixerMonitorPanel(
        patchState = patchState,
        drawDeckControls = { mixer, label, deck, width, height, isDeckA -> deckControlPanel.drawDeckControls(mixer, label, deck, width, height, isDeckA, deckUtilityAction) },
        onUtilityAction = deckUtilityAction,
        onSaveDeck = { deck, isDeckA, isSaveAs ->
            val activeName = when {
                deck === currentMixer?.deckA -> PatchManager.activePresetA
                deck === currentMixer?.deckB -> PatchManager.activePresetB
                deck === currentMixer?.deckC -> PatchManager.activePresetC
                else -> null
            }
            if (activeName != null && !isSaveAs) {
                saveDeckPreset(activeName, deck, isDeckA)
            } else {
                if (deck === currentMixer?.deckA) deckABrowser.open()
                else if (deck === currentMixer?.deckB) deckBBrowser.open()
                // Deck C save-as could be added here if needed
            }
        }
    )

    fun render(mixer: Mixer, displayWidth: Float, displayHeight: Float) {
        currentMixer = mixer

        // Update window title dynamically with project name and dirty status
        val title = if (uiMode == UiMode.LAB) ProjectConfig.App.UI_LAB_WINDOW_TITLE else ProjectConfig.App.NAME
        if (title != lastWindowTitle) {
            org.lwjgl.glfw.GLFW.glfwSetWindowTitle(windowHandle, title)
            lastWindowTitle = title
        }

        if (uiMode == UiMode.LAB) {
            imguiGlfw.newFrame()
            ImGui.newFrame()
            updateUiTransparency()
            UiLabPanel.draw(displayWidth, displayHeight)
            ImGui.render()
            imguiGl3.renderDrawData(ImGui.getDrawData())
            return
        }

        // Drain all MIDI events queued by the MIDI receiver thread.
        var midiCcDelta = 0
        while (true) {
            val event = llm.slop.spirals.midi.MidiEngine.receivedCcEvents.poll() ?: break
            val (channel, cc) = event
            val target = patchState.midiLearnTarget
            if (target != null) {
                val midiId = "midi_cc_${channel}_${cc}"
                when (target) {
                    is MidiLearnTarget.BaseValueSlider -> {
                        llm.slop.spirals.midi.MidiMappingManager.addMapping(target.paramKey, cc, channel, target.min, target.max)
                        llm.slop.spirals.midi.MidiMappingManager.saveActiveProfile()
                    }
                    is MidiLearnTarget.GridCell -> {
                        val existingMods = target.param.modulators.filter { it.sourceId.startsWith("midi_cc_") }
                        target.param.modulators.removeAll(existingMods)
                        val exists = target.param.modulators.any { it.sourceId == midiId }
                        if (!exists) {
                            target.param.modulators.add(
                                llm.slop.spirals.parameters.CvModulator(
                                    sourceId = midiId,
                                    amplitude = 1.0f,
                                    operator = llm.slop.spirals.parameters.ModulationOperator.ADD
                                )
                            )
                        }
                    }
                }
                patchState.midiLearnTarget = null
            } else {
                val nextCc = llm.slop.spirals.midi.MidiMappingManager.getCcForSpecial("Global/queueNext")
                val nextCh = llm.slop.spirals.midi.MidiMappingManager.getChannelForSpecial("Global/queueNext")
                if (nextCc != -1 && cc == nextCc && channel == nextCh) {
                    val valNow = llm.slop.spirals.midi.MidiEngine.getCcValue(channel, cc)
                    val isHigh = valNow > 0.5f
                    if (isHigh && !lastNextMidiCcHigh) {
                        midiCcDelta += 1
                    }
                    lastNextMidiCcHigh = isHigh
                }
                val prevCc = llm.slop.spirals.midi.MidiMappingManager.getCcForSpecial("Global/queuePrev")
                val prevCh = llm.slop.spirals.midi.MidiMappingManager.getChannelForSpecial("Global/queuePrev")
                if (prevCc != -1 && cc == prevCc && channel == prevCh) {
                    val valNow = llm.slop.spirals.midi.MidiEngine.getCcValue(channel, cc)
                    val isHigh = valNow > 0.5f
                    if (isHigh && !lastPrevMidiCcHigh) {
                        midiCcDelta -= 1
                    }
                    lastPrevMidiCcHigh = isHigh
                }
            }
        }

        val cvDelta = mixer.pollQueueAdvance()
        var keyDelta = 0
        if (!ImGui.getIO().wantCaptureKeyboard) {
            when (UITheme.queueKeyTrigger) {
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
                PlayQueueManager.triggerNext(mixer)
            } else {
                PlayQueueManager.triggerPrevious(mixer)
            }
        }

        pendingFontSize?.let { newSize ->
            pendingFontSize = null
            UITheme.baseSize = newSize
            UITheme.rebuildFonts(ImGui.getIO())
            imguiGl3.updateFontsTexture()
            scaleStyleFromDefault(newSize)
            UITheme.saveSettings()
            logger.info { "Font size applied: ${newSize}px" }
        }

        imguiGlfw.newFrame()
        ImGui.newFrame()
        updateUiTransparency()

        if (!UITheme.cleanModeEnabled) {
            menuBar.draw(mixer)
            if (pendingOpenSettings) {
                SettingsPanel.open()
                pendingOpenSettings = false
            }
            if (pendingOpenAudioEngineMonitor) {
                AudioEnginePanel.open()
                pendingOpenAudioEngineMonitor = false
            }

            if (popupManager.pendingOpenExitPopup) {
                ImGui.openPopup("${ProjectConfig.App.EXIT_CONFIRM_TITLE}##confirm")
                popupManager.pendingOpenExitPopup = false
            }
            if (popupManager.pendingOpenMidiWarningPopup) {
                ImGui.openPopup("No MIDI Devices Connected##midi_warning")
                popupManager.pendingOpenMidiWarningPopup = false
            }

            drawLayout(mixer, displayWidth, displayHeight)

            SettingsPanel.draw(UITheme.baseSize, displayWidth, displayHeight) { newSize ->
                applyFontSize(newSize)
            }

            AudioEnginePanel.draw(displayWidth, displayHeight)


            popupManager.drawExitPopup(mixer, displayWidth, displayHeight)
            popupManager.drawDeckConfirmPopups(mixer)
            popupManager.drawMidiWarningPopup(displayWidth, displayHeight)



            deckABrowser.draw(
                activePresetName = PatchManager.activePresetA,
                isDirty          = PatchManager.isDeckDirty(mixer.deckA, mixer),
                onSelect         = { name ->
                    if (name == null) {
                        llm.slop.spirals.patches.PatchManager.activePresetA = null
                        llm.slop.spirals.patches.PatchManager.cachedDtoA = null
                    } else {
                        loadDeckPreset(name, mixer.deckA, true)
                    }
                },
                onSaveAs = { name, tags -> saveDeckPreset(name, mixer.deckA, true, tags) }
            )
            deckBBrowser.draw(
                activePresetName = PatchManager.activePresetB,
                isDirty          = PatchManager.isDeckDirty(mixer.deckB, mixer),
                onSelect         = { name ->
                    if (name == null) {
                        llm.slop.spirals.patches.PatchManager.activePresetB = null
                        llm.slop.spirals.patches.PatchManager.cachedDtoB = null
                    } else {
                        loadDeckPreset(name, mixer.deckB, false)
                    }
                },
                onSaveAs = { name, tags -> saveDeckPreset(name, mixer.deckB, false, tags) }
            )

            deckAFileBrowser.draw { file ->
                llm.slop.spirals.patches.PatchManager.loadDeckPresetAsync(file, true)
            }
            deckBFileBrowser.draw { file ->
                llm.slop.spirals.patches.PatchManager.loadDeckPresetAsync(file, false)
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
        if (newSize != UITheme.baseSize) pendingFontSize = newSize
    }

    fun adjustFontSize(delta: Float) {
        val currentSize = UITheme.baseSize
        val targetSize = currentSize + delta
        val constrainedSize = targetSize.coerceIn(10f, 28f)
        applyFontSize(constrainedSize)
    }

    fun triggerExitFlow() {
        UITheme.cleanModeEnabled = false
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
     * pointed at the configured patches directory instead of `java.awt.FileDialog`.
     *
     * The browser is shared with the global project browser but uses a
     * separate instance per deck so both decks can have independent state.
     */
    private val deckAFileBrowser = ImGuiFileBrowser("deckAFileBrowser")
    private val deckBFileBrowser = ImGuiFileBrowser("deckBFileBrowser")

    private fun performLoadDeckPreset(isDeckA: Boolean) {
        val browser = if (isDeckA) deckAFileBrowser else deckBFileBrowser
        browser.open(
            ImGuiFileBrowser.Mode.LOAD,
            startDir = File(ProjectConfig.Paths.PATCHES_DIR).canonicalFile
        )
    }



    private fun loadDeckPreset(presetName: String, deck: Deck, isDeckA: Boolean) {
        if (presetName == "None") return
        var file = File(ProjectConfig.Paths.PATCHES_DIR, "$presetName.lsd")
        if (!file.exists()) {
            file = File(ProjectConfig.Paths.PATCHES_DIR, "$presetName.json")
        }
        if (file.exists()) {
            llm.slop.spirals.patches.PatchManager.loadDeckPresetAsync(file, isDeckA)
        }
    }

    /**
     * Save a deck preset.  [tags] are stored in `DeckPatchDto.tags` (Phase 2c).
     * Existing callers that don't supply tags preserve the current tag list by
     * reading it from the cached DTO, so an overwrite never silently strips tags.
     */
    private fun saveDeckPreset(name: String, deck: Deck, isDeckA: Boolean, tags: List<String>? = null) {
        if (name.isBlank()) return

        // Restore existing tags when overwriting unless the caller explicitly supplies new ones
        val resolvedTags = tags ?: run {
            val cached = when {
                deck === currentMixer?.deckA -> llm.slop.spirals.patches.PatchManager.cachedDtoA
                deck === currentMixer?.deckB -> llm.slop.spirals.patches.PatchManager.cachedDtoB
                deck === currentMixer?.deckC -> llm.slop.spirals.patches.PatchManager.cachedDtoC
                else -> null
            }
            cached?.tags ?: emptyList()
        }

        val dto = deck.toDto(name, resolvedTags)
        when {
            deck === currentMixer?.deckA -> {
                llm.slop.spirals.patches.PatchManager.activePresetA = name
                llm.slop.spirals.patches.PatchManager.cachedDtoA = dto
            }
            deck === currentMixer?.deckB -> {
                llm.slop.spirals.patches.PatchManager.activePresetB = name
                llm.slop.spirals.patches.PatchManager.cachedDtoB = dto
            }
            deck === currentMixer?.deckC -> {
                llm.slop.spirals.patches.PatchManager.activePresetC = name
                llm.slop.spirals.patches.PatchManager.cachedDtoC = dto
            }
        }
        val file = File(ProjectConfig.Paths.PATCHES_DIR, "$name.lsd")
        llm.slop.spirals.patches.PatchManager.saveDeckPresetAsync(file, deck, name, resolvedTags)
    }

    private fun drawLayout(mixer: Mixer, displayWidth: Float, displayHeight: Float) {
        val menuBarH = 32f
        val contentH = displayHeight - menuBarH
        val noDecorate = ImGuiWindowFlags.NoResize or
                         ImGuiWindowFlags.NoMove or
                         ImGuiWindowFlags.NoCollapse

        drawAssetManagementLayout(displayWidth, displayHeight, menuBarH, contentH, noDecorate)
    }

    private fun drawAssetManagementLayout(displayWidth: Float, displayHeight: Float, menuBarH: Float, contentH: Float, noDecorate: Int) {
        val layout = AppMainLayoutCalculator.calculate(displayWidth, contentH, UITheme.assetBrowserMode)
        val libraryW = layout.libraryWidth
        val rightW = layout.rightWidth
        val assetBrowserH = layout.assetBrowserHeight

        if (UITheme.assetBrowserMode != UITheme.AssetBrowserMode.FULL) {
            val topH = contentH - assetBrowserH
            val leftW = layout.patchGridWidth

            ImGui.setNextWindowPos(0f, menuBarH)
            ImGui.setNextWindowSize(leftW, topH)
            if (ImGui.begin("Patch Grid", noDecorate)) {
                PatchGridPanel.draw(currentMixer!!, patchState)
            }
            ImGui.end()

            val middleW = layout.cellConfigWidth
            ImGui.setNextWindowPos(leftW, menuBarH)
            ImGui.setNextWindowSize(middleW, topH)
            if (ImGui.begin("Cell Config", noDecorate)) {
                CellConfigPanel.draw(patchState, currentMixer!!)
            }
            ImGui.end()
        }

        // Asset Browser
        val assetBrowserPosH = if (UITheme.assetBrowserMode == UITheme.AssetBrowserMode.FULL) menuBarH else (menuBarH + contentH - assetBrowserH)
        ImGui.setNextWindowPos(0f, assetBrowserPosH)
        ImGui.setNextWindowSize(libraryW, assetBrowserH)
        val flags = (if (UITheme.assetBrowserMode == UITheme.AssetBrowserMode.HIDE) noDecorate or ImGuiWindowFlags.NoScrollbar else noDecorate) or
                ImGuiWindowFlags.NoTitleBar or ImGuiWindowFlags.MenuBar
        if (ImGui.begin("Asset Browser", flags)) {
            AssetBrowserPanel.draw(libraryW, assetBrowserH, currentMixer!!)
        }
        ImGui.end()

        // Right: Mixer / Monitor (30% width)
        ImGui.setNextWindowPos(libraryW, menuBarH)
        ImGui.setNextWindowSize(rightW, contentH)
        val noTitleDecorate = noDecorate or imgui.flag.ImGuiWindowFlags.NoTitleBar
        if (ImGui.begin("Mixer / Monitor", noTitleDecorate)) {
            drawMixerMonitor(currentMixer!!)
        }
        ImGui.end()
    }

    private fun drawMixerMonitor(mixer: Mixer) {
        mixerMonitorPanel.draw(mixer)
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
        style.setWindowPadding(10f * scale, 8f * scale)
        style.setFramePadding(7f * scale, 4f * scale)
        style.setCellPadding(8f * scale, 5f * scale)
        style.setItemSpacing(8f * scale, 5f * scale)
        style.setItemInnerSpacing(6f * scale, 4f * scale)
        style.setWindowRounding(0f)
        style.setChildRounding(2f * scale)
        style.setPopupRounding(2f * scale)
        style.setFrameRounding(2f * scale)
        style.setGrabRounding(2f * scale)
        style.setScrollbarRounding(2f * scale)
        style.setWindowBorderSize(1f)
        style.setChildBorderSize(1f)
        style.setFrameBorderSize(0f)
        style.setScrollbarSize(12f * scale)
        UITheme.applyStyleColors(UITheme.backgroundVideoEnabled)
    }

    fun dispose() {
        defaultStyle.destroy()
        imguiGl3.dispose()
        imguiGlfw.dispose()
        ImGui.destroyContext()
    }
}
