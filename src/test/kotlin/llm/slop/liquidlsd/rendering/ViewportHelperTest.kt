package llm.slop.liquidlsd.rendering

import llm.slop.liquidlsd.ui.UITheme
import kotlin.test.Test
import kotlin.test.assertEquals

class ViewportHelperTest {

    @Test
    fun testStretchMode() {
        val vp = ViewportHelper.computeViewport(
            screenWidth = 1920,
            screenHeight = 1080,
            contentWidth = 800,
            contentHeight = 800,
            scaleMode = UITheme.OutputScaleMode.STRETCH
        )
        assertEquals(0, vp.x)
        assertEquals(0, vp.y)
        assertEquals(1920, vp.width)
        assertEquals(1080, vp.height)
    }

    @Test
    fun testFitModePillarbox() {
        // Screen is 16:9 (1920x1080), content is square 1:1 (800x800) -> bars on left & right
        val vp = ViewportHelper.computeViewport(
            screenWidth = 1920,
            screenHeight = 1080,
            contentWidth = 800,
            contentHeight = 800,
            scaleMode = UITheme.OutputScaleMode.FIT
        )
        assertEquals(1080, vp.height)
        assertEquals(1080, vp.width)
        assertEquals(420, vp.x) // (1920 - 1080) / 2
        assertEquals(0, vp.y)
    }

    @Test
    fun testFitModeLetterbox() {
        // Screen is 4:3 (1600x1200), content is 16:9 (1920x1080) -> bars on top & bottom
        val vp = ViewportHelper.computeViewport(
            screenWidth = 1600,
            screenHeight = 1200,
            contentWidth = 1920,
            contentHeight = 1080,
            scaleMode = UITheme.OutputScaleMode.FIT
        )
        assertEquals(1600, vp.width)
        assertEquals(900, vp.height) // 1600 / (16/9) = 900
        assertEquals(0, vp.x)
        assertEquals(150, vp.y) // (1200 - 900) / 2
    }

    @Test
    fun testFillModeCrop() {
        // Screen is 16:9 (1920x1080), content is 4:3 (1600x1200) -> match width, crop top/bottom
        val vp = ViewportHelper.computeViewport(
            screenWidth = 1920,
            screenHeight = 1080,
            contentWidth = 1600,
            contentHeight = 1200,
            scaleMode = UITheme.OutputScaleMode.FILL
        )
        assertEquals(1920, vp.width)
        assertEquals(1440, vp.height) // 1920 / (4/3) = 1440
        assertEquals(0, vp.x)
        assertEquals(-180, vp.y) // (1080 - 1440) / 2
    }
}
