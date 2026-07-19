package llm.slop.liquidlsd.ui

import imgui.ImGui
import llm.slop.liquidlsd.rendering.Deck
import llm.slop.liquidlsd.rendering.Mixer
import llm.slop.liquidlsd.rendering.Mandala
import llm.slop.liquidlsd.rendering.DynamicVisualSource
import llm.slop.liquidlsd.parameters.ModulatableParameter
import kotlin.math.roundToInt

object PatchGridTabs {
    fun drawTopTabs(session: llm.slop.liquidlsd.SessionContext, state: PatchGridState) {
        ImGui.pushStyleVar(imgui.flag.ImGuiStyleVar.ItemSpacing, 0f, 0f)
        val tabs = listOf("Mixer", "Deck A", "Deck B", "Deck C")
        val buttonWidth = 80f
        tabs.forEachIndexed { i, tab ->
            if (i > 0) ImGui.sameLine()
            val isActive = state.activeTopTab == tab
            if (isActive) {
                val activeCol = when (tab) {
                    "Deck A" -> ImGui.colorConvertFloat4ToU32(0.2f, 0.4f, 0.8f, 1f)
                    "Deck B" -> ImGui.colorConvertFloat4ToU32(0.8f, 0.4f, 0.2f, 1f)
                    "Deck C" -> ImGui.colorConvertFloat4ToU32(0.2f, 0.7f, 0.5f, 1f) // Emerald/Teal for Deck C
                    else     -> ImGui.colorConvertFloat4ToU32(0.4f, 0.4f, 0.4f, 1f)
                }
                ImGui.pushStyleColor(imgui.flag.ImGuiCol.Button,        activeCol)
                ImGui.pushStyleColor(imgui.flag.ImGuiCol.ButtonHovered, activeCol)
                ImGui.pushStyleColor(imgui.flag.ImGuiCol.ButtonActive,  activeCol)
            } else {
                ImGui.pushStyleColor(imgui.flag.ImGuiCol.Button,        ImGui.colorConvertFloat4ToU32(0.1f, 0.1f, 0.1f, 1f))
                ImGui.pushStyleColor(imgui.flag.ImGuiCol.ButtonHovered, ImGui.colorConvertFloat4ToU32(0.2f, 0.2f, 0.2f, 1f))
                ImGui.pushStyleColor(imgui.flag.ImGuiCol.ButtonActive,  ImGui.colorConvertFloat4ToU32(0.3f, 0.3f, 0.3f, 1f))
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
            val transformNames = setOf("Zoom", "Rotate X", "Rotate Y", "Rotate Z",
                                       "Cam Rotate X", "Cam Rotate Y", "Cam Rotate Z")
            if (activeSource.parameters.keys.any { transformNames.contains(it) }) {
                tabs.add("View")
            }
            tabs.add(activeSource.displayName)
        }
        tabs.add("Feedback")
        return tabs.distinct()
    }

    fun drawSubTabs(session: llm.slop.liquidlsd.SessionContext, state: PatchGridState, mixer: Mixer) {
        if (state.activeTopTab == "Mixer") {
            ImGui.dummy(0f, 24f) // placeholder to prevent layout jumping
            return
        }

        val deck = when (state.activeTopTab) {
            "Deck A" -> mixer.deckA
            "Deck B" -> mixer.deckB
            "Deck C" -> mixer.deckC
            else -> mixer.deckA
        }
        val tabs = getDeckSubTabs(deck)

        if (tabs.isEmpty()) return

        // Auto-correct stale subtab value (e.g. after deck source changes)
        val activeSubTab = when (state.activeTopTab) {
            "Deck A" -> state.activeDeckASubTab
            "Deck B" -> state.activeDeckBSubTab
            "Deck C" -> state.activeDeckCSubTab
            else -> state.activeDeckASubTab
        }
        if (activeSubTab !in tabs) {
            when (state.activeTopTab) {
                "Deck A" -> state.activeDeckASubTab = tabs.first()
                "Deck B" -> state.activeDeckBSubTab = tabs.first()
                "Deck C" -> state.activeDeckCSubTab = tabs.first()
            }
        }
        val currentSubTab = when (state.activeTopTab) {
            "Deck A" -> state.activeDeckASubTab
            "Deck B" -> state.activeDeckBSubTab
            "Deck C" -> state.activeDeckCSubTab
            else -> state.activeDeckASubTab
        }

        ImGui.pushStyleVar(imgui.flag.ImGuiStyleVar.ItemSpacing, 0f, 0f)
        tabs.forEachIndexed { i, tab ->
            if (i > 0) ImGui.sameLine()
            val isActive = currentSubTab == tab
            if (isActive) {
                val bgCol = getSubTabColor(tab, 1f)
                ImGui.pushStyleColor(imgui.flag.ImGuiCol.Button,        bgCol)
                ImGui.pushStyleColor(imgui.flag.ImGuiCol.ButtonHovered, bgCol)
                ImGui.pushStyleColor(imgui.flag.ImGuiCol.ButtonActive,  bgCol)
            } else {
                ImGui.pushStyleColor(imgui.flag.ImGuiCol.Button,        ImGui.colorConvertFloat4ToU32(0.15f, 0.15f, 0.15f, 1f))
                ImGui.pushStyleColor(imgui.flag.ImGuiCol.ButtonHovered, ImGui.colorConvertFloat4ToU32(0.25f, 0.25f, 0.25f, 1f))
                ImGui.pushStyleColor(imgui.flag.ImGuiCol.ButtonActive,  ImGui.colorConvertFloat4ToU32(0.35f, 0.35f, 0.35f, 1f))
            }
            var tw = 0f
            UITheme.withFont(UITheme.FontLevel.BODY) { tw = ImGui.calcTextSize(tab).x }
            val btnW = (tw + 20f).coerceAtLeast(80f)
            if (ImGui.button(tab, btnW, 24f)) {
                when (state.activeTopTab) {
                    "Deck A" -> state.activeDeckASubTab = tab
                    "Deck B" -> state.activeDeckBSubTab = tab
                    "Deck C" -> state.activeDeckCSubTab = tab
                }
            }
            ImGui.popStyleColor(3)
        }
        ImGui.popStyleVar()
    }

    private fun getSubTabColor(label: String, alpha: Float): Int {
        return ImGui.colorConvertFloat4ToU32(0.25f, 0.55f, 0.85f, alpha)
    }

    /**
     * Renders content only when the named section matches the deck's active sub-tab.
     * For the Mixer top-tab, always renders when Mixer is the active top-tab.
     */
    fun drawSubGroupContent(
        session: llm.slop.liquidlsd.SessionContext,
        parentLabel: String,
        label: String,
        state: PatchGridState,
        content: () -> Unit
    ) {
        val key = "$parentLabel/$label"

        val isVisible = if (parentLabel == "Mixer") {
            state.activeTopTab == "Mixer"
        } else {
            val activeSubTab = when (parentLabel) {
                "Deck A" -> state.activeDeckASubTab
                "Deck B" -> state.activeDeckBSubTab
                "Deck C" -> state.activeDeckCSubTab
                else -> ""
            }
            activeSubTab == label
        }

        if (!isVisible) return

        val startY  = ImGui.getCursorScreenPosY()
        val dl      = ImGui.getWindowDrawList()
        val subStartY = startY

        ImGui.indent()
        content()
        ImGui.unindent()

        val endY = ImGui.getCursorScreenPosY()

        if (parentLabel != "Mixer") {
            val lineX   = ImGui.getWindowPos().x + 4f
            val lineCol = getSubTabColor(label, 0.6f)
            dl.addLine(lineX, subStartY, lineX, endY - 2f, lineCol, 3f)
        }

        state.subgroupHeight[key] = endY - startY
    }

    fun drawDeckGroupContent(
        session: llm.slop.liquidlsd.SessionContext,
        deckLabel: String,
        deck: Deck,
        state: PatchGridState,
        labelColW: Float,
        mixer: Mixer,
        gridStartX: Float,
        getCvColumns: () -> List<String>,
        getColumnOffset: (String) -> Float,
        getCvColor: (String, Float) -> Int,
        onPushUndo: () -> Unit
    ) {
        val activeSource = deck.source
        val mandala = activeSource as? Mandala

        if (mandala != null) {
            // -- View ----------------------------------------------------------
            drawSubGroupContent(session, deckLabel, "View", state) {
                PatchGridRenderer.drawParamRow(session, "Zoom",     "$deckLabel/View/Zoom",   mandala.parameters["Zoom"]!!,     state, labelColW, mixer, gridStartX, 0, getCvColumns, getColumnOffset, getCvColor, onPushUndo)
                PatchGridRenderer.drawParamRow(session, "Rotate Z", "$deckLabel/View/RotateZ", mandala.parameters["Rotate Z"]!!, state, labelColW, mixer, gridStartX, 1, getCvColumns, getColumnOffset, getCvColor, onPushUndo)

                val modeVal = mandala.parameters["3D Mode"]?.value ?: 0f
                val mode    = modeVal.roundToInt().coerceIn(0, 4)
                if (mode > 0) {
                    PatchGridRenderer.drawParamRow(session, "Rotate X", "$deckLabel/View/RotateX", mandala.parameters["Rotate X"]!!, state, labelColW, mixer, gridStartX, 2, getCvColumns, getColumnOffset, getCvColor, onPushUndo)
                    PatchGridRenderer.drawParamRow(session, "Rotate Y", "$deckLabel/View/RotateY", mandala.parameters["Rotate Y"]!!, state, labelColW, mixer, gridStartX, 3, getCvColumns, getColumnOffset, getCvColor, onPushUndo)
                    PatchGridRenderer.drawParamRow(session, "3D Persp", "$deckLabel/View/Persp",   mandala.parameters["3D Persp"]!!, state, labelColW, mixer, gridStartX, 4, getCvColumns, getColumnOffset, getCvColor, onPushUndo)
                }
            }

            // -- Geometry ------------------------------------------------------
            drawSubGroupContent(session, deckLabel, "Geometry", state) {
                var row = 0
                PatchGridRenderer.drawParamRow(session, "Lobe Count",    "$deckLabel/Geometry/Lobes",       mandala.parameters["Lobes"]!!,         state, labelColW, mixer, gridStartX, row++, getCvColumns, getColumnOffset, getCvColor, onPushUndo)
                PatchGridRenderer.drawParamRow(session, "Recipe ID",     "$deckLabel/Geometry/Recipe",      mandala.parameters["Recipe Select"]!!,  state, labelColW, mixer, gridStartX, row++, getCvColumns, getColumnOffset, getCvColor, onPushUndo)
                PatchGridRenderer.drawParamRow(session, "L1",            "$deckLabel/Geometry/L1",          mandala.parameters["L1"]!!,             state, labelColW, mixer, gridStartX, row++, getCvColumns, getColumnOffset, getCvColor, onPushUndo)
                PatchGridRenderer.drawParamRow(session, "L2",            "$deckLabel/Geometry/L2",          mandala.parameters["L2"]!!,             state, labelColW, mixer, gridStartX, row++, getCvColumns, getColumnOffset, getCvColor, onPushUndo)
                PatchGridRenderer.drawParamRow(session, "L3",            "$deckLabel/Geometry/L3",          mandala.parameters["L3"]!!,             state, labelColW, mixer, gridStartX, row++, getCvColumns, getColumnOffset, getCvColor, onPushUndo)
                PatchGridRenderer.drawParamRow(session, "L4",            "$deckLabel/Geometry/L4",          mandala.parameters["L4"]!!,             state, labelColW, mixer, gridStartX, row++, getCvColumns, getColumnOffset, getCvColor, onPushUndo)
                PatchGridRenderer.drawParamRow(session, "Freq Offset",   "$deckLabel/Geometry/FreqOffset",  mandala.parameters["Freq Offset"]!!,    state, labelColW, mixer, gridStartX, row++, getCvColumns, getColumnOffset, getCvColor, onPushUndo)
                PatchGridRenderer.drawParamRow(session, "Harmonic Lock", "$deckLabel/Geometry/HarmonicLock",mandala.parameters["Harmonic Lock"]!!,  state, labelColW, mixer, gridStartX, row++, getCvColumns, getColumnOffset, getCvColor, onPushUndo)
                PatchGridRenderer.drawParamRow(session, "3D Mode",       "$deckLabel/Geometry/3DMode",      mandala.parameters["3D Mode"]!!,        state, labelColW, mixer, gridStartX, row++, getCvColumns, getColumnOffset, getCvColor, onPushUndo)

                val modeVal = mandala.parameters["3D Mode"]?.value ?: 0f
                val mode    = modeVal.roundToInt().coerceIn(0, 4)
                when (mode) {
                    1 -> {
                        PatchGridRenderer.drawParamRow(session, "Sphere Wrap X", "$deckLabel/Geometry/SphereWrapX", mandala.parameters["Sphere Wrap X"]!!, state, labelColW, mixer, gridStartX, row++, getCvColumns, getColumnOffset, getCvColor, onPushUndo)
                        PatchGridRenderer.drawParamRow(session, "Sphere Wrap Y", "$deckLabel/Geometry/SphereWrapY", mandala.parameters["Sphere Wrap Y"]!!, state, labelColW, mixer, gridStartX, row++, getCvColumns, getColumnOffset, getCvColor, onPushUndo)
                    }
                    2 -> {
                        PatchGridRenderer.drawParamRow(session, "Mirror Group",  "$deckLabel/Geometry/MirrorGroup",  mandala.parameters["Mirror Group"]!!,  state, labelColW, mixer, gridStartX, row++, getCvColumns, getColumnOffset, getCvColor, onPushUndo)
                        PatchGridRenderer.drawParamRow(session, "Sphere Wrap X", "$deckLabel/Geometry/SphereWrapX",  mandala.parameters["Sphere Wrap X"]!!, state, labelColW, mixer, gridStartX, row++, getCvColumns, getColumnOffset, getCvColor, onPushUndo)
                        PatchGridRenderer.drawParamRow(session, "Sphere Wrap Y", "$deckLabel/Geometry/SphereWrapY",  mandala.parameters["Sphere Wrap Y"]!!, state, labelColW, mixer, gridStartX, row++, getCvColumns, getColumnOffset, getCvColor, onPushUndo)
                    }
                    3 -> {
                        PatchGridRenderer.drawParamRow(session, "Permute XY", "$deckLabel/Geometry/PermuteXY", mandala.parameters["Permute XY"]!!, state, labelColW, mixer, gridStartX, row++, getCvColumns, getColumnOffset, getCvColor, onPushUndo)
                        PatchGridRenderer.drawParamRow(session, "Permute YZ", "$deckLabel/Geometry/PermuteYZ", mandala.parameters["Permute YZ"]!!, state, labelColW, mixer, gridStartX, row++, getCvColumns, getColumnOffset, getCvColor, onPushUndo)
                        PatchGridRenderer.drawParamRow(session, "Permute ZX", "$deckLabel/Geometry/PermuteZX", mandala.parameters["Permute ZX"]!!, state, labelColW, mixer, gridStartX, row++, getCvColumns, getColumnOffset, getCvColor, onPushUndo)
                    }
                    4 -> {
                        PatchGridRenderer.drawParamRow(session, "Mirror Group", "$deckLabel/Geometry/MirrorGroup", mandala.parameters["Mirror Group"]!!, state, labelColW, mixer, gridStartX, row++, getCvColumns, getColumnOffset, getCvColor, onPushUndo)
                    }
                }
            }

            // -- Color ---------------------------------------------------------
            drawSubGroupContent(session, deckLabel, "Color", state) {
                PatchGridRenderer.drawParamRow(session, "Thickness",  "$deckLabel/Color/Thickness",  mandala.parameters["Thickness"]!!,  state, labelColW, mixer, gridStartX, 0, getCvColumns, getColumnOffset, getCvColor, onPushUndo)
                PatchGridRenderer.drawParamRow(session, "Hue Offset", "$deckLabel/Color/HueOffset",  mandala.parameters["Hue Offset"]!!, state, labelColW, mixer, gridStartX, 1, getCvColumns, getColumnOffset, getCvColor, onPushUndo)
                PatchGridRenderer.drawParamRow(session, "Hue Sweep",  "$deckLabel/Color/HueSweep",   mandala.parameters["Hue Sweep"]!!,  state, labelColW, mixer, gridStartX, 2, getCvColumns, getColumnOffset, getCvColor, onPushUndo)
                PatchGridRenderer.drawParamRow(session, "Depth",      "$deckLabel/Color/Depth",      mandala.parameters["Depth"]!!,      state, labelColW, mixer, gridStartX, 3, getCvColumns, getColumnOffset, getCvColor, onPushUndo)
                PatchGridRenderer.drawParamRow(session, "Gain",       "$deckLabel/Color/Gain",       mandala.globalAlpha,                state, labelColW, mixer, gridStartX, 4, getCvColumns, getColumnOffset, getCvColor, onPushUndo)
            }

            // -- Background ----------------------------------------------------
            drawSubGroupContent(session, deckLabel, "Background", state) {
                PatchGridRenderer.drawParamRow(session, "Bg Style",    "$deckLabel/Background/Style",    mandala.parameters["Bg Style"]!!,    state, labelColW, mixer, gridStartX, 0, getCvColumns, getColumnOffset, getCvColor, onPushUndo)
                PatchGridRenderer.drawParamRow(session, "Bg Feedback", "$deckLabel/Background/Feedback", mandala.parameters["Bg Feedback"]!!, state, labelColW, mixer, gridStartX, 1, getCvColumns, getColumnOffset, getCvColor, onPushUndo)
                PatchGridRenderer.drawParamRow(session, "Bg Hue",      "$deckLabel/Background/Hue",      mandala.parameters["Bg Hue"]!!,      state, labelColW, mixer, gridStartX, 2, getCvColumns, getColumnOffset, getCvColor, onPushUndo)
                PatchGridRenderer.drawParamRow(session, "Bg Sat",      "$deckLabel/Background/Sat",      mandala.parameters["Bg Sat"]!!,      state, labelColW, mixer, gridStartX, 3, getCvColumns, getColumnOffset, getCvColor, onPushUndo)
                PatchGridRenderer.drawParamRow(session, "Bg Val",      "$deckLabel/Background/Val",      mandala.parameters["Bg Val"]!!,      state, labelColW, mixer, gridStartX, 4, getCvColumns, getColumnOffset, getCvColor, onPushUndo)
                PatchGridRenderer.drawParamRow(session, "Bg Sweep",    "$deckLabel/Background/Sweep",    mandala.parameters["Bg Sweep"]!!,    state, labelColW, mixer, gridStartX, 5, getCvColumns, getColumnOffset, getCvColor, onPushUndo)
                PatchGridRenderer.drawParamRow(session, "Bg Speed",    "$deckLabel/Background/Speed",    mandala.parameters["Bg Speed"]!!,    state, labelColW, mixer, gridStartX, 6, getCvColumns, getColumnOffset, getCvColor, onPushUndo)
                PatchGridRenderer.drawParamRow(session, "Bg Zoom",     "$deckLabel/Background/Zoom",     mandala.parameters["Bg Zoom"]!!,     state, labelColW, mixer, gridStartX, 7, getCvColumns, getColumnOffset, getCvColor, onPushUndo)
            }

        } else if (activeSource is DynamicVisualSource) {
            val transformNames = setOf("Zoom", "Rotate X", "Rotate Y", "Rotate Z",
                                       "Cam Rotate X", "Cam Rotate Y", "Cam Rotate Z")
            val transformParams = mutableListOf<Map.Entry<String, ModulatableParameter>>()
            val otherParams     = mutableListOf<Map.Entry<String, ModulatableParameter>>()

            activeSource.parameters.forEach { entry ->
                if (transformNames.contains(entry.key)) transformParams.add(entry)
                else otherParams.add(entry)
            }

            if (transformParams.isNotEmpty()) {
                drawSubGroupContent(session, deckLabel, "View", state) {
                    transformParams.forEachIndexed { i, (name, param) ->
                        PatchGridRenderer.drawParamRow(session, name, "$deckLabel/View/$name", param, state, labelColW, mixer, gridStartX, i, getCvColumns, getColumnOffset, getCvColor, onPushUndo)
                    }
                }
            }

            drawSubGroupContent(session, deckLabel, activeSource.displayName, state) {
                otherParams.forEachIndexed { i, (name, param) ->
                    PatchGridRenderer.drawParamRow(session, name, "$deckLabel/${activeSource.displayName}/$name", param, state, labelColW, mixer, gridStartX, i, getCvColumns, getColumnOffset, getCvColor, onPushUndo)
                }
                PatchGridRenderer.drawParamRow(session, "Gain", "$deckLabel/${activeSource.displayName}/Gain", activeSource.globalAlpha, state, labelColW, mixer, gridStartX, otherParams.size, getCvColumns, getColumnOffset, getCvColor, onPushUndo)
            }
        }

        // -- Feedback (always present for every deck source) -------------------
        drawSubGroupContent(session, deckLabel, "Feedback", state) {
            PatchGridRenderer.drawParamRow(session, "Feedback",     "$deckLabel/FB/Decay",    deck.fbDecay,    state, labelColW, mixer, gridStartX, 0, getCvColumns, getColumnOffset, getCvColor, onPushUndo)
            PatchGridRenderer.drawParamRow(session, "FB Gain",      "$deckLabel/FB/Gain",     deck.fbGain,     state, labelColW, mixer, gridStartX, 1, getCvColumns, getColumnOffset, getCvColor, onPushUndo)
            PatchGridRenderer.drawParamRow(session, "FB Zoom",      "$deckLabel/FB/Zoom",     deck.fbZoom,     state, labelColW, mixer, gridStartX, 2, getCvColumns, getColumnOffset, getCvColor, onPushUndo)
            PatchGridRenderer.drawParamRow(session, "FB Rotate",    "$deckLabel/FB/Rotate",   deck.fbRotate,   state, labelColW, mixer, gridStartX, 3, getCvColumns, getColumnOffset, getCvColor, onPushUndo)
            PatchGridRenderer.drawParamRow(session, "FB Hue Shift", "$deckLabel/FB/HueShift", deck.fbHueShift, state, labelColW, mixer, gridStartX, 4, getCvColumns, getColumnOffset, getCvColor, onPushUndo)
            PatchGridRenderer.drawParamRow(session, "FB Blur",      "$deckLabel/FB/Blur",     deck.fbBlur,     state, labelColW, mixer, gridStartX, 5, getCvColumns, getColumnOffset, getCvColor, onPushUndo)
            PatchGridRenderer.drawParamRow(session, "FB Chroma",    "$deckLabel/FB/Chroma",   deck.fbChroma,   state, labelColW, mixer, gridStartX, 6, getCvColumns, getColumnOffset, getCvColor, onPushUndo)
            PatchGridRenderer.drawParamRow(session, "FB Mode",      "$deckLabel/FB/Mode",     deck.fbMode,     state, labelColW, mixer, gridStartX, 7, getCvColumns, getColumnOffset, getCvColor, onPushUndo)
            PatchGridRenderer.drawParamRow(session, "FB Kaleido",   "$deckLabel/FB/Kaleido",  deck.fbKaleido,  state, labelColW, mixer, gridStartX, 8, getCvColumns, getColumnOffset, getCvColor, onPushUndo)
        }
    }
}
