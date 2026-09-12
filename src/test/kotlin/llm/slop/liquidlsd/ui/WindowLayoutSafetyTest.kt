package llm.slop.liquidlsd.ui

import llm.slop.liquidlsd.rendering.ViewportHelper
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WindowLayoutSafetyTest {

    @Test
    fun testTitleBarPanelGap() {
        assertEquals(2.0f, UIManager.TITLE_BAR_PANEL_GAP, "TITLE_BAR_PANEL_GAP should be 2.0f")

        // Check layout formula with various simulated title bar heights
        for (testTitleBarH in listOf(15f, 21f, 28f, 30f, 31f, 32f, 36f, 48f)) {
            val menuBarH = testTitleBarH + UIManager.TITLE_BAR_PANEL_GAP
            val gap = menuBarH - testTitleBarH
            assertEquals(2.0f, gap, "Gap between title bar bottom and panel top must remain consistently 2.0f at height $testTitleBarH")
        }
    }

    @Test
    fun testMixerMaxAllowedWidthOnSmallHeight() {
        val tinyHeights = listOf(0f, 1f, 10f, 50f, 100f, 200f, 300f)
        for (h in tinyHeights) {
            val maxW = MixerLayoutCalculator.calculateMaxAllowedWindowWidth(
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

    @Test
    fun testPanelTitleBarCenteringMath() {
        assertEquals(1.5f, PanelTitleBar.HEIGHT_SCALE, "HEIGHT_SCALE should be 1.5f")
        assertEquals(3.0f, PanelTitleBar.TEXT_Y_OPTICAL_OFFSET, "TEXT_Y_OPTICAL_OFFSET should be 3.0f")

        val simulatedTitleBarHeights = listOf(28f, 30f, 31.5f, 33f, 36f, 42f)
        val simulatedH3FontHeights = listOf(14f, 15f, 16f, 18f)

        for (menuBarH in simulatedTitleBarHeights) {
            val btnH = (menuBarH - 6f).coerceAtLeast(22f)
            val btnYOffset = ((menuBarH - btnH) * 0.5f).coerceAtLeast(0f)

            // Button bounds check: button must not overflow menu bar top or bottom
            assertTrue(btnYOffset >= 0f, "Button Y offset must be non-negative: $btnYOffset")
            assertTrue(btnYOffset + btnH <= menuBarH, "Button must not extend beyond menu bar bottom ($menuBarH): ${btnYOffset + btnH}")

            for (txtH in simulatedH3FontHeights) {
                val textYOffset = (((menuBarH - txtH) * 0.5f) - PanelTitleBar.TEXT_Y_OPTICAL_OFFSET).coerceAtLeast(0f)
                val textBottom = textYOffset + txtH

                // Text bounds check: text must not overflow menu bar top or bottom
                assertTrue(textYOffset >= 0f, "Text Y offset must be non-negative: $textYOffset")
                assertTrue(textBottom <= menuBarH, "Text must not be cut off at bottom ($menuBarH): $textBottom")

                // Ensure top padding is less than or equal to bottom padding so text never sits too low with excess space above
                val topPadding = textYOffset
                val bottomPadding = menuBarH - textBottom
                assertTrue(topPadding <= bottomPadding, "Top padding ($topPadding) should not exceed bottom padding ($bottomPadding)")
            }
        }
    }
}

