package llm.slop.liquidlsd.ui

import imgui.ImGui
import imgui.flag.ImGuiTreeNodeFlags
import llm.slop.liquidlsd.rendering.DynamicVisualSource
import llm.slop.liquidlsd.rendering.Mixer
import llm.slop.liquidlsd.rendering.StructuredEditorSource

/**
 * Draws a [StructuredEditorSource]'s tabs and sections in place of the flat parameter list. Every parameter is still an
 * ordinary [ParametersRenderer.drawParamRow], so modulation cells, learn and the kebab menu behave as in the flat list.
 */
object StructuredEditorPanel {
    /** Selected tab per deck. UI state only: not saved, and it resets to the first tab when the id disappears. */
    private val selected = HashMap<String, String>()

    fun draw(
        session: llm.slop.liquidlsd.SessionContext,
        deckLabel: String,
        editor: StructuredEditorSource,
        source: DynamicVisualSource,
        firstRow: Int,
        state: ParametersState,
        labelColW: Float,
        mixer: Mixer,
        gridStartX: Float,
        getCvColumns: () -> List<String>,
        getColumnOffset: (String) -> Float,
        getCvColor: (String, Float) -> Int,
        onPushUndo: () -> Unit
    ) {
        val tabs = editor.editorTabs(selected[deckLabel])
        val tab = tabs.firstOrNull { it.id == selected[deckLabel] } ?: tabs.first()

        ImGui.pushID("structured_$deckLabel")
        ImGui.pushStyleVar(imgui.flag.ImGuiStyleVar.ItemSpacing, 4f, 4f)
        val h = (ImGui.getTextLineHeight() + 6f).coerceAtLeast(22f)
        tabs.forEachIndexed { i, t ->
            if (i > 0) ImGui.sameLine()
            val active = t.id == tab.id
            if (active) {
                val col = ParametersTabs.getSubTabColor(state, 1f)
                ImGui.pushStyleColor(imgui.flag.ImGuiCol.Button, col)
                ImGui.pushStyleColor(imgui.flag.ImGuiCol.ButtonHovered, col)
                ImGui.pushStyleColor(imgui.flag.ImGuiCol.ButtonActive, col)
                val ink = TangoPalette.inkFor(ParametersTabs.getDeckColorRGB(state.activeTopTab))
                ImGui.pushStyleColor(imgui.flag.ImGuiCol.Text, ink[0], ink[1], ink[2], 1f)
            } else {
                ImGui.pushStyleColor(imgui.flag.ImGuiCol.Button, ImGui.colorConvertFloat4ToU32(0.15f, 0.15f, 0.15f, 1f))
                ImGui.pushStyleColor(imgui.flag.ImGuiCol.ButtonHovered, ImGui.colorConvertFloat4ToU32(0.25f, 0.25f, 0.25f, 1f))
                ImGui.pushStyleColor(imgui.flag.ImGuiCol.ButtonActive, ImGui.colorConvertFloat4ToU32(0.35f, 0.35f, 0.35f, 1f))
                ImGui.pushStyleColor(imgui.flag.ImGuiCol.Text, 1f, 1f, 1f, 1f)
            }
            if (ButtonChrome.button(t.label, (ImGui.calcTextSize(t.label).x + 12f).coerceAtLeast(26f), h)) selected[deckLabel] = t.id
            ImGui.popStyleColor(4)
        }
        editor.addTabLabel()?.let { label ->
            ImGui.sameLine()
            if (ImGui.button(label)) editor.addEditorTab()?.let { selected[deckLabel] = it }
        }
        ImGui.popStyleVar()

        var row = firstRow
        for (section in tab.sections) {
            val flags = if (section.collapsed) 0 else ImGuiTreeNodeFlags.DefaultOpen
            if (!ImGui.collapsingHeader("${section.title}##${tab.id}_${section.title}", flags)) continue
            for (name in section.params) {
                val param = source.parameters[name] ?: continue
                ParametersRenderer.drawParamRow(
                    session, name.removePrefix("${tab.id} "), "$deckLabel/${source.displayName}/$name", param, state,
                    labelColW, mixer, gridStartX, row++, getCvColumns, getColumnOffset, getCvColor, onPushUndo
                )
            }
        }
        ImGui.popID()
    }
}
