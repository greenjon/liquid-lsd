package llm.slop.liquidlsd.ui

import kotlin.math.max
import kotlin.math.min

/** Fixed measurements shared by the deck tiles of the mixer pane. */
object DeckTileMetrics {
    /** Gap in pixels between a tile's border and its preview image. */
    const val IMAGE_INSET = 3f

    /** Height reserved under a tile's image for toolbars; the toolbars are gone, so none. */
    @Suppress("UNUSED_PARAMETER")
    fun bottomBarHeight(frameHeight: Float = 0f, textLineHeight: Float = 0f, itemSpacingY: Float = 0f): Float = 0f
}

data class MixerLayout(
    val contentWidth: Float,
    val renderWidth: Float,
    val offsetX: Float,
    val masterHeight: Float,
    val deckChildHeight: Float,
    val deckPVHeight: Float = deckChildHeight,
)

/**
 * Solves the mixer pane's sizes. Top to bottom the pane stacks a master preview, a controls bar and
 * two rows of two deck tiles; every preview height follows the output aspect ratio (height / width).
 */
object MixerLayoutCalculator {
    private const val TILE_GUTTER = 8f
    private const val MIN_MASTER_HEIGHT = 120f
    private const val MIN_DECK_HEIGHT = 80f
    private const val MIN_ASPECT = 0.2f
    private const val MAX_ASPECT = 5f

    private fun clampAspect(aspectRatio: Float) = aspectRatio.coerceIn(MIN_ASPECT, MAX_ASPECT)

    /** Everything in the pane that is not preview image: bar, separators, spacings and the row gap. */
    private fun verticalChrome(frameHeightWithSpacing: Float, itemSpacingY: Float): Float {
        val controlsBar = max(frameHeightWithSpacing + 12f, 34f)
        val belowMaster = 3f * itemSpacingY + 1f
        val belowBar = 4f * itemSpacingY + 1f
        val rowGap = itemSpacingY + 6f
        val perTileExtra = 2f * DeckTileMetrics.bottomBarHeight()
        return controlsBar + belowMaster + belowBar + rowGap + perTileExtra + itemSpacingY
    }

    /** Widest content the height allows, or [Float.MAX_VALUE] when no height is left for previews. */
    private fun maxContentWidth(availableHeight: Float, frameHeightWithSpacing: Float, itemSpacingY: Float, aspect: Float): Float {
        val previewHeight = availableHeight - verticalChrome(frameHeightWithSpacing, itemSpacingY)
        if (previewHeight <= 0f) return Float.MAX_VALUE
        return (previewHeight + TILE_GUTTER * aspect) / (2f * aspect)
    }

    @Suppress("UNUSED_PARAMETER")
    fun calculate(
        windowWidth: Float,
        availableHeight: Float,
        windowPaddingX: Float,
        scrollbarWidth: Float,
        textLineHeightWithSpacing: Float,
        frameHeightWithSpacing: Float,
        itemSpacingY: Float,
        aspectRatio: Float = 9f / 16f,
        randomizationEnabled: Boolean = true,
    ): MixerLayout {
        val aspect = clampAspect(aspectRatio)
        val contentWidth = max(windowWidth - 2f * windowPaddingX, 1f)
        val limit = maxContentWidth(availableHeight, frameHeightWithSpacing, itemSpacingY, aspect)
        val renderWidth = max(min(contentWidth, limit), 1f)
        val offsetX = max((contentWidth - renderWidth) / 2f, 0f)

        val tileWidth = max((renderWidth - TILE_GUTTER) / 2f, 1f)
        val imageWidth = max(tileWidth - 2f * DeckTileMetrics.IMAGE_INSET, 1f)
        val deckHeight = max(imageWidth * aspect + DeckTileMetrics.bottomBarHeight(), MIN_DECK_HEIGHT)
        return MixerLayout(
            contentWidth = contentWidth,
            renderWidth = renderWidth,
            offsetX = offsetX,
            masterHeight = max(renderWidth * aspect, MIN_MASTER_HEIGHT),
            deckChildHeight = deckHeight,
            deckPVHeight = deckHeight,
        )
    }

    @Suppress("UNUSED_PARAMETER")
    fun calculateMaxAllowedWindowWidth(
        availableHeight: Float,
        windowPaddingX: Float,
        textLineHeightWithSpacing: Float,
        frameHeightWithSpacing: Float,
        itemSpacingY: Float,
        aspectRatio: Float = 9f / 16f,
        randomizationEnabled: Boolean = true,
    ): Float {
        val aspect = clampAspect(aspectRatio)
        val content = maxContentWidth(availableHeight, frameHeightWithSpacing, itemSpacingY, aspect)
        return if (content == Float.MAX_VALUE) content else content + 2f * windowPaddingX
    }
}
