package llm.slop.liquidlsd.ui

import llm.slop.liquidlsd.SessionContext

/**
 * Layout tokens for Preset Grid cell rendering fixed at the 95% UI baseline.
 * Uses a precomputed singleton to eliminate per-frame heap allocations.
 */
data class GridMetrics(
    val cell: Float,          // Cell bounding box diameter (px)
    val cellPad: Float,       // Padding between cells (px)
    val radius: Float,        // Outer cell radius = cell * 0.5f
    val trackRadius: Float,   // Inner circular meter track radius
    val strokeWidth: Float,   // Dynamic meter line stroke width
    val dotRadius: Float,     // Value indicator dot size
    val diceW: Float,         // Dice button width
    val diceH: Float          // Dice button height
) {
    companion object {
        val INSTANCE = GridMetrics(
            cell = 33.25f,
            cellPad = 4.75f,
            radius = 16.625f,
            trackRadius = 11.875f,
            strokeWidth = 1.425f,
            dotRadius = 2.85f,
            diceW = 55.4135f,
            diceH = 33.25f
        )

        fun compute(session: SessionContext? = null): GridMetrics = INSTANCE
    }
}
