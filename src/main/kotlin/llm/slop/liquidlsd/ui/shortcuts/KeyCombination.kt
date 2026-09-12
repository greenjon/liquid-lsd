package llm.slop.liquidlsd.ui.shortcuts

import org.lwjgl.glfw.GLFW.*

/**
 * Represents a keyboard combination consisting of a primary key code (GLFW key)
 * and modifier key flags (Ctrl, Shift, Alt, Super/Cmd).
 */
data class KeyCombination(
    val keyCode: Int,
    val modifiers: Int = 0
) {
    val isCtrl: Boolean get() = (modifiers and GLFW_MOD_CONTROL) != 0
    val isShift: Boolean get() = (modifiers and GLFW_MOD_SHIFT) != 0
    val isAlt: Boolean get() = (modifiers and GLFW_MOD_ALT) != 0
    val isSuper: Boolean get() = (modifiers and GLFW_MOD_SUPER) != 0

    val isEmpty: Boolean get() = keyCode == 0

    /**
     * Formats this key combination into a clean, human-readable display string
     * such as "Ctrl + Shift + S" or "Esc".
     */
    fun toDisplayString(): String {
        if (isEmpty) return "None"
        val parts = mutableListOf<String>()
        if (isCtrl || isSuper) {
            // Display Ctrl or Cmd depending on OS preference / standard
            parts.add(if (isMacOs()) "Cmd" else "Ctrl")
        }
        if (isShift) parts.add("Shift")
        if (isAlt) parts.add("Alt")

        val keyName = getKeyName(keyCode)
        if (keyName.isNotEmpty()) {
            parts.add(keyName)
        }
        return parts.joinToString(" + ")
    }

    companion object {
        fun isMacOs(): Boolean {
            return System.getProperty("os.name", "").lowercase().contains("mac")
        }

        fun getKeyName(keyCode: Int): String {
            return when (keyCode) {
                GLFW_KEY_SPACE -> "Space"
                GLFW_KEY_ESCAPE -> "Esc"
                GLFW_KEY_ENTER -> "Enter"
                GLFW_KEY_TAB -> "Tab"
                GLFW_KEY_BACKSPACE -> "Backspace"
                GLFW_KEY_DELETE -> "Delete"
                GLFW_KEY_UP -> "Up Arrow"
                GLFW_KEY_DOWN -> "Down Arrow"
                GLFW_KEY_LEFT -> "Left Arrow"
                GLFW_KEY_RIGHT -> "Right Arrow"
                GLFW_KEY_MINUS -> "-"
                GLFW_KEY_EQUAL -> "="
                GLFW_KEY_SLASH -> "/"
                GLFW_KEY_BACKSLASH -> "\\"
                GLFW_KEY_CAPS_LOCK -> "Caps Lock"
                GLFW_KEY_KP_SUBTRACT -> "Keypad -"
                GLFW_KEY_KP_ADD -> "Keypad +"
                GLFW_KEY_KP_0, GLFW_KEY_0 -> "0"
                GLFW_KEY_KP_1, GLFW_KEY_1 -> "1"
                GLFW_KEY_KP_2, GLFW_KEY_2 -> "2"
                GLFW_KEY_KP_3, GLFW_KEY_3 -> "3"
                GLFW_KEY_KP_4, GLFW_KEY_4 -> "4"
                GLFW_KEY_KP_5, GLFW_KEY_5 -> "5"
                GLFW_KEY_KP_6, GLFW_KEY_6 -> "6"
                GLFW_KEY_KP_7, GLFW_KEY_7 -> "7"
                GLFW_KEY_KP_8, GLFW_KEY_8 -> "8"
                GLFW_KEY_KP_9, GLFW_KEY_9 -> "9"
                in GLFW_KEY_A..GLFW_KEY_Z -> (keyCode.toChar()).toString()
                in GLFW_KEY_F1..GLFW_KEY_F12 -> "F${keyCode - GLFW_KEY_F1 + 1}"
                else -> {
                    val glfwName = glfwGetKeyName(keyCode, 0)
                    glfwName?.uppercase() ?: "Key $keyCode"
                }
            }
        }

        fun parse(input: String): KeyCombination {
            val trimmed = input.trim()
            if (trimmed.isEmpty() || trimmed.equalsIgnoreCase("none")) {
                return KeyCombination(0, 0)
            }

            val parts = trimmed.split("+").map { it.trim() }.filter { it.isNotEmpty() }
            var mods = 0
            var primaryKeyCode = 0

            parts.forEach { part ->
                val lower = part.lowercase()
                when (lower) {
                    "ctrl", "control" -> mods = mods or GLFW_MOD_CONTROL
                    "cmd", "command", "super" -> mods = mods or (if (isMacOs()) GLFW_MOD_SUPER else GLFW_MOD_CONTROL)
                    "shift" -> mods = mods or GLFW_MOD_SHIFT
                    "alt", "opt", "option" -> mods = mods or GLFW_MOD_ALT
                    else -> {
                        primaryKeyCode = parseKeyCode(part)
                    }
                }
            }
            return KeyCombination(primaryKeyCode, mods)
        }

        private fun String.equalsIgnoreCase(other: String): Boolean = this.equals(other, ignoreCase = true)

        private fun parseKeyCode(name: String): Int {
            val lower = name.lowercase()
            return when (lower) {
                "space" -> GLFW_KEY_SPACE
                "esc", "escape" -> GLFW_KEY_ESCAPE
                "enter", "return" -> GLFW_KEY_ENTER
                "tab" -> GLFW_KEY_TAB
                "backspace" -> GLFW_KEY_BACKSPACE
                "del", "delete" -> GLFW_KEY_DELETE
                "up", "up arrow" -> GLFW_KEY_UP
                "down", "down arrow" -> GLFW_KEY_DOWN
                "left", "left arrow" -> GLFW_KEY_LEFT
                "right", "right arrow" -> GLFW_KEY_RIGHT
                "-", "minus" -> GLFW_KEY_MINUS
                "=", "equal", "equals" -> GLFW_KEY_EQUAL
                "/", "slash" -> GLFW_KEY_SLASH
                "\\", "backslash" -> GLFW_KEY_BACKSLASH
                "caps lock", "capslock" -> GLFW_KEY_CAPS_LOCK
                "keypad -", "kp -" -> GLFW_KEY_KP_SUBTRACT
                "keypad +", "kp +" -> GLFW_KEY_KP_ADD
                else -> {
                    if (lower.length == 1) {
                        val ch = lower[0]
                        if (ch in 'a'..'z') GLFW_KEY_A + (ch - 'a')
                        else if (ch in '0'..'9') GLFW_KEY_0 + (ch - '0')
                        else 0
                    } else if (lower.startsWith("f") && lower.length in 2..3) {
                        val num = lower.substring(1).toIntOrNull() ?: 0
                        if (num in 1..12) GLFW_KEY_F1 + (num - 1) else 0
                    } else {
                        0
                    }
                }
            }
        }
    }
}
