package llm.slop.liquidlsd.ui.shortcuts

import org.lwjgl.glfw.GLFW.*
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.logging.Logger

object ShortcutManager {
    private val logger = Logger.getLogger("ShortcutManager")
    private val actions = LinkedHashMap<String, ShortcutAction>()
    private val configFile: File by lazy {
        val userHome = System.getProperty("user.home", ".")
        val dir = File(userHome, ".liquidlsd")
        if (!dir.exists()) dir.mkdirs()
        File(dir, "keybindings.json")
    }

    init {
        registerDefaultActions()
        loadKeybindings()
    }

    private fun registerDefaultActions() {
        actions.clear()

        // 1. Global & Display
        register(ShortcutAction("global.fullscreen", ShortcutCategory.GLOBAL, "Toggle Fullscreen / Clean Mode", "Hides all UI chrome to display full master video output.", KeyCombination(GLFW_KEY_F)))
        register(ShortcutAction("global.exit_fullscreen", ShortcutCategory.GLOBAL, "Exit Fullscreen / Clean Mode", "Restores the user interface when in Fullscreen Clean Mode.", KeyCombination(GLFW_KEY_ESCAPE)))
        register(ShortcutAction("global.bg_video", ShortcutCategory.GLOBAL, "Toggle Background Video", "Renders master visuals behind the semi-transparent interface.", KeyCombination(GLFW_KEY_B)))
        register(ShortcutAction("global.preset_size_dec", ShortcutCategory.GLOBAL, "Decrease Preset Name Size", "Reduces Library browser preset name font size by 10% (80%–120%).", KeyCombination(GLFW_KEY_MINUS, GLFW_MOD_CONTROL)))
        register(ShortcutAction("global.preset_size_inc", ShortcutCategory.GLOBAL, "Increase Preset Name Size", "Increases Library browser preset name font size by 10% (80%–120%).", KeyCombination(GLFW_KEY_EQUAL, GLFW_MOD_CONTROL)))
        register(ShortcutAction("global.record_output", ShortcutCategory.GLOBAL, "Start / Stop Recording", "Toggles live master output recording to MP4 video.", KeyCombination(GLFW_KEY_R, GLFW_MOD_CONTROL)))
        register(ShortcutAction("global.preferences", ShortcutCategory.GLOBAL, "Open Preferences", "Opens the application preferences dialog.", KeyCombination(GLFW_KEY_P, GLFW_MOD_CONTROL)))

        // 2. Parameters & Modulation Matrix
        register(ShortcutAction("parameters.save_deck", ShortcutCategory.PARAMETERS, "Save Active Deck Preset", "Saves active deck preset in Parameters (opens Save As if untitled).", KeyCombination(GLFW_KEY_S, GLFW_MOD_CONTROL)))
        register(ShortcutAction("parameters.save_deck_as", ShortcutCategory.PARAMETERS, "Save Active Deck Preset As...", "Opens Save As modal for active deck in Parameters.", KeyCombination(GLFW_KEY_S, GLFW_MOD_CONTROL or GLFW_MOD_SHIFT)))
        register(ShortcutAction("parameters.undo", ShortcutCategory.PARAMETERS, "Undo Parameter Action", "Reverts last parameter tweak, randomize, paste, or reset.", KeyCombination(GLFW_KEY_Z, GLFW_MOD_CONTROL)))
        register(ShortcutAction("parameters.copy", ShortcutCategory.PARAMETERS, "Copy Cell or Row", "Copies modulation routing (or row settings if Base/Final cell is selected).", KeyCombination(GLFW_KEY_C, GLFW_MOD_CONTROL)))
        register(ShortcutAction("parameters.paste", ShortcutCategory.PARAMETERS, "Paste Cell or Row", "Applies copied modulators or parameter settings with an undo point.", KeyCombination(GLFW_KEY_V, GLFW_MOD_CONTROL)))
        register(ShortcutAction("parameters.clear_reset", ShortcutCategory.PARAMETERS, "Clear Cell / Reset Parameter", "Clears modulators on active cell, or resets parameter to default.", KeyCombination(GLFW_KEY_DELETE), allowConflict = true))

        // 3. Properties & Inputs
        register(ShortcutAction("properties.step_value", ShortcutCategory.PROPERTIES, "Step Numeric Value (Focused Input)", "Increments/decrements focused number box (\u00B10.001 fine, Shift \u00B10.01, Ctrl+Shift \u00B10.1).", KeyCombination(GLFW_KEY_UP), allowConflict = true))

        // 4. Library & Asset Browser
        register(ShortcutAction("library.load_deck_a", ShortcutCategory.LIBRARY, "Load to Deck A", "Loads selected preset into Deck A.", KeyCombination(GLFW_KEY_1)))
        register(ShortcutAction("library.load_deck_b", ShortcutCategory.LIBRARY, "Load to Deck B", "Loads selected preset into Deck B.", KeyCombination(GLFW_KEY_2)))
        register(ShortcutAction("library.load_deck_bg", ShortcutCategory.LIBRARY, "Load to Deck BG", "Loads selected preset into Background Deck (BG).", KeyCombination(GLFW_KEY_3)))
        register(ShortcutAction("library.load_deck_pv", ShortcutCategory.LIBRARY, "Preview on Deck PV", "Loads selected preset into Preview Deck (PV).", KeyCombination(GLFW_KEY_4)))
        register(ShortcutAction("library.queue_ab", ShortcutCategory.LIBRARY, "Add to A/B Queue", "Appends selected preset to the A/B Play Queue.", KeyCombination(GLFW_KEY_Q)))
        register(ShortcutAction("library.queue_bg", ShortcutCategory.LIBRARY, "Add to Background Queue", "Appends selected preset to the Background Queue (BG).", KeyCombination(GLFW_KEY_Q, GLFW_MOD_SHIFT)))
        register(ShortcutAction("library.navigate", ShortcutCategory.LIBRARY, "Navigate List Items", "Moves focus selection across presets, playlists, and queue items.", KeyCombination(GLFW_KEY_UP), allowConflict = true))
        register(ShortcutAction("library.delete_asset", ShortcutCategory.LIBRARY, "Delete Preset / Remove Queue Item", "Deletes selected user preset or removes item from queue.", KeyCombination(GLFW_KEY_DELETE), allowConflict = true))

        // 5. Audio & Clock Controls
        register(ShortcutAction("clock.tap_tempo", ShortcutCategory.CLOCK, "Tap Tempo", "Taps in manual BPM tempo when not typing in text fields.", KeyCombination(GLFW_KEY_T)))
    }

