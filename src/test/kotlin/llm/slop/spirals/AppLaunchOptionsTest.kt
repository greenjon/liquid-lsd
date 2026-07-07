package llm.slop.spirals

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppLaunchOptionsTest {
    @Test
    fun parsesUiLabScreenshotAndWindowFlags() {
        val options = AppLaunchOptions.parse(
            arrayOf(
                "--ui-lab",
                "--window=1440x900",
                "--screenshot-ui=build/ui-lab.png",
                "--screenshot-after-frames=3",
                "--no-audio",
                "--no-watchdog"
            )
        )

        assertTrue(options.uiLab)
        assertEquals(1440, options.windowWidth)
        assertEquals(900, options.windowHeight)
        assertEquals("build${java.io.File.separator}ui-lab.png", options.screenshotPath?.path)
        assertEquals(3, options.screenshotAfterFrames)
        assertEquals(false, options.audioEnabled)
        assertFalse(options.watchdogEnabled)
    }

    @Test
    fun ignoresMalformedWindowFlagAndClampsFrameDelay() {
        val options = AppLaunchOptions.parse(
            arrayOf("--window=wide", "--screenshot-after-frames=0")
        )

        assertEquals(1920, options.windowWidth)
        assertEquals(1080, options.windowHeight)
        assertEquals(1, options.screenshotAfterFrames)
    }

    @Test
    fun parsesMaximizedWindowOption() {
        val options = AppLaunchOptions.parse(arrayOf("--window=maximized"))

        assertTrue(options.startMaximized)
    }
}
