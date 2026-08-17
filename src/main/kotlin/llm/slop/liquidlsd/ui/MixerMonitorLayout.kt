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
        val contentWidth = (windowWidth - (windowPaddingX * 2f)).coerceAtLeast(1f)

        val masterControlsH = (frameHeightWithSpacing * 2f + itemSpacingY + 8f).coerceAtLeast(60f)
        val presetNameExtraHeight = maxOf(frameHeightWithSpacing, textLineHeightWithSpacing + 6f) + 8f

        val verticalChrome = estimateVerticalChrome(
            masterControlsH = masterControlsH,
            presetNameExtraHeight = presetNameExtraHeight,
            itemSpacingY = itemSpacingY
        )
        val availableForPreviews = (availableHeight - verticalChrome).coerceAtLeast(0f)

        // Calculate maximum allowed width to maintain exact 16:9 aspect ratios given available height.
        // Sum of aspect preview heights:
        // masterHeight = renderWidth * 9/16 = 0.5625 * renderWidth
        // deckChildPreviewHeight = (halfWidth) * 9/16 = ((renderWidth - 16) * 0.5) * 9/16 = 0.28125 * renderWidth - 4.5
        // deckCHeight = renderWidth * 9/16 = 0.5625 * renderWidth
        // Total aspect preview height = 1.40625 * renderWidth - 4.5
        // To guarantee previews fit within availableForPreviews:
        // 1.40625 * renderWidth - 4.5 <= availableForPreviews
        // renderWidth <= (availableForPreviews + 4.5) / 1.40625
        val maxAllowedWidth = if (availableForPreviews > 0f) {
            (availableForPreviews + 4.5f) / 1.40625f
        } else {
            contentWidth
        }

        val renderWidth = contentWidth.coerceAtMost(maxAllowedWidth).coerceAtLeast(1f)
        val offsetX = ((contentWidth - renderWidth) * 0.5f).coerceAtLeast(0f)

        val halfWidth = ((renderWidth - TWO_DECK_PADDING) * 0.5f).coerceAtLeast(1f)
        val desiredMasterHeight = renderWidth * ASPECT_16_9
        val desiredDeckChildHeight = (halfWidth * ASPECT_16_9) + presetNameExtraHeight
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
        presetNameExtraHeight: Float,
        itemSpacingY: Float
    ): Float {
        val separatorBands = itemSpacingY * 12f + 6f
        val safetyMargin = itemSpacingY * 3f + 8f
        return masterControlsH + (presetNameExtraHeight * 2f) + separatorBands + safetyMargin
    }
}
