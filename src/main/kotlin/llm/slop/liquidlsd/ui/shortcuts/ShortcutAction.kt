package llm.slop.liquidlsd.ui.shortcuts

enum class ShortcutCategory(val label: String) {
    GLOBAL("Global & Display Controls"),
    PARAMETERS("Deep Edit & Modulation Matrix"),
    PROPERTIES("Properties & Number Inputs"),
    LIBRARY("Library & Asset Browser"),
    CLOCK("Audio & Clock Controls")
}

data class ShortcutAction(
    val id: String,
    val category: ShortcutCategory,
    val name: String,
    val description: String,
    val defaultKey: KeyCombination?,
    var currentKey: KeyCombination? = defaultKey,
    val allowConflict: Boolean = false
) {
    val isModified: Boolean get() = currentKey != defaultKey
}
