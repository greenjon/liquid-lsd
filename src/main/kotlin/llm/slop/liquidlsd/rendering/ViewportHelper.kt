package llm.slop.liquidlsd.rendering

import llm.slop.liquidlsd.ui.UITheme

data class ViewportRect(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int
)

object ViewportHelper {
    fun computeViewport(
        screenWidth: Int,
        screenHeight: Int,
        contentWidth: Int,
        contentHeight: Int,
        scaleMode: UITheme.OutputScaleMode
    ): ViewportRect {
        if (screenWidth <= 0 || screenHeight <= 0 || contentWidth <= 0 || contentHeight <= 0) {
            return ViewportRect(0, 0, maxOf(1, screenWidth), maxOf(1, screenHeight))
        }

        return when (scaleMode) {
            UITheme.OutputScaleMode.STRETCH -> {
                ViewportRect(0, 0, screenWidth, screenHeight)
            }
            UITheme.OutputScaleMode.FIT -> {
                val screenAspect = screenWidth.toFloat() / screenHeight.toFloat()
                val contentAspect = contentWidth.toFloat() / contentHeight.toFloat()

                if (screenAspect > contentAspect) {
                    // Screen is wider than content -> Pillarbox (bars on left/right)
                    val vh = screenHeight
                    val vw = (screenHeight * contentAspect).toInt().coerceAtLeast(1)
                    val vx = (screenWidth - vw) / 2
                    ViewportRect(vx, 0, vw, vh)
                } else {
                    // Screen is taller than content -> Letterbox (bars on top/bottom)
                    val vw = screenWidth
                    val vh = (screenWidth / contentAspect).toInt().coerceAtLeast(1)
                    val vy = (screenHeight - vh) / 2
                    ViewportRect(0, vy, vw, vh)
                }
            }
            UITheme.OutputScaleMode.FILL -> {
                val screenAspect = screenWidth.toFloat() / screenHeight.toFloat()
                val contentAspect = contentWidth.toFloat() / contentHeight.toFloat()

                if (screenAspect > contentAspect) {
                    // Screen is wider than content -> match width, crop top/bottom
                    val vw = screenWidth
                    val vh = (screenWidth / contentAspect).toInt().coerceAtLeast(1)
                    val vy = (screenHeight - vh) / 2
                    ViewportRect(0, vy, vw, vh)
                } else {
                    // Screen is taller than content -> match height, crop left/right
                    val vh = screenHeight
                    val vw = (screenHeight * contentAspect).toInt().coerceAtLeast(1)
                    val vx = (screenWidth - vw) / 2
                    ViewportRect(vx, 0, vw, vh)
                }
            }
        }
    }
}
