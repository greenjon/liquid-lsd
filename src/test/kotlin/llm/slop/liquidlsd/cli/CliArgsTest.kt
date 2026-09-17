package llm.slop.liquidlsd.cli

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class CliArgsTest {

    @Test
    fun testDefaultArguments() {
        val args = CliArgs.parse(emptyArray())
        assertNull(args.screenshotUi)
        assertEquals(5, args.screenshotAfterFrames)
        assertNull(args.windowSpec)
        assertNull(args.windowWidth)
        assertNull(args.windowHeight)
        assertFalse(args.isMaximized)
        assertFalse(args.noAudio)
        assertFalse(args.uiLab)
        assertFalse(args.helpRequested)
        assertFalse(args.versionRequested)
        assertFalse(args.smokeTestRequested)
    }

    @Test
    fun testFlags() {
        val args = CliArgs.parse(arrayOf("--no-audio", "--ui-lab", "--help", "-v", "--smoke-test"))
        assertTrue(args.noAudio)
        assertTrue(args.uiLab)
        assertTrue(args.helpRequested)
        assertTrue(args.versionRequested)
        assertTrue(args.smokeTestRequested)
    }

    @Test
    fun testScreenshotUiWithEqualsAndSpace() {
        val args1 = CliArgs.parse(arrayOf("--screenshot-ui=docs/assets/out.png", "--screenshot-after-frames=10"))
        assertEquals("docs/assets/out.png", args1.screenshotUi)
        assertEquals(10, args1.screenshotAfterFrames)

        val args2 = CliArgs.parse(arrayOf("--screenshot-ui", "custom.png", "--screenshot-after-frames", "3"))
        assertEquals("custom.png", args2.screenshotUi)
        assertEquals(3, args2.screenshotAfterFrames)
    }

    @Test
    fun testWindowResolutionParsing() {
        val args1 = CliArgs.parse(arrayOf("--window=1920x1080"))
        assertEquals("1920x1080", args1.windowSpec)
        assertEquals(1920, args1.windowWidth)
        assertEquals(1080, args1.windowHeight)
        assertFalse(args1.isMaximized)

        val args2 = CliArgs.parse(arrayOf("--window", "maximized"))
        assertEquals("maximized", args2.windowSpec)
        assertNull(args2.windowWidth)
        assertNull(args2.windowHeight)
        assertTrue(args2.isMaximized)

        val args3 = CliArgs.parse(arrayOf("--window=1280x720"))
        assertEquals(1280, args3.windowWidth)
        assertEquals(720, args3.windowHeight)
    }
}
