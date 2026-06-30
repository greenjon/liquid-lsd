package llm.slop.spirals.ui

import imgui.ImDrawList
import imgui.ImGui
import imgui.flag.ImGuiTreeNodeFlags
import imgui.flag.ImGuiKey
import llm.slop.spirals.cv.CVRegistry
import llm.slop.spirals.parameters.ModulatableParameter
import llm.slop.spirals.parameters.CvModulator
import llm.slop.spirals.rendering.Deck
import llm.slop.spirals.rendering.DynamicVisualSource
import llm.slop.spirals.rendering.Mandala
import llm.slop.spirals.rendering.Mixer
import llm.slop.spirals.models.ClipboardManager
import llm.slop.spirals.models.CellClipboardData
import llm.slop.spirals.models.RowClipboardData
import llm.slop.spirals.models.toDto
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.roundToInt

/**
 * Draws the Patch Grid panel. Rows = grouped ModulatableParameters.
 * Columns = CV sources. Each intersection is a clickable cell.
 */
object PatchGridPanel {

    private fun getCvColumns(): List<String> {
        return if (UITheme.audioEngineEnabled) {
            listOf("gen1", /*"gen2",*/ "audio", "trigger")
        } else {
            listOf("gen1" /*, "gen2"*/)
        }
    }

    private fun getCvLabels(): List<String> {
        return if (UITheme.audioEngineEnabled) {
            listOf("LFO", /*"GEN 2",*/ "AUDIO", "TRIGGER")
        } else {
            listOf("LFO" /*, "GEN 2"*/)
        }
    }


    // Size of each cell square (px)
    private const val CELL = 35f
    private const val CELL_PAD = 5f
    private const val GROUP_GAP = 10f

    private fun getColumnOffset(colId: String): Float {
        // Build the visible column list dynamically
        val visibleCols = mutableListOf("final", "midi")
        visibleCols.addAll(getCvColumns())
        
        // Find the index of this column in the visible list
        val index = visibleCols.indexOf(colId)
        if (index < 0) return 0f
        
        // Calculate offset based on position in visible columns
        // Add GROUP_GAP after "midi" (between special columns and CV columns)
        val gapAfterMidi = if (index > 1) GROUP_GAP else 0f
        return index * (CELL + CELL_PAD) + gapAfterMidi
    }

    private fun getCvColor(colId: String, alpha: Float = 1f): Int {
        return when (colId) {
            // Special
            "final"          -> ImGui.colorConvertFloat4ToU32(0.4f, 1.0f, 0.8f, alpha)
            "base"           -> ImGui.colorConvertFloat4ToU32(0.8f, 0.6f, 0.2f, alpha)
            "midi"           -> ImGui.colorConvertFloat4ToU32(0.3f, 1.0f, 0.4f, alpha)
            // Synthetic / Generators
            "gen1"           -> ImGui.colorConvertFloat4ToU32(0.1f, 0.7f, 0.9f, alpha)
            "gen2"           -> ImGui.colorConvertFloat4ToU32(0.1f, 0.8f, 0.7f, alpha)
            "lfo"            -> ImGui.colorConvertFloat4ToU32(0.2f, 0.8f, 1.0f, alpha)
            "sampleAndHold"  -> ImGui.colorConvertFloat4ToU32(0.7f, 0.4f, 1.0f, alpha)
            "beatPhase"      -> ImGui.colorConvertFloat4ToU32(0.4f, 0.4f, 1.0f, alpha)
            // Amplitude / Spectral
            "audio"          -> ImGui.colorConvertFloat4ToU32(0.4f, 0.9f, 0.1f, alpha)
            "amp"            -> ImGui.colorConvertFloat4ToU32(0.7f, 0.9f, 0.1f, alpha)
            "bass"           -> ImGui.colorConvertFloat4ToU32(0.9f, 0.2f, 0.2f, alpha)
            "mid"            -> ImGui.colorConvertFloat4ToU32(0.9f, 0.5f, 0.1f, alpha)
            "high"           -> ImGui.colorConvertFloat4ToU32(0.9f, 0.9f, 0.2f, alpha)
            // Transients / Triggers
            "trigger"        -> ImGui.colorConvertFloat4ToU32(0.9f, 0.2f, 0.7f, alpha)
            "onset"          -> ImGui.colorConvertFloat4ToU32(1.0f, 0.3f, 0.6f, alpha)
            "accent"         -> ImGui.colorConvertFloat4ToU32(0.9f, 0.1f, 0.9f, alpha)
            else             -> ImGui.colorConvertFloat4ToU32(0.5f, 0.5f, 0.5f, alpha)
        }
    }

    private var gridStartX = 0f
    private var rowIndex = 0

