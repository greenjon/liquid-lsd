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
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Manages the ImGui overlay for desktop control.
 */
class UIManager(private val windowHandle: Long) {
    private val logger = KotlinLogging.logger {}
    private val imguiGlfw = ImGuiImplGlfw()
    private val imguiGl3 = ImGuiImplGl3()

    // Tracks received MIDI CC events to process on the render thread
    private val pendingMidiEvents = ConcurrentLinkedQueue<Pair<Int, Int>>()

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
            style.setColor(ImGuiCol.PopupBg, 0.08f, 0.08f, 0.08f, 0.75f)
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

    private val deckAPresetIndex = ImInt(0)
    private val deckBPresetIndex = ImInt(0)
    private val deckASaveName = ImString(32)
    private val deckBSaveName = ImString(32)
    private var currentGlobalPatchFile: File? = null
    private var currentMixer: Mixer? = null

    private var lastWindowTitle: String? = null

    private enum class PendingProjectAction {
        NONE, NEW, LOAD
    }
    private var pendingProjectAction = PendingProjectAction.NONE
    private var pendingOpenConfirmPopup = false

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
        
        llm.slop.spirals.midi.MidiEngine.onMidiCcReceived = { channel, cc ->
            pendingMidiEvents.offer(channel to cc)
        }

