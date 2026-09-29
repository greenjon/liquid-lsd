package llm.slop.liquidlsd.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TooltipHelperTest {

    @Test
    fun testNormalPlacementBeneathPointer() {
        // Standard cursor at (200, 300) in 1920x1080 viewport
        val res = TooltipHelper.calculateTooltipPos(
            mouseX = 200f,
            mouseY = 300f,
            tipWidth = 150f,
            tipHeight = 40f,
            vpLeft = 0f,
            vpTop = 0f,
            vpRight = 1920f,
            vpBottom = 1080f
        )

        // Target X should match mouseX (left-aligned with cursor box left)
        assertEquals(200f, res.targetX)
        assertEquals(0.0f, res.pivotX)

        // Target Y should be mouseY + cursorHeight (22) + gapY (4) = 326
        assertEquals(326f, res.targetY)
        assertEquals(0.0f, res.pivotY)
    }

    @Test
    fun testRightEdgeCollisionAlignsWithCursorBoxRight() {
        // Cursor at (1850, 300), tooltip width 150, viewport width 1920
        // 1850 + 150 + 8 = 2008 > 1920 -> Overflows right!
        val res = TooltipHelper.calculateTooltipPos(
            mouseX = 1850f,
            mouseY = 300f,
            tipWidth = 150f,
            tipHeight = 40f,
            vpLeft = 0f,
            vpTop = 0f,
            vpRight = 1920f,
            vpBottom = 1080f
        )

        // Should align with right edge of cursor box: mouseX (1850) + cursorWidth (16) = 1866
        // and pivotX = 1.0f (so window extends to the left of 1866)
        assertEquals(1866f, res.targetX)
        assertEquals(1.0f, res.pivotX)

        // Vertical remains beneath pointer
        assertEquals(326f, res.targetY)
        assertEquals(0.0f, res.pivotY)
    }

    @Test
    fun testBottomEdgeCollisionFlipsAbovePointer() {
        // Cursor at (200, 1050), tooltip height 50, viewport height 1080
        // 1050 + 22 + 4 + 50 + 8 = 1134 > 1080 -> Overflows bottom!
        val res = TooltipHelper.calculateTooltipPos(
            mouseX = 200f,
            mouseY = 1050f,
            tipWidth = 150f,
            tipHeight = 50f,
            vpLeft = 0f,
            vpTop = 0f,
            vpRight = 1920f,
            vpBottom = 1080f
        )

        // Horizontal remains left-aligned
        assertEquals(200f, res.targetX)
        assertEquals(0.0f, res.pivotX)

        // Target Y should flip above: mouseY (1050) - gapY (4) = 1046, with pivotY = 1.0f
        assertEquals(1046f, res.targetY)
        assertEquals(1.0f, res.pivotY)
    }

    @Test
    fun testBottomRightCollisionFlipsBothHorizontallyAndVertically() {
        // Near bottom-right corner: (1850, 1050)
        val res = TooltipHelper.calculateTooltipPos(
            mouseX = 1850f,
            mouseY = 1050f,
            tipWidth = 150f,
            tipHeight = 50f,
            vpLeft = 0f,
            vpTop = 0f,
            vpRight = 1920f,
            vpBottom = 1080f
        )

        // Right-aligned with cursor box right: 1866, pivotX = 1.0f
        assertEquals(1866f, res.targetX)
        assertEquals(1.0f, res.pivotX)

        // Flipped above: 1046, pivotY = 1.0f
        assertEquals(1046f, res.targetY)
        assertEquals(1.0f, res.pivotY)
    }

    @Test
    fun testClampWhenTooltipExceedsViewportBounds() {
        // Extremely wide tooltip near right edge that would also cross left edge
        val res = TooltipHelper.calculateTooltipPos(
            mouseX = 700f,
            mouseY = 100f,
            tipWidth = 900f,
            tipHeight = 40f,
            vpLeft = 0f,
            vpTop = 0f,
            vpRight = 800f,
            vpBottom = 600f
        )

        // Overflows right (700 + 900 > 800)
        // targetX - tipWidth = (700 + 16) - 900 = -184 < 8 (vpLeft + padding)
        // Clamped to vpLeft + padding = 8f with pivotX = 0f
        assertEquals(8f, res.targetX)
        assertEquals(0.0f, res.pivotX)
    }

    @Test
    fun testHoverDelayTracking() {
        TooltipHelper.resetHoverTimer()

        val key1 = 12345
        val key2 = 67890
        val delay = 250L

        // Frame 1: hover begins
        assertFalse(TooltipHelper.shouldShowTooltip(key1, delay, currentFrame = 1, currentTimeMs = 1000L))

        // Frame 2, 100ms later: not ready
        assertFalse(TooltipHelper.shouldShowTooltip(key1, delay, currentFrame = 2, currentTimeMs = 1100L))

        // Frame 3, 260ms later: ready!
        assertTrue(TooltipHelper.shouldShowTooltip(key1, delay, currentFrame = 3, currentTimeMs = 1260L))

        // Still hovering same item on frame 4: still ready
        assertTrue(TooltipHelper.shouldShowTooltip(key1, delay, currentFrame = 4, currentTimeMs = 1300L))

        // Switched to key2 on frame 5: resets, not ready
        assertFalse(TooltipHelper.shouldShowTooltip(key2, delay, currentFrame = 5, currentTimeMs = 1310L))

        // Frame 6, 260ms after switching to key2: ready for key2
        assertTrue(TooltipHelper.shouldShowTooltip(key2, delay, currentFrame = 6, currentTimeMs = 1580L))

        // Mouse leaves for 3 frames (frame gap > 1), then returns to key2 on frame 10: timer must reset
        assertFalse(TooltipHelper.shouldShowTooltip(key2, delay, currentFrame = 10, currentTimeMs = 1800L))
    }

    @Test
    fun testCustomTooltipSizeCaching() {
        val key = 99999
        // Initial cache is null
        assertEquals(null, TooltipHelper.getCachedCustomSize(key))

        // Record rendered size
        TooltipHelper.recordCustomSize(key, 320f, 180f)

        val cached = TooltipHelper.getCachedCustomSize(key)
        assertEquals(320f, cached?.first)
        assertEquals(180f, cached?.second)
    }

    @Test
    fun testBuildControlTooltipFullCanonicalOrder() {
        // Properties configured deliberately out of canonical order to verify strict Order 2 sorting
        val tooltip = buildControlTooltip {
            rightClick = "Learn"
            shiftDrag = "fine-tune"
            leftClick = "inspect"
            osc = "/macro/deckBG_fx/knob/1 (always live, no Learn needed)"
            doubleClick = "type"
            binding = "Deck BG/FX/Super [0.00 – 1.00]"
            drag = "adjust"
            header = "SUPER: 0.52"
        }

        val expected = """
            SUPER: 0.52
            Bound to: Deck BG/FX/Super [0.00 – 1.00]

            • Drag to adjust
            • Shift-drag to fine-tune
            • Left-click to inspect
            • Double-click to type
            • Right-click for Learn

            OSC: /macro/deckBG_fx/knob/1 (always live, no Learn needed)
        """.trimIndent()

        assertEquals(expected, tooltip)
    }

    @Test
    fun testBuildControlTooltipIntelligentPrefixes() {
        val tooltip = buildControlTooltip {
            header = "Volume: 80%"
            drag = "to adjust level"
            shiftDrag = "for fine tuning"
            scroll = "nudge by 1%"
            leftClick = "to mute"
            doubleClick = "to reset to 0dB"
            middleClick = "center"
            rightClick = "reset levels"
            midi = "Ch 1 CC 7"
        }

        val expected = """
            Volume: 80%

            • Drag to adjust level
            • Shift-drag for fine tuning
            • Scroll to nudge by 1%
            • Left-click to mute
            • Double-click to reset to 0dB
            • Middle-click to center
            • Right-click to reset levels

            MIDI: Ch 1 CC 7
        """.trimIndent()

        assertEquals(expected, tooltip)
    }

    @Test
    fun testBuildControlTooltipUnboundAndEmptySections() {
        val tooltip = buildControlTooltip {
            header = "MACRO 1: 0.00"
            binding = "Unbound – right-click to assign"
            leftClick = "select"
            rightClick = "for Learn"
        }

        val expected = """
            MACRO 1: 0.00
            Unbound – right-click to assign

            • Left-click to select
            • Right-click for Learn
        """.trimIndent()

        assertEquals(expected, tooltip)
    }

    @Test
    fun testBuildControlTooltipCustomActions() {
        val tooltip = buildControlTooltip {
            header = "Master Crossfader"
            drag = "blend"
            middleClick = "snap to center"
            action("Drop video clip here to load")
            osc = "OSC: /master/xfade"
        }

        val expected = """
            Master Crossfader

            • Drag to blend
            • Middle-click to snap to center
            • Drop video clip here to load

            OSC: /master/xfade
        """.trimIndent()

        assertEquals(expected, tooltip)
    }
}

