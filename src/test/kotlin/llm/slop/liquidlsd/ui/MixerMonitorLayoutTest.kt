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
        assertTrue(layout.deckPVHeight > 0f)
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
        assertTrue(tight.deckPVHeight < roomy.deckPVHeight)
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
        assertEquals(80f, tiny.deckPVHeight)
    }

    @Test
    fun testDynamicAspectRatios() {
        // 4:3 aspect ratio (aspect = 1200 / 1600 = 0.75f)
        val layout4x3 = MixerMonitorLayoutCalculator.calculate(
            windowWidth = 800f,
            availableHeight = 1600f,
            windowPaddingX = 8f,
            scrollbarWidth = 14f,
            textLineHeightWithSpacing = 22f,
            frameHeightWithSpacing = 25f,
            itemSpacingY = 4f,
            aspectRatio = 0.75f
        )
        assertEquals(layout4x3.renderWidth * 0.75f, layout4x3.masterHeight, 0.1f)
        assertTrue(layout4x3.deckChildHeight > 0f)

        // 1:1 aspect ratio (aspect = 1.0f)
        val layoutSquare = MixerMonitorLayoutCalculator.calculate(
            windowWidth = 800f,
            availableHeight = 1600f,
            windowPaddingX = 8f,
            scrollbarWidth = 14f,
            textLineHeightWithSpacing = 22f,
            frameHeightWithSpacing = 25f,
            itemSpacingY = 4f,
            aspectRatio = 1.0f
        )
        assertEquals(layoutSquare.renderWidth * 1.0f, layoutSquare.masterHeight, 0.1f)
        assertTrue(layoutSquare.deckChildHeight > 0f)
    }

    @Test
    fun testMaxAllowedWindowWidthMatchesLayoutCapacity() {
        val windowPaddingX = 8f
        val maxAllowedW = MixerMonitorLayoutCalculator.calculateMaxAllowedWindowWidth(
            availableHeight = 1048f,
            windowPaddingX = windowPaddingX,
            textLineHeightWithSpacing = 22f,
            frameHeightWithSpacing = 25f,
            itemSpacingY = 4f,
            aspectRatio = 9f / 16f
        )

        // At exact maxAllowedWidth, renderWidth must equal contentWidth and offsetX must be 0
        val exactLayout = MixerMonitorLayoutCalculator.calculate(
            windowWidth = maxAllowedW,
            availableHeight = 1048f,
            windowPaddingX = windowPaddingX,
            scrollbarWidth = 14f,
            textLineHeightWithSpacing = 22f,
            frameHeightWithSpacing = 25f,
            itemSpacingY = 4f,
            aspectRatio = 9f / 16f
        )
        assertEquals(maxAllowedW - (windowPaddingX * 2f), exactLayout.contentWidth, 0.01f)
        assertEquals(exactLayout.contentWidth, exactLayout.renderWidth, 0.01f)
        assertEquals(0f, exactLayout.offsetX, 0.01f)

        // If windowWidth exceeds maxAllowedW, renderWidth stays capped at maxAllowedContentWidth and offsetX > 0
        val oversizedLayout = MixerMonitorLayoutCalculator.calculate(
            windowWidth = maxAllowedW + 200f,
            availableHeight = 1048f,
            windowPaddingX = windowPaddingX,
            scrollbarWidth = 14f,
            textLineHeightWithSpacing = 22f,
            frameHeightWithSpacing = 25f,
            itemSpacingY = 4f,
            aspectRatio = 9f / 16f
        )
        assertEquals(exactLayout.renderWidth, oversizedLayout.renderWidth, 0.01f)
        assertEquals(100f, oversizedLayout.offsetX, 0.01f)
    }
}
