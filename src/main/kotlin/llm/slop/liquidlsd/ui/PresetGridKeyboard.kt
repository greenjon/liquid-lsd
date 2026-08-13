package llm.slop.liquidlsd.ui

import imgui.ImGui
import imgui.flag.ImGuiKey
import llm.slop.liquidlsd.rendering.Mixer
import llm.slop.liquidlsd.models.ClipboardManager
import llm.slop.liquidlsd.models.CellClipboardData
import llm.slop.liquidlsd.models.RowClipboardData
import llm.slop.liquidlsd.parameters.ParameterResolver
import llm.slop.liquidlsd.models.toDto

object PresetGridKeyboard {
    fun handleKeyboardShortcuts(state: PresetGridState, mixer: Mixer, onPushUndo: (PresetGridState, Mixer) -> Unit, onPerformUndo: (PresetGridState, Mixer) -> Unit) {
        val io = ImGui.getIO()
        val isCtrl = io.keyCtrl
        val isShift = io.keyShift
        val isCmd = io.keySuper
        val modActive = isCtrl || isCmd
        
        if (modActive && ImGui.isKeyPressed(ImGui.getKeyIndex(ImGuiKey.Z), false)) {
            if (isShift) {
                // Currently no redo queue is tracked by PresetGridUndo.kt but we swallow the key
            } else {
                onPerformUndo(state, mixer)
            }
        }
        
        if (modActive && ImGui.isKeyPressed(ImGui.getKeyIndex(ImGuiKey.C), false)) {
            val cell = state.selectedCell
            if (cell != null) {
                val p = ParameterResolver.findParameterByPath(mixer, cell.paramKey)
                if (p != null) {
                    val modsToCopy = if (cell.cvSourceId == "base") emptyList() else p.modulators.filter { it.sourceId == cell.cvSourceId }
                    ClipboardManager.cellClipboard = CellClipboardData(
                        sourceParamKey = cell.paramKey,
                        sourceCvId = cell.cvSourceId,
                        modulators = modsToCopy.map { it.toDto() }
                    )
                }
            } else if (state.selectedParam != null) {
                val p = state.selectedParam!!
                // We need the param key for RowClipboardData. Let's find it.
                // ParameterResolver.getAllParameterPaths(mixer) would be O(N), but selectedParamKey is not in state.
                // We'll iterate.
                val key = ParameterResolver.getAllParameterPaths(mixer).find { it.second === p }?.first
                if (key != null) {
                    ClipboardManager.rowClipboard = RowClipboardData(
                        sourceParamKey = key,
                        parameter = p.toDto()
                    )
                }
            }
        }
        
        if (modActive && ImGui.isKeyPressed(ImGui.getKeyIndex(ImGuiKey.V), false)) {
            val cellData = ClipboardManager.cellClipboard
            val rowData = ClipboardManager.rowClipboard
            
            val cell = state.selectedCell
            if (cell != null && cellData != null) {
                val p = ParameterResolver.findParameterByPath(mixer, cell.paramKey)
                if (p != null) {
                    onPushUndo(state, mixer)
                    ClipboardManager.applyCellClipboard(p, cell.cvSourceId, cellData)
                }
            } else if (state.selectedParam != null && rowData != null) {
                val p = state.selectedParam!!
                onPushUndo(state, mixer)
                ClipboardManager.applyRowClipboard(p, rowData, mixer)
            }
        }
        
        if (ImGui.isKeyPressed(ImGui.getKeyIndex(ImGuiKey.Backspace), false) ||
            ImGui.isKeyPressed(ImGui.getKeyIndex(ImGuiKey.Delete), false)) {
            
            val cell = state.selectedCell
            if (cell != null) {
                val p = ParameterResolver.findParameterByPath(mixer, cell.paramKey)
                if (p != null) {
                    if (cell.cvSourceId == "base") {
                        onPushUndo(state, mixer)
                        p.value = p.defaultValue
                    } else {
                        val mod = p.modulators.find { it.sourceId == cell.cvSourceId }
                        if (mod != null) {
                            onPushUndo(state, mixer)
                            p.modulators.remove(mod)
                        }
                    }
                }
            } else if (state.selectedParam != null) {
                val p = state.selectedParam!!
                onPushUndo(state, mixer)
                p.value = p.defaultValue
                p.modulators.clear()
            }
        }
    }
}


