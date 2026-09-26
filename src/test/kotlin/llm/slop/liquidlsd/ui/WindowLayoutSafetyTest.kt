package llm.slop.liquidlsd.ui

import llm.slop.liquidlsd.rendering.ViewportHelper
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
    fun testPreferencesPanelHeightClampingOnSmallDisplays() {
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
    fun testLibraryShortcutTogglesHalfFullAndLeavesEditView() {
        val session = llm.slop.liquidlsd.SessionContext()
        val savedMode = session.uiTheme.libraryMode
        try {
            session.parametersState.collapseAllRackModules()
            session.uiTheme.libraryMode = UITheme.LibraryMode.HALF
            assertFalse(LibraryPanel.isEditView(session))

            // Perform view: the shortcut toggles HALF <-> FULL.
            LibraryPanel.cycleMode(session)
            assertEquals(UITheme.LibraryMode.FULL, session.uiTheme.libraryMode)
            LibraryPanel.cycleMode(session)
            assertEquals(UITheme.LibraryMode.HALF, session.uiTheme.libraryMode)

            // Edit view (a module in Deep Edit hides the Library): the shortcut brings the Library back.
            session.parametersState.setDisclosure(llm.slop.liquidlsd.macro.MacroEngine.DECK_A, ParametersState.DisclosureLevel.DEEP_EDIT)
            assertTrue(LibraryPanel.isEditView(session))
            LibraryPanel.cycleMode(session)
            assertFalse(LibraryPanel.isEditView(session))
            assertEquals(UITheme.LibraryMode.HALF, session.uiTheme.libraryMode)
        } finally {
            session.parametersState.collapseAllRackModules()
            session.uiTheme.libraryMode = savedMode
        }
    }

    @Test
    fun testTitleBarDecorationsRightAlignment() {
        val btnW = 24f
        val btnGap = 2f
        val statsToBtnsGap = 10f
        val windowBtnsW = (btnW * 3f) + (btnGap * 2f)

        assertEquals(76f, windowBtnsW, "3 buttons of 24px + 2 gaps of 2px should equal 76px")

        for (contentRightX in listOf(1280f, 1920f, 2560f, 3840f)) {
            val btnsStartX = contentRightX - windowBtnsW
            val closeButtonEnd = btnsStartX + (btnW * 2f + btnGap * 2f) + btnW
            assertEquals(contentRightX, closeButtonEnd, "Close button right edge must be flush with contentRightX")

            val statsEndX = btnsStartX - statsToBtnsGap
            assertEquals(contentRightX - windowBtnsW - statsToBtnsGap, statsEndX)
        }
    }
}
