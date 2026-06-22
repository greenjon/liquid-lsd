package llm.slop.spirals.ui

import imgui.ImGui
import imgui.flag.ImGuiConfigFlags
import imgui.flag.ImGuiWindowFlags
import imgui.type.ImInt
import imgui.type.ImString
import java.io.File
import llm.slop.spirals.rendering.Deck
import llm.slop.spirals.rendering.Mandala
import llm.slop.spirals.rendering.MandalaRatio
import llm.slop.spirals.rendering.Mixer
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

    // Tracks the base size we last passed to scaleAllSizes so we can compute
    // the correct delta ratio on subsequent changes.
    private var appliedBaseSize: Float = UITheme.baseSize

    // Font rebuild must happen between frames (atlas is locked during a frame).
    // Store the requested size here; it is consumed at the top of the next render().
    private var pendingFontSize: Float? = null

    // Set to true for one frame when the Settings menu item is clicked; consumed
    // immediately after endMainMenuBar so openPopup runs at root ID-stack level.
    private var pendingOpenSettings = false

    // Patch grid state shared between PatchGridPanel and CellConfigPanel
    private val patchState = PatchGridState()

    private val deckAPresetIndex = ImInt(0)
    private val deckBPresetIndex = ImInt(0)
    private val deckASaveName = ImString(32)
    private val deckBSaveName = ImString(32)
    private var currentGlobalPatchFile: File? = null
    private var currentMixer: Mixer? = null

    private val recipes = listOf(
        MandalaRatio("Recipe A", 26, 23, 14, 14),
        MandalaRatio("Recipe B", 32, 23, 11, 11),
        MandalaRatio("Recipe C", 28, 19, 16, 16),
        MandalaRatio("Recipe D", 31, 19, 19, 10),
        MandalaRatio("Recipe E", 35, 20, 11, 11),
        MandalaRatio("Recipe F", 38, 23, 14, 5)
    )
    private val recipeNames = arrayOf(
        "Recipe A (26,23,14,14)", "Recipe B (32,23,11,11)", "Recipe C (28,19,16,16)",
        "Recipe D (31,19,19,10)", "Recipe E (35,20,11,11)", "Recipe F (38,23,14,5)"
    )

    init {
        logger.info { "Initializing ImGui..." }
        ImGui.createContext()
        val io = ImGui.getIO()
        io.addConfigFlags(ImGuiConfigFlags.NavEnableKeyboard)

        // Load semantic fonts before the GL3 backend initialises so the atlas
        // is ready for the backend to upload on its first render call.
        UITheme.loadFonts(io)

        // Scale style sizes proportionally to the loaded baseSize relative to the baseline of 15f
        val startupScale = UITheme.baseSize / 15f
        if (startupScale != 1f) {
            ImGui.getStyle().scaleAllSizes(startupScale)
            logger.info { "Applied startup UI style scale: $startupScale (baseSize: ${UITheme.baseSize})" }
        }

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
                        // Clear existing modulators for this column/cell
                        val cvId = target.cellId.cvSourceId
                        val existingMods = target.param.modulators.filter {
                            it.sourceId == cvId || (it.sourceId.startsWith("midi_cc_") && it.sourceId.endsWith("_$cvId"))
                        }
                        target.param.modulators.removeAll(existingMods)

                        // Create new MIDI modulator linked to this column
                        val exists = target.param.modulators.any { it.sourceId == "${midiId}_$cvId" }
                        if (!exists) {
                            target.param.modulators.add(
                                llm.slop.spirals.parameters.CvModulator(
                                    sourceId = "${midiId}_$cvId",
                                    weight = 1.0f,
                                    operator = llm.slop.spirals.parameters.ModulationOperator.ADD
                                )
                            )
                        }
                    }
                }
                patchState.midiLearnTarget = null
            }
        }

        // ── Between-frame work (atlas is unlocked here) ───────────────────────
        pendingFontSize?.let { newSize ->
            pendingFontSize = null
            val ratio = newSize / appliedBaseSize
            UITheme.baseSize = newSize
            UITheme.rebuildFonts(ImGui.getIO())
            imguiGl3.updateFontsTexture()
            ImGui.getStyle().scaleAllSizes(ratio)
            appliedBaseSize = newSize
            UITheme.saveSettings()
            logger.info { "Font size applied: ${newSize}px (ratio $ratio)" }
        }

        imguiGlfw.newFrame()
        ImGui.newFrame()

        drawMenuBar(mixer)
        // openPopup must be called at root ID-stack level — not inside the menu bar.
        if (pendingOpenSettings) {
            SettingsPanel.open()
            pendingOpenSettings = false
        }
        drawLayout(mixer, displayWidth, displayHeight)

        // Settings modal — drawn outside any docked window so it floats freely.
        SettingsPanel.draw(UITheme.baseSize, displayWidth, displayHeight) { newSize ->
            applyFontSize(newSize)
        }

        ImGui.render()
        imguiGl3.renderDrawData(ImGui.getDrawData())
    }

    /**
     * Rebuilds the font atlas at [newSize] and scales widget style proportionally.
     * [ImGui.getStyle().scaleAllSizes] is multiplicative, so we compute the delta
     * ratio from the last applied size each time.
     */
    private fun applyFontSize(newSize: Float) {
        if (newSize != appliedBaseSize) pendingFontSize = newSize
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

    private fun saveGlobalPatch(mixer: Mixer, forceAs: Boolean) {
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
        if (file != null) {
            currentGlobalPatchFile = file
            val name = file.nameWithoutExtension
            llm.slop.spirals.patches.PatchManager.saveGlobalPatchAsync(file, mixer, name)
        }
    }

    private fun drawMenuBar(mixer: Mixer) {
        if (ImGui.beginMainMenuBar()) {
            if (ImGui.beginMenu("File")) {
                if (ImGui.menuItem("Load Project...")) {
                    loadGlobalPatchWithDialog()
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
            CellConfigPanel.draw(patchState)
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

        // Save the Y cursor position below the image
        val nextY = ImGui.getCursorPosY()

        // Draw overlay text on top of the master output image
        ImGui.setCursorScreenPos(imgScreenX + 10f, imgScreenY + 10f)
        UITheme.h2Colored(1.0f, 1.0f, 1.0f, 0.8f, "Master Output")

        // Restore Y cursor position
        ImGui.setCursorPosY(nextY)
        ImGui.spacing()

        val btnW = 40f
        val subW = (availW - btnW - 8f) * 0.5f
        val subH = subW * (9f / 16f)

        ImGui.columns(3, "subMonitors", false)
        ImGui.setColumnWidth(0, (availW - btnW) * 0.5f)
        ImGui.setColumnWidth(1, btnW)
        ImGui.setColumnWidth(2, (availW - btnW) * 0.5f)

        // Column 0: Deck A Header + Preview
        drawDeckHeader("Deck A", mixer.deckA, true)
        ImGui.image(mixer.deckA.getCurrentHistoryFBO().texture, subW, subH, 0f, 1f, 1f, 0f)
        ImGui.nextColumn()

        // Column 1: Copy Buttons (above the previews, centered horizontally)
        ImGui.setCursorPosY(ImGui.getCursorPosY() + 5f)
        if (ImGui.button("<##copyToA", btnW - 4f, 25f)) {
            val dto = mixer.deckB.toDto("Deck B")
            mixer.deckA.applyDto(dto)
        }
        ImGui.spacing()
        if (ImGui.button(">##copyToB", btnW - 4f, 25f)) {
            val dto = mixer.deckA.toDto("Deck A")
            mixer.deckB.applyDto(dto)
        }
        ImGui.nextColumn()

        // Column 2: Deck B Header + Preview
        drawDeckHeader("Deck B", mixer.deckB, false)
        ImGui.image(mixer.deckB.getCurrentHistoryFBO().texture, subW, subH, 0f, 1f, 1f, 0f)
        ImGui.nextColumn()

        ImGui.columns(1)
        ImGui.spacing()

        // Crossfader (mapped display value from -1.0 to 1.0)
        drawFlatSlider("Crossfader", mixer.crossfade, 0f, 1f, 100f, -1f, 1f) {
            "A <-- %.2f --> B".format(it)
        }

        // Blend mode inline combo
        UITheme.body("Blend Mode")
        ImGui.sameLine(100f)
        ImGui.pushItemWidth(ImGui.getContentRegionAvailX() - 5f)
        val modes = arrayOf("ADD", "SCREEN", "MULT", "MAX", "XFADE")
        val modeIdx = ImInt(mixer.mode.baseValue.toInt())
        if (ImGui.combo("##blendmode", modeIdx, modes)) {
            mixer.mode.set(modeIdx.get().toFloat())
        }
        ImGui.popItemWidth()

        // Alpha (renamed from "Master Alpha")
        drawFlatSlider("Alpha", mixer.masterAlpha, 0f, 1f, 100f)
        ImGui.spacing()

        ImGui.columns(2, "deckCtrls", true)
        UITheme.h3("Deck A")
        ImGui.nextColumn()
        UITheme.h3("Deck B")
        ImGui.nextColumn()
        
        ImGui.separator()
        
        drawDeckControls("Deck A", mixer.deckA)
        ImGui.nextColumn()
        drawDeckControls("Deck B", mixer.deckB)
        ImGui.nextColumn()
        ImGui.columns(1)
    }

    private fun drawDeckControls(label: String, deck: Deck) {
        ImGui.pushID(label)

        // Recipe inline combo
        val mandala = deck.source as? Mandala
        if (mandala != null) {
            val idx = recipes.indexOfFirst {
                it.a == mandala.recipe.a && it.b == mandala.recipe.b &&
                it.c == mandala.recipe.c && it.d == mandala.recipe.d
            }.coerceAtLeast(0)
            val combo = ImInt(idx)

            UITheme.body("Recipe")
            ImGui.sameLine(80f)
            ImGui.pushItemWidth(ImGui.getContentRegionAvailX() - 5f)
            if (ImGui.combo("##recipe", combo, recipeNames)) {
                mandala.recipe = recipes[combo.get()]
            }
            ImGui.popItemWidth()
        }

        fun slider(lbl: String, param: ModulatableParameter,
                   min: Float, max: Float, fmt: String = "%.3f") {
            drawFlatSlider(lbl, param, min, max, 80f) { fmt.format(it) }
        }

        slider("Gain",      deck.source.globalAlpha, 0f, 1f)
        slider("Feedback",  deck.fbDecay,   0f, 1f)
        slider("FB Gain",   deck.fbGain,    0.9f, 1.1f)
        slider("FB Zoom",   deck.fbZoom,   -0.1f, 0.1f)
        slider("FB Rotate", deck.fbRotate, -0.1f, 0.1f)
        slider("FB Hue",    deck.fbHueShift,-0.1f, 0.1f)
        slider("FB Blur",   deck.fbBlur,    0f, 0.2f)

        ImGui.spacing()
        if (ImGui.button("Randomize Modulators", ImGui.getContentRegionAvailX(), 30f)) {
            deck.randomizeModulators()
        }

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
        formatValue: (Float) -> String = { "%.3f".format(it) }
    ) {
        ImGui.pushID(label)

        UITheme.body(label)
        ImGui.sameLine(labelW)

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
        } else {
            // Process mouse dragging
            val mousePressed = ImGui.isItemActive() || (ImGui.isItemHovered() && ImGui.isMouseDown(0))
            val valueRange = max - min
            val displayRange = displayMax - displayMin

            if (mousePressed) {
                val io = ImGui.getIO()
                val pct = ((io.mousePos.x - barStartX) / barW).coerceIn(0f, 1f)
                val nextDisplayVal = displayMin + pct * displayRange
                val nextInternalVal = min + if (displayRange > 0f) ((nextDisplayVal - displayMin) / displayRange) * valueRange else 0f
                param.set(nextInternalVal)
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

        val currentDisplayVal = displayMin + if (valueRange > 0f) ((param.baseValue - min) / valueRange) * displayRange else 0f
        val fillCol = ImGui.colorConvertFloat4ToU32(0.8f, 0.6f, 0.2f, 1f)

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
            val fillPct = if (valueRange > 0f) ((param.baseValue - min) / valueRange).coerceIn(0f, 1f) else 0f
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
        val valStr = if (midiIndicator != null) "$midiIndicator $baseValStr" else baseValStr
        val textW = ImGui.calcTextSize(valStr).x
        val valTextH = ImGui.calcTextSize(valStr).y
        val valTextX = barStartX + barW - textW - 5f
        val valTextY = barScreenY + (barH - valTextH) * 0.5f

        UITheme.withFont(UITheme.FontLevel.CAPTION) {
            dl.addText(valTextX, valTextY, ImGui.colorConvertFloat4ToU32(0.9f, 0.9f, 0.9f, 0.8f), valStr)
        }

        ImGui.popID()
    }

    fun dispose() {
        imguiGl3.dispose()
        imguiGlfw.dispose()
        ImGui.destroyContext()
    }
}
