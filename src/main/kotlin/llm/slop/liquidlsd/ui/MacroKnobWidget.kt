package llm.slop.liquidlsd.ui

import imgui.ImGui
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Reusable rotary "Macro Knob" control and its sibling "Macro Switch" button, used by
 * [MacroPanel] (Phase 2 of the Macro Controls system -- see
 * docs/developer/macro_controls_and_parameter_linking_proposal.md).
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
        val faceCol = ImGui.colorConvertFloat4ToU32(0.12f, 0.12f, 0.12f, 1f)
        val trackCol = ImGui.colorConvertFloat4ToU32(0.22f, 0.22f, 0.22f, 1f)
        val fillCol = ImGui.colorConvertFloat4ToU32(1.0f, 0.75f, 0.15f, 1f) // Bright Amber Gold

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
            isActive -> ImGui.colorConvertFloat4ToU32(0.0f, 0.85f, 1.0f, 1.0f) // Electric Cyan while dragging
            isSelected -> ImGui.colorConvertFloat4ToU32(0.10f, 0.65f, 0.92f, 1.0f) // Selected accent ring
            isHovered -> ImGui.colorConvertFloat4ToU32(1.0f, 0.75f, 0.15f, 0.9f) // Amber Gold on hover
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

        if (isHovered) {
            val learnTip = if (isLearning) " [LEARNING... Click target to bind]" else ""
            showTooltip("$label: ${"%.2f".format(newValue)}$learnTip\nDrag to adjust. Left-click to inspect. Right-click for Learn.")
        }

        val captionH = session.uiTheme.withFont(UITheme.FontLevel.CAPTION) { ImGui.getTextLineHeight() }
        ImGui.setCursorScreenPos(startX, startY + diameter + 3f + captionH + 4f)
        ImGui.dummy(0f, 0f)
    }

    // -- Macro Switch -------------------------------------------------------------------------

    private var activeSwitchId: String? = null

    /**
     * Draws one macro switch button. Calls [llm.slop.liquidlsd.macro.MacroControl.onPress] on the mouse-down edge
     * (not full click completion -- MOMENTARY needs press semantics) and
     * [llm.slop.liquidlsd.macro.MacroControl.onRelease] on the release edge.
     */
    fun drawSwitch(
        session: llm.slop.liquidlsd.SessionContext,
        id: String,
        label: String,
        control: llm.slop.liquidlsd.macro.MacroControl,
        width: Float = 64f,
        height: Float = 34f,
        isSelected: Boolean = false,
        isLearning: Boolean = false,
        onSelect: () -> Unit = {},
        onToggleLearn: () -> Unit = {}
    ) {
        val startX = ImGui.getCursorScreenPosX()
        val startY = ImGui.getCursorScreenPosY()

        ImGui.invisibleButton("##macro_switch_$id", width, height)
        val isHovered = ImGui.isItemHovered()
        val isActivated = ImGui.isItemActivated()
        val isActive = ImGui.isItemActive()

        if (ImGui.isItemClicked(0)) {
            onSelect()
        }
        if (ImGui.isItemClicked(1)) {
            onToggleLearn()
        }

        if (isActivated) {
            activeSwitchId = id
            control.onPress()
        }
        if (!isActive && activeSwitchId == id) {
            control.onRelease()
            activeSwitchId = null
        }

        val isLit = control.value >= 0.5f
        val dl = ImGui.getWindowDrawList()
        val bgCol = when {
            isLit -> ImGui.colorConvertFloat4ToU32(0.0f, 0.85f, 1.0f, 0.9f) // Electric Cyan lit
            isActive -> ImGui.colorConvertFloat4ToU32(0.3f, 0.3f, 0.3f, 1f)
            isHovered -> ImGui.colorConvertFloat4ToU32(0.24f, 0.24f, 0.24f, 1f)
            else -> ImGui.colorConvertFloat4ToU32(0.15f, 0.15f, 0.15f, 1f)
        }

        val pulseAlpha = if (isLearning) {
            (sin(System.currentTimeMillis() * 0.008) * 0.35 + 0.65).toFloat()
        } else 1.0f

        val borderCol = when {
            isLearning -> ImGui.colorConvertFloat4ToU32(0.0f, 0.95f, 1.0f, pulseAlpha)
            isActive -> ImGui.colorConvertFloat4ToU32(0.0f, 0.85f, 1.0f, 1.0f)
            isSelected -> ImGui.colorConvertFloat4ToU32(0.10f, 0.65f, 0.92f, 1.0f)
            isHovered -> ImGui.colorConvertFloat4ToU32(1.0f, 0.75f, 0.15f, 0.9f)
            isLit -> ImGui.colorConvertFloat4ToU32(0.6f, 0.95f, 1.0f, 1f)
            else -> ImGui.colorConvertFloat4ToU32(0.35f, 0.35f, 0.35f, 0.8f)
        }

        val thickness = if (isLearning || isSelected) 2.5f else 1.5f
        dl.addRectFilled(startX, startY, startX + width, startY + height, bgCol, 4f)
        dl.addRect(startX, startY, startX + width, startY + height, borderCol, 4f, 0, thickness)

        var tw = 0f
        var th = 0f
        session.uiTheme.withFont(UITheme.FontLevel.CAPTION) {
            val sz = ImGui.calcTextSize(label)
            tw = sz.x
            th = sz.y
        }
        val textCol = if (isLit) ImGui.colorConvertFloat4ToU32(0.02f, 0.02f, 0.02f, 1f) else ImGui.colorConvertFloat4ToU32(0.85f, 0.85f, 0.85f, 0.95f)
        val textX = startX + (width - tw) / 2f
        val textY = startY + (height - th) / 2f
        session.uiTheme.withFont(UITheme.FontLevel.CAPTION) {
            dl.addText(textX, textY, textCol, label)
        }

        if (isHovered) {
            val behaviorText = when (control.switchBehavior) {
                llm.slop.liquidlsd.macro.SwitchBehavior.TOGGLE -> "Toggle: click to latch on/off."
                llm.slop.liquidlsd.macro.SwitchBehavior.MOMENTARY -> "Momentary: on while held."
                llm.slop.liquidlsd.macro.SwitchBehavior.TRIGGER -> "Trigger: sends a one-frame pulse."
            }
            val learnTip = if (isLearning) " [LEARNING...]" else ""
            showTooltip("$label$learnTip\n$behaviorText\nLeft-click to trigger/select. Right-click for Learn.")
        }
    }
}
