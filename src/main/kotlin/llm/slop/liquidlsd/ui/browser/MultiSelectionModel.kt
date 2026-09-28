package llm.slop.liquidlsd.ui.browser

/**
 * Generic headless model for managing multi-selection in list views.
 *
 * Supports standard desktop interaction paradigms:
 * - **Normal Click**: Selects a single item, clearing any previous selection. Sets lead and anchor items.
 * - **Ctrl/Cmd+Click**: Toggles selection of an individual item without affecting others.
 * - **Shift+Click**: Selects an inclusive continuous range from the [anchorItem] to the clicked item,
 *   based on the order of [visibleItems].
 * - **Ctrl+Shift+Click**: Adds the range between [anchorItem] and clicked item to the existing selection.
 *
 * Zero allocation per frame during query checks ([isSelected]).
 */
class MultiSelectionModel<T> {
    private val _selectedItems = linkedSetOf<T>()

    /** Unmodifiable view of currently selected items in insertion/selection order. */
    val selectedItems: Set<T> get() = _selectedItems

    /** The most recently clicked or focused item. Used for auditioning and single-target actions. */
    var leadItem: T? = null
        private set

    /** The anchor/pivot item used as the starting point for Shift+Click range selections. */
    var anchorItem: T? = null
        private set

    val count: Int get() = _selectedItems.size
    val isEmpty: Boolean get() = _selectedItems.isEmpty()
    val isNotEmpty: Boolean get() = _selectedItems.isNotEmpty()

    /** Fast $O(1)$ selection check for row rendering. */
    fun isSelected(item: T): Boolean = _selectedItems.contains(item)

    /**
     * Handles a user click on [item] within [visibleItems], applying modifier semantics.
     *
     * @param item The clicked item
     * @param visibleItems The current visible/filtered list of items (used for range indexing)
     * @param isCtrl Whether Ctrl (Windows/Linux) or Cmd (macOS) is held
     * @param isShift Whether Shift is held
     */
    fun handleClick(
        item: T,
        visibleItems: List<T>,
        isCtrl: Boolean,
        isShift: Boolean
    ) {
        if (isShift && anchorItem != null) {
            val fromIndex = visibleItems.indexOf(anchorItem)
            val toIndex = visibleItems.indexOf(item)
            if (fromIndex != -1 && toIndex != -1) {
                val start = minOf(fromIndex, toIndex)
                val end = maxOf(fromIndex, toIndex)
                val range = visibleItems.subList(start, end + 1)
                if (!isCtrl) {
                    _selectedItems.clear()
                }
                _selectedItems.addAll(range)
                leadItem = item
                return
            }
        }

        if (isCtrl) {
            if (_selectedItems.contains(item)) {
                _selectedItems.remove(item)
                if (leadItem == item) {
                    leadItem = _selectedItems.lastOrNull()
                }
            } else {
                _selectedItems.add(item)
                leadItem = item
                anchorItem = item
            }
        } else {
            // Normal click
            _selectedItems.clear()
            _selectedItems.add(item)
            leadItem = item
            anchorItem = item
        }
    }

    /** Sets single item selection, or clears if [item] is null. */
    fun setSingle(item: T?) {
        _selectedItems.clear()
        if (item != null) {
            _selectedItems.add(item)
            leadItem = item
            anchorItem = item
        } else {
            leadItem = null
            anchorItem = null
        }
    }

    /** Clears all selected items and resets lead/anchor. */
    fun clear() {
        _selectedItems.clear()
        leadItem = null
        anchorItem = null
    }

    /** Selects all items from [items], setting anchor to the first and lead to the last. */
    fun selectAll(items: Collection<T>) {
        _selectedItems.clear()
        _selectedItems.addAll(items)
        anchorItem = items.firstOrNull()
        leadItem = items.lastOrNull()
    }

    /** Returns the list of selected items in the order they appear in [orderedSource]. */
    fun getSelectedInOrder(orderedSource: List<T>): List<T> {
        if (_selectedItems.isEmpty()) return emptyList()
        return orderedSource.filter { _selectedItems.contains(it) }
    }
}
