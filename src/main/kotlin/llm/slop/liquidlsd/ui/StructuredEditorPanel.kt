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

    /** Last Keep per deck (offspring name, when to stop showing it), as feedback next to the star. UI state only. */
    private val kept = HashMap<String, Pair<String, Long>>()
    private const val KEPT_SHOW_MS = 4000L

    /** Visible rows of a choice popup (ImGui's default is 8). */
    private const val POPUP_ITEMS = 32

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
        drawKeepStar(deckLabel, mixer, h, source)
        ImGui.popStyleVar()

        var row = firstRow
        for (section in tab.sections) {
            val flags = if (section.collapsed) 0 else ImGuiTreeNodeFlags.DefaultOpen
            if (!ImGui.collapsingHeader("${section.title}##${tab.id}_${section.title}", flags)) continue
            // Named choice for discrete-id params, above the ordinary row (which stays for macros and learn).
            for (name in section.params) {
                val p = source.parameters[name] ?: continue
                val labels = p.labels ?: continue
                val cur = p.baseValue.toInt().coerceIn(0, labels.size - 1)
                ImGui.setNextItemWidth(labelColW)
                // popup 4x the default 8-item height; entries alphabetical while the stored value stays the id
                ImGui.setNextWindowSizeConstraints(0f, 0f, Float.MAX_VALUE, ImGui.getTextLineHeightWithSpacing() * POPUP_ITEMS)
                if (ImGui.beginCombo("##choice_$name", labels[cur])) {
                    for (i in labels.indices.sortedBy { labels[it].lowercase() }) {
                        if (ImGui.selectable(labels[i], i == cur) && i != cur) { onPushUndo(); p.baseValue = i.toFloat() }
                    }
                    ImGui.endCombo()
                }
            }
            for (name in section.params) {
                val param = source.parameters[name] ?: continue
                ParametersRenderer.drawParamRow(
                    session, section.rowLabels[name] ?: ParamLabels.humanize(name.removePrefix("${tab.id} ")), "$deckLabel/${source.displayName}/$name", param, state,
                    labelColW, mixer, gridStartX, row++, getCvColumns, getColumnOffset, getCvColor, onPushUndo
                )
            }
        }
        ImGui.popID()
    }

    /** The star at the end of the tab strip: keeps what the deck is playing as a new Garden offspring. */
    private fun drawKeepStar(deckLabel: String, mixer: Mixer, h: Float, source: DynamicVisualSource) {
        val index = when (deckLabel) { "Deck A" -> 0; "Deck B" -> 1; "Deck BG" -> 2; "Deck PV" -> 3; else -> return }
        val deck = listOf(mixer.deckA, mixer.deckB, mixer.deckBG, mixer.deckPV)[index]
        ImGui.sameLine()
        if (ButtonChrome.button("\u2605##keep", 26f, h)) {
            val lineage = (source as? llm.slop.liquidlsd.rendering.FlameSource)?.currentLineage()
            val file = llm.slop.liquidlsd.presets.Garden.keep(deck, -1, lineage?.parents ?: emptyList(), lineage?.parentTags ?: emptyList())
            kept[deckLabel] = file.nameWithoutExtension to llm.slop.liquidlsd.utils.TimeSource.getEpochMillis() + KEPT_SHOW_MS
        }
        itemTooltip("Keep: save what is playing as a new offspring in the Garden (library/presets/garden/); mid-morph, the nearer end.\nThe Pool and Breed strategies draw from the Garden.")
        ImGui.sameLine()
        if (ButtonChrome.button("Skip##veto", (ImGui.calcTextSize("Skip").x + 12f), h)) deck.morphController.vetoTarget()
        itemTooltip("Veto: skip the genome this deck is morphing towards and pick another with the current strategy.\nThe view jumps to the new target at the current morph position.")
        kept[deckLabel]?.let { (name, until) ->
            if (llm.slop.liquidlsd.utils.TimeSource.getEpochMillis() > until) kept.remove(deckLabel)
            else { ImGui.sameLine(); ImGui.alignTextToFramePadding(); ImGui.textDisabled("kept $name") }
        }
    }
}
