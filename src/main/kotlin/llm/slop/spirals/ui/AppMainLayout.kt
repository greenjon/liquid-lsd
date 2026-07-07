package llm.slop.spirals.ui

data class AppMainLayout(
    val libraryWidth: Float,
    val rightWidth: Float,
    val patchGridWidth: Float,
    val cellConfigWidth: Float,
    val assetBrowserHeight: Float
)

object AppMainLayoutCalculator {
    fun calculate(displayWidth: Float, contentHeight: Float, assetBrowserMode: UITheme.AssetBrowserMode): AppMainLayout {
        val rightMin = when {
            displayWidth < 1100f -> 320f
            displayWidth < 1500f -> 360f
            else -> 420f
        }
        val rightTarget = (displayWidth * 0.30f).coerceAtLeast(rightMin)
        val rightMax = (displayWidth * 0.38f).coerceAtMost(displayWidth - 560f).coerceAtLeast(rightMin)
        val rightWidth = rightTarget.coerceAtMost(rightMax).coerceAtMost(displayWidth - 360f)
        val libraryWidth = (displayWidth - rightWidth).coerceAtLeast(360f)

        val assetBrowserHeight = when (assetBrowserMode) {
            UITheme.AssetBrowserMode.FULL -> contentHeight
            UITheme.AssetBrowserMode.HALF -> contentHeight * 0.5f
            UITheme.AssetBrowserMode.HIDE -> 38f
        }

        val patchMin = if (displayWidth < 1000f) 360f else 420f
        val cellMin = if (displayWidth < 1000f) 220f else 320f
        val patchTarget = displayWidth * if (displayWidth < 1300f) 0.34f else 0.30f
        val patchMax = (libraryWidth - cellMin).coerceAtLeast(patchMin)
        val patchGridWidth = patchTarget.coerceAtLeast(patchMin).coerceAtMost(patchMax)
        val cellConfigWidth = (libraryWidth - patchGridWidth).coerceAtLeast(1f)

        return AppMainLayout(
            libraryWidth = libraryWidth,
            rightWidth = rightWidth,
            patchGridWidth = patchGridWidth,
            cellConfigWidth = cellConfigWidth,
            assetBrowserHeight = assetBrowserHeight
        )
    }
}
