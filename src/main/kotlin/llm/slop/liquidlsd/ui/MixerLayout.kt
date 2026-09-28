package llm.slop.liquidlsd.ui

/**
 * Single source of truth for the per-deck-tile geometry that [DeckControlPanel] actually draws
 * (image inset, toolbar-row height), so [MixerLayoutCalculator] can reserve exactly what will be
 * rendered instead of a second, independently-drifting estimate.
 */
object DeckTileMetrics {
    const val IMAGE_INSET = 3f

    /** Height of any toolbar rows above a deck preview (now 0f since toolbars were removed). */
    fun bottomBarHeight(frameHeight: Float = 0f, textLineHeight: Float = 0f, itemSpacingY: Float = 0f): Float = 0f
}

data class MixerLayout(
    val contentWidth: Float,
    val renderWidth: Float,
    val offsetX: Float,
    val masterHeight: Float,
    val deckChildHeight: Float,
    val deckPVHeight: Float = deckChildHeight
)

object MixerLayoutCalculator {
    private const val TWO_DECK_PADDING = 8f
    private const val MIN_MASTER_HEIGHT = 120f
    private const val MIN_DECK_CHILD_HEIGHT = 80f

    fun calculateMaxAllowedWindowWidth(
        availableHeight: Float,
        windowPaddingX: Float,
        textLineHeightWithSpacing: Float,
        frameHeightWithSpacing: Float,
        itemSpacingY: Float,
        aspectRatio: Float = 9f / 16f,
        randomizationEnabled: Boolean = true
    ): Float {
        val aspect = aspectRatio.coerceIn(0.2f, 5.0f)
        val masterControlsH = (frameHeightWithSpacing + 12f).coerceAtLeast(34f)
        val presetNameExtraHeight = deckPreviewNonImageHeight(frameHeightWithSpacing, textLineHeightWithSpacing, itemSpacingY)

        val verticalChrome = estimateVerticalChrome(
            masterControlsH = masterControlsH,
            presetNameExtraHeight = presetNameExtraHeight,
            itemSpacingY = itemSpacingY
        )
        val availableForPreviews = (availableHeight - verticalChrome).coerceAtLeast(0f)

        // Sum of aspect preview heights: master (1.0 * aspect) + Row 1 (0.5 * aspect) + Row 2 (0.5 * aspect) = 2.0 * aspect
        val aspectMultiplier = 2.0f * aspect
        val aspectOffset = 8f * aspect
        val maxAllowedContentWidth = if (availableForPreviews > 0f && aspectMultiplier > 0f) {
            (availableForPreviews + aspectOffset) / aspectMultiplier
        } else {
            Float.MAX_VALUE
        }
        return maxAllowedContentWidth + (windowPaddingX * 2f)
    }

    fun calculate(
        windowWidth: Float,
        availableHeight: Float,
        windowPaddingX: Float,
        scrollbarWidth: Float,
        textLineHeightWithSpacing: Float,
        frameHeightWithSpacing: Float,
        itemSpacingY: Float,
        aspectRatio: Float = 9f / 16f,
        randomizationEnabled: Boolean = true
    ): MixerLayout {
        val aspect = aspectRatio.coerceIn(0.2f, 5.0f)
        val contentWidth = (windowWidth - (windowPaddingX * 2f)).coerceAtLeast(1f)

        val masterControlsH = (frameHeightWithSpacing + 12f).coerceAtLeast(34f)
        val presetNameExtraHeight = deckPreviewNonImageHeight(frameHeightWithSpacing, textLineHeightWithSpacing, itemSpacingY)

        val verticalChrome = estimateVerticalChrome(
            masterControlsH = masterControlsH,
            presetNameExtraHeight = presetNameExtraHeight,
            itemSpacingY = itemSpacingY
        )
        val availableForPreviews = (availableHeight - verticalChrome).coerceAtLeast(0f)

        val aspectMultiplier = 2.0f * aspect
        val aspectOffset = 8f * aspect
        val maxAllowedWidth = if (availableForPreviews > 0f && aspectMultiplier > 0f) {
            (availableForPreviews + aspectOffset) / aspectMultiplier
        } else {
            contentWidth
        }

        val renderWidth = contentWidth.coerceAtMost(maxAllowedWidth).coerceAtLeast(1f)
        val offsetX = ((contentWidth - renderWidth) * 0.5f).coerceAtLeast(0f)

        val halfWidth = ((renderWidth - TWO_DECK_PADDING) * 0.5f).coerceAtLeast(1f)
        val imageWidth = (halfWidth - (DeckTileMetrics.IMAGE_INSET * 2f)).coerceAtLeast(1f)
        val desiredMasterHeight = renderWidth * aspect
        val desiredDeckChildHeight = (imageWidth * aspect) + presetNameExtraHeight

        return MixerLayout(
            contentWidth = contentWidth,
            renderWidth = renderWidth,
            offsetX = offsetX,
            masterHeight = desiredMasterHeight.coerceAtLeast(MIN_MASTER_HEIGHT),
            deckChildHeight = desiredDeckChildHeight.coerceAtLeast(MIN_DECK_CHILD_HEIGHT),
            deckPVHeight = desiredDeckChildHeight.coerceAtLeast(MIN_DECK_CHILD_HEIGHT)
        )
    }

    /**
     * Mirrors DeckControlPanel's real `naturalH` non-image portion (toolbar bar via
     * [DeckTileMetrics.bottomBarHeight], plus its extra +6f visual buffer) so the width-solving
     * math reserves exactly what's actually drawn instead of a separate, drifting guess.
     * `getFrameHeightWithSpacing`/`getTextLineHeightWithSpacing` already bake in one itemSpacingY,
     * so it's subtracted back out to recover the raw values DeckControlPanel queries directly.
     */
    private fun deckPreviewNonImageHeight(frameHeightWithSpacing: Float, textLineHeightWithSpacing: Float, itemSpacingY: Float): Float {
        return 0f
    }

    /**
     * Reconstructs the real non-preview vertical overhead in [MixerPanel.draw] (mode toggle,
     * master controls, the two spacing+separator+spacing bands, and the inter-row gap) instead of
     * blind multipliers.
     */
    private fun estimateVerticalChrome(
        masterControlsH: Float,
        presetNameExtraHeight: Float,
        itemSpacingY: Float
    ): Float {
        val modeToggleH = 28f + itemSpacingY * 2f
        // spacing() + separator() + spacing() after the master image.
        val postMasterSeparatorH = itemSpacingY * 3f + 1f
        // Same triple after the master-controls child, plus the item-spacing the child itself consumes.
        val postControlsSeparatorH = itemSpacingY * 4f + 1f
        // Gap between deck row 1 and row 2 (matches MixerPanel's row2Y calculation exactly).
        val rowGap = itemSpacingY + 6f
        val roundingBuffer = itemSpacingY
        return modeToggleH + masterControlsH + postMasterSeparatorH + postControlsSeparatorH + rowGap + (presetNameExtraHeight * 2f) + roundingBuffer
    }
}
