package llm.slop.liquidlsd.ui.shortcuts

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.lwjgl.glfw.GLFW.*

class ShortcutManagerTest {

    @BeforeEach
    fun setUp() {
        ShortcutManager.resetAllToDefaults()
    }

    @Test
    fun testKeyCombinationDisplayString() {
        val combo1 = KeyCombination(GLFW_KEY_F)
        assertEquals("F", combo1.toDisplayString())

        val combo2 = KeyCombination(GLFW_KEY_S, GLFW_MOD_CONTROL)
        val expectedCtrl = if (KeyCombination.isMacOs()) "Cmd + S" else "Ctrl + S"
        assertEquals(expectedCtrl, combo2.toDisplayString())

        val combo3 = KeyCombination(GLFW_KEY_S, GLFW_MOD_CONTROL or GLFW_MOD_SHIFT)
        val expectedCtrlShift = if (KeyCombination.isMacOs()) "Cmd + Shift + S" else "Ctrl + Shift + S"
        assertEquals(expectedCtrlShift, combo3.toDisplayString())

        val emptyCombo = KeyCombination(0)
        assertEquals("None", emptyCombo.toDisplayString())
    }

    @Test
    fun testKeyCombinationParse() {
        val parsed1 = KeyCombination.parse("Ctrl + S")
        assertEquals(GLFW_KEY_S, parsed1.keyCode)
        assertTrue(parsed1.isCtrl || parsed1.isSuper)

        val parsed2 = KeyCombination.parse("Shift + F5")
        assertEquals(GLFW_KEY_F5, parsed2.keyCode)
        assertTrue(parsed2.isShift)

        val parsedNone = KeyCombination.parse("None")
        assertTrue(parsedNone.isEmpty)
    }

    @Test
    fun testConflictDetection() {
        // Attempting to assign 'F' (which is global.fullscreen) to global.bg_video should detect a conflict
        val fKey = KeyCombination(GLFW_KEY_F)
        val conflicts = ShortcutManager.findConflicts("global.bg_video", fKey)
        assertEquals(1, conflicts.size)
        assertEquals("global.fullscreen", conflicts[0].id)
    }

    @Test
    fun testNoConflictForDistinctKeys() {
        val unusedKey = KeyCombination(GLFW_KEY_X, GLFW_MOD_ALT)
        val conflicts = ShortcutManager.findConflicts("global.fullscreen", unusedKey)
        assertTrue(conflicts.isEmpty())
    }

    @Test
    fun testRebindAndReset() {
        val actionId = "global.fullscreen"
        val newKey = KeyCombination(GLFW_KEY_G)

        ShortcutManager.updateKeyBinding(actionId, newKey)
        assertTrue(ShortcutManager.matchesKey(actionId, GLFW_KEY_G))
        assertFalse(ShortcutManager.matchesKey(actionId, GLFW_KEY_F))

        ShortcutManager.resetToDefault(actionId)
        assertTrue(ShortcutManager.matchesKey(actionId, GLFW_KEY_F))
    }

    @Test
    fun testSwapBindings() {
        val id1 = "library.load_deck_a" // 1
        val id2 = "library.load_deck_b" // 2

        ShortcutManager.swapBindings(id1, id2)
        assertTrue(ShortcutManager.matchesKey(id1, GLFW_KEY_2))
        assertTrue(ShortcutManager.matchesKey(id2, GLFW_KEY_1))

        ShortcutManager.resetAllToDefaults()
        assertTrue(ShortcutManager.matchesKey(id1, GLFW_KEY_1))
        assertTrue(ShortcutManager.matchesKey(id2, GLFW_KEY_2))
    }
}
