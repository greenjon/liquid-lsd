package llm.slop.liquidlsd.ui

import imgui.ImGui
import imgui.flag.ImGuiKey
import imgui.flag.ImGuiTableColumnFlags
import imgui.flag.ImGuiTableFlags
import imgui.type.ImString

/**
 * Dedicated UI component for the Keyboard Shortcuts preferences category:
 * - Filterable list of rebindable actions grouped by [llm.slop.liquidlsd.ui.shortcuts.ShortcutCategory].
 * - Inline text box capture of raw key combinations (click/focus and press keys, or type text).
 * - Conflict detection/swap and per-action reset-to-default controls.
 *
 * Rendered within the "Keyboard Shortcuts" category of [PreferencesPanel].
 */
object ShortcutsPreferencesPanel {

    private var shortcutsFilterBuf = ImString(128)
    private val actionInputBuffers = mutableMapOf<String, ImString>()

    private fun drawShortcutGridTable(
        session: llm.slop.liquidlsd.SessionContext,
        tableId: String,
        actions: List<llm.slop.liquidlsd.ui.shortcuts.ShortcutAction>
    ) {
        val tableFlags = ImGuiTableFlags.BordersInnerH or ImGuiTableFlags.RowBg or ImGuiTableFlags.SizingStretchProp
        if (ImGui.beginTable(tableId, 2, tableFlags)) {
            ImGui.tableSetupColumn("Action & Description", ImGuiTableColumnFlags.WidthStretch, 1f)
            ImGui.tableSetupColumn("Shortcut Binding & Actions", ImGuiTableColumnFlags.WidthFixed, 240f)
            ImGui.tableHeadersRow()

            actions.forEach { action ->
                val conflicts = llm.slop.liquidlsd.ui.shortcuts.ShortcutManager.findConflicts(action.id, action.currentKey)
                val hasConflict = conflicts.isNotEmpty()

                ImGui.tableNextRow()

                // Column 0 (Left): Action Name & Detailed Description
                ImGui.tableNextColumn()
                session.uiTheme.body(action.name)
                if (action.description.isNotEmpty()) {
                    session.uiTheme.captionColored(0.7f, 0.7f, 0.7f, 0.85f, action.description)
                }
                if (hasConflict) {
                    val conflictNames = conflicts.joinToString(", ") { it.name }
                    session.uiTheme.captionColored(
                        1.0f, 0.65f, 0.2f, 1.0f,
                        "${Icons.ALERT} Conflict with: $conflictNames"
                    )
                }

                // Column 1 (Right): Editable Text Box & Rebind Controls
                ImGui.tableNextColumn()
                val buf = actionInputBuffers.getOrPut(action.id) { ImString(64) }
                val displayKey = action.currentKey?.toDisplayString() ?: "None"

                if (hasConflict) {
                    ImGui.pushStyleColor(imgui.flag.ImGuiCol.FrameBg, 0.5f, 0.2f, 0.05f, 0.8f)
                    ImGui.pushStyleColor(imgui.flag.ImGuiCol.Text, 1.0f, 0.7f, 0.3f, 1.0f)
                }

                ImGui.setNextItemWidth(140f)
                session.uiTheme.withFont(UITheme.FontLevel.CODE) {
                    val changed = ImGui.inputText("##input_${action.id}", buf)
                    val isFocused = ImGui.isItemActive() || ImGui.isItemFocused()

                    if (!isFocused && !changed) {
                        if (buf.get() != displayKey) {
                            buf.set(displayKey)
                        }
                    }

                    if (isFocused) {
                        val io = ImGui.getIO()
                        if (ImGui.isKeyPressed(ImGuiKey.Escape, false)) {
                            buf.set(displayKey)
                        } else if (ImGui.isKeyPressed(ImGuiKey.Backspace, false) && buf.get().isEmpty()) {
                            llm.slop.liquidlsd.ui.shortcuts.ShortcutManager.updateKeyBinding(action.id, null)
                            buf.set("None")
                        } else {
                            var capturedKey = 0
                            for (k in 32..348) {
                                if (k in 256..257 || k in 340..347) continue // Skip modifiers like Left/Right Ctrl, Shift, Alt, Super
                                val imguiKey = llm.slop.liquidlsd.ui.shortcuts.KeyCombination.glfwKeyToImGuiKey(k)
                                if (imguiKey != ImGuiKey.None && ImGui.isKeyPressed(imguiKey, false)) {
                                    capturedKey = k
                                    break
                                }
                            }
                            if (capturedKey != 0) {
                                var mods = 0
                                if (io.keyCtrl) mods = mods or org.lwjgl.glfw.GLFW.GLFW_MOD_CONTROL
                                if (io.keyShift) mods = mods or org.lwjgl.glfw.GLFW.GLFW_MOD_SHIFT
                                if (io.keyAlt) mods = mods or org.lwjgl.glfw.GLFW.GLFW_MOD_ALT
                                if (io.keySuper) mods = mods or org.lwjgl.glfw.GLFW.GLFW_MOD_SUPER

                                val newCombo = llm.slop.liquidlsd.ui.shortcuts.KeyCombination(capturedKey, mods)
                                llm.slop.liquidlsd.ui.shortcuts.ShortcutManager.updateKeyBinding(action.id, newCombo)
                                buf.set(newCombo.toDisplayString())
                            }
                        }
                    }

                    if (changed || ImGui.isItemDeactivatedAfterEdit()) {
                        val parsed = llm.slop.liquidlsd.ui.shortcuts.KeyCombination.parse(buf.get())
                        llm.slop.liquidlsd.ui.shortcuts.ShortcutManager.updateKeyBinding(action.id, parsed)
                    }
                }

                if (hasConflict) {
                    ImGui.popStyleColor(2)
                    itemTooltip("Warning: Key combination conflicts with ${conflicts.size} other action(s). Focus box and press keys or type combo.")
                } else {
                    itemTooltip("Click or focus box and press keys directly, or type shortcut text (e.g., 'Ctrl+S', 'F', 'None').")
                }

                if (hasConflict) {
                    ImGui.sameLine()
                    if (ImGui.button("Swap##swap_${action.id}")) {
                        llm.slop.liquidlsd.ui.shortcuts.ShortcutManager.swapBindings(action.id, conflicts.first().id)
                        buf.set(action.currentKey?.toDisplayString() ?: "None")
                    }
                    itemTooltip("Swap keybinding with '${conflicts.first().name}'")
                }

                if (action.isModified) {
                    ImGui.sameLine()
                    if (ImGui.button("${Icons.REFRESH}##reset_${action.id}")) {
                        llm.slop.liquidlsd.ui.shortcuts.ShortcutManager.resetToDefault(action.id)
                        buf.set(action.defaultKey?.toDisplayString() ?: "None")
                    }
                    itemTooltip("Reset to factory default (${action.defaultKey?.toDisplayString() ?: "None"})")
                }
            }
            ImGui.endTable()
        }
    }

