package llm.slop.liquidlsd.ui

data class MixerMonitorLayout(
    val contentWidth: Float,
    val renderWidth: Float,
    val offsetX: Float,
    val masterHeight: Float,
    val deckChildHeight: Float,
    val deckCHeight: Float = deckChildHeight
)

object MixerMonitorLayoutCalculator {
    private const val TWO_DECK_PADDING = 16f
    private const val MIN_MASTER_HEIGHT = 120f
    private const val MIN_DECK_CHILD_HEIGHT = 80f

    fun calculateMaxAllowedWindowWidth(
        availableHeight: Float,
        windowPaddingX: Float,
        textLineHeightWithSpacing: Float,
        frameHeightWithSpacing: Float,
        itemSpacingY: Float,
        aspectRatio: Float = 9f / 16f
    ): Float {
        val aspect = aspectRatio.coerceIn(0.2f, 5.0f)
        val masterControlsH = (frameHeightWithSpacing * 3f + itemSpacingY * 2f + 8f).coerceAtLeast(85f)
        val presetNameExtraHeight = maxOf(frameHeightWithSpacing, textLineHeightWithSpacing + 6f) + 8f

        val verticalChrome = estimateVerticalChrome(
            masterControlsH = masterControlsH,
            presetNameExtraHeight = presetNameExtraHeight,
            itemSpacingY = itemSpacingY
        )
        val availableForPreviews = (availableHeight - verticalChrome).coerceAtLeast(0f)

        // Sum of aspect preview heights: master (1.0 * aspect) + Row 1 (0.5 * aspect) + Row 2 (0.5 * aspect) = 2.0 * aspect
        val aspectMultiplier = 2.0f * aspect
        val aspectOffset = 16f * aspect
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
        aspectRatio: Float = 9f / 16f
    ): MixerMonitorLayout {
        val aspect = aspectRatio.coerceIn(0.2f, 5.0f)
        val contentWidth = (windowWidth - (windowPaddingX * 2f)).coerceAtLeast(1f)

        val masterControlsH = (frameHeightWithSpacing * 3f + itemSpacingY * 2f + 8f).coerceAtLeast(85f)
        val presetNameExtraHeight = maxOf(frameHeightWithSpacing, textLineHeightWithSpacing + 6f) + 8f

        val verticalChrome = estimateVerticalChrome(
            masterControlsH = masterControlsH,
            presetNameExtraHeight = presetNameExtraHeight,
            itemSpacingY = itemSpacingY
        )
        val availableForPreviews = (availableHeight - verticalChrome).coerceAtLeast(0f)

        val aspectMultiplier = 2.0f * aspect
        val aspectOffset = 16f * aspect
        val maxAllowedWidth = if (availableForPreviews > 0f && aspectMultiplier > 0f) {
            (availableForPreviews + aspectOffset) / aspectMultiplier
        } else {
            contentWidth
        }

        val renderWidth = contentWidth.coerceAtMost(maxAllowedWidth).coerceAtLeast(1f)
        val offsetX = ((contentWidth - renderWidth) * 0.5f).coerceAtLeast(0f)

        val halfWidth = ((renderWidth - TWO_DECK_PADDING) * 0.5f).coerceAtLeast(1f)
        val desiredMasterHeight = renderWidth * aspect
        val desiredDeckChildHeight = (halfWidth * aspect) + presetNameExtraHeight

        return MixerMonitorLayout(
            contentWidth = contentWidth,
            renderWidth = renderWidth,
            offsetX = offsetX,
            masterHeight = desiredMasterHeight.coerceAtLeast(MIN_MASTER_HEIGHT),
            deckChildHeight = desiredDeckChildHeight.coerceAtLeast(MIN_DECK_CHILD_HEIGHT),
            deckCHeight = desiredDeckChildHeight.coerceAtLeast(MIN_DECK_CHILD_HEIGHT)
        )
    }

    private fun estimateVerticalChrome(
        masterControlsH: Float,
        presetNameExtraHeight: Float,
        itemSpacingY: Float
    ): Float {
        val separatorBands = itemSpacingY * 12f + 6f
        val safetyMargin = itemSpacingY * 3f + 8f
        return masterControlsH + (presetNameExtraHeight * 2f) + separatorBands + safetyMargin
    }
}