    private fun register(action: ShortcutAction) {
        actions[action.id] = action
    }

    fun getAllActions(): List<ShortcutAction> = actions.values.toList()

    fun getActionsForCategory(category: ShortcutCategory): List<ShortcutAction> {
        return actions.values.filter { it.category == category }
    }

    fun getAction(id: String): ShortcutAction? = actions[id]

    /**
     * Checks if a candidate key combination conflicts with any existing action bindings.
     * Returns a list of conflicting actions.
     */
    fun findConflicts(actionId: String, candidateKey: KeyCombination?): List<ShortcutAction> {
        if (candidateKey == null || candidateKey.isEmpty) return emptyList()
        val targetAction = actions[actionId] ?: return emptyList()
        if (targetAction.allowConflict) return emptyList()

        return actions.values.filter { other ->
            other.id != actionId &&
            !other.allowConflict &&
            other.currentKey != null &&
            !other.currentKey!!.isEmpty &&
            other.currentKey!!.keyCode == candidateKey.keyCode &&
            other.currentKey!!.modifiers == candidateKey.modifiers &&
            (targetAction.category == ShortcutCategory.GLOBAL ||
             other.category == ShortcutCategory.GLOBAL ||
             targetAction.category == other.category)
        }
    }

    fun updateKeyBinding(actionId: String, newKey: KeyCombination?) {
        val action = actions[actionId] ?: return
        action.currentKey = newKey
        saveKeybindings()
    }

    fun resetToDefault(actionId: String) {
        val action = actions[actionId] ?: return
        action.currentKey = action.defaultKey
        saveKeybindings()
    }

    fun resetAllToDefaults() {
        actions.values.forEach { it.currentKey = it.defaultKey }
        saveKeybindings()
    }