        logger.info { "UIManager initialized" }
    }

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

        // Poll received MIDI events from our callback-driven queue
        while (true) {
            val event = pendingMidiEvents.poll() ?: break
            val (channel, cc) = event
            val target = patchState.midiLearnTarget
            if (target != null) {
                val midiId = "midi_cc_${channel}_${cc}"
                when (target) {
                    is MidiLearnTarget.BaseValueSlider -> {
                        target.param.mappedMidiId = midiId
                        target.param.midiMapMin = target.min
                        target.param.midiMapMax = target.max
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
            drawLayout(mixer, displayWidth, displayHeight)

            // Settings modal — drawn outside any docked window so it floats freely.
            SettingsPanel.draw(UITheme.baseSize, displayWidth, displayHeight, { newSize ->
                applyFontSize(newSize)
            }, {
                patchState.applyAutocollapseSetting()
            })

            // Audio Engine Monitor modal — drawn outside any docked window so it floats freely.
            AudioEnginePanel.draw(displayWidth, displayHeight)

            drawConfirmPopup(mixer, displayWidth, displayHeight)
            drawDeckConfirmPopups(mixer.deckA, mixer.deckB)
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

    private fun loadGlobalPatchWithDialog() {
        val dialog = java.awt.FileDialog(null as java.awt.Frame?, "Load Project", java.awt.FileDialog.LOAD)
        dialog.file = "*.json"
        dialog.isVisible = true
        if (dialog.directory != null && dialog.file != null) {
            val file = File(dialog.directory, dialog.file)
            currentGlobalPatchFile = file
            llm.slop.spirals.patches.PatchManager.loadGlobalPatchAsync(file)
        }
    }

    private fun saveGlobalPatch(mixer: Mixer, forceAs: Boolean): Boolean {
        val file = if (forceAs || currentGlobalPatchFile == null) {
            val dialog = java.awt.FileDialog(null as java.awt.Frame?, "Save Project", java.awt.FileDialog.SAVE)
            dialog.file = "project.json"
            dialog.isVisible = true
            if (dialog.directory != null && dialog.file != null) {
                File(dialog.directory, dialog.file)
            } else null
        } else {
            currentGlobalPatchFile
        }
        return if (file != null) {
            currentGlobalPatchFile = file
            val name = file.nameWithoutExtension
            llm.slop.spirals.patches.PatchManager.saveGlobalPatchAsync(file, mixer, name)
            true
        } else {
            false
        }
    }

    private fun performNewProject(mixer: Mixer) {
        llm.slop.spirals.patches.PatchManager.resetToDefault(mixer)
        currentGlobalPatchFile = null
    }

    private fun performLoadProject() {
        loadGlobalPatchWithDialog()
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
                    when (pendingProjectAction) {
                        PendingProjectAction.NEW -> performNewProject(mixer)
                        PendingProjectAction.LOAD -> performLoadProject()
                        else -> {}
                    }
                }
                pendingProjectAction = PendingProjectAction.NONE
                ImGui.closeCurrentPopup()
            }
            ImGui.sameLine()
            if (ImGui.button("Discard", 80f, 0f)) {
                when (pendingProjectAction) {
                    PendingProjectAction.NEW -> performNewProject(mixer)
                    PendingProjectAction.LOAD -> performLoadProject()
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

    private fun performLoadDeckPreset(isDeckA: Boolean) {
        val dialog = java.awt.FileDialog(null as java.awt.Frame?, "Load Deck Preset", java.awt.FileDialog.LOAD)
        dialog.file = "*.json"
        dialog.isVisible = true
        if (dialog.directory != null && dialog.file != null) {
            val sourceFile = java.io.File(dialog.directory, dialog.file)
            val presetsDir = java.io.File("presets/decks")
            if (!presetsDir.exists()) presetsDir.mkdirs()
            
            val targetFile = java.io.File(presetsDir, sourceFile.name)
            if (sourceFile.absolutePath != targetFile.absolutePath) {
                sourceFile.copyTo(targetFile, overwrite = true)
            }
            llm.slop.spirals.patches.PatchManager.loadDeckPresetAsync(targetFile, isDeckA)
        }
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
                if (ImGui.menuItem("Exit")) { logger.info { "Exit clicked" } }
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

            if (ImGui.menuItem("Audio Engine")) {
                pendingOpenAudioEngineMonitor = true
            }

            if (ImGui.beginMenu("Help")) {
                if (ImGui.menuItem("Documentation")) {
                    DocManager.openDocumentation()
                }
                ImGui.endMenu()
            }
            ImGui.endMainMenuBar()
        }
    }

    private fun getAvailableDeckPresets(isDeckA: Boolean): Array<String> {
        val dir = File("presets/decks")
        if (!dir.exists()) dir.mkdirs()
        val files = dir.listFiles { _, name -> name.endsWith(".json") } ?: emptyArray()
        val list = mutableListOf<String>()
        list.add("None")

        val activePreset = if (isDeckA) llm.slop.spirals.patches.PatchManager.activePresetA else llm.slop.spirals.patches.PatchManager.activePresetB
        val deck = if (isDeckA) currentMixer?.deckA else currentMixer?.deckB
        val isDirty = if (deck != null) llm.slop.spirals.patches.PatchManager.isDeckDirty(deck, isDeckA) else false

        val presetNames = files.map { it.nameWithoutExtension }
        for (pName in presetNames) {
            if (pName == activePreset && isDirty) {
                list.add("$pName *")
            } else {
                list.add(pName)
            }
        }
        return list.toTypedArray()
    }

    private fun loadDeckPreset(presetName: String, deck: Deck, isDeckA: Boolean) {
        if (presetName == "None") return
        val file = File("presets/decks/$presetName.json")
        if (file.exists()) {
            llm.slop.spirals.patches.PatchManager.loadDeckPresetAsync(file, isDeckA)
        }
    }

    private fun saveDeckPreset(name: String, deck: Deck, isDeckA: Boolean) {
        if (name.isBlank()) return
        val dto = deck.toDto(name)
        if (isDeckA) {
            llm.slop.spirals.patches.PatchManager.activePresetA = name
            llm.slop.spirals.patches.PatchManager.cachedDtoA = dto
        } else {
            llm.slop.spirals.patches.PatchManager.activePresetB = name
            llm.slop.spirals.patches.PatchManager.cachedDtoB = dto
        }
        val file = File("presets/decks/$name.json")
        llm.slop.spirals.patches.PatchManager.saveDeckPresetAsync(file, deck, name)
    }

    private fun drawDeckHeader(label: String, deck: Deck, isDeckA: Boolean) {
        ImGui.pushID(label)
        UITheme.h3(label)
        ImGui.sameLine(80f)

        val presets = getAvailableDeckPresets(isDeckA)
        val activePreset = if (isDeckA) llm.slop.spirals.patches.PatchManager.activePresetA else llm.slop.spirals.patches.PatchManager.activePresetB
        val isDirty = llm.slop.spirals.patches.PatchManager.isDeckDirty(deck, isDeckA)

        val targetName = if (isDirty && activePreset != null) "$activePreset *" else activePreset
        val idx = presets.indexOfFirst { it == targetName }.coerceAtLeast(0)

        val selectedIndex = if (isDeckA) deckAPresetIndex else deckBPresetIndex
        selectedIndex.set(idx)

        ImGui.pushItemWidth(120f)
        if (ImGui.combo("##preset_$label", selectedIndex, presets)) {
            val chosen = presets[selectedIndex.get()]
            val cleanName = chosen.removeSuffix(" *")
            if (cleanName == "None") {
                if (isDeckA) {
                    llm.slop.spirals.patches.PatchManager.activePresetA = null
                    llm.slop.spirals.patches.PatchManager.cachedDtoA = null
                } else {
                    llm.slop.spirals.patches.PatchManager.activePresetB = null
                    llm.slop.spirals.patches.PatchManager.cachedDtoB = null
                }
            } else {
                loadDeckPreset(cleanName, deck, isDeckA)
            }
        }
        ImGui.popItemWidth()

        ImGui.sameLine()
        if (ImGui.button("Save##save_$label")) {
            ImGui.openPopup("save_deck_preset_popup_$label")
        }

        if (ImGui.beginPopup("save_deck_preset_popup_$label")) {
            val activeName = if (isDeckA) llm.slop.spirals.patches.PatchManager.activePresetA else llm.slop.spirals.patches.PatchManager.activePresetB
            val isDeckDirty = llm.slop.spirals.patches.PatchManager.isDeckDirty(deck, isDeckA)

            if (activeName != null) {
                if (isDeckDirty) {
                    if (ImGui.button("Overwrite '$activeName'", ImGui.getContentRegionAvailX(), 25f)) {
                        saveDeckPreset(activeName, deck, isDeckA)
                        ImGui.closeCurrentPopup()
                    }
                    ImGui.separator()
                } else {
                    ImGui.textDisabled("Preset is up to date")
                    ImGui.separator()
                }
            }

            ImGui.text("Save As New:")
            val nameInput = if (isDeckA) deckASaveName else deckBSaveName
            ImGui.inputText("##nameInput_$label", nameInput)
            ImGui.sameLine()
            if (ImGui.button("Save##asNew_$label")) {
                val newName = nameInput.get().trim()
                if (newName.isNotEmpty()) {
                    saveDeckPreset(newName, deck, isDeckA)
                    ImGui.closeCurrentPopup()
                }
            }
            ImGui.endPopup()
        }
        ImGui.popID()
    }

    private fun drawLayout(mixer: Mixer, displayWidth: Float, displayHeight: Float) {
        val menuBarH = 32f
        val contentH = displayHeight - menuBarH
        val noDecorate = ImGuiWindowFlags.NoResize or
                         ImGuiWindowFlags.NoMove or
                         ImGuiWindowFlags.NoCollapse

        // Left: Patch Grid (40% width, full content height)
        val leftW = displayWidth * 0.4f
        ImGui.setNextWindowPos(0f, menuBarH)
        ImGui.setNextWindowSize(leftW, contentH)
        if (ImGui.begin("Patch Grid", noDecorate)) {
            PatchGridPanel.draw(mixer, patchState)
        }
        ImGui.end()

        // Middle: Cell Config (30% width, full content height)
        val middleW = displayWidth * 0.3f
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
        val noTitleDecorate = noDecorate or ImGuiWindowFlags.NoTitleBar
        if (ImGui.begin("Mixer / Monitor", noTitleDecorate)) {
            drawMixerMonitor(mixer)
        }
        ImGui.end()
    }

    private fun drawMixerMonitor(mixer: Mixer) {
        val availW = ImGui.getContentRegionAvailX()
        val masterH = availW * (9f / 16f)

        val imgScreenX = ImGui.getCursorScreenPosX()
        val imgScreenY = ImGui.getCursorScreenPosY()

        ImGui.image(mixer.masterFBO.texture, availW, masterH, 0f, 1f, 1f, 0f)

        // Draw overlay text on top of the master output image
        val dl = ImGui.getWindowDrawList()
        val overlayStr = "Master Output"
        val overlayH = ImGui.calcTextSize(overlayStr).y + 10f
        dl.addRectFilled(imgScreenX, imgScreenY, imgScreenX + availW, imgScreenY + overlayH, ImGui.colorConvertFloat4ToU32(0f, 0f, 0f, 0.6f))
        
        ImGui.setCursorScreenPos(imgScreenX + 10f, imgScreenY + 5f)
        UITheme.captionColored(0.4f, 1.0f, 0.8f, 1.0f, overlayStr) // Mint green text

        // Restore Y cursor position
        ImGui.setCursorScreenPos(imgScreenX, imgScreenY + masterH)
        ImGui.spacing()
        ImGui.separator()
        ImGui.spacing()

        // --- Master Mixer Controls ---
        ImGui.pushStyleColor(imgui.flag.ImGuiCol.ChildBg, ImGui.colorConvertFloat4ToU32(0.05f, 0.1f, 0.08f, 0.4f)) // Faint mint background
        ImGui.beginChild("MasterControls", availW, 100f, true)
        
        // Crossfader (mapped display value from -1.0 to 1.0)
        drawFlatSlider("Crossfader", mixer.crossfade, 0f, 1f, 80f, -1f, 1f, ImGui.colorConvertFloat4ToU32(0.4f, 1.0f, 0.8f, 1f)) {
            "A <-- %.2f --> B".format(it)
        }

        ImGui.columns(2, "masterMixerCols", false)
        
        // Alpha (renamed from "Master Alpha")
        drawFlatSlider("Alpha", mixer.masterAlpha, 0f, 1f, 50f, 0f, 1f, ImGui.colorConvertFloat4ToU32(0.4f, 1.0f, 0.8f, 1f))
        ImGui.nextColumn()
        
        drawFlatSlider("Bloom", mixer.bloom, 0f, 1f, 50f, 0f, 1f, ImGui.colorConvertFloat4ToU32(0.4f, 1.0f, 0.8f, 1f))
        ImGui.nextColumn()
        
        ImGui.columns(1)
        
        // Blend mode inline text readout
        val modes = arrayOf("ADD", "SCREEN", "MULT", "MAX", "XFADE")
        val modeIdx = mixer.mode.value.toInt().coerceIn(0, 4)
        ImGui.spacing()
        UITheme.captionColored(0.4f, 1.0f, 0.8f, 1.0f, "Blend Mode: ${modes[modeIdx]}")
        
        ImGui.endChild()
        ImGui.popStyleColor()

        ImGui.spacing()
        ImGui.separator()
        ImGui.spacing()

        // --- Deck Monitors ---
        // Completely removed columns logic for the header/presets! We will do absolute positioning.
        val btnW = 40f
        val padding = 16f
        val halfW = (availW - padding) * 0.5f
        
        val startX = ImGui.getCursorScreenPosX()
        val centerY = ImGui.getCursorScreenPosY()
        
        // Use an invisible full-width dummy to reserve vertical space for the two rows
        val headerRowH = ImGui.getTextLineHeightWithSpacing()
        val presetRowH = ImGui.getFrameHeightWithSpacing()
        ImGui.dummy(availW, headerRowH + presetRowH)
        
        // 1. Deck A Header
        var twA = 0f
        var twB = 0f
        UITheme.withFont(UITheme.FontLevel.H2) {
            twA = ImGui.calcTextSize("Deck A").x
            twB = ImGui.calcTextSize("Deck B").x
        }
        
        ImGui.setCursorScreenPos(startX + (halfW - twA) * 0.5f, centerY)
        UITheme.h2("Deck A")
        
        // 2. Centered Copy Buttons (drawn exactly in the middle of availW)
        val centerOfPanel = startX + availW * 0.5f
        ImGui.setCursorScreenPos(centerOfPanel - btnW - 5f, centerY + 2f)
        if (ImGui.button("<##copyToA", btnW, 25f)) {
            val dto = mixer.deckB.toDto("Deck B")
            mixer.deckA.applyDto(dto)
        }
        
        ImGui.setCursorScreenPos(centerOfPanel + 5f, centerY + 2f)
        if (ImGui.button(">##copyToB", btnW, 25f)) {
            val dto = mixer.deckA.toDto("Deck A")
            mixer.deckB.applyDto(dto)
        }
        
        // 3. Deck B Header
        val deckBStartX = startX + halfW + padding
        ImGui.setCursorScreenPos(deckBStartX + (halfW - twB) * 0.5f, centerY)
        UITheme.h2("Deck B")
        
        // 4. Presets Row
        val presetY = centerY + headerRowH
        ImGui.setCursorScreenPos(startX, presetY)
        drawDeckPresetDropdown("Deck A", mixer.deckA, true, halfW)
        
        ImGui.setCursorScreenPos(deckBStartX, presetY)
        drawDeckPresetDropdown("Deck B", mixer.deckB, false, halfW)
        
        ImGui.spacing()
        
        // --- Render the exact exact child panels ---
        val subH = halfW * (9f / 16f)
        
        // We will just draw them side by side manually without ImGui.columns to guarantee perfect math.
        val childY = ImGui.getCursorScreenPosY()
        
        ImGui.setCursorScreenPos(startX, childY)
        drawDeckControls("Deck A", mixer.deckA, halfW, subH, true)
        
        ImGui.setCursorScreenPos(deckBStartX, childY)
        drawDeckControls("Deck B", mixer.deckB, halfW, subH, false)
        
        // Advance cursor past the tallest child
        val endY = ImGui.getCursorScreenPosY()
        ImGui.setCursorScreenPos(startX, endY)
    }

    private fun drawDeckPresetDropdown(label: String, deck: Deck, isDeckA: Boolean, fixedWidth: Float) {
        ImGui.beginGroup()
        ImGui.pushID("presetRow_$label")
        
        val presets = getAvailableDeckPresets(isDeckA)
        val activePreset = if (isDeckA) llm.slop.spirals.patches.PatchManager.activePresetA else llm.slop.spirals.patches.PatchManager.activePresetB
        val isDirty = llm.slop.spirals.patches.PatchManager.isDeckDirty(deck, isDeckA)

        val targetName = if (isDirty && activePreset != null) "$activePreset *" else activePreset
        val idx = presets.indexOfFirst { it == targetName }.coerceAtLeast(0)

        val selectedIndex = if (isDeckA) deckAPresetIndex else deckBPresetIndex
        selectedIndex.set(idx)

        val saveBtnW = 45f
        val comboW = (fixedWidth - saveBtnW - ImGui.getStyle().itemSpacing.x).coerceAtLeast(50f)
        
        ImGui.pushItemWidth(comboW)
        if (ImGui.combo("##preset", selectedIndex, presets)) {
            val chosen = presets[selectedIndex.get()]
            val cleanName = chosen.removeSuffix(" *")
            
            if (isDirty) {
                if (isDeckA) {
                    pendingDeckActionA = PendingDeckAction.LOAD_PRESET
                    pendingDeckTargetPresetA = cleanName
                } else {
                    pendingDeckActionB = PendingDeckAction.LOAD_PRESET
                    pendingDeckTargetPresetB = cleanName
                }
            } else {
                if (cleanName == "None") {
                    if (isDeckA) {
                        llm.slop.spirals.patches.PatchManager.activePresetA = null
                        llm.slop.spirals.patches.PatchManager.cachedDtoA = null
                    } else {
                        llm.slop.spirals.patches.PatchManager.activePresetB = null
                        llm.slop.spirals.patches.PatchManager.cachedDtoB = null
                    }
                } else {
                    loadDeckPreset(cleanName, deck, isDeckA)
                }
            }
        }
        ImGui.popItemWidth()

        var openSaveAs = false
        var openDeleteConfirm = false
        
        ImGui.sameLine()
        if (ImGui.button("Menu", saveBtnW, 0f)) {
            ImGui.openPopup("deck_preset_menu_$label")
        }

        if (ImGui.beginPopup("deck_preset_menu_$label")) {
            if (ImGui.menuItem("New (Reset Deck)")) {
                if (isDirty) {
                    if (isDeckA) pendingDeckActionA = PendingDeckAction.NEW
                    else pendingDeckActionB = PendingDeckAction.NEW
                } else {
                    deck.reset()
                    if (isDeckA) {
                        llm.slop.spirals.patches.PatchManager.activePresetA = null
                        llm.slop.spirals.patches.PatchManager.cachedDtoA = null
                    } else {
                        llm.slop.spirals.patches.PatchManager.activePresetB = null
                        llm.slop.spirals.patches.PatchManager.cachedDtoB = null
                    }
                }
            }
            if (ImGui.menuItem("Load File...")) {
                if (isDirty) {
                    if (isDeckA) pendingDeckActionA = PendingDeckAction.LOAD_FILE
                    else pendingDeckActionB = PendingDeckAction.LOAD_FILE
                } else {
                    performLoadDeckPreset(isDeckA)
                }
            }
            
            ImGui.separator()
            
            if (ImGui.menuItem("Save")) {
                if (activePreset != null) saveDeckPreset(activePreset, deck, isDeckA)
                else openSaveAs = true
            }
            if (ImGui.menuItem("Save As...")) {
                openSaveAs = true
            }
            
            // Delete option - only show if there is an active loaded preset
            if (activePreset != null && activePreset != "None") {
                ImGui.separator()
                if (ImGui.menuItem("Delete '$activePreset'")) {
                    openDeleteConfirm = true
                }
            }
            ImGui.endPopup()
        }

        if (openSaveAs) ImGui.openPopup("save_as_deck_preset_popup_$label")
        if (openDeleteConfirm) ImGui.openPopup("delete_deck_preset_popup_$label")

        if (ImGui.beginPopup("save_as_deck_preset_popup_$label")) {
            ImGui.text("Save As New:")
            val nameInput = if (isDeckA) deckASaveName else deckBSaveName
            ImGui.inputText("##nameInput_$label", nameInput)
            ImGui.sameLine()
            if (ImGui.button("Save##asNew")) {
                val newName = nameInput.get().trim()
                if (newName.isNotEmpty()) {
                    saveDeckPreset(newName, deck, isDeckA)
                    ImGui.closeCurrentPopup()
                }
            }
            ImGui.endPopup()
        }

        // Delete confirmation popup
        if (ImGui.beginPopupModal("delete_deck_preset_popup_$label", imgui.flag.ImGuiWindowFlags.AlwaysAutoResize)) {
            ImGui.text("Are you sure you want to permanently delete '$activePreset'?")
            ImGui.spacing()
            if (ImGui.button("Delete", 80f, 0f)) {
                val file = java.io.File("presets/decks/$activePreset.json")
                if (file.exists()) file.delete()
                
                // Clear active preset state
                if (isDeckA) {
                    llm.slop.spirals.patches.PatchManager.activePresetA = null
                    llm.slop.spirals.patches.PatchManager.cachedDtoA = null
                } else {
                    llm.slop.spirals.patches.PatchManager.activePresetB = null
                    llm.slop.spirals.patches.PatchManager.cachedDtoB = null
                }
                ImGui.closeCurrentPopup()
            }
            ImGui.sameLine()
            if (ImGui.button("Cancel", 80f, 0f)) {
                ImGui.closeCurrentPopup()
            }
            ImGui.endPopup()
        }
        ImGui.popID()
        ImGui.endGroup()
    }

    private fun drawDeckControls(label: String, deck: Deck, panelW: Float, previewH: Float, isDeckA: Boolean) {
        ImGui.pushID(label)

        val themeCol = if (isDeckA) {
            ImGui.colorConvertFloat4ToU32(0.2f, 0.4f, 0.8f, 1f) // Deck A Blue
        } else {
            ImGui.colorConvertFloat4ToU32(0.8f, 0.4f, 0.2f, 1f) // Deck B Orange
        }
        
        val bgCol = if (isDeckA) {
            ImGui.colorConvertFloat4ToU32(0.2f, 0.4f, 0.8f, 0.15f)
        } else {
            ImGui.colorConvertFloat4ToU32(0.8f, 0.4f, 0.2f, 0.15f)
        }

        ImGui.pushStyleColor(imgui.flag.ImGuiCol.ChildBg, bgCol)
        // Ensure no internal padding interferes with drawing
        ImGui.pushStyleVar(imgui.flag.ImGuiStyleVar.WindowPadding, 0f, 0f)
        
        // Explicitly set the Child window width
        ImGui.beginChild("Child_$label", panelW, 0f, false)

        ImGui.spacing()

        val inset = 3f
        val imgAvailW = panelW - (inset * 2f)
        val imgAvailH = imgAvailW * (9f / 16f)
        
        ImGui.setCursorPosX(inset)
        val imgX = ImGui.getCursorScreenPosX()
        val imgY = ImGui.getCursorScreenPosY()
        
        ImGui.image(deck.getOutputTexture(), imgAvailW, imgAvailH, 0f, 1f, 1f, 0f)
        
        val dl = ImGui.getWindowDrawList()
        // Draw border perfectly wrapped around the image
        dl.addRect(imgX - 1f, imgY - 1f, imgX + imgAvailW + 1f, imgY + imgAvailH + 1f, themeCol, 0f, 0, 2f)

        ImGui.spacing()
        ImGui.separator()
        ImGui.spacing()
        
        ImGui.indent(8f)

        // Structural Identity Readouts
        val mandala = deck.source as? Mandala
        if (mandala != null) {
            val lobesParam = mandala.parameters["Lobes"]!!
            val currentLobe = lobesParam.value.roundToInt()
            val closestLobe = MandalaLibrary.uniquePetals.minByOrNull { kotlin.math.abs(it - currentLobe) } ?: 3
            
            UITheme.captionColored(0.8f, 0.8f, 0.8f, 1.0f, "Geometry:")
            UITheme.body("  Lobes: $currentLobe")

            val recipeParam = mandala.parameters["Recipe Select"]!!
            val filtered = MandalaLibrary.recipesByPetals[closestLobe] ?: emptyList()
            val currentSelect = recipeParam.value
            val recipeIdx = (currentSelect * (filtered.size - 1)).roundToInt().coerceIn(0, filtered.size - 1)

            if (filtered.isNotEmpty() && recipeIdx in filtered.indices) {
                val recipe = filtered[recipeIdx]
                UITheme.body("  Recipe: ${recipe.a},${recipe.b},${recipe.c},${recipe.d}")
            } else {
                UITheme.body("  Recipe: None")
            }

            ImGui.spacing()
            val bgStyleParam = mandala.parameters["Bg Style"]!!
            val bgStyleIdx = bgStyleParam.value.toInt().coerceIn(0, 2)
            val bgStyleLabels = arrayOf("Off", "Solid Color", "Plasma")
            
            UITheme.captionColored(0.8f, 0.8f, 0.8f, 1.0f, "Background:")
            UITheme.body("  Style: ${bgStyleLabels[bgStyleIdx]}")
        }
        ImGui.unindent(8f)

        ImGui.endChild()
        ImGui.popStyleVar()
        ImGui.popStyleColor()
        ImGui.popID()
    }

    private fun drawFlatSlider(
        label: String,
        param: ModulatableParameter,
        min: Float,
        max: Float,
        labelW: Float = 100f,
        displayMin: Float = min,
        displayMax: Float = max,
        themeColor: Int = ImGui.colorConvertFloat4ToU32(0.8f, 0.6f, 0.2f, 1f),
        formatValue: (Float) -> String = { "%.3f".format(it) }
    ) {
        ImGui.pushID(label)

        var textW = 0f
        UITheme.withFont(UITheme.FontLevel.BODY) { textW = ImGui.calcTextSize(label).x }
        UITheme.body(label)
        ImGui.sameLine(textW + 15f)

        val barStartX = ImGui.getCursorScreenPosX()
        val barScreenY = ImGui.getCursorScreenPosY() + 3f
        val barW = ImGui.getContentRegionAvailX() - 5f
        val barH = 14f

        ImGui.invisibleButton("##slider", barW, barH)

        val isTarget = patchState.midiLearnTarget?.let {
            it is MidiLearnTarget.BaseValueSlider && it.param === param
        } ?: false

        if (patchState.isMidiLearnMode) {
            if (ImGui.isItemClicked(0)) {
                patchState.midiLearnTarget = MidiLearnTarget.BaseValueSlider(label, param, min, max)
            }
        }

        val valueRange = max - min
        val displayRange = displayMax - displayMin

        // Draw the flat bar visual using DrawList
        val dl = ImGui.getWindowDrawList()
        dl.addRectFilled(
            barStartX, barScreenY,
            barStartX + barW, barScreenY + barH,
            ImGui.colorConvertFloat4ToU32(0.15f, 0.15f, 0.15f, 1f),
            3f
        )

        val currentDisplayVal = displayMin + if (valueRange > 0f) ((param.value - min) / valueRange) * displayRange else 0f
        val fillCol = themeColor

        if (displayMin < 0f && displayMax > 0f) {
            val pctCenter = (0f - displayMin) / displayRange
            val pctVal = (currentDisplayVal - displayMin) / displayRange
            val centerX = barStartX + barW * pctCenter
            val valX = barStartX + barW * pctVal

            dl.addRectFilled(
                minOf(centerX, valX), barScreenY,
                maxOf(centerX, valX), barScreenY + barH,
                fillCol,
                3f
            )
        } else {
            val fillPct = if (valueRange > 0f) ((param.value - min) / valueRange).coerceIn(0f, 1f) else 0f
            if (fillPct > 0f) {
                dl.addRectFilled(
                    barStartX, barScreenY,
                    barStartX + barW * fillPct, barScreenY + barH,
                    fillCol,
                    3f
                )
            }
        }

        // Draw highlight if it's the active learn target
        if (isTarget) {
            val borderCol = ImGui.colorConvertFloat4ToU32(0.0f, 0.8f, 1.0f, 1.0f) // bright cyan
            dl.addRect(
                barStartX - 2f, barScreenY - 2f,
                barStartX + barW + 2f, barScreenY + barH + 2f,
                borderCol,
                4f,
                15,
                2.0f
            )
        }

        // MIDI mapped indicator
        val midiIndicator = param.mappedMidiId?.let { id ->
            if (id.startsWith("midi_cc_")) {
                val parts = id.substring("midi_cc_".length).split('_')
                if (parts.size >= 2) {
                    val ch = parts[0].toIntOrNull() ?: 0
                    val cc = parts[1].toIntOrNull() ?: 0
                    if (ch == 0) "[CC $cc]" else "[Ch ${ch + 1} CC $cc]"
                } else null
            } else null
        }

        // Value text overlay
        val baseValStr = formatValue(currentDisplayVal)
        val valStr = if (midiIndicator != null) {
            if (baseValStr.isNotEmpty()) "$midiIndicator $baseValStr" else midiIndicator
        } else {
            baseValStr
        }
        
        if (valStr.isNotEmpty()) {
            val textWidth = ImGui.calcTextSize(valStr).x
            val valTextH = ImGui.calcTextSize(valStr).y
            val valTextX = barStartX + barW - textWidth - 5f
            val valTextY = barScreenY + (barH - valTextH) * 0.5f

            UITheme.withFont(UITheme.FontLevel.CAPTION) {
                dl.addText(valTextX, valTextY, ImGui.colorConvertFloat4ToU32(0.9f, 0.9f, 0.9f, 0.8f), valStr)
            }
        }

        ImGui.popID()
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
