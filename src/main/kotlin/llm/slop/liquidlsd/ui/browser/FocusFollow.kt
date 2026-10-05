package llm.slop.liquidlsd.ui.browser

/**
 * Lets a list's selection follow ImGui keyboard focus (arrow-key navigation) only when the focus *moves*.
 * Comparing "focused row != selected row" instead would pull a cursor moved by the controller or a shortcut
 * straight back to the row that still holds focus, so knob 1 would silently do nothing once the list had
 * been clicked. One instance per list; [K] identifies a row (path, index).
 */
internal class FocusFollow<K> {
    private var lastFocused: K? = null

    /** Call once per row after it is submitted, with `ImGui.isItemFocused()`; true when focus just arrived on [key]. */
    fun arrived(key: K, focused: Boolean): Boolean {
        if (!focused || key == lastFocused) return false
        lastFocused = key
        return true
    }
}
