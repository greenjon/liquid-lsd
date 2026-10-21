package llm.slop.liquidlsd.ui

import imgui.ImGui
import imgui.flag.ImGuiCol
import llm.slop.liquidlsd.SessionContext
import kotlin.math.abs

/** Row of one-click beat-interval buttons ("1/4", "1", "2" ...) shared by the Step Sequencer and LFO. */
object BeatPresetPills {
    private const val FONT_SCALE = 0.95f

    /** Label to beat-count pairs, ascending. */
    val SEQ_PRESETS = listOf(
        "1/16" to 0.0625f, "1/8" to 0.125f, "1/4" to 0.25f, "1/2" to 0.5f,
        "1" to 1.0f, "2" to 2.0f, "4" to 4.0f
    )
    val LFO_PRESETS = listOf(
        "1/8" to 0.125f, "1/4" to 0.25f, "1/2" to 0.5f, "1" to 1.0f,
        "2" to 2.0f, "4" to 4.0f, "8" to 8.0f, "16" to 16.0f
    )

    /** Draws the labelled pill row; [onPick] receives the chosen beat value. The pill matching [current] is highlighted. */
    fun draw(
        session: SessionContext,
        idPrefix: String,
        themeColor: Int,
        current: Float,
        presets: List<Pair<String, Float>>,
        onPick: (Float) -> Unit
    ) {
        session.uiTheme.body("Beat Presets:")
        ImGui.sameLine(0f, 8f * FONT_SCALE)
        presets.forEachIndexed { idx, (label, value) ->
            if (idx > 0) ImGui.sameLine(0f, 4f * FONT_SCALE)
            val selected = abs(current - value) < 0.001f
            if (ButtonChrome.toggleButton("$label##beat_preset_${idPrefix}_$idx", selected, themeColor, 42f * FONT_SCALE, 0f)) onPick(value)
        }
    }
}
