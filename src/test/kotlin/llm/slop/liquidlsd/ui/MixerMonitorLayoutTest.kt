package llm.slop.liquidlsd.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MixerMonitorLayoutTest {
    @Test
    fun usesFullAvailableWidthWithoutUnconditionalScrollbarReservation() {
        val layout = MixerMonitorLayoutCalculator.calculate(
            windowWidth = 576f,
            availableHeight = 1048f,
            windowPaddingX = 8f,
            scrollbarWidth = 14f,
            textLineHeightWithSpacing = 22f,
            frameHeightWithSpacing = 25f,
            itemSpacingY = 4f
        )

        assertEquals(560f, layout.contentWidth)
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

        assertTrue(tight.renderWidth < roomy.renderWidth)
        assertTrue(tight.masterHeight < roomy.masterHeight)
        assertTrue(tight.deckChildHeight < roomy.deckChildHeight)
        assertTrue(tight.deckCHeight < roomy.deckCHeight)
    }

    @Test
    fun clampsToMinimumHeightsOnExtremelySmallScreens() {
        val tiny = MixerMonitorLayoutCalculator.calculate(
            windowWidth = 576f,
            availableHeight = 300f,
            windowPaddingX = 8f,
            scrollbarWidth = 14f,
            textLineHeightWithSpacing = 22f,
            frameHeightWithSpacing = 25f,
            itemSpacingY = 4f
        )

        assertEquals(120f, tiny.masterHeight)
        assertEquals(80f, tiny.deckChildHeight)
        assertEquals(120f, tiny.deckCHeight)
    }
}
