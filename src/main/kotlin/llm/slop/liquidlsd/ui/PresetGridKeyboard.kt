package llm.slop.liquidlsd.ui

import imgui.ImGui
import imgui.flag.ImGuiKey
import llm.slop.liquidlsd.cv.isAudioSource
import llm.slop.liquidlsd.cv.isTriggerSource
import llm.slop.liquidlsd.rendering.Mixer
import llm.slop.liquidlsd.models.ClipboardManager
import llm.slop.liquidlsd.models.CellClipboardData
import llm.slop.liquidlsd.models.RowClipboardData
import llm.slop.liquidlsd.parameters.CvModulator
import llm.slop.liquidlsd.parameters.ModulatableParameter
import llm.slop.liquidlsd.parameters.ParameterResolver
import llm.slop.liquidlsd.models.toDto

import org.lwjgl.glfw.GLFW.GLFW_KEY_S

object PresetGridKeyboard {
    fun getModsForCell(param: ModulatableParameter, cvSourceId: String): List<CvModulator> {
        return when (cvSourceId) {
            "value", "final", "base" -> emptyList()
            "audio"   -> param.modulators.filter { isAudioSource(it.sourceId) }
            "trigger" -> param.modulators.filter { isTriggerSource(it.sourceId) }
            "midi"    -> param.modulators.filter { it.sourceId.startsWith("midi_cc_") }
            else      -> param.modulators.filter { it.sourceId == cvSourceId }
        }
    }

    fun handleKeyboardShortcuts(
        state: PresetGridState,
        mixer: Mixer,
        deckPresetController: DeckPresetController? = null,
        onPushUndo: (PresetGridState, Mixer) -> Unit,
        onPerformUndo: (PresetGridState, Mixer) -> Unit
    ) {
        val io = ImGui.getIO()
        if (io.wantTextInput) return

        val isCtrl = io.keyCtrl
        val isShift = io.keyShift
        val isCmd = io.keySuper
        val modActive = isCtrl || isCmd

        // Save: Ctrl+S / Cmd+S, Save As: Shift+Ctrl+S / Shift+Cmd+S
        if (modActive && ImGui.isKeyPressed(GLFW_KEY_S, false)) {
            val activeDeck = when (state.activeTopTab) {
                "Deck A" -> mixer.deckA
                "Deck B" -> mixer.deckB
                "Deck BG" -> mixer.deckBG
                "Deck PV" -> mixer.deckPV
                else -> null
            }
            if (activeDeck != null && !activeDeck.isEmpty) {
                val isDeckA = state.activeTopTab == "Deck A"
                deckPresetController?.handleSaveDeck(mixer, activeDeck, isDeckA, isSaveAs = isShift)
            }
        }
        
        // Undo: Ctrl+Z / Cmd+Z
        if (modActive && ImGui.isKeyPressed(ImGui.getKeyIndex(ImGuiKey.Z), false)) {
            if (isShift) {
                // Currently no redo queue is tracked by PresetGridUndo.kt but we swallow the key
            } else {
                onPerformUndo(state, mixer)
            }
        }
        
        // Copy: Ctrl+C / Cmd+C
        if (modActive && ImGui.isKeyPressed(ImGui.getKeyIndex(ImGuiKey.C), false)) {
            val cell = state.selectedCell
            if (cell != null) {
                val p = ParameterResolver.findParameterByPath(mixer, cell.paramKey)
                if (p != null) {
                    if (cell.cvSourceId == "value" || cell.cvSourceId == "final" || cell.cvSourceId == "base") {
                        ClipboardManager.rowClipboard = RowClipboardData(
                            sourceParamKey = cell.paramKey,
                            parameter = p.toDto()
                        )
                    } else {
                        val modsToCopy = getModsForCell(p, cell.cvSourceId)
                        ClipboardManager.cellClipboard = CellClipboardData(
                            sourceParamKey = cell.paramKey,
                            sourceCvId = cell.cvSourceId,
                            modulators = modsToCopy.map { it.toDto() }
                        )
                    }
                }
            } else if (state.selectedParam != null) {
                val p = state.selectedParam!!
                val key = ParameterResolver.getAllParameterPaths(mixer).find { it.second === p }?.first
                if (key != null) {
                    ClipboardManager.rowClipboard = RowClipboardData(
                        sourceParamKey = key,
                        parameter = p.toDto()
                    )
                }
            }
        }
        
        // Paste: Ctrl+V / Cmd+V
        if (modActive && ImGui.isKeyPressed(ImGui.getKeyIndex(ImGuiKey.V), false)) {
            val cellData = ClipboardManager.cellClipboard
            val rowData = ClipboardManager.rowClipboard
            
            val cell = state.selectedCell
            if (cell != null) {
                val p = ParameterResolver.findParameterByPath(mixer, cell.paramKey)
                if (p != null) {
                    if (cell.cvSourceId == "value" || cell.cvSourceId == "final" || cell.cvSourceId == "base") {
                        if (rowData != null) {
                            onPushUndo(state, mixer)
                            ClipboardManager.applyRowClipboard(p, rowData, mixer)
                            if (cell.paramKey == "Mixer/crossfade") mixer.onCrossfadeCvUnmuted()
                        }
                    } else {
                        if (cellData != null) {
                            onPushUndo(state, mixer)
                            ClipboardManager.applyCellClipboard(p, cell.cvSourceId, cellData)
                            if (cell.paramKey == "Mixer/crossfade") mixer.onCrossfadeCvUnmuted()
                        }
                    }
                }
            } else if (state.selectedParam != null && rowData != null) {
                val p = state.selectedParam!!
                onPushUndo(state, mixer)
                ClipboardManager.applyRowClipboard(p, rowData, mixer)
            }
        }
        
        // Delete / Backspace: Reset parameter or clear cell modulators
        if (ImGui.isKeyPressed(ImGui.getKeyIndex(ImGuiKey.Backspace), false) ||
            ImGui.isKeyPressed(ImGui.getKeyIndex(ImGuiKey.Delete), false)) {
            
            val cell = state.selectedCell
            if (cell != null) {
                val p = ParameterResolver.findParameterByPath(mixer, cell.paramKey)
                if (p != null) {
                    if (cell.cvSourceId == "value" || cell.cvSourceId == "final" || cell.cvSourceId == "base") {
                        onPushUndo(state, mixer)
                        p.reset()
                    } else {
                        val modsToRemove = getModsForCell(p, cell.cvSourceId)
                        if (modsToRemove.isNotEmpty()) {
                            onPushUndo(state, mixer)
                            p.modulators.removeAll(modsToRemove)
                        }
                    }
                }
            } else if (state.selectedParam != null) {
                val p = state.selectedParam!!
                onPushUndo(state, mixer)
                p.reset()
            }
        }
    }
}



