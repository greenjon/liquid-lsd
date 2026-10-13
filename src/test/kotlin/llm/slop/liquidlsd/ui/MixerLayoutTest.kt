package llm.slop.liquidlsd.ui
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val PADDING_X = 8f

class MixerLayoutTest {
    private fun layout(width: Float, height: Float, aspect: Float = 9f / 16f) = MixerLayoutCalculator.calculate(
        windowWidth = width,
        availableHeight = height,
        windowPaddingX = PADDING_X,
        scrollbarWidth = 14f,
        textLineHeightWithSpacing = 20f,
        frameHeightWithSpacing = 24f,
        itemSpacingY = 4f,
        aspectRatio = aspect,
    )

    private fun maxWidth(height: Float, aspect: Float = 9f / 16f) = MixerLayoutCalculator.calculateMaxAllowedWindowWidth(
        availableHeight = height,
        windowPaddingX = PADDING_X,
        textLineHeightWithSpacing = 20f,
        frameHeightWithSpacing = 24f,
        itemSpacingY = 4f,
        aspectRatio = aspect,
    )

    @Test
    fun wideTallPaneUsesFullContentWidth() {
        val result = layout(width = 600f, height = 4000f)
        assertEquals(584f, result.contentWidth)
        assertEquals(result.contentWidth, result.renderWidth)
        assertEquals(0f, result.offsetX)
    }

    @Test
    fun tightHeightShrinksAndCentresPreviews() {
        val result = layout(width = 1200f, height = 500f)
        assertTrue(result.renderWidth < result.contentWidth)
        assertTrue(result.offsetX > 0f)
        assertEquals((result.contentWidth - result.renderWidth) / 2f, result.offsetX, 0.001f)
        // The window width that fits this height solves back to the same render width.
        val fitted = layout(width = maxWidth(500f), height = 500f)
        assertEquals(result.renderWidth, fitted.renderWidth, 0.01f)
    }

    @Test
    fun tinyScreenKeepsMinimumHeights() {
        for (height in listOf(0f, 1f, 50f)) {
            val result = layout(width = 20f, height = height)
            assertTrue(result.masterHeight >= 120f)
            assertTrue(result.deckChildHeight >= 80f)
            assertTrue(result.deckPVHeight >= 80f)
        }
    }

    @Test
    fun masterHeightFollowsAspectRatio() {
        for (aspect in listOf(9f / 16f, 3f / 4f, 1f, 16f / 9f)) {
            val result = layout(width = 1000f, height = 5000f, aspect = aspect)
            assertEquals(result.renderWidth * aspect, result.masterHeight, 0.01f)
        }
    }

    @Test
    fun aspectRatioIsClamped() {
        val tooFlat = layout(width = 1000f, height = 5000f, aspect = 0.01f)
        val flat = layout(width = 1000f, height = 5000f, aspect = 0.2f)
        assertEquals(flat.masterHeight, tooFlat.masterHeight, 0.01f)
        val tooTall = layout(width = 400f, height = 50000f, aspect = 40f)
        val tall = layout(width = 400f, height = 50000f, aspect = 5f)
        assertEquals(tall.masterHeight, tooTall.masterHeight, 0.01f)
    }

    @Test
    fun maxWindowWidthFillsAvailableHeight() {
        val aspect = 9f / 16f
        val availableHeight = 700f
        val result = layout(width = maxWidth(availableHeight), height = availableHeight)
        // Previews stack as master + two deck rows, minus the 8 px gutter per unit of aspect.
        val previews = 2f * aspect * result.renderWidth - 8f * aspect
        // Chrome for these inputs: bar 36, below master 13, below bar 17, row gap 10, rounding 4.
        val stacked = previews + 36f + 13f + 17f + 10f + 4f
        assertTrue(abs(stacked - availableHeight) <= 1f, "stacked $stacked vs available $availableHeight")
    }

    @Test
    fun noHeightMeansUnboundedWidth() {
        assertEquals(Float.MAX_VALUE, maxWidth(0f))
    }
}
