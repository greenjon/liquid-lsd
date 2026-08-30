package llm.slop.liquidlsd.ui

import llm.slop.liquidlsd.SessionContext

/**
 * Dynamic resolution-independent layout tokens for Preset Grid cell rendering.
 * All sizes derive proportionally from fontScale (baseSize / 15f).
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
        fun compute(session: SessionContext): GridMetrics {
            val scale = (session.uiTheme.baseSize / 15f).coerceIn(0.8f, 2.5f)
            
            val cell = 35f * scale
            val r = cell * 0.5f
            return GridMetrics(
                cell = cell,
                cellPad = 5f * scale,
                radius = r,
                trackRadius = (r - 5f * scale).coerceAtLeast(4f),
                strokeWidth = (1.5f * scale).coerceIn(1.0f, 4.0f),
                dotRadius = (3.0f * scale).coerceIn(2.0f, 8.0f),
                diceW = 58.33f * scale,
                diceH = cell
            )
        }
    }
}
