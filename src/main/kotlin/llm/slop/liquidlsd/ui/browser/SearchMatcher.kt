package llm.slop.liquidlsd.ui.browser

/**
 * The one search rule shared by every asset browser (Library source/FX/transition panels, the inline
 * [llm.slop.liquidlsd.ui.ShaderPickerPopup], and the saved-chain list in the Browse bay), so the same
 * text finds the same things wherever it is typed.
 *
 * The query is split on whitespace; an item matches when **every** word is a case-insensitive substring of
 * at least one of the item's fields (name, id, folder, categories/tags). An empty query matches everything.
 */
object SearchMatcher {
    /** Buffer size for every browser search box. */
    const val BUFFER_SIZE = 256

    fun tokens(raw: String): List<String> = raw.lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }

    fun matches(tokens: List<String>, vararg fields: String): Boolean =
        matches(tokens, fields.asList())

    fun matches(tokens: List<String>, fields: Collection<String>, extra: Collection<String> = emptyList()): Boolean {
        if (tokens.isEmpty()) return true
        return tokens.all { token ->
            fields.any { it.contains(token, ignoreCase = true) } || extra.any { it.contains(token, ignoreCase = true) }
        }
    }
}
