package llm.slop.liquidlsd.ui

import imgui.ImGui
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Reusable rotary "Macro Knob" control, used by [MacroPanel] (Macro Controls system -- see
 * docs/user_guide/macros_and_rack.md).
 *
 * Follows the same hand-rolled-ImGui-widget idiom as [CustomRangeSlider]: an
 * [ImGui.invisibleButton] hit-region, [ImGui.isItemActivated]/[ImGui.isItemActive] for drag-state
 * tracking, manual [ImGui.getWindowDrawList] rendering, and the app-wide hover/active border
 * convention (Amber Gold on hover, Electric Cyan while actively dragging).
 *
 * The pure math ([applyDragDelta], [valueToAngleRadians]) is factored out so it is unit-testable
 * without an ImGui context -- see MacroKnobWidgetTest.
 */
object MacroKnobWidget {

    /** Half of the total sweep, in degrees. Standard pot sweep: -135 deg (value=0) .. +135 deg (value=1). */
    private const val SWEEP_DEGREES = 135f
    private val SWEEP_RADIANS = SWEEP_DEGREES * (PI.toFloat() / 180f)

    /** Screen-space angle (radians, 0 = +x axis, increasing clockwise since screen Y grows downward) for "straight up". */
    private const val SCREEN_UP_ANGLE = -(PI.toFloat() / 2f)

    // -- Pure math (unit-testable without ImGui) --------------------------------------------

    /**
     * Maps a vertical drag delta (in screen pixels, positive = downward, matching raw ImGui mouse
     * coordinates) to a new normalized [0,1] value. Dragging up (negative [dragDeltaYPixels])
     * increases the value; dragging down decreases it -- the standard DAW/synth convention.
     * [pixelsForFullSweep] pixels of drag sweeps the entire [0,1] range; further drag saturates
     * rather than wrapping.
     */
    fun applyDragDelta(currentValue: Float, dragDeltaYPixels: Float, pixelsForFullSweep: Float = 200f): Float {
        val safeSweep = if (pixelsForFullSweep > 0f) pixelsForFullSweep else 200f
        val delta = -dragDeltaYPixels / safeSweep
        return (currentValue + delta).coerceIn(0f, 1f)
    }

    /**
     * Maps a normalized [0,1] value to the angle (radians) of the knob's indicator, sweeping from
     * -135 deg (value=0, fully counter-clockwise) to +135 deg (value=1, fully clockwise), with 0 deg
     * (straight up) at value=0.5. This is knob-space angle (0 = up); see [toScreenAngle] for the
     * conversion applied when actually drawing.
     */
    fun valueToAngleRadians(value: Float): Float {
        val v = value.coerceIn(0f, 1f)
        return -SWEEP_RADIANS + v * (2f * SWEEP_RADIANS)
    }

    /** Converts a knob-space angle (0 = up) into the screen-space angle used by [imgui.ImDrawList] path ops. */
    private fun toScreenAngle(knobAngleRadians: Float): Float = SCREEN_UP_ANGLE + knobAngleRadians

    // -- Drag/interaction state (single active knob at a time, mirrors CustomRangeSlider) ----

    private var activeKnobId: String? = null
    private var dragStartY = 0f
    private var dragStartValue = 0f

