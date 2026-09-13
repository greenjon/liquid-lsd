package llm.slop.liquidlsd.ui.shortcuts

import org.lwjgl.glfw.GLFW.*
import imgui.flag.ImGuiKey

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
    val imguiKey: Int get() = glfwKeyToImGuiKey(keyCode)

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

        fun glfwKeyToImGuiKey(glfwKey: Int): Int {
            return when (glfwKey) {
                GLFW_KEY_TAB -> ImGuiKey.Tab
                GLFW_KEY_LEFT -> ImGuiKey.LeftArrow
                GLFW_KEY_RIGHT -> ImGuiKey.RightArrow
                GLFW_KEY_UP -> ImGuiKey.UpArrow
                GLFW_KEY_DOWN -> ImGuiKey.DownArrow
                GLFW_KEY_PAGE_UP -> ImGuiKey.PageUp
                GLFW_KEY_PAGE_DOWN -> ImGuiKey.PageDown
                GLFW_KEY_HOME -> ImGuiKey.Home
                GLFW_KEY_END -> ImGuiKey.End
                GLFW_KEY_INSERT -> ImGuiKey.Insert
                GLFW_KEY_DELETE -> ImGuiKey.Delete
                GLFW_KEY_BACKSPACE -> ImGuiKey.Backspace
                GLFW_KEY_SPACE -> ImGuiKey.Space
                GLFW_KEY_ENTER -> ImGuiKey.Enter
                GLFW_KEY_ESCAPE -> ImGuiKey.Escape
                GLFW_KEY_APOSTROPHE -> ImGuiKey.Apostrophe
                GLFW_KEY_COMMA -> ImGuiKey.Comma
                GLFW_KEY_MINUS -> ImGuiKey.Minus
                GLFW_KEY_PERIOD -> ImGuiKey.Period
                GLFW_KEY_SLASH -> ImGuiKey.Slash
                GLFW_KEY_SEMICOLON -> ImGuiKey.Semicolon
                GLFW_KEY_EQUAL -> ImGuiKey.Equal
                GLFW_KEY_LEFT_BRACKET -> ImGuiKey.LeftBracket
                GLFW_KEY_BACKSLASH -> ImGuiKey.Backslash
                GLFW_KEY_RIGHT_BRACKET -> ImGuiKey.RightBracket
                GLFW_KEY_GRAVE_ACCENT -> ImGuiKey.GraveAccent
                GLFW_KEY_CAPS_LOCK -> ImGuiKey.CapsLock
                GLFW_KEY_SCROLL_LOCK -> ImGuiKey.ScrollLock
                GLFW_KEY_NUM_LOCK -> ImGuiKey.NumLock
                GLFW_KEY_PRINT_SCREEN -> ImGuiKey.PrintScreen
                GLFW_KEY_PAUSE -> ImGuiKey.Pause
                GLFW_KEY_KP_0 -> ImGuiKey.Keypad0
                GLFW_KEY_KP_1 -> ImGuiKey.Keypad1
                GLFW_KEY_KP_2 -> ImGuiKey.Keypad2
                GLFW_KEY_KP_3 -> ImGuiKey.Keypad3
                GLFW_KEY_KP_4 -> ImGuiKey.Keypad4
                GLFW_KEY_KP_5 -> ImGuiKey.Keypad5
                GLFW_KEY_KP_6 -> ImGuiKey.Keypad6
                GLFW_KEY_KP_7 -> ImGuiKey.Keypad7
                GLFW_KEY_KP_8 -> ImGuiKey.Keypad8
                GLFW_KEY_KP_9 -> ImGuiKey.Keypad9
                GLFW_KEY_KP_DECIMAL -> ImGuiKey.KeypadDecimal
                GLFW_KEY_KP_DIVIDE -> ImGuiKey.KeypadDivide
                GLFW_KEY_KP_MULTIPLY -> ImGuiKey.KeypadMultiply
                GLFW_KEY_KP_SUBTRACT -> ImGuiKey.KeypadSubtract
                GLFW_KEY_KP_ADD -> ImGuiKey.KeypadAdd
                GLFW_KEY_KP_ENTER -> ImGuiKey.KeypadEnter
                GLFW_KEY_KP_EQUAL -> ImGuiKey.KeypadEqual
                GLFW_KEY_LEFT_SHIFT -> ImGuiKey.LeftShift
                GLFW_KEY_LEFT_CONTROL -> ImGuiKey.LeftCtrl
                GLFW_KEY_LEFT_ALT -> ImGuiKey.LeftAlt
                GLFW_KEY_LEFT_SUPER -> ImGuiKey.LeftSuper
                GLFW_KEY_RIGHT_SHIFT -> ImGuiKey.RightShift
                GLFW_KEY_RIGHT_CONTROL -> ImGuiKey.RightCtrl
                GLFW_KEY_RIGHT_ALT -> ImGuiKey.RightAlt
                GLFW_KEY_RIGHT_SUPER -> ImGuiKey.RightSuper
                GLFW_KEY_MENU -> ImGuiKey.Menu
                GLFW_KEY_0 -> ImGuiKey._0
                GLFW_KEY_1 -> ImGuiKey._1
                GLFW_KEY_2 -> ImGuiKey._2
                GLFW_KEY_3 -> ImGuiKey._3
                GLFW_KEY_4 -> ImGuiKey._4
                GLFW_KEY_5 -> ImGuiKey._5
                GLFW_KEY_6 -> ImGuiKey._6
                GLFW_KEY_7 -> ImGuiKey._7
                GLFW_KEY_8 -> ImGuiKey._8
                GLFW_KEY_9 -> ImGuiKey._9
                GLFW_KEY_A -> ImGuiKey.A
                GLFW_KEY_B -> ImGuiKey.B
                GLFW_KEY_C -> ImGuiKey.C
                GLFW_KEY_D -> ImGuiKey.D
                GLFW_KEY_E -> ImGuiKey.E
                GLFW_KEY_F -> ImGuiKey.F
                GLFW_KEY_G -> ImGuiKey.G
                GLFW_KEY_H -> ImGuiKey.H
                GLFW_KEY_I -> ImGuiKey.I
                GLFW_KEY_J -> ImGuiKey.J
                GLFW_KEY_K -> ImGuiKey.K
                GLFW_KEY_L -> ImGuiKey.L
                GLFW_KEY_M -> ImGuiKey.M
                GLFW_KEY_N -> ImGuiKey.N
                GLFW_KEY_O -> ImGuiKey.O
                GLFW_KEY_P -> ImGuiKey.P
                GLFW_KEY_Q -> ImGuiKey.Q
                GLFW_KEY_R -> ImGuiKey.R
                GLFW_KEY_S -> ImGuiKey.S
                GLFW_KEY_T -> ImGuiKey.T
                GLFW_KEY_U -> ImGuiKey.U
                GLFW_KEY_V -> ImGuiKey.V
                GLFW_KEY_W -> ImGuiKey.W
                GLFW_KEY_X -> ImGuiKey.X
                GLFW_KEY_Y -> ImGuiKey.Y
                GLFW_KEY_Z -> ImGuiKey.Z
                GLFW_KEY_F1 -> ImGuiKey.F1
                GLFW_KEY_F2 -> ImGuiKey.F2
                GLFW_KEY_F3 -> ImGuiKey.F3
                GLFW_KEY_F4 -> ImGuiKey.F4
                GLFW_KEY_F5 -> ImGuiKey.F5
                GLFW_KEY_F6 -> ImGuiKey.F6
                GLFW_KEY_F7 -> ImGuiKey.F7
                GLFW_KEY_F8 -> ImGuiKey.F8
                GLFW_KEY_F9 -> ImGuiKey.F9
                GLFW_KEY_F10 -> ImGuiKey.F10
                GLFW_KEY_F11 -> ImGuiKey.F11
                GLFW_KEY_F12 -> ImGuiKey.F12
                else -> ImGuiKey.None
            }
        }
    }
}
