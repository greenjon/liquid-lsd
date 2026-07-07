package llm.slop.spirals.ui

import llm.slop.spirals.config.UiConfig

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
            displayWidth < UiConfig.MainLayout.RIGHT_COMPACT_BREAKPOINT -> UiConfig.MainLayout.RIGHT_COMPACT_MIN
            displayWidth < UiConfig.MainLayout.RIGHT_MEDIUM_BREAKPOINT -> UiConfig.MainLayout.RIGHT_MEDIUM_MIN
            else -> UiConfig.MainLayout.RIGHT_WIDE_MIN
        }
        val rightTarget = (displayWidth * UiConfig.MainLayout.RIGHT_TARGET_RATIO).coerceAtLeast(rightMin)
        val rightMax = (displayWidth * UiConfig.MainLayout.RIGHT_MAX_RATIO)
            .coerceAtMost(displayWidth - UiConfig.MainLayout.RIGHT_MAX_RESERVED_WIDTH)
            .coerceAtLeast(rightMin)
        val rightWidth = rightTarget.coerceAtMost(rightMax).coerceAtMost(displayWidth - UiConfig.MainLayout.RIGHT_RESERVED_MIN_WIDTH)
        val libraryWidth = (displayWidth - rightWidth).coerceAtLeast(UiConfig.MainLayout.LIBRARY_MIN_WIDTH)

        val assetBrowserHeight = when (assetBrowserMode) {
            UITheme.AssetBrowserMode.FULL -> contentHeight
            UITheme.AssetBrowserMode.HALF -> contentHeight * UiConfig.MainLayout.ASSET_BROWSER_HALF_RATIO
            UITheme.AssetBrowserMode.HIDE -> UiConfig.MainLayout.ASSET_BROWSER_HIDDEN_HEIGHT
        }

        val patchMin = if (displayWidth < UiConfig.MainLayout.NARROW_WIDTH) {
            UiConfig.MainLayout.PATCH_GRID_COMPACT_MIN
        } else {
            UiConfig.MainLayout.PATCH_GRID_WIDE_MIN
        }
        val cellMin = if (displayWidth < UiConfig.MainLayout.NARROW_WIDTH) {
            UiConfig.MainLayout.CELL_CONFIG_COMPACT_MIN
        } else {
            UiConfig.MainLayout.CELL_CONFIG_WIDE_MIN
        }
        val patchTarget = displayWidth * if (displayWidth < UiConfig.MainLayout.PATCH_GRID_COMPACT_BREAKPOINT) {
            UiConfig.MainLayout.PATCH_GRID_COMPACT_RATIO
        } else {
            UiConfig.MainLayout.PATCH_GRID_WIDE_RATIO
        }
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
