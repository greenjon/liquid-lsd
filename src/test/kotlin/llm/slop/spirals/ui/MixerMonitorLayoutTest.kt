package llm.slop.spirals.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MixerMonitorLayoutTest {
    @Test
    fun reservesScrollbarWidthBeforeComputingPreviewSizes() {
        val layout = MixerMonitorLayoutCalculator.calculate(
            windowWidth = 576f,
            availableHeight = 1048f,
            windowPaddingX = 8f,
            scrollbarWidth = 14f,
            textLineHeightWithSpacing = 22f,
            frameHeightWithSpacing = 25f,
            itemSpacingY = 4f
        )

        assertEquals(546f, layout.contentWidth)
        assertTrue(layout.masterHeight > 0f)
        assertTrue(layout.deckChildHeight > 0f)
        assertTrue(layout.deckCHeight > 0f)
    }

    @Test
    fun shrinksPreviewsWhenPaneHeightIsTight() {
        val roomy = MixerMonitorLayoutCalculator.calculate(
            windowWidth = 576f,
            availableHeight = 1048f,
            windowPaddingX = 8f,
            scrollbarWidth = 14f,
            textLineHeightWithSpacing = 22f,
            frameHeightWithSpacing = 25f,
            itemSpacingY = 4f
        )
        val tight = MixerMonitorLayoutCalculator.calculate(
            windowWidth = 576f,
            availableHeight = 720f,
            windowPaddingX = 8f,
            scrollbarWidth = 14f,
            textLineHeightWithSpacing = 22f,
            frameHeightWithSpacing = 25f,
            itemSpacingY = 4f
        )

        assertTrue(tight.masterHeight < roomy.masterHeight)
        assertTrue(tight.deckChildHeight < roomy.deckChildHeight)
        assertTrue(tight.deckCHeight < roomy.deckCHeight)
    }
}
