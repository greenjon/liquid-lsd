package llm.slop.liquidlsd.ui

import llm.slop.liquidlsd.rendering.ViewportHelper
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WindowLayoutSafetyTest {

    @Test
    fun testMixerMonitorMaxAllowedWidthOnSmallHeight() {
        val tinyHeights = listOf(0f, 1f, 10f, 50f, 100f, 200f, 300f)
        for (h in tinyHeights) {
            val maxW = MixerMonitorLayoutCalculator.calculateMaxAllowedWindowWidth(
                availableHeight = h,
                windowPaddingX = 8f,
                textLineHeightWithSpacing = 22f,
                frameHeightWithSpacing = 25f,
                itemSpacingY = 4f,
                aspectRatio = 9f / 16f
            )
            assertTrue(maxW >= 0f, "Max allowed width should be non-negative for height $h: got $maxW")
            assertTrue(!maxW.isNaN(), "Max allowed width should not be NaN for height $h")
        }
    }

    @Test
    fun testColumnWidthClampingMathOnSmallWidths() {
        val testWidths = listOf(0f, 10f, 50f, 100f, 200f, 300f, 350f, 400f, 800f, 1280f, 1920f)
        val minRatio = 0.15f

        for (w in testWidths) {
            val reqCol1W = w * 0.30f
            val maxCol1W = (w * 0.50f).coerceAtMost(w - 200f).coerceAtLeast(w * minRatio)
            val minCol1W = (w * minRatio).coerceAtMost(maxCol1W)

            assertTrue(minCol1W <= maxCol1W, "minCol1W ($minCol1W) must be <= maxCol1W ($maxCol1W) for width $w")

            val col1W = reqCol1W.coerceIn(minCol1W, maxCol1W)

            val maxRightW = 300f
            val maxAllowedRightW = (w - col1W - 50f).coerceAtLeast(100f)
            val rightW = maxRightW.coerceIn(100f, maxAllowedRightW)

            val libraryW = (w - rightW).coerceAtLeast(100f)
            val col2W = (libraryW - col1W).coerceAtLeast(20f)

            assertTrue(col1W >= 0f, "col1W should be non-negative")
            assertTrue(col2W >= 0f, "col2W should be non-negative")
            assertTrue(libraryW >= 0f, "libraryW should be non-negative")
            assertTrue(rightW >= 0f, "rightW should be non-negative")
        }
    }

    @Test
    fun testSettingsPanelHeightClampingOnSmallDisplays() {
        val testHeights = listOf(0f, 50f, 100f, 200f, 300f, 380f, 400f, 600f, 1080f)
        for (displayH in testHeights) {
            val desiredH = 330f
            val maxH = (displayH * 0.78f).coerceAtLeast(100f)
            val minH = 300f.coerceAtMost(maxH)

            assertTrue(minH <= maxH, "minH ($minH) must be <= maxH ($maxH) for displayH $displayH")
            val contentH = desiredH.coerceIn(minH, maxH)
            assertTrue(contentH >= 100f, "contentH must be at least 100f")
        }
    }

    @Test
    fun testViewportHelperWithZeroAndSmallSizes() {
        val zeroVp = ViewportHelper.computeViewport(0, 0, 1920, 1080, UITheme.OutputScaleMode.FIT)
        assertEquals(0, zeroVp.x)
        assertEquals(0, zeroVp.y)
        assertEquals(1, zeroVp.width)
        assertEquals(1, zeroVp.height)

        val tinyVp = ViewportHelper.computeViewport(10, 10, 1920, 1080, UITheme.OutputScaleMode.FIT)
        assertTrue(tinyVp.width >= 1)
        assertTrue(tinyVp.height >= 1)
    }

    @Test
    fun testLibraryModeCycleSequence() {
        val session = llm.slop.liquidlsd.SessionContext()
        session.uiTheme.libraryMode = UITheme.LibraryMode.HIDE
        LibraryPanel.isLibraryExpanding = true

        // Step 1: HIDE -> HALF (expanding)
        LibraryPanel.cycleMode(session)
        assertEquals(UITheme.LibraryMode.HALF, session.uiTheme.libraryMode)
        assertTrue(LibraryPanel.isLibraryExpanding)

        // Step 2: HALF -> FULL
        LibraryPanel.cycleMode(session)
        assertEquals(UITheme.LibraryMode.FULL, session.uiTheme.libraryMode)
        assertTrue(!LibraryPanel.isLibraryExpanding)

        // Step 3: FULL -> HALF (collapsing)
        LibraryPanel.cycleMode(session)
        assertEquals(UITheme.LibraryMode.HALF, session.uiTheme.libraryMode)
        assertTrue(!LibraryPanel.isLibraryExpanding)

        // Step 4: HALF -> HIDE
        LibraryPanel.cycleMode(session)
        assertEquals(UITheme.LibraryMode.HIDE, session.uiTheme.libraryMode)
        assertTrue(LibraryPanel.isLibraryExpanding)
    }
}