    /**
     * Draws one rotary macro knob at the current ImGui cursor position and handles its
     * interaction (vertical drag, mouse wheel fine-adjust, middle-click reset). Calls
     * [onChanged] with the new normalized value whenever it changes; does not mutate any state
     * itself -- the caller (typically binding straight to a [llm.slop.liquidlsd.macro.MacroControl.value]) owns that.
     *
     * @param accentColor Optional RGB float array `[r, g, b]` (values 0–1). When supplied the arc
     *   fill, indicator line, and hover/active border ring use this tint instead of the default
     *   amber gold. Pass `null` to keep the existing amber style (used by [MacroPanel]).
     */
    fun draw(
        session: llm.slop.liquidlsd.SessionContext,
        id: String,
        label: String,
        value: Float,
        diameter: Float = 56f,
        defaultValue: Float = 0.5f,
        pixelsForFullSweep: Float = 200f,
        isSelected: Boolean = false,
        isLearning: Boolean = false,
        accentColor: FloatArray? = null,
        bindings: List<llm.slop.liquidlsd.macro.MacroBinding> = emptyList(),
        showValue: Boolean = false,
        onSelect: () -> Unit = {},
        onToggleLearn: () -> Unit = {},
        onChanged: (Float) -> Unit
    ) {
        val radius = diameter / 2f
        val startX = ImGui.getCursorScreenPosX()
        val startY = ImGui.getCursorScreenPosY()
        val cx = startX + radius
        val cy = startY + radius

        ImGui.invisibleButton("##macro_knob_$id", diameter, diameter)
        val isHovered = ImGui.isItemHovered()
        val isActivated = ImGui.isItemActivated()
        val isActive = ImGui.isItemActive()

        if (ImGui.isItemClicked(0)) {
            onSelect()
        }
        if (ImGui.isItemClicked(1)) {
            onToggleLearn()
        }

        val io = ImGui.getIO()

        if (isActivated) {
            activeKnobId = id
            dragStartY = io.mousePos.y
            dragStartValue = value
        }

        var newValue = value
        if (isActive && activeKnobId == id) {
            val deltaY = io.mousePos.y - dragStartY
            newValue = applyDragDelta(dragStartValue, deltaY, pixelsForFullSweep)
        } else if (!isActive && activeKnobId == id) {
            activeKnobId = null
        }

        if (isHovered && io.mouseWheel != 0f) {
            val ctrl = io.keyCtrl
            val shift = io.keyShift
            val step = if (ctrl && shift) 0.1f else if (shift) 0.01f else 0.001f
            newValue = (value + io.mouseWheel * step).coerceIn(0f, 1f)
            io.mouseWheel = 0f
        }

        if (isHovered && (ImGui.isMouseClicked(2) || ImGui.isItemClicked(2))) {
            newValue = defaultValue.coerceIn(0f, 1f)
        }

        if (newValue != value) {
            onChanged(newValue)
        }

        // -- Drawing --
        val dl = ImGui.getWindowDrawList()

        // Accent-aware colors: use deck tint if provided, fall back to amber gold.
        val ar = accentColor?.getOrElse(0) { 1.0f } ?: 1.0f
        val ag = accentColor?.getOrElse(1) { 0.75f } ?: 0.75f
        val ab = accentColor?.getOrElse(2) { 0.15f } ?: 0.15f
        // The hover/active ring shares the arc's accent color, so on its own it reads too subtly --
        // the knob body also blends toward the accent (stronger while dragging).
        val faceTint = when {
            isActive  -> 0.38f
            isHovered -> 0.22f
            else -> 0f
        }
        val faceCol = ImGui.colorConvertFloat4ToU32(
            0.12f + (ar - 0.12f) * faceTint,
            0.12f + (ag - 0.12f) * faceTint,
            0.12f + (ab - 0.12f) * faceTint,
            1f
        )
        val fillCol   = ImGui.colorConvertFloat4ToU32(ar, ag, ab, 1f)
        val trackCol  = ImGui.colorConvertFloat4ToU32(ar * 0.35f, ag * 0.35f, ab * 0.35f, 1f)

        dl.addCircleFilled(cx, cy, radius - 2f, faceCol, 32)

        val minAngle = toScreenAngle(valueToAngleRadians(0f))
        val maxAngle = toScreenAngle(valueToAngleRadians(1f))
        val valAngle = toScreenAngle(valueToAngleRadians(newValue))

        dl.pathArcTo(cx, cy, radius, minAngle, maxAngle, 32)
        dl.pathStroke(trackCol, 0, 3f)

        if (newValue > 0f) {
            dl.pathArcTo(cx, cy, radius, minAngle, valAngle, 32)
            dl.pathStroke(fillCol, 0, 3f)
        }

        // Indicator line from inner radius to near the rim, at the current value's angle.
        val innerR = radius * 0.3f
        val outerR = radius - 4f
        val ix = cx + innerR * cos(valAngle)
        val iy = cy + innerR * sin(valAngle)
        val ox = cx + outerR * cos(valAngle)
        val oy = cy + outerR * sin(valAngle)
        dl.addLine(ix, iy, ox, oy, fillCol, 2.5f)
        dl.addCircleFilled(ox, oy, 2.2f, fillCol, 8)

        val pulseAlpha = if (isLearning) {
            (sin(System.currentTimeMillis() * 0.008) * 0.35 + 0.65).toFloat()
        } else 1.0f

        val borderCol = when {
            isLearning -> ImGui.colorConvertFloat4ToU32(0.0f, 0.95f, 1.0f, pulseAlpha)
            isActive   -> ImGui.colorConvertFloat4ToU32(ar, ag, ab, 1.0f)
            isSelected -> ImGui.colorConvertFloat4ToU32(0.10f, 0.65f, 0.92f, 1.0f)
            isHovered  -> ImGui.colorConvertFloat4ToU32(ar, ag, ab, 0.9f)
            else -> null
        }
        if (borderCol != null) {
            val thickness = if (isLearning || isSelected) 2.5f else 2f
            dl.addCircle(cx, cy, radius + 1.5f, borderCol, 32, thickness)
        }

        // Label centered below the knob face.
        val labelY = startY + diameter + 3f
        var labelW = 0f
        session.uiTheme.withFont(UITheme.FontLevel.CAPTION) { labelW = ImGui.calcTextSize(label).x }
        val labelX = cx - labelW / 2f
        ImGui.setCursorScreenPos(labelX, labelY)
        val labelCol = if (isSelected) {
            ImGui.colorConvertFloat4ToU32(0.2f, 0.85f, 1.0f, 1.0f)
        } else {
            ImGui.colorConvertFloat4ToU32(0.8f, 0.8f, 0.8f, 0.9f)
        }
        session.uiTheme.withFont(UITheme.FontLevel.CAPTION) {
            dl.addText(labelX, labelY, labelCol, label)
        }

        val captionH = session.uiTheme.withFont(UITheme.FontLevel.CAPTION) { ImGui.getTextLineHeight() }
        if (showValue) {
            val valStr = "Val: ${"%.2f".format(newValue)}"
            var valW = 0f
            session.uiTheme.withFont(UITheme.FontLevel.CAPTION) { valW = ImGui.calcTextSize(valStr).x }
            val valX = cx - valW / 2f
            val valY = labelY + captionH + 1f
            val valCol = if (isSelected) {
                ImGui.colorConvertFloat4ToU32(0.2f, 0.85f, 1.0f, 0.95f)
            } else {
                ImGui.colorConvertFloat4ToU32(0.6f, 0.65f, 0.75f, 0.85f)
            }
            session.uiTheme.withFont(UITheme.FontLevel.CAPTION) {
                dl.addText(valX, valY, valCol, valStr)
            }
        }

        val learnTip = if (isLearning) " [LEARNING... Click target to bind]" else ""
        val bindingLine = formatBindingSummary(bindings)
        itemTooltip("$label: ${"%.2f".format(newValue)}$learnTip\n$bindingLine\nDrag to adjust. Left-click to inspect. Right-click for Learn.")

        val totalTextH = if (showValue) captionH * 2f + 2f else captionH
        ImGui.setCursorScreenPos(startX, startY + diameter + 3f + totalTextH)
    }

    /**
     * Builds the one-line binding summary shown in knob/switch tooltips, e.g.
     * "Bound to: viewZoom [0.20 – 3.00]", sourced straight from the control's primary
     * (first) [llm.slop.liquidlsd.macro.MacroBinding]. Falls back to an "Unbound" hint
     * matching the empty-state affordance (right-click arms Learn Mode; see
     * onToggleLearn) when no binding exists yet.
     */
    private fun formatBindingSummary(bindings: List<llm.slop.liquidlsd.macro.MacroBinding>): String {
        val binding = bindings.firstOrNull() ?: return "Unbound – right-click to assign"
        return "Bound to: ${binding.parameterId} [${"%.2f".format(binding.minVal)} – ${"%.2f".format(binding.maxVal)}]"
    }
}
