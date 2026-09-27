package llm.slop.liquidlsd.ui

/**
 * Where everything on a Performance row goes -- knobs, the strip under each knob, the side-button
 * stack left of each knob -- computed from window size and font metrics only. It deliberately
 * takes no row mode (SRC / MIX / FX group / FX focus) and no bank id: a mode changes *what* a knob
 * shows ([KnobSpec]), never *where* anything is drawn, so toggling any row can't resize or move a
 * knob on any row. Pure (no ImGui calls) so the invariants are unit-tested -- see PerfRowLayoutTest.
 *
 * X coordinates are relative to the grid's left edge; Y coordinates to the row's top edge.
 *
 * @param gridW grid width.
 * @param rowH one row's Perform-view height (Deep Edit's extras are added below it, not inside).
 * @param bodyLineH BODY font line height -- the under-knob label's font.
 * @param leftW widest left-of-knobs control block across both tabs (badge + control lines).
 * @param rightW widest right-of-knobs control block across both tabs.
 */
internal class PerfRowGeometry(
    val gridW: Float,
    val rowH: Float,
    bodyLineH: Float,
    leftW: Float,
    rightW: Float
) {
    companion object {
        /** Horizontal inset of row content from the box edge. */
        const val PAD = 6f
        /** Gap between a row's box and the next row's / the grid's edge. */
        const val BOX_MARGIN_Y = 2f
        /** Inner padding between the box border and its content. */
        const val BOX_PAD = 2.5f
        /** Gap between the side control blocks and the knob cluster. */
        const val SIDE_GUTTER = 12f
        /** Gap between the knob face and its strip. */
        const val STRIP_GAP = 2f
        /** Gap below the strip. */
        const val STRIP_MARGIN = 2f
        /** Side buttons (Super Knob link / slot bypass / param reset), stacked left of the knob. */
        const val SIDE_BTN = 18f
        const val SIDE_BTN_GAP = 2f
        /** Gap between the side-button stack and the knob face. */
        const val SIDE_BTN_INSET = 2f
        const val MIN_DIAMETER = 20f
        const val MAX_DIAMETER = 100f

        /** Every knob's strip fits a BODY label *and* an FX slot/param cell, so all modes share it. */
        fun stripHeight(bodyLineH: Float): Float = maxOf(bodyLineH, FxSlotCell.HEIGHT, FxParamCell.HEIGHT)
    }

    /** Height of the under-knob strip (label or FX cell), identical for every knob. */
    val stripH: Float = stripHeight(bodyLineH)

    /** Left edge of the knob cluster's reserved band, after the left controls. */
    val clusterLeft: Float = PAD + leftW + SIDE_GUTTER
    private val clusterRight: Float = gridW - PAD - rightW - SIDE_GUTTER
    private val clusterW: Float = (clusterRight - clusterLeft).coerceAtLeast(100f)

    /** Column pitch: 4 equal columns across the cluster band. */
    val colW: Float = clusterW / 4f

    val diameter: Float = run {
        // The side-button stack is reserved on both sides so the knob stays centered in its column
        // and the stack never reaches the previous column.
        val byWidth = colW - 2f * (SIDE_BTN + SIDE_BTN_INSET + 2f)
        val contentH = rowH - 2f * BOX_MARGIN_Y - 2f * BOX_PAD
        val byHeight = contentH - (STRIP_GAP + stripH + STRIP_MARGIN)
        minOf(byWidth, byHeight).coerceIn(MIN_DIAMETER, MAX_DIAMETER)
    }

    /** Knob face + strip, the fixed footprint centered vertically in every row. */
    val widgetH: Float = diameter + STRIP_GAP + stripH + STRIP_MARGIN

    /** Top of every knob face, from the row top. */
    val knobTop: Float = run {
        val contentTop = BOX_MARGIN_Y + BOX_PAD
        val contentH = rowH - 2f * BOX_MARGIN_Y - 2f * BOX_PAD
        contentTop + ((contentH - widgetH) * 0.5f).coerceAtLeast(0f)
    }

    /** Top of every strip, from the row top. */
    val stripTop: Float = knobTop + diameter + STRIP_GAP

    fun colCenterX(col: Int): Float = clusterLeft + col * colW + colW / 2f
    fun knobX(col: Int): Float = colCenterX(col) - diameter / 2f

    /** Strip spans the column minus a small inset, so neighboring cells never touch. */
    val stripW: Float = (colW - 6f).coerceAtLeast(40f)
    fun stripX(col: Int): Float = colCenterX(col) - stripW / 2f

    fun sideBtnX(col: Int): Float = knobX(col) - SIDE_BTN_INSET - SIDE_BTN

    /** Y of side button [index] in a stack of [count] (1 or 2) buttons centered on the knob. */
    fun sideBtnY(index: Int, count: Int): Float {
        val stackH = count * SIDE_BTN + (count - 1) * SIDE_BTN_GAP
        return knobTop + diameter / 2f - stackH / 2f + index * (SIDE_BTN + SIDE_BTN_GAP)
    }
}
