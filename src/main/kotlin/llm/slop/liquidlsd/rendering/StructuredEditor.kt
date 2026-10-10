package llm.slop.liquidlsd.rendering

/**
 * A titled run of parameter names inside an editor tab. [collapsed] = starts closed. [rowLabels] overrides the row
 * label of a parameter (by full name) where the default (the name minus the tab prefix) is not descriptive.
 */
class EditorSection(
    val title: String, val params: List<String>, val collapsed: Boolean = false,
    val rowLabels: Map<String, String> = emptyMap()
)

/** One tab of a structured source editor; [id] is stable, [label] is what the tab button shows. */
class EditorTab(val id: String, val label: String, val sections: List<EditorSection>)

/**
 * Optional: a source with too many parameters for the flat Edit list describes a tabbed layout instead. The UI draws
 * every listed parameter with the ordinary parameter row (modulation, learn and the kebab keep working); this only
 * says which parameters appear where, so it stays UI-free and testable.
 */
interface StructuredEditorSource {
    /** Tabs to show now. [keep] is the tab the user is on, which must stay listed even if it would normally be hidden. */
    fun editorTabs(keep: String?): List<EditorTab>

    /** Label for a "+" button that reveals one more hidden tab, or null when there is nothing to add. */
    fun addTabLabel(): String?

    /** Reveals the hidden tab; returns its id so the UI can select it. */
    fun addEditorTab(): String?
}