    fun drawContent(session: llm.slop.liquidlsd.SessionContext) {
        session.uiTheme.h2("Keyboard Shortcuts & Input Settings")
        ImGui.separator()
        ImGui.spacing()

        session.uiTheme.caption("Filter actions, edit keyboard shortcuts directly in inline text boxes, and resolve key collisions:")
        ImGui.spacing()

        // Top Filter & Reset Bar
        ImGui.setNextItemWidth(340f)
        ImGui.inputTextWithHint("##shortcut_filter", "${Icons.SEARCH} Filter shortcuts by name or key...", shortcutsFilterBuf)
        if (shortcutsFilterBuf.get().isNotEmpty()) {
            ImGui.sameLine()
            if (ImGui.button("${Icons.X}##clear_filter")) {
                shortcutsFilterBuf.set("")
            }
            itemTooltip("Clear search filter")
        }

        ImGui.sameLine()
        if (ImGui.button("${Icons.REFRESH} Reset All Defaults")) {
            llm.slop.liquidlsd.ui.shortcuts.ShortcutManager.resetAllToDefaults()
            actionInputBuffers.clear()
        }
        itemTooltip("Restore factory default keybindings for all actions.")

        ImGui.spacing()
        ImGui.separator()
        ImGui.spacing()

        val query = shortcutsFilterBuf.get().trim().lowercase()
        val categories = llm.slop.liquidlsd.ui.shortcuts.ShortcutCategory.values()

        categories.forEach { category ->
            val actions = llm.slop.liquidlsd.ui.shortcuts.ShortcutManager.getActionsForCategory(category)
            val filtered = actions.filter { action ->
                query.isEmpty() ||
                action.name.lowercase().contains(query) ||
                action.description.lowercase().contains(query) ||
                (action.currentKey?.toDisplayString()?.lowercase()?.contains(query) == true)
            }

            if (filtered.isNotEmpty()) {
                session.uiTheme.h3(category.label)
                ImGui.spacing()
                drawShortcutGridTable(session, "##table_${category.name.lowercase()}", filtered)
                ImGui.spacing()
                ImGui.separator()
                ImGui.spacing()
            }
        }
    }
}
