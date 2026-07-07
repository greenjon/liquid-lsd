package llm.slop.spirals.ui

import imgui.ImGui
import llm.slop.spirals.rendering.Deck
import llm.slop.spirals.rendering.Mixer
import llm.slop.spirals.rendering.Mandala
import llm.slop.spirals.rendering.DynamicVisualSource
import llm.slop.spirals.parameters.ModulatableParameter
import kotlin.math.roundToInt

object PatchGridTabs {
    private fun topTabDisplayLabel(tab: String, compact: Boolean): String {
        if (!compact) return tab
        return when (tab) {
            "Deck A" -> "A"
            "Deck B" -> "B"
            "Deck C" -> "C"
            else -> tab
        }
    }

    private fun subTabDisplayLabel(tab: String, compact: Boolean): String {
        if (!compact) return tab
        return when (tab) {
            "Geometry" -> "Geom"
            "Background" -> "Back"
            "Feedback" -> "Feed"
            else -> tab
        }
    }

    fun drawTopTabs(state: PatchGridState) {
        ImGui.pushStyleVar(imgui.flag.ImGuiStyleVar.ItemSpacing, 4f, 2f)
        val tabs = listOf("Mixer", "Deck A", "Deck B", "Deck C")
        val compact = ImGui.getContentRegionAvailX() < 300f
        val buttonWidth = if (compact) 58f else 80f
        tabs.forEachIndexed { i, tab ->
            if (i > 0) ImGui.sameLine()
            val isActive = state.activeTopTab == tab
            val activeCol = if (tab == "Mixer") {
                UITheme.colorU32(UITheme.Colors.ACCENT_R, UITheme.Colors.ACCENT_G, UITheme.Colors.ACCENT_B, 1f)
            } else {
                UITheme.deckAccentColor(tab, 1f)
            }
            if (UITheme.tabButton("##top_tab_$tab", topTabDisplayLabel(tab, compact), isActive, activeCol, buttonWidth, 28f)) {
                state.activeTopTab = tab
            }
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

    fun drawSubTabs(state: PatchGridState, mixer: Mixer) {
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

        val compactLabels = ImGui.getContentRegionAvailX() < 460f
        ImGui.pushStyleVar(imgui.flag.ImGuiStyleVar.ItemSpacing, 4f, 2f)
        tabs.forEachIndexed { i, tab ->
            val displayLabel = subTabDisplayLabel(tab, compactLabels)
            val isActive = currentSubTab == tab
            var tw = 0f
            UITheme.withFont(UITheme.FontLevel.BODY) { tw = ImGui.calcTextSize(displayLabel).x }
            val btnW = (tw + 20f).coerceAtLeast(if (compactLabels) 56f else 64f)
            if (i > 0) {
                val spacing = ImGui.getStyle().itemSpacing.x
                val nextRight = ImGui.getItemRectMaxX() + spacing + btnW
                val contentRight = ImGui.getWindowPosX() + ImGui.getWindowContentRegionMaxX()
                if (nextRight <= contentRight) {
                    ImGui.sameLine(0f, spacing)
                }
            }
            if (UITheme.tabButton("##sub_tab_${state.activeTopTab}_$tab", displayLabel, isActive, getSubTabColor(tab, 1f), btnW, 28f)) {
                when (state.activeTopTab) {
                    "Deck A" -> state.activeDeckASubTab = tab
                    "Deck B" -> state.activeDeckBSubTab = tab
                    "Deck C" -> state.activeDeckCSubTab = tab
                }
            }
        }
        ImGui.popStyleVar()
    }

    private fun getSubTabColor(label: String, alpha: Float): Int {
        return UITheme.colorU32(UITheme.Colors.ACCENT_R, UITheme.Colors.ACCENT_G, UITheme.Colors.ACCENT_B, alpha)
    }

    /**
     * Renders content only when the named section matches the deck's active sub-tab.
     * For the Mixer top-tab, always renders when Mixer is the active top-tab.
     */
    fun drawSubGroupContent(
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
            drawSubGroupContent(deckLabel, "View", state) {
                PatchGridRenderer.drawParamRow("Zoom",     "$deckLabel/View/Zoom",   mandala.parameters["Zoom"]!!,     state, labelColW, mixer, gridStartX, 0, getCvColumns, getColumnOffset, getCvColor, onPushUndo)
                PatchGridRenderer.drawParamRow("Rotate Z", "$deckLabel/View/RotateZ", mandala.parameters["Rotate Z"]!!, state, labelColW, mixer, gridStartX, 1, getCvColumns, getColumnOffset, getCvColor, onPushUndo)

                val modeVal = mandala.parameters["3D Mode"]?.value ?: 0f
                val mode    = modeVal.roundToInt().coerceIn(0, 4)
                if (mode > 0) {
                    PatchGridRenderer.drawParamRow("Rotate X", "$deckLabel/View/RotateX", mandala.parameters["Rotate X"]!!, state, labelColW, mixer, gridStartX, 2, getCvColumns, getColumnOffset, getCvColor, onPushUndo)
                    PatchGridRenderer.drawParamRow("Rotate Y", "$deckLabel/View/RotateY", mandala.parameters["Rotate Y"]!!, state, labelColW, mixer, gridStartX, 3, getCvColumns, getColumnOffset, getCvColor, onPushUndo)
                    PatchGridRenderer.drawParamRow("3D Persp", "$deckLabel/View/Persp",   mandala.parameters["3D Persp"]!!, state, labelColW, mixer, gridStartX, 4, getCvColumns, getColumnOffset, getCvColor, onPushUndo)
                }
            }

            // -- Geometry ------------------------------------------------------
            drawSubGroupContent(deckLabel, "Geometry", state) {
                var row = 0
                PatchGridRenderer.drawParamRow("Lobe Count",    "$deckLabel/Geometry/Lobes",       mandala.parameters["Lobes"]!!,         state, labelColW, mixer, gridStartX, row++, getCvColumns, getColumnOffset, getCvColor, onPushUndo)
                PatchGridRenderer.drawParamRow("Recipe ID",     "$deckLabel/Geometry/Recipe",      mandala.parameters["Recipe Select"]!!,  state, labelColW, mixer, gridStartX, row++, getCvColumns, getColumnOffset, getCvColor, onPushUndo)
                PatchGridRenderer.drawParamRow("L1",            "$deckLabel/Geometry/L1",          mandala.parameters["L1"]!!,             state, labelColW, mixer, gridStartX, row++, getCvColumns, getColumnOffset, getCvColor, onPushUndo)
                PatchGridRenderer.drawParamRow("L2",            "$deckLabel/Geometry/L2",          mandala.parameters["L2"]!!,             state, labelColW, mixer, gridStartX, row++, getCvColumns, getColumnOffset, getCvColor, onPushUndo)
                PatchGridRenderer.drawParamRow("L3",            "$deckLabel/Geometry/L3",          mandala.parameters["L3"]!!,             state, labelColW, mixer, gridStartX, row++, getCvColumns, getColumnOffset, getCvColor, onPushUndo)
                PatchGridRenderer.drawParamRow("L4",            "$deckLabel/Geometry/L4",          mandala.parameters["L4"]!!,             state, labelColW, mixer, gridStartX, row++, getCvColumns, getColumnOffset, getCvColor, onPushUndo)
                PatchGridRenderer.drawParamRow("Freq Offset",   "$deckLabel/Geometry/FreqOffset",  mandala.parameters["Freq Offset"]!!,    state, labelColW, mixer, gridStartX, row++, getCvColumns, getColumnOffset, getCvColor, onPushUndo)
                PatchGridRenderer.drawParamRow("Harmonic Lock", "$deckLabel/Geometry/HarmonicLock",mandala.parameters["Harmonic Lock"]!!,  state, labelColW, mixer, gridStartX, row++, getCvColumns, getColumnOffset, getCvColor, onPushUndo)
                PatchGridRenderer.drawParamRow("3D Mode",       "$deckLabel/Geometry/3DMode",      mandala.parameters["3D Mode"]!!,        state, labelColW, mixer, gridStartX, row++, getCvColumns, getColumnOffset, getCvColor, onPushUndo)

                val modeVal = mandala.parameters["3D Mode"]?.value ?: 0f
                val mode    = modeVal.roundToInt().coerceIn(0, 4)
                when (mode) {
                    1 -> {
                        PatchGridRenderer.drawParamRow("Sphere Wrap X", "$deckLabel/Geometry/SphereWrapX", mandala.parameters["Sphere Wrap X"]!!, state, labelColW, mixer, gridStartX, row++, getCvColumns, getColumnOffset, getCvColor, onPushUndo)
                        PatchGridRenderer.drawParamRow("Sphere Wrap Y", "$deckLabel/Geometry/SphereWrapY", mandala.parameters["Sphere Wrap Y"]!!, state, labelColW, mixer, gridStartX, row++, getCvColumns, getColumnOffset, getCvColor, onPushUndo)
                    }
                    2 -> {
                        PatchGridRenderer.drawParamRow("Mirror Group",  "$deckLabel/Geometry/MirrorGroup",  mandala.parameters["Mirror Group"]!!,  state, labelColW, mixer, gridStartX, row++, getCvColumns, getColumnOffset, getCvColor, onPushUndo)
                        PatchGridRenderer.drawParamRow("Sphere Wrap X", "$deckLabel/Geometry/SphereWrapX",  mandala.parameters["Sphere Wrap X"]!!, state, labelColW, mixer, gridStartX, row++, getCvColumns, getColumnOffset, getCvColor, onPushUndo)
                        PatchGridRenderer.drawParamRow("Sphere Wrap Y", "$deckLabel/Geometry/SphereWrapY",  mandala.parameters["Sphere Wrap Y"]!!, state, labelColW, mixer, gridStartX, row++, getCvColumns, getColumnOffset, getCvColor, onPushUndo)
                    }
                    3 -> {
                        PatchGridRenderer.drawParamRow("Permute XY", "$deckLabel/Geometry/PermuteXY", mandala.parameters["Permute XY"]!!, state, labelColW, mixer, gridStartX, row++, getCvColumns, getColumnOffset, getCvColor, onPushUndo)
                        PatchGridRenderer.drawParamRow("Permute YZ", "$deckLabel/Geometry/PermuteYZ", mandala.parameters["Permute YZ"]!!, state, labelColW, mixer, gridStartX, row++, getCvColumns, getColumnOffset, getCvColor, onPushUndo)
                        PatchGridRenderer.drawParamRow("Permute ZX", "$deckLabel/Geometry/PermuteZX", mandala.parameters["Permute ZX"]!!, state, labelColW, mixer, gridStartX, row++, getCvColumns, getColumnOffset, getCvColor, onPushUndo)
                    }
                    4 -> {
                        PatchGridRenderer.drawParamRow("Mirror Group", "$deckLabel/Geometry/MirrorGroup", mandala.parameters["Mirror Group"]!!, state, labelColW, mixer, gridStartX, row++, getCvColumns, getColumnOffset, getCvColor, onPushUndo)
                    }
                }
            }

            // -- Color ---------------------------------------------------------
            drawSubGroupContent(deckLabel, "Color", state) {
                PatchGridRenderer.drawParamRow("Thickness",  "$deckLabel/Color/Thickness",  mandala.parameters["Thickness"]!!,  state, labelColW, mixer, gridStartX, 0, getCvColumns, getColumnOffset, getCvColor, onPushUndo)
                PatchGridRenderer.drawParamRow("Hue Offset", "$deckLabel/Color/HueOffset",  mandala.parameters["Hue Offset"]!!, state, labelColW, mixer, gridStartX, 1, getCvColumns, getColumnOffset, getCvColor, onPushUndo)
                PatchGridRenderer.drawParamRow("Hue Sweep",  "$deckLabel/Color/HueSweep",   mandala.parameters["Hue Sweep"]!!,  state, labelColW, mixer, gridStartX, 2, getCvColumns, getColumnOffset, getCvColor, onPushUndo)
                PatchGridRenderer.drawParamRow("Depth",      "$deckLabel/Color/Depth",      mandala.parameters["Depth"]!!,      state, labelColW, mixer, gridStartX, 3, getCvColumns, getColumnOffset, getCvColor, onPushUndo)
                PatchGridRenderer.drawParamRow("Gain",       "$deckLabel/Color/Gain",       mandala.globalAlpha,                state, labelColW, mixer, gridStartX, 4, getCvColumns, getColumnOffset, getCvColor, onPushUndo)
            }

            // -- Background ----------------------------------------------------
            drawSubGroupContent(deckLabel, "Background", state) {
                PatchGridRenderer.drawParamRow("Bg Style",    "$deckLabel/Background/Style",    mandala.parameters["Bg Style"]!!,    state, labelColW, mixer, gridStartX, 0, getCvColumns, getColumnOffset, getCvColor, onPushUndo)
                PatchGridRenderer.drawParamRow("Bg Feedback", "$deckLabel/Background/Feedback", mandala.parameters["Bg Feedback"]!!, state, labelColW, mixer, gridStartX, 1, getCvColumns, getColumnOffset, getCvColor, onPushUndo)
                PatchGridRenderer.drawParamRow("Bg Hue",      "$deckLabel/Background/Hue",      mandala.parameters["Bg Hue"]!!,      state, labelColW, mixer, gridStartX, 2, getCvColumns, getColumnOffset, getCvColor, onPushUndo)
                PatchGridRenderer.drawParamRow("Bg Sat",      "$deckLabel/Background/Sat",      mandala.parameters["Bg Sat"]!!,      state, labelColW, mixer, gridStartX, 3, getCvColumns, getColumnOffset, getCvColor, onPushUndo)
                PatchGridRenderer.drawParamRow("Bg Val",      "$deckLabel/Background/Val",      mandala.parameters["Bg Val"]!!,      state, labelColW, mixer, gridStartX, 4, getCvColumns, getColumnOffset, getCvColor, onPushUndo)
                PatchGridRenderer.drawParamRow("Bg Sweep",    "$deckLabel/Background/Sweep",    mandala.parameters["Bg Sweep"]!!,    state, labelColW, mixer, gridStartX, 5, getCvColumns, getColumnOffset, getCvColor, onPushUndo)
                PatchGridRenderer.drawParamRow("Bg Speed",    "$deckLabel/Background/Speed",    mandala.parameters["Bg Speed"]!!,    state, labelColW, mixer, gridStartX, 6, getCvColumns, getColumnOffset, getCvColor, onPushUndo)
                PatchGridRenderer.drawParamRow("Bg Zoom",     "$deckLabel/Background/Zoom",     mandala.parameters["Bg Zoom"]!!,     state, labelColW, mixer, gridStartX, 7, getCvColumns, getColumnOffset, getCvColor, onPushUndo)
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
                drawSubGroupContent(deckLabel, "View", state) {
                    transformParams.forEachIndexed { i, (name, param) ->
                        PatchGridRenderer.drawParamRow(name, "$deckLabel/View/$name", param, state, labelColW, mixer, gridStartX, i, getCvColumns, getColumnOffset, getCvColor, onPushUndo)
                    }
                }
            }

            drawSubGroupContent(deckLabel, activeSource.displayName, state) {
                otherParams.forEachIndexed { i, (name, param) ->
                    PatchGridRenderer.drawParamRow(name, "$deckLabel/${activeSource.displayName}/$name", param, state, labelColW, mixer, gridStartX, i, getCvColumns, getColumnOffset, getCvColor, onPushUndo)
                }
                PatchGridRenderer.drawParamRow("Gain", "$deckLabel/${activeSource.displayName}/Gain", activeSource.globalAlpha, state, labelColW, mixer, gridStartX, otherParams.size, getCvColumns, getColumnOffset, getCvColor, onPushUndo)
            }
        }

        // -- Feedback (always present for every deck source) -------------------
        drawSubGroupContent(deckLabel, "Feedback", state) {
            PatchGridRenderer.drawParamRow("Feedback",     "$deckLabel/FB/Decay",    deck.fbDecay,    state, labelColW, mixer, gridStartX, 0, getCvColumns, getColumnOffset, getCvColor, onPushUndo)
            PatchGridRenderer.drawParamRow("FB Gain",      "$deckLabel/FB/Gain",     deck.fbGain,     state, labelColW, mixer, gridStartX, 1, getCvColumns, getColumnOffset, getCvColor, onPushUndo)
            PatchGridRenderer.drawParamRow("FB Zoom",      "$deckLabel/FB/Zoom",     deck.fbZoom,     state, labelColW, mixer, gridStartX, 2, getCvColumns, getColumnOffset, getCvColor, onPushUndo)
            PatchGridRenderer.drawParamRow("FB Rotate",    "$deckLabel/FB/Rotate",   deck.fbRotate,   state, labelColW, mixer, gridStartX, 3, getCvColumns, getColumnOffset, getCvColor, onPushUndo)
            PatchGridRenderer.drawParamRow("FB Hue Shift", "$deckLabel/FB/HueShift", deck.fbHueShift, state, labelColW, mixer, gridStartX, 4, getCvColumns, getColumnOffset, getCvColor, onPushUndo)
            PatchGridRenderer.drawParamRow("FB Blur",      "$deckLabel/FB/Blur",     deck.fbBlur,     state, labelColW, mixer, gridStartX, 5, getCvColumns, getColumnOffset, getCvColor, onPushUndo)
            PatchGridRenderer.drawParamRow("FB Chroma",    "$deckLabel/FB/Chroma",   deck.fbChroma,   state, labelColW, mixer, gridStartX, 6, getCvColumns, getColumnOffset, getCvColor, onPushUndo)
            PatchGridRenderer.drawParamRow("FB Mode",      "$deckLabel/FB/Mode",     deck.fbMode,     state, labelColW, mixer, gridStartX, 7, getCvColumns, getColumnOffset, getCvColor, onPushUndo)
            PatchGridRenderer.drawParamRow("FB Kaleido",   "$deckLabel/FB/Kaleido",  deck.fbKaleido,  state, labelColW, mixer, gridStartX, 8, getCvColumns, getColumnOffset, getCvColor, onPushUndo)
        }
    }
}
