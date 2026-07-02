package llm.slop.spirals.ui

import imgui.ImGui
import imgui.flag.ImGuiCol
import imgui.flag.ImGuiConfigFlags
import imgui.flag.ImGuiWindowFlags
import imgui.type.ImInt
import imgui.type.ImString
import java.io.File
import llm.slop.spirals.rendering.Deck
import llm.slop.spirals.rendering.Mandala
import llm.slop.spirals.rendering.MandalaLibrary
import llm.slop.spirals.rendering.MandalaRatio





import llm.slop.spirals.rendering.Mixer
import kotlin.math.roundToInt
import mu.KotlinLogging
import org.lwjgl.opengl.GL33.*
import imgui.gl3.ImGuiImplGl3
import imgui.glfw.ImGuiImplGlfw
import llm.slop.spirals.parameters.ModulatableParameter
import llm.slop.spirals.models.toDto
import llm.slop.spirals.models.applyDto

/**
 * Manages the ImGui overlay for desktop control.
 */
class UIManager(private val windowHandle: Long) {
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

        val style = ImGui.getStyle()
        if (enabled) {
            // Semi-transparent style for a cool VJ look
            style.setColor(ImGuiCol.WindowBg, 0.06f, 0.06f, 0.06f, 0.75f)
            style.setColor(ImGuiCol.TitleBg, 0.04f, 0.04f, 0.04f, 0.75f)
            style.setColor(ImGuiCol.TitleBgActive, 0.16f, 0.16f, 0.16f, 0.75f)
            style.setColor(ImGuiCol.MenuBarBg, 0.14f, 0.14f, 0.14f, 0.75f)
            style.setColor(ImGuiCol.PopupBg, 0.08f, 0.08f, 0.08f, 1.00f)
        } else {
            // Completely opaque colors
            style.setColor(ImGuiCol.WindowBg, 0.06f, 0.06f, 0.06f, 1.00f)
            style.setColor(ImGuiCol.TitleBg, 0.04f, 0.04f, 0.04f, 1.00f)
            style.setColor(ImGuiCol.TitleBgActive, 0.16f, 0.16f, 0.16f, 1.00f)
            style.setColor(ImGuiCol.MenuBarBg, 0.14f, 0.14f, 0.14f, 1.00f)
            style.setColor(ImGuiCol.PopupBg, 0.08f, 0.08f, 0.08f, 1.00f)
        }
    }

    // Patch grid state shared between PatchGridPanel and CellConfigPanel
    private val patchState = PatchGridState()

    // Phase 1 — ImGui-native file browser (replaces java.awt.FileDialog)
    private val fileBrowser = ImGuiFileBrowser("globalFileBrowser")
    /** Last directory the user navigated to in the Load browser. */
    private var lastLoadDir: File = File("presets/global").canonicalFile

    // Phase 2 — Deck preset browsers (replaces flat ImGui.combo)
    private val deckABrowser = DeckPresetBrowser("A")
    private val deckBBrowser = DeckPresetBrowser("B")

    // Phase 3b — Setlist panel
    /** Pending file chosen from the setlist while isDirty — resolved via confirm popup. */
    private var pendingSetlistFile: File? = null
    private var pendingOpenSetlist = false

    private var lastNextMidiCcHigh = false
    private var lastPrevMidiCcHigh = false

    private var currentGlobalPatchFile: File? = null
    private var currentMixer: Mixer? = null

    private var lastWindowTitle: String? = null

    private enum class PendingProjectAction {
        NONE, NEW, LOAD, LOAD_SETLIST, EXIT
    }
    private var pendingProjectAction = PendingProjectAction.NONE
    private var pendingOpenConfirmPopup = false
    private var pendingOpenExitPopup = false
    private var pendingOpenMidiWarningPopup = false

    private enum class PendingDeckAction {
        NONE, NEW, LOAD_FILE, LOAD_PRESET
    }
    
    private var pendingDeckActionA = PendingDeckAction.NONE
    private var pendingDeckActionB = PendingDeckAction.NONE
    private var pendingDeckTargetPresetA: String? = null
    private var pendingDeckTargetPresetB: String? = null


    init {
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
                if (isDeckA) pendingDeckActionA = PendingDeckAction.NEW
                else         pendingDeckActionB = PendingDeckAction.NEW
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
                if (isDeckA) pendingDeckActionA = PendingDeckAction.LOAD_FILE
                else         pendingDeckActionB = PendingDeckAction.LOAD_FILE
            } else {
                performLoadDeckPreset(isDeckA)
            }
        },
        onSaveDeck = { name, deck, isDeckA -> saveDeckPreset(name, deck, isDeckA) },
        onDeleteDeck = { isDeckA ->
            if (isDeckA) {
                llm.slop.spirals.patches.PatchManager.activePresetA = null
                llm.slop.spirals.patches.PatchManager.cachedDtoA = null
            } else {
                llm.slop.spirals.patches.PatchManager.activePresetB = null
                llm.slop.spirals.patches.PatchManager.cachedDtoB = null
            }
        }
    )

    private val mixerMonitorPanel = MixerMonitorPanel(
        patchState = patchState,
        advanceSetlist = { delta -> advanceSetlist(delta) },
        drawDeckPresetDropdown = { label, deck, isDeckA, width -> deckControlPanel.drawDeckPresetDropdown(label, deck, isDeckA, width) },
        drawDeckControls = { label, deck, width, height, isDeckA -> deckControlPanel.drawDeckControls(label, deck, width, height, isDeckA) }
    )

    fun render(mixer: Mixer, displayWidth: Float, displayHeight: Float) {
        currentMixer = mixer

        // Update window title dynamically with project name and dirty status
        val projectName = currentGlobalPatchFile?.nameWithoutExtension ?: "Untitled"
        val isDirty = llm.slop.spirals.patches.PatchManager.isGlobalPatchDirty(mixer)
        val title = "Spirals Desktop - $projectName${if (isDirty) "*" else ""}"
        if (title != lastWindowTitle) {
            org.lwjgl.glfw.GLFW.glfwSetWindowTitle(windowHandle, title)
            lastWindowTitle = title
        }

        // Drain all MIDI events queued by the MIDI receiver thread.
        // MidiEngine.receivedCcEvents is the canonical thread-safe delivery queue;
        // we process all state mutations here on the render thread.
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
                        // Clear existing MIDI modulators for this parameter
                        val existingMods = target.param.modulators.filter {
                            it.sourceId.startsWith("midi_cc_")
                        }
                        target.param.modulators.removeAll(existingMods)

                        // Create new MIDI modulator directly
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
                val nextCc = llm.slop.spirals.midi.MidiMappingManager.getCcForSpecial("Global/setlistNext")
                val nextCh = llm.slop.spirals.midi.MidiMappingManager.getChannelForSpecial("Global/setlistNext")
                if (nextCc != -1 && cc == nextCc && channel == nextCh) {
                    val valNow = llm.slop.spirals.midi.MidiEngine.getCcValue(channel, cc)
                    val isHigh = valNow > 0.5f
                    if (isHigh && !lastNextMidiCcHigh) {
                        midiCcDelta += 1
                    }
                    lastNextMidiCcHigh = isHigh
                }
                val prevCc = llm.slop.spirals.midi.MidiMappingManager.getCcForSpecial("Global/setlistPrev")
                val prevCh = llm.slop.spirals.midi.MidiMappingManager.getChannelForSpecial("Global/setlistPrev")
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

        val cvDelta = mixer.pollSetlistAdvance()
        var keyDelta = 0
        if (!ImGui.getIO().wantCaptureKeyboard) {
            when (UITheme.setlistKeyTrigger) {
                UITheme.SetlistKeyTrigger.ARROWS -> {
                    if (ImGui.isKeyPressed(ImGui.getKeyIndex(imgui.flag.ImGuiKey.LeftArrow))) keyDelta -= 1
                    if (ImGui.isKeyPressed(ImGui.getKeyIndex(imgui.flag.ImGuiKey.RightArrow))) keyDelta += 1
                }
                UITheme.SetlistKeyTrigger.PAGE_UP_DOWN -> {
                    if (ImGui.isKeyPressed(ImGui.getKeyIndex(imgui.flag.ImGuiKey.PageUp))) keyDelta -= 1
                    if (ImGui.isKeyPressed(ImGui.getKeyIndex(imgui.flag.ImGuiKey.PageDown))) keyDelta += 1
                }
                UITheme.SetlistKeyTrigger.SPACE_BACKSPACE -> {
                    if (ImGui.isKeyPressed(ImGui.getKeyIndex(imgui.flag.ImGuiKey.Backspace))) keyDelta -= 1
                    if (ImGui.isKeyPressed(ImGui.getKeyIndex(imgui.flag.ImGuiKey.Space))) keyDelta += 1
                }
                else -> {}
            }
        }
        val totalDelta = midiCcDelta + cvDelta + keyDelta
        if (totalDelta != 0) {
            advanceSetlist(totalDelta)
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
            drawMenuBar(mixer)
            // openPopup must be called at root ID-stack level — not inside the menu bar.
            if (pendingOpenSettings) {
                SettingsPanel.open()
                pendingOpenSettings = false
            }
            if (pendingOpenAudioEngineMonitor) {
                AudioEnginePanel.open()
                pendingOpenAudioEngineMonitor = false
            }
            if (pendingOpenConfirmPopup) {
                ImGui.openPopup("Save Changes?##confirm")
                pendingOpenConfirmPopup = false
            }
            if (pendingOpenExitPopup) {
                ImGui.openPopup("Exit Spirals?##confirm")
                pendingOpenExitPopup = false
            }
            if (pendingOpenMidiWarningPopup) {
                ImGui.openPopup("No MIDI Devices Connected##midi_warning")
                pendingOpenMidiWarningPopup = false
            }
            // Phase 3b — open setlist panel
            if (pendingOpenSetlist) {
                SetlistPanel.open()
                pendingOpenSetlist = false
            }
            drawLayout(mixer, displayWidth, displayHeight)

            // Settings modal — drawn outside any docked window so it floats freely.
            SettingsPanel.draw(UITheme.baseSize, displayWidth, displayHeight, { newSize ->
                applyFontSize(newSize)
            }, {
                // Layout is now tabbed, no-op for autocollapse setting
            })

            // Audio Engine Monitor modal — drawn outside any docked window so it floats freely.
            AudioEnginePanel.draw(displayWidth, displayHeight)

            drawConfirmPopup(mixer, displayWidth, displayHeight)
            drawExitPopup(mixer, displayWidth, displayHeight)
            drawDeckConfirmPopups(mixer.deckA, mixer.deckB)
            drawMidiWarningPopup(displayWidth, displayHeight)

            // Phase 1 — Global project file browser modal
            drawGlobalFileBrowser(mixer)

            // Phase 2 — Deck preset browsers (search + tags + save-as)
            deckABrowser.draw(
                activePresetName = llm.slop.spirals.patches.PatchManager.activePresetA,
                isDirty          = llm.slop.spirals.patches.PatchManager.isDeckDirty(mixer.deckA, true),
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
                activePresetName = llm.slop.spirals.patches.PatchManager.activePresetB,
                isDirty          = llm.slop.spirals.patches.PatchManager.isDeckDirty(mixer.deckB, false),
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

            // Phase 2 — Deck file-load browsers (for "Load File…" menu item)
            deckAFileBrowser.draw { file ->
                llm.slop.spirals.patches.PatchManager.loadDeckPresetAsync(file, true)
            }
            deckBFileBrowser.draw { file ->
                llm.slop.spirals.patches.PatchManager.loadDeckPresetAsync(file, false)
            }

            // Phase 3b — Setlist panel modal
            SetlistPanel.draw(
                currentFile  = currentGlobalPatchFile,
                isDirty      = llm.slop.spirals.patches.PatchManager.isGlobalPatchDirty(mixer),
                onLoad       = { file -> performLoadFromSetlist(file) },
                onLoadDirty  = { file ->
                    pendingSetlistFile = file
                    pendingProjectAction = PendingProjectAction.LOAD_SETLIST
                    pendingOpenConfirmPopup = true
                }
            )
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
        pendingOpenExitPopup = true
    }

    private fun loadGlobalPatchWithDialog() {
        pendingProjectAction = PendingProjectAction.LOAD
        // Phase 1: open the ImGui file browser instead of java.awt.FileDialog
        val lastDir = currentGlobalPatchFile?.parentFile
            ?: File("presets/global").canonicalFile
        fileBrowser.open(ImGuiFileBrowser.Mode.LOAD, startDir = lastDir)
    }

    private fun saveGlobalPatch(mixer: Mixer, forceAs: Boolean): Boolean {
        if (!forceAs && currentGlobalPatchFile != null) {
            // Fast-save: no dialog needed
            val file = currentGlobalPatchFile!!
            llm.slop.spirals.patches.PatchManager.saveGlobalPatchAsync(file, mixer, file.nameWithoutExtension)
            return true
        }
        // Phase 1: open the ImGui file browser for Save / Save As
        val initialName = currentGlobalPatchFile?.nameWithoutExtension ?: "project"
        fileBrowser.open(
            ImGuiFileBrowser.Mode.SAVE,
            startDir = File("presets/global").canonicalFile,
            initialName = initialName
        )
        // Return false here — the actual save happens in the browser's onConfirm callback
        return false
    }

    private fun performNewProject(mixer: Mixer) {
        llm.slop.spirals.patches.PatchManager.resetToDefault(mixer)
        currentGlobalPatchFile = null
    }

    private fun performLoadProject() {
        loadGlobalPatchWithDialog()
    }

    /** Called when the setlist panel fires onLoad directly (no dirty guard needed). */
    private fun performLoadFromSetlist(file: File) {
        currentGlobalPatchFile = file
        llm.slop.spirals.patches.PatchManager.loadGlobalPatchAsync(file)
    }

    fun advanceSetlist(delta: Int) {
        val mixer = currentMixer ?: return
        val targetFile = SetlistPanel.getFileOffset(currentGlobalPatchFile, delta) ?: return
        if (targetFile.canonicalPath == currentGlobalPatchFile?.canonicalPath) return

        logger.info { "Advancing setlist by $delta to file: ${targetFile.name}" }

        val isDirty = llm.slop.spirals.patches.PatchManager.isGlobalPatchDirty(mixer)
        if (!isDirty) {
            performLoadFromSetlist(targetFile)
            return
        }

        when (UITheme.setlistTransitionBehavior) {
            UITheme.SetlistTransitionBehavior.PROMPT -> {
                pendingSetlistFile = targetFile
                pendingProjectAction = PendingProjectAction.LOAD_SETLIST
                pendingOpenConfirmPopup = true
            }
            UITheme.SetlistTransitionBehavior.AUTO_DISCARD -> {
                performLoadFromSetlist(targetFile)
            }
            UITheme.SetlistTransitionBehavior.AUTO_SAVE -> {
                val currentFile = currentGlobalPatchFile
                if (currentFile == null) {
                    // Autosave with timestamped filename: New-yyMMdd-HH.mm.ss
                    val formatter = java.time.format.DateTimeFormatter.ofPattern("yyMMdd-HH.mm.ss")
                    val filename = "New-${java.time.LocalDateTime.now().format(formatter)}.json"
                    val dir = File("presets/global")
                    if (!dir.exists()) dir.mkdirs()
                    val file = File(dir, filename)
                    logger.info { "Autosaving untitled patch as $filename" }
                    llm.slop.spirals.patches.PatchManager.saveGlobalPatchAsync(file, mixer, file.nameWithoutExtension)
                    currentGlobalPatchFile = file
                } else {
                    logger.info { "Autosaving current patch: ${currentFile.name}" }
                    llm.slop.spirals.patches.PatchManager.saveGlobalPatchAsync(currentFile, mixer, currentFile.nameWithoutExtension)
                }
                performLoadFromSetlist(targetFile)
            }
        }
    }

    /**
     * Phase 1 — draws the global project file browser modal every frame.
     * Must be called at root ID-stack level (outside any child window).
     *
     * The browser's onConfirm callback determines whether this was a LOAD or
     * SAVE by inspecting [pendingProjectAction]:
     * - LOAD / NONE  → load the chosen file
     * - NEW          → save to the chosen file, then reset the project
     *
     * For a plain Save-As (no pending action), [pendingProjectAction] is NONE
     * and the browser was opened in SAVE mode, so we just save.
     */
    private fun drawGlobalFileBrowser(mixer: Mixer) {
        fileBrowser.draw { file ->
            val name = file.nameWithoutExtension
            when (pendingProjectAction) {
                PendingProjectAction.LOAD -> {
                    if (fileBrowser.mode == ImGuiFileBrowser.Mode.SAVE) {
                        // User named a file to save the current changes first.
                        // Save the project to the file, then open the LOAD dialog.
                        llm.slop.spirals.patches.PatchManager.saveGlobalPatchAsync(file, mixer, name)
                        currentGlobalPatchFile = file
                        // Now trigger the LOAD dialog
                        loadGlobalPatchWithDialog()
                    } else {
                        // User chose a file to load
                        lastLoadDir = file.parentFile?.canonicalFile ?: lastLoadDir
                        currentGlobalPatchFile = file
                        llm.slop.spirals.patches.PatchManager.loadGlobalPatchAsync(file)
                        pendingProjectAction = PendingProjectAction.NONE
                    }
                }
                PendingProjectAction.NEW -> {
                    // Save-then-new: save first, then reset
                    currentGlobalPatchFile = file
                    llm.slop.spirals.patches.PatchManager.saveGlobalPatchAsync(file, mixer, name)
                    performNewProject(mixer)
                    pendingProjectAction = PendingProjectAction.NONE
                }
                PendingProjectAction.LOAD_SETLIST -> {
                    // Save-then-load-setlist: save first, then load the setlist file
                    currentGlobalPatchFile = file
                    llm.slop.spirals.patches.PatchManager.saveGlobalPatchAsync(file, mixer, name)
                    pendingSetlistFile?.let { performLoadFromSetlist(it) }
                    pendingProjectAction = PendingProjectAction.NONE
                }
                PendingProjectAction.NONE -> {
                    // Plain Save / Save As — browser was opened in SAVE mode
                    currentGlobalPatchFile = file
                    llm.slop.spirals.patches.PatchManager.saveGlobalPatchAsync(file, mixer, name)
                }
                PendingProjectAction.EXIT -> {
                    currentGlobalPatchFile = file
                    llm.slop.spirals.patches.PatchManager.saveGlobalPatchAsync(file, mixer, name)
                    pendingProjectAction = PendingProjectAction.NONE
                    org.lwjgl.glfw.GLFW.glfwSetWindowShouldClose(windowHandle, true)
                }
            }
        }
    }

    private fun drawConfirmPopup(mixer: Mixer, displayW: Float, displayH: Float) {
        ImGui.setNextWindowPos(
            displayW * 0.5f, displayH * 0.5f,
            imgui.flag.ImGuiCond.Appearing, 0.5f, 0.5f
        )
        
        val flags = imgui.flag.ImGuiWindowFlags.AlwaysAutoResize or
                    imgui.flag.ImGuiWindowFlags.NoMove            or
                    imgui.flag.ImGuiWindowFlags.NoCollapse

        if (ImGui.beginPopupModal("Save Changes?##confirm", flags)) {
            ImGui.text("You have unsaved changes. Do you want to save them before proceeding?")
            ImGui.spacing()
            ImGui.separator()
            ImGui.spacing()
            
            if (ImGui.button("Save", 80f, 0f)) {
                val saved = saveGlobalPatch(mixer, false)
                if (saved) {
                    // Fast-save completed synchronously — execute the pending action now.
                    when (pendingProjectAction) {
                        PendingProjectAction.NEW  -> performNewProject(mixer)
                        PendingProjectAction.LOAD -> performLoadProject()
                        PendingProjectAction.LOAD_SETLIST -> pendingSetlistFile?.let { performLoadFromSetlist(it) }
                        else -> {}
                    }
                    pendingProjectAction = PendingProjectAction.NONE
                }
                // If saved == false the file browser was opened; pendingProjectAction
                // stays set so drawGlobalFileBrowser can execute it on confirm.
                ImGui.closeCurrentPopup()
            }
            ImGui.sameLine()
            if (ImGui.button("Discard", 80f, 0f)) {
                when (pendingProjectAction) {
                    PendingProjectAction.NEW -> performNewProject(mixer)
                    PendingProjectAction.LOAD -> performLoadProject()
                    PendingProjectAction.LOAD_SETLIST -> pendingSetlistFile?.let { performLoadFromSetlist(it) }
                    else -> {}
                }
                pendingProjectAction = PendingProjectAction.NONE
                ImGui.closeCurrentPopup()
            }
            ImGui.sameLine()
            if (ImGui.button("Cancel", 80f, 0f)) {
                pendingProjectAction = PendingProjectAction.NONE
                ImGui.closeCurrentPopup()
            }
            ImGui.endPopup()
        }
    }

    private fun drawExitPopup(mixer: Mixer, displayW: Float, displayH: Float) {
        ImGui.setNextWindowPos(
            displayW * 0.5f, displayH * 0.5f,
            imgui.flag.ImGuiCond.Appearing, 0.5f, 0.5f
        )
        
        val flags = imgui.flag.ImGuiWindowFlags.AlwaysAutoResize or
                    imgui.flag.ImGuiWindowFlags.NoMove            or
                    imgui.flag.ImGuiWindowFlags.NoCollapse

        val isDirty = llm.slop.spirals.patches.PatchManager.isGlobalPatchDirty(mixer)

        if (ImGui.beginPopupModal("Exit Spirals?##confirm", flags)) {
            if (isDirty) {
                ImGui.text("You have unsaved changes. Do you want to save them before exiting?")
                ImGui.spacing()
                ImGui.separator()
                ImGui.spacing()
                
                if (ImGui.button("Save & Exit", 120f, 0f)) {
                    pendingProjectAction = PendingProjectAction.EXIT
                    val saved = saveGlobalPatch(mixer, false)
                    if (saved) {
                        // Fast-save completed synchronously — exit now!
                        pendingProjectAction = PendingProjectAction.NONE
                        org.lwjgl.glfw.GLFW.glfwSetWindowShouldClose(windowHandle, true)
                    }
                    ImGui.closeCurrentPopup()
                }
                ImGui.sameLine()
                if (ImGui.button("Discard & Exit", 120f, 0f)) {
                    pendingProjectAction = PendingProjectAction.NONE
                    org.lwjgl.glfw.GLFW.glfwSetWindowShouldClose(windowHandle, true)
                    ImGui.closeCurrentPopup()
                }
                ImGui.sameLine()
                if (ImGui.button("Cancel", 120f, 0f)) {
                    pendingProjectAction = PendingProjectAction.NONE
                    ImGui.closeCurrentPopup()
                }
            } else {
                ImGui.text("Are you sure you want to exit?")
                ImGui.text("Accidentally exiting during a show would be bad!")
                ImGui.spacing()
                ImGui.separator()
                ImGui.spacing()

                if (ImGui.button("Exit", 120f, 0f)) {
                    org.lwjgl.glfw.GLFW.glfwSetWindowShouldClose(windowHandle, true)
                    ImGui.closeCurrentPopup()
                }
                ImGui.sameLine()
                if (ImGui.button("Cancel", 120f, 0f)) {
                    ImGui.closeCurrentPopup()
                }
            }
            ImGui.endPopup()
        }
    }

    private fun drawMidiWarningPopup(displayW: Float, displayH: Float) {
        ImGui.setNextWindowPos(
            displayW * 0.5f, displayH * 0.5f,
            imgui.flag.ImGuiCond.Appearing, 0.5f, 0.5f
        )
        
        val flags = imgui.flag.ImGuiWindowFlags.AlwaysAutoResize or
                    imgui.flag.ImGuiWindowFlags.NoMove            or
                    imgui.flag.ImGuiWindowFlags.NoCollapse

        if (ImGui.beginPopupModal("No MIDI Devices Connected##midi_warning", flags)) {
            ImGui.textWrapped("There are currently no MIDI input devices detected by the system.")
            ImGui.spacing()
            ImGui.textWrapped("You can still map parameters by clicking them, but you will need")
            ImGui.textWrapped("to plug in a MIDI hardware controller to send actual control values.")
            ImGui.spacing()
            ImGui.pushStyleColor(imgui.flag.ImGuiCol.Text, 1.0f, 0.6f, 0.0f, 1.0f)
            ImGui.textWrapped("A background watchdog is active. Plugging in a MIDI controller")
            ImGui.textWrapped("will automatically activate it within a few seconds.")
            ImGui.popStyleColor()
            ImGui.spacing()
            ImGui.separator()
            ImGui.spacing()
            
            if (ImGui.button("OK", ImGui.getContentRegionAvailX(), 0f)) {
                ImGui.closeCurrentPopup()
            }
            ImGui.endPopup()
        }
    }

    private fun drawDeckConfirmPopups(deckA: Deck, deckB: Deck) {
        // Deck A Confirmation
        if (pendingDeckActionA != PendingDeckAction.NONE) {
            ImGui.openPopup("Save Changes Deck A?##confirm")
        }
        if (ImGui.beginPopupModal("Save Changes Deck A?##confirm", imgui.flag.ImGuiWindowFlags.AlwaysAutoResize)) {
            ImGui.text("You have unsaved changes in Deck A. Save before proceeding?")
            ImGui.spacing()
            if (ImGui.button("Save", 80f, 0f)) {
                val activeName = llm.slop.spirals.patches.PatchManager.activePresetA
                if (activeName != null) {
                    saveDeckPreset(activeName, deckA, true)
                } else {
                    // Fallback to saving as a generic name if it was "None"
                    saveDeckPreset("Untitled_A", deckA, true)
                }
                executePendingDeckAction(deckA, true)
                ImGui.closeCurrentPopup()
            }
            ImGui.sameLine()
            if (ImGui.button("Discard", 80f, 0f)) {
                executePendingDeckAction(deckA, true)
                ImGui.closeCurrentPopup()
            }
            ImGui.sameLine()
            if (ImGui.button("Cancel", 80f, 0f)) {
                pendingDeckActionA = PendingDeckAction.NONE
                ImGui.closeCurrentPopup()
            }
            ImGui.endPopup()
        }

        // Deck B Confirmation
        if (pendingDeckActionB != PendingDeckAction.NONE) {
            ImGui.openPopup("Save Changes Deck B?##confirm")
        }
        
        if (ImGui.beginPopupModal("Save Changes Deck B?##confirm", imgui.flag.ImGuiWindowFlags.AlwaysAutoResize)) {
            ImGui.text("You have unsaved changes in Deck B. Save before proceeding?")
            ImGui.spacing()
            if (ImGui.button("Save", 80f, 0f)) {
                val activeName = llm.slop.spirals.patches.PatchManager.activePresetB
                if (activeName != null) {
                    saveDeckPreset(activeName, deckB, false)
                } else {
                    saveDeckPreset("Untitled_B", deckB, false)
                }
                executePendingDeckAction(deckB, false)
                ImGui.closeCurrentPopup()
            }
            ImGui.sameLine()
            if (ImGui.button("Discard", 80f, 0f)) {
                executePendingDeckAction(deckB, false)
                ImGui.closeCurrentPopup()
            }
            ImGui.sameLine()
            if (ImGui.button("Cancel", 80f, 0f)) {
                pendingDeckActionB = PendingDeckAction.NONE
                ImGui.closeCurrentPopup()
            }
            ImGui.endPopup()
        }
    }

    private fun executePendingDeckAction(deck: Deck, isDeckA: Boolean) {
        val action = if (isDeckA) pendingDeckActionA else pendingDeckActionB
        val targetPreset = if (isDeckA) pendingDeckTargetPresetA else pendingDeckTargetPresetB

        when (action) {
            PendingDeckAction.NEW -> {
                deck.reset()
                if (isDeckA) {
                    llm.slop.spirals.patches.PatchManager.activePresetA = null
                    llm.slop.spirals.patches.PatchManager.cachedDtoA = null
                } else {
                    llm.slop.spirals.patches.PatchManager.activePresetB = null
                    llm.slop.spirals.patches.PatchManager.cachedDtoB = null
                }
            }
            PendingDeckAction.LOAD_FILE -> {
                performLoadDeckPreset(isDeckA)
            }
            PendingDeckAction.LOAD_PRESET -> {
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
            PendingDeckAction.NONE -> {}
        }

        if (isDeckA) {
            pendingDeckActionA = PendingDeckAction.NONE
            pendingDeckTargetPresetA = null
        } else {
            pendingDeckActionB = PendingDeckAction.NONE
            pendingDeckTargetPresetB = null
        }
    }

    /**
     * Phase 2: deck preset "Load File…" now opens the ImGui file browser
     * pointed at `presets/decks/` instead of `java.awt.FileDialog`.
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
            startDir = File("presets/decks").canonicalFile
        )
    }

    private fun drawMenuBar(mixer: Mixer) {
        if (ImGui.beginMainMenuBar()) {
            if (ImGui.beginMenu("File")) {
                if (ImGui.menuItem("New Project")) {
                    if (llm.slop.spirals.patches.PatchManager.isGlobalPatchDirty(mixer)) {
                        pendingProjectAction = PendingProjectAction.NEW
                        pendingOpenConfirmPopup = true
                    } else {
                        performNewProject(mixer)
                    }
                }
                if (ImGui.menuItem("Load Project...")) {
                    if (llm.slop.spirals.patches.PatchManager.isGlobalPatchDirty(mixer)) {
                        pendingProjectAction = PendingProjectAction.LOAD
                        pendingOpenConfirmPopup = true
                    } else {
                        performLoadProject()
                    }
                }
                if (ImGui.menuItem("Save Project")) {
                    saveGlobalPatch(mixer, false)
                }
                if (ImGui.menuItem("Save Project As...")) {
                    saveGlobalPatch(mixer, true)
                }
                ImGui.separator()
                if (ImGui.menuItem("Exit")) {
                    logger.info { "Exit clicked" }
                    triggerExitFlow()
                }
                ImGui.endMenu()
            }

            // MIDI Map toggle button
            val isMidiLearn = patchState.isMidiLearnMode
            if (isMidiLearn) {
                ImGui.pushStyleColor(imgui.flag.ImGuiCol.Text, 1.0f, 0.6f, 0.0f, 1.0f) // orange
            }
            if (ImGui.menuItem("MIDI Map", "", isMidiLearn)) {
                patchState.isMidiLearnMode = !isMidiLearn
                if (!patchState.isMidiLearnMode) {
                    patchState.midiLearnTarget = null
                } else {
                    if (llm.slop.spirals.midi.MidiEngine.getActiveDeviceCount() == 0) {
                        pendingOpenMidiWarningPopup = true
                    }
                }
            }
            if (isMidiLearn) {
                ImGui.popStyleColor()
            }

            // Use menuItem (not beginMenu) so there's no dropdown — clicking
            // sets a flag that triggers openPopup after endMainMenuBar.
            if (ImGui.menuItem("Settings")) {
                pendingOpenSettings = true
            }

            val isAudioActive = llm.slop.spirals.audio.AudioEngine.isActive()
            if (!isAudioActive && UITheme.audioEngineEnabled) {
                ImGui.pushStyleColor(imgui.flag.ImGuiCol.Text, 1.0f, 0.6f, 0.0f, 1.0f) // orange warning
            }
            val audioEngineLabel = if (!isAudioActive && UITheme.audioEngineEnabled) "Audio Engine ⚠" else "Audio Engine"
            if (ImGui.menuItem(audioEngineLabel)) {
                pendingOpenAudioEngineMonitor = true
            }
            if (!isAudioActive && UITheme.audioEngineEnabled) {
                ImGui.popStyleColor()
            }

            // Phase 3b — Setlist quick-load panel
            if (ImGui.menuItem("Setlist")) {
                pendingOpenSetlist = true
            }

            if (ImGui.beginMenu("Help")) {
                if (ImGui.menuItem("Documentation")) {
                    DocManager.openDocumentation()
                }
                ImGui.endMenu()
            }

            val tooltipsEnabled = UITheme.tooltipsEnabled
            if (tooltipsEnabled) {
                ImGui.pushStyleColor(imgui.flag.ImGuiCol.Text, 0.2f, 0.8f, 0.2f, 1.0f) // green
            } else {
                ImGui.pushStyleColor(imgui.flag.ImGuiCol.Text, 0.8f, 0.2f, 0.2f, 1.0f) // red
            }
            if (ImGui.menuItem("Tooltips", "", tooltipsEnabled)) {
                UITheme.tooltipsEnabled = !tooltipsEnabled
                UITheme.saveSettings()
            }
            ImGui.popStyleColor()

            ImGui.endMainMenuBar()
        }
    }

    private fun loadDeckPreset(presetName: String, deck: Deck, isDeckA: Boolean) {
        if (presetName == "None") return
        val file = File("presets/decks/$presetName.json")
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

        // Preserve existing tags when overwriting unless the caller explicitly supplies new ones
        val resolvedTags = tags ?: run {
            val cached = if (isDeckA) llm.slop.spirals.patches.PatchManager.cachedDtoA
                         else        llm.slop.spirals.patches.PatchManager.cachedDtoB
            cached?.tags ?: emptyList()
        }

        val dto = deck.toDto(name, resolvedTags)
        if (isDeckA) {
            llm.slop.spirals.patches.PatchManager.activePresetA = name
            llm.slop.spirals.patches.PatchManager.cachedDtoA = dto
        } else {
            llm.slop.spirals.patches.PatchManager.activePresetB = name
            llm.slop.spirals.patches.PatchManager.cachedDtoB = dto
        }
        val file = File("presets/decks/$name.json")
        llm.slop.spirals.patches.PatchManager.saveDeckPresetAsync(file, deck, name, resolvedTags)
    }

    private fun drawLayout(mixer: Mixer, displayWidth: Float, displayHeight: Float) {
        val menuBarH = 32f
        val contentH = displayHeight - menuBarH
        val noDecorate = ImGuiWindowFlags.NoResize or
                         ImGuiWindowFlags.NoMove or
                         ImGuiWindowFlags.NoCollapse

        // Left: Patch Grid (30% width, full content height)
        val leftW = displayWidth * 0.3f
        ImGui.setNextWindowPos(0f, menuBarH)
        ImGui.setNextWindowSize(leftW, contentH)
        if (ImGui.begin("Patch Grid", noDecorate)) {
            PatchGridPanel.draw(mixer, patchState)
        }
        ImGui.end()

        // Middle: Cell Config (40% width, full content height)
        val middleW = displayWidth * 0.4f
        ImGui.setNextWindowPos(leftW, menuBarH)
        ImGui.setNextWindowSize(middleW, contentH)
        if (ImGui.begin("Cell Config", noDecorate)) {
            CellConfigPanel.draw(patchState, mixer)
        }
        ImGui.end()

        // Right: Mixer / Monitor (30% width, full content height)
        val rightW = displayWidth - leftW - middleW
        ImGui.setNextWindowPos(leftW + middleW, menuBarH)
        ImGui.setNextWindowSize(rightW, contentH)
        val noTitleDecorate = noDecorate or imgui.flag.ImGuiWindowFlags.NoTitleBar
        if (ImGui.begin("Mixer / Monitor", noTitleDecorate)) {
            drawMixerMonitor(mixer)
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
    }

    fun dispose() {
        defaultStyle.destroy()
        imguiGl3.dispose()
        imguiGlfw.dispose()
        ImGui.destroyContext()
    }
}
