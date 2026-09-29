package llm.slop.liquidlsd.ui

import imgui.ImGui
import imgui.flag.ImGuiInputTextFlags
import imgui.flag.ImGuiKey
import imgui.type.ImString
import llm.slop.liquidlsd.parameters.MeterType
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Rotary "Macro Knob" control for the Performance rows ([PerformanceMatrixPanel]; Macro Controls
 * system -- see docs/user_guide/macros_and_rack.md).
 *
 * Draws the knob face only (plus an optional in-face [draw] `valueOverlay` or interactive direct
 * numeric entry). Captions and value readouts belong to the caller, which places them in the
 * fixed strip under each knob from [PerfRowGeometry] -- see docs/developer/ui.md §6c.
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

    /** Multiplier applied to [pixelsForFullSweep] when Shift is held for fine-tuning. */
    const val FINE_SWEEP_MULTIPLIER = 6f

    // -- Pure math (unit-testable without ImGui) --------------------------------------------

    /**
     * Maps horizontal and vertical drag deltas (in screen pixels, positive X = rightward, positive Y = downward,
     * matching raw ImGui mouse coordinates) to a new normalized [0,1] value. Dragging up (negative [dragDeltaYPixels])
     * or right (positive [dragDeltaXPixels]) increases the value; dragging down or left decreases it -- the standard
     * DAW/synth convention. This ensures knobs near the top of the screen can be comfortably adjusted by dragging
     * horizontally. [pixelsForFullSweep] pixels of drag sweeps the entire [0,1] range; further drag saturates
     * rather than wrapping.
     */
    fun applyDragDelta(
        currentValue: Float,
        dragDeltaXPixels: Float,
        dragDeltaYPixels: Float,
        pixelsForFullSweep: Float = 200f
    ): Float {
        val safeSweep = if (pixelsForFullSweep > 0f) pixelsForFullSweep else 200f
        val delta = (dragDeltaXPixels - dragDeltaYPixels) / safeSweep
        return (currentValue + delta).coerceIn(0f, 1f)
    }

    /**
     * Vertical-only overload for backwards compatibility.
     */
    fun applyDragDelta(
        currentValue: Float,
        dragDeltaYPixels: Float,
        pixelsForFullSweep: Float = 200f
    ): Float = applyDragDelta(currentValue, 0f, dragDeltaYPixels, pixelsForFullSweep)

    /**
     * Maps a normalized [0,1] value to the angle (radians) of the knob's indicator.
     *
     * For [MeterType.MONOPOLAR] and [MeterType.BIPOLAR], sweeps from -135 deg (value=0, fully counter-clockwise)
     * to +135 deg (value=1, fully clockwise), with 0 deg (straight up) at value=0.5.
     *
     * For [MeterType.ENDLESS] and [MeterType.DISCRETE], 0 and 1 meet at 6 o'clock (straight down in screen space),
     * sweeping a full 360 deg clockwise circle.
     *
     * This is knob-space angle (0 = up); see [toScreenAngle] for the conversion applied when actually drawing.
     */
    fun valueToAngleRadians(value: Float, meterType: MeterType = MeterType.MONOPOLAR): Float {
        val v = value.coerceIn(0f, 1f)
        return when (meterType) {
            MeterType.ENDLESS, MeterType.DISCRETE -> {
                // In knob-space (where 0 = up / screen -PI/2):
                // 6 o'clock straight down is screen +PI/2, which corresponds to knob angle +PI.
                PI.toFloat() + v * (2f * PI.toFloat())
            }
            MeterType.MONOPOLAR, MeterType.BIPOLAR -> {
                -SWEEP_RADIANS + v * (2f * SWEEP_RADIANS)
            }
        }
    }

    /** Converts a knob-space angle (0 = up) into the screen-space angle used by [imgui.ImDrawList] path ops. */
    fun toScreenAngle(knobAngleRadians: Float): Float = SCREEN_UP_ANGLE + knobAngleRadians

    /** Formats a floating-point value for knob center display or text edit. */
    fun formatDisplayValue(v: Float): String =
        if (v == v.toInt().toFloat() && kotlin.math.abs(v) < 1000f) v.toInt().toString()
        else String.format(Locale.ROOT, "%.2f", v)

    // -- Drag/interaction state (single active knob at a time, mirrors CustomRangeSlider) ----

    const val DRAG_LOCK_DEADZONE_PX = 3.0f

    var activeKnobId: String? = null
        internal set
    var dragStartX: Float = 0f
        internal set
    var dragStartY: Float = 0f
        internal set
    var dragStartValue: Float = 0f
        internal set
    var lastShiftHeld: Boolean = false
        internal set
    var isDragLocked: Boolean = false
        internal set
    var lockOriginX: Float = 0f
        internal set
    var lockOriginY: Float = 0f
        internal set

    var wantsCursorLock: Boolean = false
        internal set
    var wantsCursorRelease: Boolean = false
        internal set

    // -- Inline direct text entry state ----------------------------------------------------

    var editingKnobId: String? = null
        internal set
    val editBuffer: ImString = ImString(16)
    var editJustOpened: Boolean = false
        internal set

    fun clearCursorLockRequest() {
        wantsCursorLock = false
    }

    fun clearCursorReleaseRequest() {
        wantsCursorRelease = false
    }

    fun abortDrag() {
        activeKnobId = null
        if (isDragLocked) {
            isDragLocked = false
            wantsCursorRelease = true
        }
        wantsCursorLock = false
        abortEditing()
    }

    fun abortEditing() {
        editingKnobId = null
        editJustOpened = false
    }

    /**
     * Draws one rotary macro knob at the current ImGui cursor position and handles its
     * interaction (vertical/horizontal drag, Shift fine-tuning, double-click text entry,
     * mouse wheel adjust, middle-click reset). Calls [onChanged] with the new normalized value
     * whenever it changes; does not mutate any state itself -- the caller owns that.
     *
     * @param meterType Display mode for the knob: [MeterType.MONOPOLAR] (standard 7:30 to 4:30 sweep),
     *   [MeterType.BIPOLAR] (12 o'clock center detent with left/right sweep), or [MeterType.ENDLESS]
     *   (continuous 360 circle meeting at 6 o'clock).
     * @param accentColor Optional RGB float array `[r, g, b]` (values 0–1).
     * @param label Names the knob in its tooltip (the caller draws any visible caption).
     */
    fun draw(
        session: llm.slop.liquidlsd.SessionContext,
        id: String,
        label: String,
        value: Float,
        meterType: MeterType = MeterType.MONOPOLAR,
        diameter: Float = 56f,
        defaultValue: Float = 0.5f,
        pixelsForFullSweep: Float = 200f,
        isSelected: Boolean = false,
        isLearning: Boolean = false,
        accentColor: FloatArray? = null,
        bindings: List<llm.slop.liquidlsd.macro.MacroBinding> = emptyList(),
        /** When set, drawn as a small readout inside the knob face (e.g. an FX parameter's current value). */
        valueOverlay: String? = null,
        /**
         * The knob's fixed OSC address (e.g. "/macro/deckA/knob/1"), shown in the tooltip for
         * discoverability. Macro knobs always respond to this address with no Learn step needed --
         * see [llm.slop.liquidlsd.macro.MacroOscBridge]. Pass null to omit the line.
         */
        oscAddress: String? = null,
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
        val isDoubleClicked = isHovered && ImGui.isMouseDoubleClicked(0)

        if (ImGui.isItemClicked(0)) {
            onSelect()
        }
        if (ImGui.isItemClicked(1)) {
            onToggleLearn()
        }

        // Double-click triggers inline direct text entry
        if (isDoubleClicked && editingKnobId != id) {
            editingKnobId = id
            editJustOpened = true
            activeKnobId = null
            if (isDragLocked) {
                isDragLocked = false
                wantsCursorRelease = true
            }
            val initialText = if (valueOverlay != null) {
                valueOverlay
            } else if (bindings.isNotEmpty()) {
                val b = bindings.first()
                val realVal = b.minVal + value * (b.maxVal - b.minVal)
                formatDisplayValue(realVal)
            } else {
                formatDisplayValue(value)
            }
            editBuffer.set(initialText)
        }

        val io = ImGui.getIO()

        if (isActivated && editingKnobId != id) {
            activeKnobId = id
            dragStartX = io.mousePos.x
            dragStartY = io.mousePos.y
            dragStartValue = value
            lastShiftHeld = io.keyShift
            isDragLocked = false
            wantsCursorLock = false
            wantsCursorRelease = false
        }

        var newValue = value
        if (isActive && activeKnobId == id && editingKnobId != id) {
            // Re-anchor drag origin if Shift is toggled mid-drag to prevent sudden jumps
            if (io.keyShift != lastShiftHeld) {
                dragStartX = io.mousePos.x
                dragStartY = io.mousePos.y
                dragStartValue = newValue
                lastShiftHeld = io.keyShift
            }

            val deltaX = io.mousePos.x - dragStartX
            val deltaY = io.mousePos.y - dragStartY

            if (!isDragLocked && session.uiTheme.lockCursorOnKnobDrag && !session.touchConsoleController.isActive) {
                val distSq = deltaX * deltaX + deltaY * deltaY
                if (distSq >= DRAG_LOCK_DEADZONE_PX * DRAG_LOCK_DEADZONE_PX) {
                    isDragLocked = true
                    lockOriginX = dragStartX
                    lockOriginY = dragStartY
                    wantsCursorLock = true
                }
            }

            val effectiveSweep = if (io.keyShift) pixelsForFullSweep * FINE_SWEEP_MULTIPLIER else pixelsForFullSweep
            newValue = applyDragDelta(dragStartValue, deltaX, deltaY, effectiveSweep)
        } else if (!isActive && activeKnobId == id) {
            if (isDragLocked) {
                wantsCursorRelease = true
                isDragLocked = false
            }
            activeKnobId = null
        }

        if (isHovered && io.mouseWheel != 0f && editingKnobId != id) {
            val ctrl = io.keyCtrl
            val shift = io.keyShift
            val step = if (ctrl && shift) 0.1f else if (shift) 0.01f else 0.001f
            newValue = (value + io.mouseWheel * step).coerceIn(0f, 1f)
            io.mouseWheel = 0f
        }

        if (isHovered && (ImGui.isMouseClicked(2) || ImGui.isItemClicked(2)) && editingKnobId != id) {
            val resetTarget = if (meterType == MeterType.BIPOLAR && defaultValue == 0f) 0.5f else defaultValue
            newValue = resetTarget.coerceIn(0f, 1f)
        }

        if (newValue != value) {
            onChanged(newValue)
        }

        // -- Drawing --
        val dl = ImGui.getWindowDrawList()

        // Accent-aware colors: use deck tint if provided, fall back to Tango's Alert (Butter).
        val default = TangoPalette.ALERT.normal
        val ar = accentColor?.getOrElse(0) { default[0] } ?: default[0]
        val ag = accentColor?.getOrElse(1) { default[1] } ?: default[1]
        val ab = accentColor?.getOrElse(2) { default[2] } ?: default[2]
        val isEditingThis = editingKnobId == id

        // Knob body blends toward the accent (stronger while dragging or editing).
        val faceTint = when {
            isEditingThis -> 0.45f
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

        val minAngle = toScreenAngle(valueToAngleRadians(0f, meterType))
        val maxAngle = toScreenAngle(valueToAngleRadians(1f, meterType))
        val valAngle = toScreenAngle(valueToAngleRadians(newValue, meterType))

        when (meterType) {
            MeterType.ENDLESS, MeterType.DISCRETE -> {
                // Continuous 360 circle meeting at 6 o'clock
                dl.addCircle(cx, cy, radius, trackCol, 32, 3f)
            }
            MeterType.MONOPOLAR -> {
                dl.pathArcTo(cx, cy, radius, minAngle, maxAngle, 32)
                dl.pathStroke(trackCol, 0, 3f)

                if (newValue > 0f) {
                    dl.pathArcTo(cx, cy, radius, minAngle, valAngle, 32)
                    dl.pathStroke(fillCol, 0, 3f)
                }
            }
            MeterType.BIPOLAR -> {
                dl.pathArcTo(cx, cy, radius, minAngle, maxAngle, 32)
                dl.pathStroke(trackCol, 0, 3f)

                // Center detent tick at 12 o'clock
                val tickOuterR = radius + 2.5f
                val tickInnerR = radius - 2.5f
                dl.addLine(cx, cy - tickInnerR, cx, cy - tickOuterR, trackCol, 2f)

                val noonAngle = SCREEN_UP_ANGLE
                if (newValue > 0.5f) {
                    dl.pathArcTo(cx, cy, radius, noonAngle, valAngle, 16)
                    dl.pathStroke(fillCol, 0, 3f)
                } else if (newValue < 0.5f) {
                    dl.pathArcTo(cx, cy, radius, valAngle, noonAngle, 16)
                    dl.pathStroke(fillCol, 0, 3f)
                }
            }
        }

        // Indicator line from inner radius to near the rim, at the current value's angle.
        if (!isEditingThis) {
            val innerR = radius * 0.3f
            val outerR = radius - 4f
            val ix = cx + innerR * cos(valAngle)
            val iy = cy + innerR * sin(valAngle)
            val ox = cx + outerR * cos(valAngle)
            val oy = cy + outerR * sin(valAngle)
            dl.addLine(ix, iy, ox, oy, fillCol, 2.5f)
            dl.addCircleFilled(ox, oy, 2.2f, fillCol, 8)
        }

        val pulseAlpha = if (isLearning) {
            (sin(System.currentTimeMillis() * 0.008) * 0.35 + 0.65).toFloat()
        } else 1.0f

        val borderCol = when {
            isLearning -> TangoPalette.u32(TangoPalette.SYNC.bright, pulseAlpha)
            isEditingThis -> TangoPalette.u32(TangoPalette.SYNC.normal)
            isActive   -> ImGui.colorConvertFloat4ToU32(ar, ag, ab, 1.0f)
            isSelected -> TangoPalette.u32(TangoPalette.SYNC.normal)
            isHovered  -> ImGui.colorConvertFloat4ToU32(ar, ag, ab, 0.9f)
            else -> null
        }
        if (borderCol != null) {
            val thickness = if (isLearning || isSelected || isEditingThis) 2.5f else 2f
            dl.addCircle(cx, cy, radius + 1.5f, borderCol, 32, thickness)
        }

        // In-face text: either active text editor, authored overlay, or subtle hover/drag readout
        if (isEditingThis) {
            val inputW = (diameter * 0.74f).coerceAtLeast(36f)
            val inputH = 18f
            val inputX = cx - inputW / 2f
            val inputY = cy - inputH / 2f

            dl.addRectFilled(inputX - 2f, inputY - 2f, inputX + inputW + 2f, inputY + inputH + 2f,
                ImGui.colorConvertFloat4ToU32(0.08f, 0.08f, 0.10f, 0.95f), 3f)
            dl.addRect(inputX - 2f, inputY - 2f, inputX + inputW + 2f, inputY + inputH + 2f,
                borderCol ?: fillCol, 3f, 0, 1.5f)

            ImGui.setCursorScreenPos(inputX, inputY)
            ImGui.pushItemWidth(inputW)
            if (editJustOpened) {
                ImGui.setKeyboardFocusHere()
                editJustOpened = false
            }
            val inputFlags = ImGuiInputTextFlags.EnterReturnsTrue or ImGuiInputTextFlags.AutoSelectAll
            val committed = ImGui.inputText("##knob_edit_$id", editBuffer, inputFlags)
            val isInputActive = ImGui.isItemActive()
            val isEscape = ImGui.isKeyPressed(ImGuiKey.Escape, false)

            if (committed) {
                val text = editBuffer.get().trim()
                val parsed = text.toFloatOrNull()
                if (parsed != null) {
                    val newNorm = if (bindings.isNotEmpty()) {
                        val b = bindings.first()
                        val range = b.maxVal - b.minVal
                        if (range != 0f) ((parsed - b.minVal) / range).coerceIn(0f, 1f) else 0.5f
                    } else {
                        parsed.coerceIn(0f, 1f)
                    }
                    newValue = newNorm
                    onChanged(newValue)
                }
                editingKnobId = null
            } else if (isEscape || (!isInputActive && !editJustOpened && ImGui.isMouseClicked(0))) {
                editingKnobId = null
            }
            ImGui.popItemWidth()
        } else {
            if (valueOverlay != null) {
                session.uiTheme.withFont(UITheme.FontLevel.CAPTION) {
                    val sz = ImGui.calcTextSize(valueOverlay)
                    val ovCol = ImGui.colorConvertFloat4ToU32(0.92f, 0.94f, 0.97f, 0.95f)
                    dl.addText(cx - sz.x / 2f, cy + radius * 0.42f - sz.y / 2f, ovCol, valueOverlay)
                }
            } else if (isHovered || isActive) {
                val readout = if (bindings.isNotEmpty()) {
                    val b = bindings.first()
                    val realVal = b.minVal + newValue * (b.maxVal - b.minVal)
                    formatDisplayValue(realVal)
                } else {
                    formatDisplayValue(newValue)
                }
                session.uiTheme.withFont(UITheme.FontLevel.CAPTION) {
                    val sz = ImGui.calcTextSize(readout)
                    val ovCol = ImGui.colorConvertFloat4ToU32(0.92f, 0.94f, 0.97f, if (isActive) 0.95f else 0.70f)
                    dl.addText(cx - sz.x / 2f, cy + radius * 0.42f - sz.y / 2f, ovCol, readout)
                }
            }
        }

        val learnTip = if (isLearning) " [LEARNING... Click target to bind]" else ""
        val bindingLine = formatBindingSummary(bindings)
        val oscLine = if (oscAddress != null) "\nOSC: $oscAddress (always live, no Learn needed)" else ""
        itemTooltip("$label: ${"%.2f".format(newValue)}$learnTip\n$bindingLine\nDrag to adjust (Shift for fine). Double-click to type. Left-click to inspect. Right-click for Learn.$oscLine")

        ImGui.setCursorScreenPos(startX, startY + diameter)
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