    fun draw(mixer: Mixer, state: PatchGridState) {
        rowIndex = 0
        val avail = ImGui.getContentRegionAvailX()
        gridStartX = ImGui.getCursorScreenPosX()
        
        val lastVisibleCol = getCvColumns().lastOrNull() ?: "midi"
        val maxGridW = getColumnOffset(lastVisibleCol) + CELL + CELL_PAD * 0.5f
        val labelColW = (avail - maxGridW - 20f).coerceAtLeast(120f)

        handleKeyboardShortcuts(state, mixer)

        drawColumnHeaders(labelColW, state, mixer)
        ImGui.separator()
        ImGui.spacing()

        drawSubTabs(state, mixer)
        ImGui.spacing()
        ImGui.spacing()

        if (ImGui.beginChild("##patch_grid_scroll", 0f, 0f, false)) {
            if (state.activeTopTab == "Mixer") {
                drawSubGroupContent("Mixer", "Mixer", state) {
                    drawParamRow("Deck A Source", "Deck A/FB/Source",   mixer.deckA.sourceSelect,state, labelColW, mixer)
                    drawParamRow("Deck B Source", "Deck B/FB/Source",   mixer.deckB.sourceSelect,state, labelColW, mixer)
                    drawParamRow("crossfade",  "Mixer/crossfade",  mixer.crossfade,  state, labelColW, mixer)
                    drawParamRow("master α",   "Mixer/masterAlpha", mixer.masterAlpha, state, labelColW, mixer)
                    drawParamRow("bloom",      "Mixer/bloom",       mixer.bloom,       state, labelColW, mixer)
                    drawParamRow("setlist prev", "Mixer/setlistPrev", mixer.setlistPrev, state, labelColW, mixer)
                    drawParamRow("setlist next", "Mixer/setlistNext", mixer.setlistNext, state, labelColW, mixer)
                }
            } else if (state.activeTopTab == "Deck A") {
                drawDeckGroupContent("Deck A", mixer.deckA, state, labelColW, mixer)
            } else if (state.activeTopTab == "Deck B") {
                drawDeckGroupContent("Deck B", mixer.deckB, state, labelColW, mixer)
            }
        }
        ImGui.endChild()
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun drawColumnHeaders(labelColW: Float, state: PatchGridState, mixer: Mixer) {
        val dl = ImGui.getWindowDrawList()
        val startX = ImGui.getCursorScreenPosX()
        val startY = ImGui.getCursorScreenPosY()
        val mousePos = ImGui.getIO().mousePos
        
        // Calculate the maximum height needed for the vertical labels
        var maxH = 0f
        val cvLabelsVertical = getCvLabels().map { it.toList().joinToString("\n") }
        UITheme.withFont(UITheme.FontLevel.CAPTION) {
            for (label in cvLabelsVertical) {
                val h = ImGui.calcTextSize(label).y
                if (h > maxH) maxH = h
            }
            val hFinal = ImGui.calcTextSize("F\nI\nN\nA\nL").y
            val hMidi = ImGui.calcTextSize("M\nI\nD\nI").y
            if (hFinal > maxH) maxH = hFinal
            if (hMidi > maxH) maxH = hMidi
        }
        
        val btnHeight = ImGui.getFrameHeight()
        val spacing = 4f
        val buttonsH = 3 * (btnHeight + spacing)
        val headerH = (maxH + 5f).coerceAtLeast(buttonsH + 28f)
        
        // Reserve vertical space for headers
        ImGui.dummy(10f, headerH)
        val afterHeadersY = ImGui.getCursorScreenPosY()

        val scale = btnHeight / 30f
        val btnWidth = 50f * scale

        // Draw the 3 randomize rows vertically
        drawRandomizeRow("rand_all", "Randomize all", startX, startY, btnWidth, btnHeight, scale) {
            pushUndoState(state, mixer)
            mixer.deckA.randomizeModulators()
            mixer.deckB.randomizeModulators()
            listOf(mixer.crossfade, mixer.masterAlpha).forEach { param ->
                val randomized = param.modulators.map { it.randomizeActiveValues() }
                param.modulators.clear()
                param.modulators.addAll(randomized)
                param.randomizeBaseValue()
            }
        }

        drawRandomizeRow("rand_deck_a", "Randomize Deck A", startX, startY + btnHeight + spacing, btnWidth, btnHeight, scale) {
            pushUndoState(state, mixer)
            mixer.deckA.randomizeModulators()
        }

        drawRandomizeRow("rand_deck_b", "Randomize Deck B", startX, startY + (btnHeight + spacing) * 2f, btnWidth, btnHeight, scale) {
            pushUndoState(state, mixer)
            mixer.deckB.randomizeModulators()
        }
        
        // Draw Top Tab Row at the bottom-left of the header area (just above the separator)
        ImGui.setCursorScreenPos(startX, startY + headerH - 24f)
        drawTopTabs(state)
        
        val lineCol = ImGui.colorConvertFloat4ToU32(1f, 1f, 1f, 0.05f) // VERY subtle extended grid line
        val bottomY = startY + ImGui.getWindowHeight() // align to parent window height
        
        // Draw FINAL header
        val finalColX = startX + labelColW + getColumnOffset("final")
        dl.addLine(finalColX - CELL_PAD * 0.5f, startY, finalColX - CELL_PAD * 0.5f, bottomY, lineCol, 1f)
        
        val isFinalHovered = mousePos.x >= finalColX && mousePos.x <= (finalColX + CELL) && mousePos.y >= startY && mousePos.y <= bottomY
        if (isFinalHovered) {
            dl.addRectFilled(finalColX, startY, finalColX + CELL, startY + headerH, ImGui.colorConvertFloat4ToU32(1f, 1f, 1f, 0.08f), 3f)
        }
        
        var twFinal = 0f
        val labelFinal = "F\nI\nN\nA\nL"
        UITheme.withFont(UITheme.FontLevel.CAPTION) { twFinal = ImGui.calcTextSize(labelFinal).x }
        var offsetX = ((CELL - twFinal) * 0.5f).coerceAtLeast(0f)
        ImGui.setCursorScreenPos(finalColX + offsetX, startY)
        ImGui.pushStyleColor(imgui.flag.ImGuiCol.Text, getCvColor("final"))
        UITheme.caption(labelFinal)
        ImGui.popStyleColor()

        // Draw MIDI header
        val midiColX = startX + labelColW + getColumnOffset("midi")
        dl.addLine(midiColX - CELL_PAD * 0.5f, startY, midiColX - CELL_PAD * 0.5f, bottomY, lineCol, 1f)
        
        val isMidiHovered = mousePos.x >= midiColX && mousePos.x <= (midiColX + CELL) && mousePos.y >= startY && mousePos.y <= bottomY
        if (isMidiHovered) {
            dl.addRectFilled(midiColX, startY, midiColX + CELL, startY + headerH, ImGui.colorConvertFloat4ToU32(1f, 1f, 1f, 0.08f), 3f)
        }
        
        var twMidi = 0f
        val labelMidi = "M\nI\nD\nI"
        UITheme.withFont(UITheme.FontLevel.CAPTION) { twMidi = ImGui.calcTextSize(labelMidi).x }
        offsetX = ((CELL - twMidi) * 0.5f).coerceAtLeast(0f)
        ImGui.setCursorScreenPos(midiColX + offsetX, startY)
        ImGui.pushStyleColor(imgui.flag.ImGuiCol.Text, getCvColor("midi"))
        UITheme.caption(labelMidi)
        ImGui.popStyleColor()

        // Draw each column header vertically
        val cvCols = getCvColumns()
        for ((idx, label) in cvLabelsVertical.withIndex()) {
            val cvId = cvCols[idx]
            val colX = startX + labelColW + getColumnOffset(cvId)
            dl.addLine(colX - CELL_PAD * 0.5f, startY, colX - CELL_PAD * 0.5f, bottomY, lineCol, 1f)
            
            val isCvHovered = mousePos.x >= colX && mousePos.x <= (colX + CELL) && mousePos.y >= startY && mousePos.y <= bottomY
            if (isCvHovered) {
                dl.addRectFilled(colX, startY, colX + CELL, startY + headerH, ImGui.colorConvertFloat4ToU32(1f, 1f, 1f, 0.08f), 3f)
            }
            
            var tw = 0f
            UITheme.withFont(UITheme.FontLevel.CAPTION) { tw = ImGui.calcTextSize(label).x }
            val offX = ((CELL - tw) * 0.5f).coerceAtLeast(0f)
            ImGui.setCursorScreenPos(colX + offX, startY)
            ImGui.pushStyleColor(imgui.flag.ImGuiCol.Text, getCvColor(cvId))
            UITheme.caption(label)
            ImGui.popStyleColor()
        }
        
        // Draw final separator line on the right edge
        val lastColId = if (cvCols.isNotEmpty()) cvCols.last() else "midi"
        val rightColX = startX + labelColW + getColumnOffset(lastColId) + CELL + CELL_PAD * 0.5f
        dl.addLine(rightColX, startY, rightColX, bottomY, lineCol, 1f)
        
        // Restore cursor to where the dummy left off
        ImGui.setCursorScreenPos(startX, afterHeadersY)
    }

    private fun drawTopTabs(state: PatchGridState) {
        ImGui.pushStyleVar(imgui.flag.ImGuiStyleVar.ItemSpacing, 0f, 0f)
        val tabs = listOf("Mixer", "Deck A", "Deck B")
        val buttonWidth = 100f
        tabs.forEachIndexed { i, tab ->
            if (i > 0) ImGui.sameLine()
            val isActive = state.activeTopTab == tab
            if (isActive) {
                val activeCol = when(tab) {
                    "Deck A" -> ImGui.colorConvertFloat4ToU32(0.2f, 0.4f, 0.8f, 1f)
                    "Deck B" -> ImGui.colorConvertFloat4ToU32(0.8f, 0.4f, 0.2f, 1f)
                    else -> ImGui.colorConvertFloat4ToU32(0.4f, 0.4f, 0.4f, 1f)
                }
                ImGui.pushStyleColor(imgui.flag.ImGuiCol.Button, activeCol)
                ImGui.pushStyleColor(imgui.flag.ImGuiCol.ButtonHovered, activeCol)
                ImGui.pushStyleColor(imgui.flag.ImGuiCol.ButtonActive, activeCol)
            } else {
                ImGui.pushStyleColor(imgui.flag.ImGuiCol.Button, ImGui.colorConvertFloat4ToU32(0.1f, 0.1f, 0.1f, 1f))
                ImGui.pushStyleColor(imgui.flag.ImGuiCol.ButtonHovered, ImGui.colorConvertFloat4ToU32(0.2f, 0.2f, 0.2f, 1f))
                ImGui.pushStyleColor(imgui.flag.ImGuiCol.ButtonActive, ImGui.colorConvertFloat4ToU32(0.3f, 0.3f, 0.3f, 1f))
            }
            if (ImGui.button(tab, buttonWidth, 24f)) {
                state.activeTopTab = tab
            }
            ImGui.popStyleColor(3)
        }
        ImGui.popStyleVar()
    }

    private fun getDeckSubTabs(deck: Deck): List<String> {
        val tabs = mutableListOf<String>()
        val activeSource = deck.source
        if (activeSource is Mandala) {
            tabs.addAll(listOf("View", "Geometry", "Color", "Background"))
        } else if (activeSource is DynamicVisualSource) {
            val transformNames = setOf("Zoom", "Rotate X", "Rotate Y", "Rotate Z", "Cam Rotate X", "Cam Rotate Y", "Cam Rotate Z")
            if (activeSource.parameters.keys.any { transformNames.contains(it) }) {
                tabs.add("View")
            }
            tabs.add(activeSource.displayName)
        }
        tabs.add("Feedback")
        return tabs.distinct()
    }

    private fun drawSubTabs(state: PatchGridState, mixer: Mixer) {
        if (state.activeTopTab == "Mixer") {
            ImGui.dummy(0f, 24f) // placeholder to prevent jumping
            return
        }
        
        val deck = if (state.activeTopTab == "Deck A") mixer.deckA else mixer.deckB
        val tabs = getDeckSubTabs(deck)
        val activeSubTab = if (state.activeTopTab == "Deck A") state.activeDeckASubTab else state.activeDeckBSubTab
        
        // Auto-correct invalid subtab
        if (activeSubTab !in tabs && tabs.isNotEmpty()) {
            if (state.activeTopTab == "Deck A") state.activeDeckASubTab = tabs.first()
            else state.activeDeckBSubTab = tabs.first()
        }
        
        val currentSubTab = if (state.activeTopTab == "Deck A") state.activeDeckASubTab else state.activeDeckBSubTab
        
        ImGui.pushStyleVar(imgui.flag.ImGuiStyleVar.ItemSpacing, 0f, 0f)
        tabs.forEachIndexed { i, tab ->
            if (i > 0) ImGui.sameLine()
            val isActive = currentSubTab == tab
            if (isActive) {
                val bgCol = getSubTabColor(tab, 1f)
                ImGui.pushStyleColor(imgui.flag.ImGuiCol.Button, bgCol)
                ImGui.pushStyleColor(imgui.flag.ImGuiCol.ButtonHovered, bgCol)
                ImGui.pushStyleColor(imgui.flag.ImGuiCol.ButtonActive, bgCol)
            } else {
                ImGui.pushStyleColor(imgui.flag.ImGuiCol.Button, ImGui.colorConvertFloat4ToU32(0.15f, 0.15f, 0.15f, 1f))
                ImGui.pushStyleColor(imgui.flag.ImGuiCol.ButtonHovered, ImGui.colorConvertFloat4ToU32(0.25f, 0.25f, 0.25f, 1f))
                ImGui.pushStyleColor(imgui.flag.ImGuiCol.ButtonActive, ImGui.colorConvertFloat4ToU32(0.35f, 0.35f, 0.35f, 1f))
            }
            // Dynamic width based on text
            var tw = 0f
            UITheme.withFont(UITheme.FontLevel.BODY) { tw = ImGui.calcTextSize(tab).x }
            val btnW = (tw + 20f).coerceAtLeast(80f)
            
            if (ImGui.button(tab, btnW, 24f)) {
                if (state.activeTopTab == "Deck A") state.activeDeckASubTab = tab
                else state.activeDeckBSubTab = tab
            }
            ImGui.popStyleColor(3)
        }
        ImGui.popStyleVar()
    }

    private fun getSubTabColor(label: String, alpha: Float): Int {
        return when(label) {
            "Geometry" -> ImGui.colorConvertFloat4ToU32(0.3f, 0.8f, 0.5f, alpha)
            "3D Geometry" -> ImGui.colorConvertFloat4ToU32(0.6f, 0.4f, 0.8f, alpha)
            "Color" -> ImGui.colorConvertFloat4ToU32(0.8f, 0.4f, 0.6f, alpha)
            "Background" -> ImGui.colorConvertFloat4ToU32(0.4f, 0.5f, 0.8f, alpha)
            "Feedback" -> ImGui.colorConvertFloat4ToU32(0.8f, 0.7f, 0.3f, alpha)
            else -> ImGui.colorConvertFloat4ToU32(0.5f, 0.5f, 0.5f, alpha)
        }
    }

    private inline fun drawSubGroupContent(parentLabel: String, label: String, state: PatchGridState, block: () -> Unit) {
        val key = "$parentLabel/$label"
        
        val isVisible = if (parentLabel == "Mixer") {
            state.activeTopTab == "Mixer"
        } else {
            val activeSubTab = if (parentLabel == "Deck A") state.activeDeckASubTab else state.activeDeckBSubTab
            activeSubTab == label
        }

        if (!isVisible) return

        val startY = ImGui.getCursorScreenPosY()
        val prevHeight = state.subgroupHeight[key] ?: 0f
        if (prevHeight > 0f && parentLabel != "Mixer") {
            val dlBg = ImGui.getWindowDrawList()
            val bgCol = getSubTabColor(label, 0.04f)
            val lastVisibleCol = getCvColumns().lastOrNull() ?: "midi"
            val avail = ImGui.getContentRegionAvailX()
            val maxGridW = getColumnOffset(lastVisibleCol) + CELL + CELL_PAD * 0.5f
            val labelColW = (avail - maxGridW - 20f).coerceAtLeast(120f)
            val rowWidth = labelColW + maxGridW
            dlBg.addRectFilled(gridStartX + 2f, startY - 2f, gridStartX + rowWidth - 2f, startY + prevHeight + 2f, bgCol, 4f)
        }

        val subStartY = ImGui.getCursorScreenPosY()
        val dl = ImGui.getWindowDrawList()
        
        ImGui.indent()
        block()
        ImGui.unindent()
        
        val endY = ImGui.getCursorScreenPosY()
        
        if (parentLabel != "Mixer") {
            val startX = gridStartX + 5f
            val lineCol = getSubTabColor(label, 0.6f)
            dl.addLine(startX, subStartY, startX, endY - 2f, lineCol, 3f)
        }
        
        state.subgroupHeight[key] = endY - startY
    }

    private fun drawDeckGroupContent(deckLabel: String, deck: Deck, state: PatchGridState, labelColW: Float, mixer: Mixer) {
        val activeSource = deck.source
        val mandala = activeSource as? Mandala
        
        if (mandala != null) {
            drawSubGroupContent(deckLabel, "View", state) {
                drawParamRow("Zoom",     "$deckLabel/View/Zoom",     mandala.parameters["Zoom"]!!,     state, labelColW, mixer)
                drawParamRow("Rotate Z", "$deckLabel/View/RotateZ",   mandala.parameters["Rotate Z"]!!,   state, labelColW, mixer)
                
                val modeVal = mandala.parameters["3D Mode"]?.value ?: 0f
                val mode = modeVal.roundToInt().coerceIn(0, 4)
                if (mode > 0) {
                    drawParamRow("Rotate X", "$deckLabel/View/RotateX", mandala.parameters["Rotate X"]!!, state, labelColW, mixer)
                    drawParamRow("Rotate Y", "$deckLabel/View/RotateY", mandala.parameters["Rotate Y"]!!, state, labelColW, mixer)
                    drawParamRow("3D Persp", "$deckLabel/View/Persp",   mandala.parameters["3D Persp"]!!,  state, labelColW, mixer)
                }
            }
            drawSubGroupContent(deckLabel, "Geometry", state) {
                drawParamRow("Lobe Count", "$deckLabel/Geometry/Lobes",    mandala.parameters["Lobes"]!!,         state, labelColW, mixer)
                drawParamRow("Recipe ID",  "$deckLabel/Geometry/Recipe",   mandala.parameters["Recipe Select"]!!,  state, labelColW, mixer)
                drawParamRow("L1",       "$deckLabel/Geometry/L1",       mandala.parameters["L1"]!!,       state, labelColW, mixer)
                drawParamRow("L2",       "$deckLabel/Geometry/L2",       mandala.parameters["L2"]!!,       state, labelColW, mixer)
                drawParamRow("L3",       "$deckLabel/Geometry/L3",       mandala.parameters["L3"]!!,       state, labelColW, mixer)
                drawParamRow("L4",       "$deckLabel/Geometry/L4",       mandala.parameters["L4"]!!,       state, labelColW, mixer)
                drawParamRow("Freq Offset", "$deckLabel/Geometry/FreqOffset", mandala.parameters["Freq Offset"]!!, state, labelColW, mixer)
                drawParamRow("Harmonic Lock", "$deckLabel/Geometry/HarmonicLock", mandala.parameters["Harmonic Lock"]!!, state, labelColW, mixer)

                val modeVal = mandala.parameters["3D Mode"]?.value ?: 0f
                val mode = modeVal.roundToInt().coerceIn(0, 4)
                drawParamRow("3D Mode", "$deckLabel/Geometry/3DMode", mandala.parameters["3D Mode"]!!, state, labelColW, mixer)

                if (mode == 1) {
                    drawParamRow("Sphere Wrap X", "$deckLabel/Geometry/SphereWrapX", mandala.parameters["Sphere Wrap X"]!!, state, labelColW, mixer)
                    drawParamRow("Sphere Wrap Y", "$deckLabel/Geometry/SphereWrapY", mandala.parameters["Sphere Wrap Y"]!!, state, labelColW, mixer)
                } else if (mode == 2) {
                    drawParamRow("Mirror Group",  "$deckLabel/Geometry/MirrorGroup", mandala.parameters["Mirror Group"]!!,  state, labelColW, mixer)
                    drawParamRow("Sphere Wrap X", "$deckLabel/Geometry/SphereWrapX", mandala.parameters["Sphere Wrap X"]!!, state, labelColW, mixer)
                    drawParamRow("Sphere Wrap Y", "$deckLabel/Geometry/SphereWrapY", mandala.parameters["Sphere Wrap Y"]!!, state, labelColW, mixer)
                } else if (mode == 3) {
                    drawParamRow("Permute XY",    "$deckLabel/Geometry/PermuteXY",   mandala.parameters["Permute XY"]!!,    state, labelColW, mixer)
                    drawParamRow("Permute YZ",    "$deckLabel/Geometry/PermuteYZ",   mandala.parameters["Permute YZ"]!!,    state, labelColW, mixer)
                    drawParamRow("Permute ZX",    "$deckLabel/Geometry/PermuteZX",   mandala.parameters["Permute ZX"]!!,    state, labelColW, mixer)
                } else if (mode == 4) {
                    drawParamRow("Mirror Group",  "$deckLabel/Geometry/MirrorGroup", mandala.parameters["Mirror Group"]!!,  state, labelColW, mixer)
                }
            }
            drawSubGroupContent(deckLabel, "Color", state) {
                drawParamRow("Thickness",  "$deckLabel/Color/Thickness",  mandala.parameters["Thickness"]!!,  state, labelColW, mixer)
                drawParamRow("Hue Offset", "$deckLabel/Color/HueOffset",  mandala.parameters["Hue Offset"]!!, state, labelColW, mixer)
                drawParamRow("Hue Sweep",  "$deckLabel/Color/HueSweep",   mandala.parameters["Hue Sweep"]!!,  state, labelColW, mixer)
                drawParamRow("Depth",      "$deckLabel/Color/Depth",      mandala.parameters["Depth"]!!,      state, labelColW, mixer)
                drawParamRow("Gain",       "$deckLabel/Color/Gain",       mandala.globalAlpha,                 state, labelColW, mixer)
            }
            drawSubGroupContent(deckLabel, "Background", state) {
                drawParamRow("Bg Style",    "$deckLabel/Background/Style",    mandala.parameters["Bg Style"]!!,    state, labelColW, mixer)
                drawParamRow("Bg Feedback", "$deckLabel/Background/Feedback", mandala.parameters["Bg Feedback"]!!, state, labelColW, mixer)
                drawParamRow("Bg Hue",      "$deckLabel/Background/Hue",      mandala.parameters["Bg Hue"]!!,      state, labelColW, mixer)
                drawParamRow("Bg Sat",      "$deckLabel/Background/Sat",      mandala.parameters["Bg Sat"]!!,      state, labelColW, mixer)
                drawParamRow("Bg Val",      "$deckLabel/Background/Val",      mandala.parameters["Bg Val"]!!,      state, labelColW, mixer)
                drawParamRow("Bg Sweep",    "$deckLabel/Background/Sweep",    mandala.parameters["Bg Sweep"]!!,    state, labelColW, mixer)
                drawParamRow("Bg Speed",    "$deckLabel/Background/Speed",    mandala.parameters["Bg Speed"]!!,    state, labelColW, mixer)
                drawParamRow("Bg Zoom",     "$deckLabel/Background/Zoom",     mandala.parameters["Bg Zoom"]!!,     state, labelColW, mixer)
            }
        } else if (activeSource is DynamicVisualSource) {
            val transformNames = setOf("Zoom", "Rotate X", "Rotate Y", "Rotate Z", "Cam Rotate X", "Cam Rotate Y", "Cam Rotate Z")
            
            val transformParams = mutableListOf<Map.Entry<String, ModulatableParameter>>()
            val otherParams = mutableListOf<Map.Entry<String, ModulatableParameter>>()
            
            activeSource.parameters.forEach { entry ->
                if (transformNames.contains(entry.key)) {
                    transformParams.add(entry)
                } else {
                    otherParams.add(entry)
                }
            }
            
            if (transformParams.isNotEmpty()) {
                drawSubGroupContent(deckLabel, "View", state) {
                    transformParams.forEach { (name, param) ->
                        drawParamRow(name, "$deckLabel/View/$name", param, state, labelColW, mixer)
                    }
                }
            }
            
            drawSubGroupContent(deckLabel, activeSource.displayName, state) {
                otherParams.forEach { (name, param) ->
                    drawParamRow(name, "$deckLabel/${activeSource.displayName}/$name", param, state, labelColW, mixer)
                }
                drawParamRow("Gain", "$deckLabel/${activeSource.displayName}/Gain", activeSource.globalAlpha, state, labelColW, mixer)
            }
        }

        drawSubGroupContent(deckLabel, "Feedback", state) {
            drawParamRow("Feedback",    "$deckLabel/FB/Decay",    deck.fbDecay,    state, labelColW, mixer)
            drawParamRow("FB Gain",     "$deckLabel/FB/Gain",     deck.fbGain,     state, labelColW, mixer)
            drawParamRow("FB Zoom",     "$deckLabel/FB/Zoom",     deck.fbZoom,     state, labelColW, mixer)
            drawParamRow("FB Rotate",   "$deckLabel/FB/Rotate",   deck.fbRotate,   state, labelColW, mixer)
            drawParamRow("FB Hue Shift","$deckLabel/FB/HueShift", deck.fbHueShift, state, labelColW, mixer)
            drawParamRow("FB Blur",     "$deckLabel/FB/Blur",     deck.fbBlur,     state, labelColW, mixer)
            drawParamRow("FB Chroma",   "$deckLabel/FB/Chroma",   deck.fbChroma,   state, labelColW, mixer)
            drawParamRow("FB Mode",     "$deckLabel/FB/Mode",     deck.fbMode,     state, labelColW, mixer)
        }
    }

    /**
     * Draws one horizontal row: the parameter label + one cell per CV column.
     */
    private fun drawParamRow(
        label: String,
        paramKey: String,
        param: ModulatableParameter,
        state: PatchGridState,
        labelColW: Float,
        mixer: Mixer
    ) {
        val isEven = (rowIndex % 2 == 0)
        rowIndex++

        val mousePos = ImGui.getIO().mousePos
        val rowScreenY = ImGui.getCursorScreenPosY()
        val lastVisibleCol = getCvColumns().lastOrNull() ?: "midi"
        val rowWidth = labelColW + getColumnOffset(lastVisibleCol) + CELL + CELL_PAD * 0.5f
        val isHoveredRow = mousePos.y >= rowScreenY && mousePos.y <= (rowScreenY + CELL) && mousePos.x >= gridStartX && mousePos.x <= (gridStartX + rowWidth)

        ImGui.pushID(paramKey)

        if (isEven || isHoveredRow) {
            val dl = ImGui.getWindowDrawList()
            val stripeCol = if (isHoveredRow) {
                ImGui.colorConvertFloat4ToU32(1f, 1f, 1f, 0.06f)
            } else {
                ImGui.colorConvertFloat4ToU32(1f, 1f, 1f, 0.03f)
            }
            dl.addRectFilled(gridStartX, rowScreenY, gridStartX + rowWidth, rowScreenY + CELL, stripeCol)
        }

        // Row label
        val rowX = ImGui.getCursorPosX()
        val rowY = ImGui.getCursorPosY()
        
        ImGui.setCursorPosY(rowY + (CELL - ImGui.getTextLineHeight()) * 0.5f)
        val cursorStartX = ImGui.getCursorPosX()
        val indent = ImGui.getCursorScreenPosX() - gridStartX
        val labelBtnW = labelColW - indent - CELL_PAD
        UITheme.body(label)
        ImGui.sameLine(cursorStartX)
        ImGui.invisibleButton("row_label_btn_$paramKey", labelBtnW, CELL)
        if (ImGui.beginPopupContextItem("row_menu_$paramKey")) {
            if (ImGui.menuItem("Randomize row")) {
                pushUndoState(state, mixer)
                val randomized = param.modulators.map { it.randomizeActiveValues() }
                param.modulators.clear()
                param.modulators.addAll(randomized)
                param.randomizeBaseValue()
            }
            ImGui.separator()
            if (ImGui.menuItem("Copy Row Modulations")) {
                ClipboardManager.rowClipboard = RowClipboardData(paramKey, param.toDto())
            }
            val hasRowClip = ClipboardManager.rowClipboard != null
            if (ImGui.menuItem("Paste Row Modulations", null, false, hasRowClip)) {
                pushUndoState(state, mixer)
                ClipboardManager.rowClipboard?.let { ClipboardManager.applyRowClipboard(param, it, mixer) }
            }
            ImGui.separator()
            if (ImGui.menuItem("Reset Parameter to Default")) {
                pushUndoState(state, mixer)
                param.reset()
            }
            if (ImGui.menuItem("Clear all CVs")) {
                pushUndoState(state, mixer)
                param.modulators.clear()
            }
            val hasMidiMap = llm.slop.spirals.midi.MidiMappingManager.hasMapping(paramKey)
            if (ImGui.menuItem("Clear MIDI mapping", null, false, hasMidiMap)) {
                llm.slop.spirals.midi.MidiMappingManager.removeMapping(paramKey)
                llm.slop.spirals.midi.MidiMappingManager.saveActiveProfile()
            }
            ImGui.endPopup()
        }
        ImGui.setCursorPosY(rowY)

        val dl = ImGui.getWindowDrawList()
        val r = CELL * 0.5f

        // 1. FINAL Cell
        val finalX = gridStartX + labelColW + getColumnOffset("final")
        val finalY = rowScreenY
        val isFinalSelected = state.selectedCell?.paramKey == paramKey && state.selectedCell?.cvSourceId == "final"
        val isFinalHoveredCol = mousePos.x >= finalX && mousePos.x <= (finalX + CELL)
        
        ImGui.setCursorScreenPos(finalX, finalY)
        ImGui.invisibleButton("##final_cell", CELL, CELL)
        if (ImGui.isItemClicked()) {
            state.select(PatchCellId(paramKey, "final"), param)
        }
        
        val finalBgCol = when {
            isFinalSelected -> ImGui.colorConvertFloat4ToU32(0.15f, 0.4f, 0.6f, 1f)
            else            -> getCvColor("final", 0.05f) // Subtle background tint
        }
        val finalBorderCol = when {
            isFinalSelected -> ImGui.colorConvertFloat4ToU32(0.3f, 0.7f, 1.0f, 1f)
            else            -> ImGui.colorConvertFloat4ToU32(0.2f, 0.2f, 0.2f, 1f)
        }
        val finalColor = getCvColor("final") // Active meter color
        
        drawKnobMeter(
            dl = dl, x = finalX, y = finalY, r = r,
            value = param.value, min = param.minClamp, max = param.maxClamp,
            meterType = param.meterType,
            baseValue = param.baseValue, baseMin = param.baseMin, baseMax = param.baseMax,
            color = finalColor, bgCol = finalBgCol, borderCol = finalBorderCol,
            isHoveredRow = isHoveredRow, isHoveredCol = isFinalHoveredCol
        )

        // 2.5 MIDI Cell
        val midiX = gridStartX + labelColW + getColumnOffset("midi")
        val midiY = rowScreenY
        val midiCellId = PatchCellId(paramKey, "midi")
        val isMidiSelected = state.selectedCell == midiCellId
        val isMidiHoveredCol = mousePos.x >= midiX && mousePos.x <= (midiX + CELL)
        val isMidiCrosshair = isHoveredRow && isMidiHoveredCol
        
        // Find any MIDI modulator for this parameter
        val midiMods = param.modulators.filter { it.sourceId.startsWith("midi_cc_") }
        val hasMidiMod = midiMods.any { !it.bypassed }
        val isMidiBypassed = midiMods.isNotEmpty() && midiMods.all { it.bypassed }
        
        val isMidiTarget = state.midiLearnTarget?.let {
            it is MidiLearnTarget.GridCell && it.cellId == midiCellId
        } ?: false

        ImGui.setCursorScreenPos(midiX, midiY)
        ImGui.invisibleButton("##midi_cell", CELL, CELL)
        if (ImGui.isItemClicked()) {
            if (state.isMidiLearnMode) {
                state.midiLearnTarget = MidiLearnTarget.GridCell(midiCellId, param)
            } else {
                state.select(midiCellId, param)
            }
        }
        
        if (ImGui.beginPopupContextItem("midi_cell_menu_$paramKey")) {
            if (midiMods.isNotEmpty()) {
                if (ImGui.menuItem("Clear MIDI Modulator")) {
                    pushUndoState(state, mixer)
                    param.modulators.removeAll(midiMods)
                }
                if (ImGui.menuItem(if (isMidiBypassed) "Enable MIDI Modulator" else "Bypass MIDI Modulator")) {
                    pushUndoState(state, mixer)
                    val updated = param.modulators.map {
                        if (it.sourceId.startsWith("midi_cc_")) it.copy(bypassed = !it.bypassed) else it
                    }
                    param.modulators.clear()
                    param.modulators.addAll(updated)
                }
            }
            ImGui.endPopup()
        }

        val midiBgCol = when {
            isMidiTarget   -> ImGui.colorConvertFloat4ToU32(0.0f, 0.4f, 0.5f, 1f)
            isMidiSelected -> ImGui.colorConvertFloat4ToU32(0.15f, 0.4f, 0.6f, 1f)
            hasMidiMod     -> ImGui.colorConvertFloat4ToU32(0.05f, 0.15f, 0.2f, 1f)
            else           -> getCvColor("midi", 0.05f)
        }
        val midiBorderCol = when {
            isMidiTarget   -> ImGui.colorConvertFloat4ToU32(0.0f, 0.8f, 1.0f, 1f)
            isMidiSelected -> ImGui.colorConvertFloat4ToU32(0.3f, 0.7f, 1.0f, 1f)
            hasMidiMod     -> ImGui.colorConvertFloat4ToU32(0.2f, 0.5f, 0.7f, 0.8f)
            else           -> ImGui.colorConvertFloat4ToU32(0.2f, 0.2f, 0.2f, 1f)
        }
        
        if (hasMidiMod || isMidiBypassed) {
            val liveVal = llm.slop.spirals.cv.getCombinedModulatorValue(midiMods).coerceIn(-1f, 1f)
            // Map the -1..1 modulator value to the parameter's visual range for display
            // For display purposes, map the raw [-1,1] CV symmetrically onto the param range.
            // This matches amp=1, dc=0 showing a full-range wave on the O-Scope.
            val displayValue = run {
                val range = param.maxClamp - param.minClamp
                param.minClamp + ((liveVal + 1f) / 2f) * range
            }
            
            drawKnobMeter(
                dl = dl, x = midiX, y = midiY, r = r,
                value = displayValue, min = param.minClamp, max = param.maxClamp,
                meterType = param.meterType,
                baseValue = null, baseMin = null, baseMax = null,
                color = getCvColor("midi"),
                bgCol = midiBgCol, borderCol = midiBorderCol,
                isBypassed = isMidiBypassed,
                isHoveredRow = isHoveredRow, isHoveredCol = isMidiHoveredCol
            )
        } else {
            // Draw empty cell
            dl.addRectFilled(midiX, midiY, midiX + CELL, midiY + CELL, midiBgCol, 3f)
            if (isMidiCrosshair) {
                dl.addRectFilled(midiX, midiY, midiX + CELL, midiY + CELL, ImGui.colorConvertFloat4ToU32(1f, 1f, 1f, 0.15f), 3f)
            } else if (isMidiHoveredCol) {
                dl.addRectFilled(midiX, midiY, midiX + CELL, midiY + CELL, ImGui.colorConvertFloat4ToU32(1f, 1f, 1f, 0.05f), 3f)
            }
            val border = if (isMidiCrosshair) ImGui.colorConvertFloat4ToU32(1f, 1f, 1f, 0.6f) else midiBorderCol
            dl.addRect(midiX, midiY, midiX + CELL, midiY + CELL, border, 3f)
        }

        // 3. CV cells
        val cvCols = getCvColumns()
        for (cvId in cvCols) {
            val cellId = PatchCellId(paramKey, cvId)
            val isSelected = state.selectedCell == cellId
            val activeMods = if (cvId == "audio") {
                param.modulators.filter { it.sourceId in setOf("audio_amp", "audio_bass", "audio_mid", "audio_high") }
            } else if (cvId == "trigger") {
                param.modulators.filter { it.sourceId in setOf("trigger_onset", "trigger_accent") }
            } else {
                param.modulators.filter { it.sourceId == cvId }
            }
            val hasModulator = activeMods.any { !it.bypassed }
            val isBypassed = activeMods.isNotEmpty() && activeMods.all { it.bypassed }

            val x = gridStartX + labelColW + getColumnOffset(cvId)
            val y = rowScreenY

            val isHoveredCol = mousePos.x >= x && mousePos.x <= (x + CELL)
            val isCrosshair = isHoveredRow && isHoveredCol

            val isTarget = state.midiLearnTarget?.let {
                it is MidiLearnTarget.GridCell && it.cellId == cellId
            } ?: false

            ImGui.setCursorScreenPos(x, y)
            ImGui.invisibleButton("##cell_$cvId", CELL, CELL)
            if (ImGui.isItemClicked()) {
                if (state.isMidiLearnMode) {
                    state.midiLearnTarget = MidiLearnTarget.GridCell(cellId, param)
                } else {
                    state.select(cellId, param)
                }
            }
            if (ImGui.beginPopupContextItem("cell_menu_$paramKey-$cvId")) {
                if (ImGui.menuItem("Copy Cell Modulators")) {
                    ClipboardManager.cellClipboard = CellClipboardData(paramKey, cvId, activeMods.map { it.toDto() })
                }
                val hasCellClip = ClipboardManager.cellClipboard != null
                if (ImGui.menuItem("Paste Modulator(s)", null, false, hasCellClip)) {
                    pushUndoState(state, mixer)
                    ClipboardManager.cellClipboard?.let { ClipboardManager.applyCellClipboard(param, cvId, it) }
                }
                if (activeMods.isNotEmpty()) {
                    if (ImGui.menuItem("Clear Modulator(s)")) {
                        pushUndoState(state, mixer)
                        param.modulators.removeAll(activeMods)
                    }
                    if (ImGui.menuItem(if (isBypassed) "Enable Modulator(s)" else "Bypass Modulator(s)")) {
                        pushUndoState(state, mixer)
                        val updated = param.modulators.map { mod ->
                            if (activeMods.any { it.id == mod.id }) {
                                mod.copy(bypassed = !mod.bypassed)
                            } else mod
                        }
                        param.modulators.clear()
                        param.modulators.addAll(updated)
                    }
                }
                ImGui.endPopup()
            }

            val bgCol = when {
                isTarget      -> ImGui.colorConvertFloat4ToU32(0.0f, 0.4f, 0.5f, 1f) // listening target
                isSelected    -> ImGui.colorConvertFloat4ToU32(0.15f, 0.4f, 0.6f, 1f)
                hasModulator  -> ImGui.colorConvertFloat4ToU32(0.05f, 0.15f, 0.2f, 1f)
                else          -> getCvColor(cvId, 0.05f)
            }
            val borderCol = when {
                isTarget     -> ImGui.colorConvertFloat4ToU32(0.0f, 0.8f, 1.0f, 1f) // bright cyan
                isSelected   -> ImGui.colorConvertFloat4ToU32(0.3f, 0.7f, 1.0f, 1f)
                hasModulator -> ImGui.colorConvertFloat4ToU32(0.2f, 0.5f, 0.7f, 0.8f)
                else         -> ImGui.colorConvertFloat4ToU32(0.2f, 0.2f, 0.2f, 1f)
            }

            if (hasModulator || isBypassed) {
                val liveVal = llm.slop.spirals.cv.getCombinedModulatorValue(activeMods).coerceIn(-1f, 1f)
                // For display purposes, map the raw [-1,1] CV symmetrically onto the param range.
                val displayValue = run {
                    val range = param.maxClamp - param.minClamp
                    param.minClamp + ((liveVal + 1f) / 2f) * range
                }
                
                drawKnobMeter(
                    dl = dl, x = x, y = y, r = r,
                    value = displayValue, min = param.minClamp, max = param.maxClamp,
                    meterType = param.meterType,
                    baseValue = null, baseMin = null, baseMax = null,
                    color = getCvColor(cvId),
                    bgCol = bgCol, borderCol = borderCol,
                    isBypassed = isBypassed,
                    isHoveredRow = isHoveredRow, isHoveredCol = isHoveredCol
                )
            } else {
                dl.addRectFilled(x, y, x + CELL, y + CELL, bgCol, 3f)
                if (isCrosshair) {
                    dl.addRectFilled(x, y, x + CELL, y + CELL, ImGui.colorConvertFloat4ToU32(1f, 1f, 1f, 0.15f), 3f)
                } else if (isHoveredCol) {
                    dl.addRectFilled(x, y, x + CELL, y + CELL, ImGui.colorConvertFloat4ToU32(1f, 1f, 1f, 0.05f), 3f)
                }
                val border = if (isCrosshair) ImGui.colorConvertFloat4ToU32(1f, 1f, 1f, 0.6f) else borderCol
                dl.addRect(x, y, x + CELL, y + CELL, border, 3f)
            }
        }

        ImGui.popID()
        ImGui.setCursorPos(rowX, rowY + CELL)
    }

    private fun drawKnobMeter(
        dl: ImDrawList,
        x: Float, y: Float, r: Float,
        value: Float,
        min: Float, max: Float,
        meterType: llm.slop.spirals.parameters.MeterType,
        baseValue: Float?,
        baseMin: Float?,
        baseMax: Float?,
        color: Int,
        bgCol: Int,
        borderCol: Int,
        isBypassed: Boolean = false,
        isHoveredRow: Boolean = false,
        isHoveredCol: Boolean = false
    ) {
        val cx = x + r
        val cy = y + r

        dl.addRectFilled(x, y, x + r * 2f, y + r * 2f, bgCol, 3f)
        
        if (isHoveredRow && isHoveredCol) {
            dl.addRectFilled(x, y, x + r * 2f, y + r * 2f, ImGui.colorConvertFloat4ToU32(1f, 1f, 1f, 0.15f), 3f)
        } else if (isHoveredCol) {
            dl.addRectFilled(x, y, x + r * 2f, y + r * 2f, ImGui.colorConvertFloat4ToU32(1f, 1f, 1f, 0.05f), 3f)
        }

        val border = if (isHoveredRow && isHoveredCol) ImGui.colorConvertFloat4ToU32(1f, 1f, 1f, 0.6f) else borderCol
        dl.addRect(x, y, x + r * 2f, y + r * 2f, border, 3f)

        val trackRadius = r - 5f
        val trackCol = ImGui.colorConvertFloat4ToU32(0.3f, 0.3f, 0.3f, if (isBypassed) 0.2f else 0.4f)
        val fillCol = if (isBypassed) ImGui.colorConvertFloat4ToU32(0.5f, 0.5f, 0.5f, 0.5f) else color

        val aMin = PI.toFloat() * 0.75f  // 135 deg
        val aMax = PI.toFloat() * 2.25f  // 405 deg
        val aCenter = PI.toFloat() * 1.5f // 270 deg

        val range = max - min
        val normalized = if (range == 0f) 0.5f else ((value - min) / range).coerceIn(0f, 1f)

        when (meterType) {
            llm.slop.spirals.parameters.MeterType.ENDLESS, llm.slop.spirals.parameters.MeterType.DISCRETE -> {
                dl.addCircle(cx, cy, trackRadius, trackCol, 32, 1.5f)
                val angle = (PI / 2.0) + normalized * 2.0 * PI
                val dotX = cx + trackRadius * cos(angle).toFloat()
                val dotY = cy + trackRadius * sin(angle).toFloat()
                dl.addCircleFilled(dotX, dotY, 3f, fillCol)

                if (baseValue != null) {
                    val bNorm = if (range == 0f) 0.5f else ((baseValue - min) / range).coerceIn(0f, 1f)
                    val bAngle = (PI / 2.0) + bNorm * 2.0 * PI
                    val bX = cx + trackRadius * cos(bAngle).toFloat()
                    val bY = cy + trackRadius * sin(bAngle).toFloat()
                    val bCol = ImGui.colorConvertFloat4ToU32(0.8f, 0.6f, 0.2f, 1f)
                    dl.addCircleFilled(bX, bY, 3f, bCol)
                }
            }
            llm.slop.spirals.parameters.MeterType.MONOPOLAR -> {
                dl.pathArcTo(cx, cy, trackRadius, aMin, aMax, 32)
                dl.pathStroke(trackCol, 0, 1.5f)

                if (normalized > 0f) {
                    val fillAngle = aMin + normalized * (aMax - aMin)
                    dl.pathArcTo(cx, cy, trackRadius, aMin, fillAngle, 32)
                    dl.pathStroke(fillCol, 0, 2.5f)
                }

                val valAngle = aMin + normalized * (aMax - aMin)
                val dotX = cx + trackRadius * cos(valAngle)
                val dotY = cy + trackRadius * sin(valAngle)
                dl.addCircleFilled(dotX, dotY, 3f, fillCol)

                if (baseValue != null) {
                    val bNorm = if (range == 0f) 0.5f else ((baseValue - min) / range).coerceIn(0f, 1f)
                    val bAngle = aMin + bNorm * (aMax - aMin)
                    val bX = cx + trackRadius * cos(bAngle)
                    val bY = cy + trackRadius * sin(bAngle)
                    val bCol = ImGui.colorConvertFloat4ToU32(0.8f, 0.6f, 0.2f, 1f)
                    dl.addCircleFilled(bX, bY, 2.5f, bCol)

                    if (baseMin != null && baseMax != null && baseMin != baseMax) {
                        val rMinNorm = if (range == 0f) 0.5f else ((baseMin - min) / range).coerceIn(0f, 1f)
                        val rMaxNorm = if (range == 0f) 0.5f else ((baseMax - min) / range).coerceIn(0f, 1f)
                        val rMinA = aMin + rMinNorm * (aMax - aMin)
                        val rMaxA = aMin + rMaxNorm * (aMax - aMin)
                        val rangeCol = ImGui.colorConvertFloat4ToU32(0.8f, 0.6f, 0.2f, 0.4f)
                        dl.pathArcTo(cx, cy, trackRadius - 3f, rMinA, rMaxA, 16)
                        dl.pathStroke(rangeCol, 0, 2f)
                    }
                }
            }
            llm.slop.spirals.parameters.MeterType.BIPOLAR -> {
                dl.pathArcTo(cx, cy, trackRadius, aMin, aMax, 32)
                dl.pathStroke(trackCol, 0, 1.5f)

                val cX = cx + (trackRadius - 2f) * cos(aCenter)
                val cY = cy + (trackRadius - 2f) * sin(aCenter)
                val cX2 = cx + (trackRadius + 2f) * cos(aCenter)
                val cY2 = cy + (trackRadius + 2f) * sin(aCenter)
                dl.addLine(cX, cY, cX2, cY2, trackCol, 1.5f)

                if (normalized != 0.5f) {
                    val valAngle = aMin + normalized * (aMax - aMin)
                    if (normalized > 0.5f) {
                        dl.pathArcTo(cx, cy, trackRadius, aCenter, valAngle, 16)
                    } else {
                        dl.pathArcTo(cx, cy, trackRadius, valAngle, aCenter, 16)
                    }
                    dl.pathStroke(fillCol, 0, 2.5f)
                }

                val valAngle = aMin + normalized * (aMax - aMin)
                val dotX = cx + trackRadius * cos(valAngle)
                val dotY = cy + trackRadius * sin(valAngle)
                dl.addCircleFilled(dotX, dotY, 3f, fillCol)

                if (baseValue != null) {
                    val bNorm = if (range == 0f) 0.5f else ((baseValue - min) / range).coerceIn(0f, 1f)
                    val bAngle = aMin + bNorm * (aMax - aMin)
                    val bX = cx + trackRadius * cos(bAngle)
                    val bY = cy + trackRadius * sin(bAngle)
                    val bCol = ImGui.colorConvertFloat4ToU32(0.8f, 0.6f, 0.2f, 1f)
                    dl.addCircleFilled(bX, bY, 2.5f, bCol)

                    if (baseMin != null && baseMax != null && baseMin != baseMax) {
                        val rMinNorm = if (range == 0f) 0.5f else ((baseMin - min) / range).coerceIn(0f, 1f)
                        val rMaxNorm = if (range == 0f) 0.5f else ((baseMax - min) / range).coerceIn(0f, 1f)
                        val rMinA = aMin + rMinNorm * (aMax - aMin)
                        val rMaxA = aMin + rMaxNorm * (aMax - aMin)
                        val rangeCol = ImGui.colorConvertFloat4ToU32(0.8f, 0.6f, 0.2f, 0.4f)
                        dl.pathArcTo(cx, cy, trackRadius - 3f, rMinA, rMaxA, 16)
                        dl.pathStroke(rangeCol, 0, 2f)
                    }
                }
            }
        }
    }

    private fun getAllGridRows(mixer: Mixer): List<Pair<String, ModulatableParameter>> {
        val list = mutableListOf<Pair<String, ModulatableParameter>>()
        
        // Mixer
        list.add("Deck A/FB/Source" to mixer.deckA.sourceSelect)
        list.add("Deck B/FB/Source" to mixer.deckB.sourceSelect)
        list.add("Mixer/crossfade" to mixer.crossfade)
        list.add("Mixer/masterAlpha" to mixer.masterAlpha)
        list.add("Mixer/bloom" to mixer.bloom)
        list.add("Mixer/setlistPrev" to mixer.setlistPrev)
        list.add("Mixer/setlistNext" to mixer.setlistNext)
        
        // Deck A and B
        for (deckLabel in listOf("Deck A", "Deck B")) {
            val deck = if (deckLabel == "Deck A") mixer.deckA else mixer.deckB
            val mandala = deck.source as? Mandala
            
            
            if (mandala != null) {
                // Geometry
                list.add("$deckLabel/Geometry/Lobes" to mandala.parameters["Lobes"]!!)
                list.add("$deckLabel/Geometry/Recipe" to mandala.parameters["Recipe Select"]!!)
                list.add("$deckLabel/Geometry/L1" to mandala.parameters["L1"]!!)
                list.add("$deckLabel/Geometry/L2" to mandala.parameters["L2"]!!)
                list.add("$deckLabel/Geometry/L3" to mandala.parameters["L3"]!!)
                list.add("$deckLabel/Geometry/L4" to mandala.parameters["L4"]!!)
                list.add("$deckLabel/Geometry/FreqOffset" to mandala.parameters["Freq Offset"]!!)
                list.add("$deckLabel/Geometry/HarmonicLock" to mandala.parameters["Harmonic Lock"]!!)
                list.add("$deckLabel/Geometry/3DMode" to mandala.parameters["3D Mode"]!!)
                list.add("$deckLabel/Geometry/SphereWrapX" to mandala.parameters["Sphere Wrap X"]!!)
                list.add("$deckLabel/Geometry/SphereWrapY" to mandala.parameters["Sphere Wrap Y"]!!)
                list.add("$deckLabel/Geometry/MirrorGroup" to mandala.parameters["Mirror Group"]!!)
                list.add("$deckLabel/Geometry/PermuteXY" to mandala.parameters["Permute XY"]!!)
                list.add("$deckLabel/Geometry/PermuteYZ" to mandala.parameters["Permute YZ"]!!)
                list.add("$deckLabel/Geometry/PermuteZX" to mandala.parameters["Permute ZX"]!!)

                // View
                list.add("$deckLabel/View/Zoom" to mandala.parameters["Zoom"]!!)
                list.add("$deckLabel/View/RotateZ" to mandala.parameters["Rotate Z"]!!)
                list.add("$deckLabel/View/RotateX" to mandala.parameters["Rotate X"]!!)
                list.add("$deckLabel/View/RotateY" to mandala.parameters["Rotate Y"]!!)
                list.add("$deckLabel/View/Persp" to mandala.parameters["3D Persp"]!!)
                
                // Color
                list.add("$deckLabel/Color/Thickness" to mandala.parameters["Thickness"]!!)
                list.add("$deckLabel/Color/HueOffset" to mandala.parameters["Hue Offset"]!!)
                list.add("$deckLabel/Color/HueSweep" to mandala.parameters["Hue Sweep"]!!)
                list.add("$deckLabel/Color/Depth" to mandala.parameters["Depth"]!!)
                list.add("$deckLabel/Color/Gain" to mandala.globalAlpha)
                
                // Background
                list.add("$deckLabel/Background/Style" to mandala.parameters["Bg Style"]!!)
                list.add("$deckLabel/Background/Feedback" to mandala.parameters["Bg Feedback"]!!)
                list.add("$deckLabel/Background/Hue" to mandala.parameters["Bg Hue"]!!)
                list.add("$deckLabel/Background/Sat" to mandala.parameters["Bg Sat"]!!)
                list.add("$deckLabel/Background/Val" to mandala.parameters["Bg Val"]!!)
                list.add("$deckLabel/Background/Sweep" to mandala.parameters["Bg Sweep"]!!)
                list.add("$deckLabel/Background/Speed" to mandala.parameters["Bg Speed"]!!)
                list.add("$deckLabel/Background/Zoom" to mandala.parameters["Bg Zoom"]!!)
            }

            val activeSource = deck.source
            if (activeSource is DynamicVisualSource) {
                activeSource.parameters.forEach { (name, param) ->
                    list.add("$deckLabel/${activeSource.displayName}/$name" to param)
                }
                list.add("$deckLabel/${activeSource.displayName}/Gain" to activeSource.globalAlpha)
            }
            
            // Feedback
            list.add("$deckLabel/FB/Decay" to deck.fbDecay)
            list.add("$deckLabel/FB/Gain" to deck.fbGain)
            list.add("$deckLabel/FB/Zoom" to deck.fbZoom)
            list.add("$deckLabel/FB/Rotate" to deck.fbRotate)
            list.add("$deckLabel/FB/HueShift" to deck.fbHueShift)
            list.add("$deckLabel/FB/Blur" to deck.fbBlur)
            list.add("$deckLabel/FB/Chroma" to deck.fbChroma)
            list.add("$deckLabel/FB/Mode" to deck.fbMode)
        }
        
        return list
    }

    private fun createUndoSnapshot(mixer: Mixer): PatchGridUndoSnapshot {
        return PatchGridUndoSnapshot(
            modulatorsByParamKey = getAllGridRows(mixer).associate { (paramKey, param) ->
                paramKey to param.modulators.toList()
            }
        )
    }

    private fun pushUndoState(state: PatchGridState, mixer: Mixer) {
        state.pushUndoState(createUndoSnapshot(mixer))
    }

    private fun performUndo(state: PatchGridState, mixer: Mixer) {
        val snapshot = state.popUndoState()
        if (snapshot != null) {
            val allRows = getAllGridRows(mixer)
            for ((paramKey, param) in allRows) {
                val savedModulators = snapshot.modulatorsByParamKey[paramKey]
                if (savedModulators != null) {
                    param.modulators.clear()
                    param.modulators.addAll(savedModulators)
                }
            }
        }
    }

    private fun handleKeyboardShortcuts(state: PatchGridState, mixer: Mixer) {
        val io = ImGui.getIO()
        val ctrl = io.keyCtrl
        if (ctrl) {
            val cKey = ImGui.getKeyIndex(ImGuiKey.C)
            val vKey = ImGui.getKeyIndex(ImGuiKey.V)
            val zKey = ImGui.getKeyIndex(ImGuiKey.Z)

            if (ImGui.isKeyPressed(cKey)) {
                val cell = state.selectedCell
                val param = state.selectedParam
                if (cell != null && param != null && cell.cvSourceId != "base" && cell.cvSourceId != "final") {
                    val activeMods = if (cell.cvSourceId == "audio") {
                        param.modulators.filter { it.sourceId in setOf("audio_amp", "audio_bass", "audio_mid", "audio_high") }
                    } else if (cell.cvSourceId == "trigger") {
                        param.modulators.filter { it.sourceId in setOf("trigger_onset", "trigger_accent") }
                    } else {
                        param.modulators.filter { it.sourceId == cell.cvSourceId }
                    }
                    ClipboardManager.cellClipboard = CellClipboardData(cell.paramKey, cell.cvSourceId, activeMods.map { it.toDto() })
                }
            }
            if (ImGui.isKeyPressed(vKey)) {
                val cell = state.selectedCell
                val param = state.selectedParam
                if (cell != null && param != null && cell.cvSourceId != "base" && cell.cvSourceId != "final") {
                    ClipboardManager.cellClipboard?.let { clip ->
                        pushUndoState(state, mixer)
                        ClipboardManager.applyCellClipboard(param, cell.cvSourceId, clip)
                    }
                }
            }
            if (ImGui.isKeyPressed(zKey)) {
                performUndo(state, mixer)
            }
        } else {
            val deleteKey = ImGui.getKeyIndex(ImGuiKey.Delete)
            if (ImGui.isKeyPressed(deleteKey)) {
                val cell = state.selectedCell
                val param = state.selectedParam
                if (cell != null && param != null) {
                    if (cell.cvSourceId == "midi") {
                        val midiMods = param.modulators.filter { it.sourceId.startsWith("midi_cc_") }
                        if (midiMods.isNotEmpty()) {
                            pushUndoState(state, mixer)
                            param.modulators.removeAll(midiMods)
                        }
                    } else if (cell.cvSourceId != "final" && cell.cvSourceId != "base") {
                        val activeMods = if (cell.cvSourceId == "audio") {
                            param.modulators.filter { it.sourceId in setOf("audio_amp", "audio_bass", "audio_mid", "audio_high") }
                        } else if (cell.cvSourceId == "trigger") {
                            param.modulators.filter { it.sourceId in setOf("trigger_onset", "trigger_accent") }
                        } else {
                            param.modulators.filter { it.sourceId == cell.cvSourceId }
                        }
                        if (activeMods.isNotEmpty()) {
                            pushUndoState(state, mixer)
                            param.modulators.removeAll(activeMods)
                        }
                    }
                }
            }

            /*
            // Cell traversal arrow keys
            val cell = state.selectedCell
            if (cell != null) {
                val allRows = getAllGridRows(mixer)
                val columns = listOf("final", "midi") + getCvColumns()
                val currentRowIdx = allRows.indexOfFirst { it.first == cell.paramKey }
                val currentColIdx = columns.indexOf(cell.cvSourceId)
                if (currentRowIdx >= 0 && currentColIdx >= 0) {
                    var newRowIdx = currentRowIdx
                    var newColIdx = currentColIdx

                    val upKey = ImGui.getKeyIndex(ImGuiKey.UpArrow)
                    val downKey = ImGui.getKeyIndex(ImGuiKey.DownArrow)
                    val leftKey = ImGui.getKeyIndex(ImGuiKey.LeftArrow)
                    val rightKey = ImGui.getKeyIndex(ImGuiKey.RightArrow)

                    if (ImGui.isKeyPressed(upKey)) {
                        newRowIdx = (currentRowIdx - 1 + allRows.size) % allRows.size
                    } else if (ImGui.isKeyPressed(downKey)) {
                        newRowIdx = (currentRowIdx + 1) % allRows.size
                    } else if (ImGui.isKeyPressed(leftKey)) {
                        newColIdx = (currentColIdx - 1 + columns.size) % columns.size
                    } else if (ImGui.isKeyPressed(rightKey)) {
                        newColIdx = (currentColIdx + 1) % columns.size
                    }

                    if (newRowIdx != currentRowIdx || newColIdx != currentColIdx) {
                        val newRow = allRows[newRowIdx]
                        val newCol = columns[newColIdx]
                        state.select(PatchCellId(newRow.first, newCol), newRow.second)

                        // Expand the new subgroup if necessary
                        val parts = newRow.first.split("/")
                        if (parts.size >= 2) {
                            val parentGroup = parts[0]
                            state.groupOpen[parentGroup] = true
                            state.groupNeedsExpand[parentGroup] = true
                            if (parts.size == 3) {
                                val subgroupLabel = if (parts[1] == "FB") "Feedback" else parts[1]
                                val subgroupKey = "${parts[0]}/$subgroupLabel"
                                if (UITheme.autocollapseEnabled) {
                                    syncAndCollapseSubgroups(subgroupLabel, state)
                                } else {
                                    state.groupOpen[subgroupKey] = true
                                    state.groupNeedsExpand[subgroupKey] = true
                                }
                            }
                        }
                    }
            */
        }
    }

    private fun drawRandomizeRow(
        id: String,
        label: String,
        startX: Float,
        startY: Float,
        btnWidth: Float,
        btnHeight: Float,
        scale: Float,
        onClick: () -> Unit
    ) {
        if (drawDiceButton(id, startX, startY, scale, btnWidth, btnHeight)) {
            onClick()
        }
        ImGui.sameLine(0f, 6f)
        val textY = startY + (btnHeight - ImGui.getTextLineHeight()) * 0.5f
        ImGui.setCursorScreenPos(ImGui.getCursorScreenPosX(), textY)
        UITheme.body(label)
    }

    private fun drawDiceButton(id: String, x: Float, y: Float, scale: Float, btnWidth: Float, btnHeight: Float): Boolean {
        ImGui.setCursorScreenPos(x, y)
        val clicked = ImGui.button("##$id", btnWidth, btnHeight)
        
        val dl = ImGui.getWindowDrawList()
        val diceColor = ImGui.colorConvertFloat4ToU32(0.9f, 0.9f, 0.9f, 1f)
        val dotColor = ImGui.colorConvertFloat4ToU32(0.1f, 0.1f, 0.1f, 1f)
        
        // Die 1
        val dieW = 23f * scale
        val d1X = x + 5f * scale
        val d1Y = y + (btnHeight - dieW) / 2f
        dl.addRectFilled(d1X, d1Y, d1X + dieW, d1Y + dieW, diceColor, 2f * scale)
        dl.addRect(d1X, d1Y, d1X + dieW, d1Y + dieW, dotColor, 2f * scale, 0, 1.2f * scale)
        // Face 3 dots
        val dotRadius = 1.7f * scale
        dl.addCircleFilled(d1X + 5f * scale, d1Y + 5f * scale, dotRadius, dotColor)
        dl.addCircleFilled(d1X + 11.5f * scale, d1Y + 11.5f * scale, dotRadius, dotColor)
        dl.addCircleFilled(d1X + 18f * scale, d1Y + 18f * scale, dotRadius, dotColor)

        // Die 2
        val d2X = x + 24f * scale
        val d2Y = y + (btnHeight - dieW) / 2f + 2f * scale
        dl.addRectFilled(d2X, d2Y, d2X + dieW, d2Y + dieW, diceColor, 2f * scale)
        dl.addRect(d2X, d2Y, d2X + dieW, d2Y + dieW, dotColor, 2f * scale, 0, 1.2f * scale)
        // Face 5 dots
        dl.addCircleFilled(d2X + 5f * scale, d2Y + 5f * scale, dotRadius, dotColor)
        dl.addCircleFilled(d2X + 18f * scale, d2Y + 5f * scale, dotRadius, dotColor)
        dl.addCircleFilled(d2X + 11.5f * scale, d2Y + 11.5f * scale, dotRadius, dotColor)
        dl.addCircleFilled(d2X + 5f * scale, d2Y + 18f * scale, dotRadius, dotColor)
        dl.addCircleFilled(d2X + 18f * scale, d2Y + 18f * scale, dotRadius, dotColor)
        
        return clicked
    }
}

fun Deck.randomizeModulators() {
    val allParams = mutableListOf<llm.slop.spirals.parameters.ModulatableParameter>()
    allParams.addAll(this.source.parameters.values)
    allParams.add(this.source.globalAlpha)
    allParams.add(this.sourceSelect)
    allParams.add(this.fbDecay)
    allParams.add(this.fbGain)
    allParams.add(this.fbZoom)
    allParams.add(this.fbRotate)
    allParams.add(this.fbHueShift)
    allParams.add(this.fbBlur)
    allParams.add(this.fbChroma)
    allParams.add(this.fbMode)

    for (param in allParams) {
        val randomized = param.modulators.map { it.randomizeActiveValues() }
        param.modulators.clear()
        param.modulators.addAll(randomized)
        param.randomizeBaseValue()
    }
}
