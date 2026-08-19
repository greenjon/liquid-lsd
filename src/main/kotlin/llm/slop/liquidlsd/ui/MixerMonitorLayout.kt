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
        itemSpacingY: Float,
        aspectRatio: Float = 9f / 16f
    ): MixerMonitorLayout {
        val aspect = aspectRatio.coerceIn(0.2f, 5.0f)
        val contentWidth = (windowWidth - (windowPaddingX * 2f)).coerceAtLeast(1f)

        val masterControlsH = (frameHeightWithSpacing * 2f + itemSpacingY + 8f).coerceAtLeast(60f)
        val presetNameExtraHeight = maxOf(frameHeightWithSpacing, textLineHeightWithSpacing + 6f) + 8f

        val verticalChrome = estimateVerticalChrome(
            masterControlsH = masterControlsH,
            presetNameExtraHeight = presetNameExtraHeight,
            itemSpacingY = itemSpacingY
        )
        val availableForPreviews = (availableHeight - verticalChrome).coerceAtLeast(0f)

        // Calculate maximum allowed width to maintain exact aspect ratios given available height.
        // Sum of aspect preview heights:
        // masterHeight = renderWidth * aspect
        // deckChildPreviewHeight = (halfWidth) * aspect = ((renderWidth - 16) * 0.5) * aspect = 0.5 * aspect * renderWidth - 8 * aspect
        // deckCHeight = renderWidth * aspect
        // Total aspect preview height = 2.5 * aspect * renderWidth - 8 * aspect
        // To guarantee previews fit within availableForPreviews:
        // (2.5 * aspect) * renderWidth - (8 * aspect) <= availableForPreviews
        // renderWidth <= (availableForPreviews + 8 * aspect) / (2.5 * aspect)
        val aspectMultiplier = 2.5f * aspect
        val aspectOffset = 8f * aspect
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
        val desiredDeckCHeight = renderWidth * aspect

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
