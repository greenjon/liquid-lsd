package llm.slop.spirals.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AppMainLayoutTest {
    @Test
    fun keepsPatchGridUsableAtCompactWidths() {
        val layout = AppMainLayoutCalculator.calculate(
            displayWidth = 900f,
            contentHeight = 668f,
            assetBrowserMode = UITheme.AssetBrowserMode.HALF
        )

        assertEquals(320f, layout.rightWidth)
        assertTrue(layout.patchGridWidth >= 360f)
        assertTrue(layout.cellConfigWidth >= 220f)
    }

    @Test
    fun givesPatchGridMoreRoomOnLaptopWidths() {
        val layout = AppMainLayoutCalculator.calculate(
            displayWidth = 1280f,
            contentHeight = 688f,
            assetBrowserMode = UITheme.AssetBrowserMode.HALF
        )

        assertTrue(layout.patchGridWidth > 420f)
        assertTrue(layout.cellConfigWidth > 360f)
        assertEquals(344f, layout.assetBrowserHeight)
    }

    @Test
    fun preservesRightMonitorMinimumOnDesktopWidths() {
        val layout = AppMainLayoutCalculator.calculate(
            displayWidth = 1920f,
            contentHeight = 1048f,
            assetBrowserMode = UITheme.AssetBrowserMode.HALF
        )

        assertTrue(layout.rightWidth >= 420f)
        assertTrue(layout.libraryWidth > layout.rightWidth)
        assertEquals(524f, layout.assetBrowserHeight)
    }
}
