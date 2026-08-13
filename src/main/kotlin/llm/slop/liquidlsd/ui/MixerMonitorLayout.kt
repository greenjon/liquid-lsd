package llm.slop.liquidlsd.ui

data class MixerMonitorLayout(
    val contentWidth: Float,
    val renderWidth: Float,
    val offsetX: Float,
    val masterHeight: Float,
    val deckChildHeight: Float,
    val deckCHeight: Float
)

object MixerMonitorLayoutCalculator {
    private const val ASPECT_16_9 = 9f / 16f
    private const val TWO_DECK_PADDING = 16f
    private const val MASTER_CONTROLS_HEIGHT = 85f
    private const val DECK_CHILD_EXTRA_HEIGHT = 10f
    private const val MIN_MASTER_HEIGHT = 120f
    private const val MIN_DECK_CHILD_HEIGHT = 80f
    private const val MIN_DECK_C_HEIGHT = 120f

    fun calculate(
        windowWidth: Float,
        availableHeight: Float,
        windowPaddingX: Float,
        scrollbarWidth: Float,
        textLineHeightWithSpacing: Float,
        frameHeightWithSpacing: Float,
        itemSpacingY: Float
    ): MixerMonitorLayout {
        val reservedScrollbarWidth = scrollbarWidth.coerceAtLeast(0f)
        val contentWidth = (windowWidth - (windowPaddingX * 2f) - reservedScrollbarWidth).coerceAtLeast(1f)

        val masterControlsH = (frameHeightWithSpacing * 2f + itemSpacingY + 8f).coerceAtLeast(60f)
        val patchNameExtraHeight = maxOf(frameHeightWithSpacing, textLineHeightWithSpacing + 6f) + 8f

        val verticalChrome = estimateVerticalChrome(
            masterControlsH = masterControlsH,
            textLineHeightWithSpacing = textLineHeightWithSpacing,
            frameHeightWithSpacing = frameHeightWithSpacing,
            itemSpacingY = itemSpacingY
        )
        val availableForPreviews = (availableHeight - verticalChrome).coerceAtLeast(0f)

        // Calculate maximum allowed width to maintain exact 16:9 aspect ratios given available height.
        val maxAllowedWidth = if (availableForPreviews > 5.5f) {
            (availableForPreviews - 5.5f) / 1.40625f
        } else {
            contentWidth
        }

        val renderWidth = contentWidth.coerceAtMost(maxAllowedWidth).coerceAtLeast(1f)
        val offsetX = ((contentWidth - renderWidth) * 0.5f).coerceAtLeast(0f)

        val halfWidth = ((renderWidth - TWO_DECK_PADDING) * 0.5f).coerceAtLeast(1f)
        val desiredMasterHeight = renderWidth * ASPECT_16_9
        val desiredDeckChildHeight = (halfWidth * ASPECT_16_9) + patchNameExtraHeight
        val desiredDeckCHeight = renderWidth * ASPECT_16_9

        return MixerMonitorLayout(
            contentWidth = contentWidth,
            renderWidth = renderWidth,
            offsetX = offsetX,
            masterHeight = desiredMasterHeight.coerceAtLeast(MIN_MASTER_HEIGHT),
            deckChildHeight = desiredDeckChildHeight.coerceAtLeast(MIN_DECK_CHILD_HEIGHT),
            deckCHeight = desiredDeckCHeight.coerceAtLeast(MIN_DECK_C_HEIGHT)
        )
    }

    private fun estimateVerticalChrome(
        masterControlsH: Float,
        textLineHeightWithSpacing: Float,
        frameHeightWithSpacing: Float,
        itemSpacingY: Float
    ): Float {
        val separatorBands = itemSpacingY * 9f
        val safetyMargin = itemSpacingY * 4f
        return masterControlsH + separatorBands + safetyMargin
    }
}
