package llm.slop.liquidlsd.ui.browser

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FocusFollowTest {
    @Test
    fun focusThatStaysOnARowDoesNotPullTheCursorBack() {
        val follow = FocusFollow<Int>()
        // Frame 1: the user clicked row 5, focus arrives there.
        assertTrue(follow.arrived(5, focused = true))
        // Knob 1 moves the cursor to row 6; ImGui focus is still on row 5 this frame. Row 5 must not re-select itself.
        assertFalse(follow.arrived(5, focused = true))
        assertFalse(follow.arrived(6, focused = false))
    }

    @Test
    fun arrowKeyFocusMovesStillSelect() {
        val follow = FocusFollow<Int>()
        assertTrue(follow.arrived(1, focused = true))
        assertTrue(follow.arrived(2, focused = true))
        assertTrue(follow.arrived(1, focused = true))
    }
}