    fun swapBindings(actionId1: String, actionId2: String) {
        val action1 = actions[actionId1] ?: return
        val action2 = actions[actionId2] ?: return
        val temp = action1.currentKey
        action1.currentKey = action2.currentKey
        action2.currentKey = temp
        saveKeybindings()
    }

    fun matchesKey(actionId: String, keyCode: Int, modifiers: Int = 0): Boolean {
        val action = actions[actionId] ?: return false
        val key = action.currentKey ?: return false
        if (key.isEmpty) return false
        return key.keyCode == keyCode && key.modifiers == modifiers
    }

    /**
     * Checks if the shortcut action was triggered in ImGui during the current frame.
     * Evaluates modifier keys (Ctrl/Cmd, Shift, Alt) against ImGui.getIO() and verifies
     * that the primary key (or keypad equivalent) was pressed via ImGui.isKeyPressed().
     */
    fun isTriggered(actionId: String): Boolean {
        val action = actions[actionId] ?: return false
        val key = action.currentKey ?: return false
        if (key.isEmpty) return false

        try {
            if (!imgui.ImGui.getCurrentContext().isValidPtr()) {
                return false
            }
        } catch (e: Throwable) {
            return false
        }

        val io = try {
            imgui.ImGui.getIO()
        } catch (e: Throwable) {
            return false
        }
        if (io.wantTextInput) return false

        val wantCtrl = key.isCtrl || key.isSuper
        val hasCtrl = io.keyCtrl || io.keySuper
        if (wantCtrl != hasCtrl) return false
        if (key.isShift != io.keyShift) return false
        if (key.isAlt != io.keyAlt) return false

        var pressed = try {
            imgui.ImGui.isKeyPressed(key.keyCode, false)
        } catch (e: Throwable) {
            false
        }

        if (!pressed) {
            val kpCode = when (key.keyCode) {
                GLFW_KEY_0 -> GLFW_KEY_KP_0
                GLFW_KEY_1 -> GLFW_KEY_KP_1
                GLFW_KEY_2 -> GLFW_KEY_KP_2
                GLFW_KEY_3 -> GLFW_KEY_KP_3
                GLFW_KEY_4 -> GLFW_KEY_KP_4
                GLFW_KEY_5 -> GLFW_KEY_KP_5
                GLFW_KEY_6 -> GLFW_KEY_KP_6
                GLFW_KEY_7 -> GLFW_KEY_KP_7
                GLFW_KEY_8 -> GLFW_KEY_KP_8
                GLFW_KEY_9 -> GLFW_KEY_KP_9
                else -> null
            }
            if (kpCode != null) {
                pressed = try {
                    imgui.ImGui.isKeyPressed(kpCode, false)
                } catch (e: Throwable) {
                    false
                }
            }
        }
        return pressed
    }

    fun saveKeybindings() {
        try {
            val sb = StringBuilder()
            sb.append("{\n")
            val items = actions.values.filter { it.currentKey != null }
            items.forEachIndexed { idx, action ->
                val key = action.currentKey!!
                sb.append("  \"${action.id}\": {\"keyCode\": ${key.keyCode}, \"modifiers\": ${key.modifiers}}")
                if (idx < items.size - 1) sb.append(",")
                sb.append("\n")
            }
            sb.append("}\n")
            configFile.writeText(sb.toString())
        } catch (e: Exception) {
            logger.warning("Failed to save keybindings to ${configFile.absolutePath}: ${e.message}")
        }
    }

    fun loadKeybindings() {
        if (!configFile.exists()) return
        try {
            val text = configFile.readText()
            // Simple JSON regex parser for keybindings map
            val regex = """"([^"]+)":\s*\{\s*"keyCode":\s*(-?\d+),\s*"modifiers":\s*(\d+)\s*\}""".toRegex()
            regex.findAll(text).forEach { match ->
                val id = match.groupValues[1]
                val keyCode = match.groupValues[2].toIntOrNull() ?: 0
                val modifiers = match.groupValues[3].toIntOrNull() ?: 0
                val action = actions[id]
                if (action != null) {
                    action.currentKey = KeyCombination(keyCode, modifiers)
                }
            }
        } catch (e: Exception) {
            logger.warning("Failed to load keybindings from ${configFile.absolutePath}: ${e.message}")
        }
    }
}
