package llm.slop.spirals.ui

data class MixerMonitorLayout(
    val contentWidth: Float,
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

        val halfWidth = ((contentWidth - TWO_DECK_PADDING) * 0.5f).coerceAtLeast(1f)
        val desiredMasterHeight = contentWidth * ASPECT_16_9
        val desiredDeckChildHeight = (halfWidth * ASPECT_16_9) + DECK_CHILD_EXTRA_HEIGHT
        val desiredDeckCHeight = desiredMasterHeight

        val verticalChrome = estimateVerticalChrome(
            textLineHeightWithSpacing = textLineHeightWithSpacing,
            frameHeightWithSpacing = frameHeightWithSpacing,
            itemSpacingY = itemSpacingY
        )
        val scalableHeight = desiredMasterHeight + desiredDeckChildHeight + desiredDeckCHeight
        val availableForPreviews = (availableHeight - verticalChrome).coerceAtLeast(0f)
        val scale = if (scalableHeight > availableForPreviews && scalableHeight > 0f) {
            (availableForPreviews / scalableHeight).coerceIn(0f, 1f)
        } else {
            1f
        }

        return MixerMonitorLayout(
            contentWidth = contentWidth,
            masterHeight = (desiredMasterHeight * scale).coerceAtLeast(MIN_MASTER_HEIGHT),
            deckChildHeight = (desiredDeckChildHeight * scale).coerceAtLeast(MIN_DECK_CHILD_HEIGHT),
            deckCHeight = (desiredDeckCHeight * scale).coerceAtLeast(MIN_DECK_C_HEIGHT)
        )
    }

    private fun estimateVerticalChrome(
        textLineHeightWithSpacing: Float,
        frameHeightWithSpacing: Float,
        itemSpacingY: Float
    ): Float {
        val separatorBands = itemSpacingY * 9f
        val deckHeaderRows = textLineHeightWithSpacing + frameHeightWithSpacing
        val deckCHeaderRows = textLineHeightWithSpacing + frameHeightWithSpacing
        val safetyMargin = itemSpacingY * 4f
        return MASTER_CONTROLS_HEIGHT + separatorBands + deckHeaderRows + deckCHeaderRows + safetyMargin
    }
}
