package llm.slop.liquidlsd.ui

import imgui.ImGui
import imgui.flag.ImGuiCond
import imgui.flag.ImGuiInputTextFlags
import imgui.flag.ImGuiWindowFlags
import imgui.type.ImString
import llm.slop.liquidlsd.notes.NotesManager

/**
 * Context describing which note is being edited.
 * Used to route saves back to the correct [NotesManager] method.
 */
sealed class NoteContext {
    /** A per-parameter note: paramKey is the full key used in paramNotes (e.g. "Deck A/Geometry/Zoom"). */
    data class Param(val deckLabel: String, val paramKey: String, val displayLabel: String) : NoteContext()
    /** A global source note, stored by sourceId. */
    data class Source(val sourceId: String, val displayName: String) : NoteContext()
    /** A preset-level note stored within the preset file. */
    data class Preset(val deckLabel: String, val presetName: String) : NoteContext()
}

/**
 * Stateful singleton that renders an ImGui popup modal for editing user notes.
 *
 * Usage:
 *   1. Call [request] to queue a note for editing.
 *   2. Call [draw] every frame — it opens the modal when one is pending and handles Save/Cancel.
 *
 * The text buffer is a pre-allocated [ImString] (no per-frame allocation).
 */
object NoteEditorModal {

    private const val POPUP_ID = "Edit Note##note_editor_modal"
    private const val BUFFER_SIZE = 2048

    private val textBuffer = ImString(BUFFER_SIZE)
    private var pendingContext: NoteContext? = null
    private var pendingOpen = false

    /** Queue a note context for editing. The modal will open on the next [draw] call. */
    fun request(context: NoteContext) {
        pendingContext = context
        val existing = when (context) {
            is NoteContext.Param  -> NotesManager.getParamNote(context.deckLabel, context.paramKey)
            is NoteContext.Source -> NotesManager.getSourceNote(context.sourceId)
            is NoteContext.Preset -> NotesManager.getPresetNote(context.deckLabel)
        }
        textBuffer.set(existing)
        pendingOpen = true
    }

    /** Call every frame from the main render loop. */
    fun draw() {
        if (pendingOpen) {
            ImGui.openPopup(POPUP_ID)
            pendingOpen = false
        }

        val ctx = pendingContext ?: return

        val displayW = ImGui.getIO().displaySizeX
        val displayH = ImGui.getIO().displaySizeY
        ImGui.setNextWindowPos(displayW * 0.5f, displayH * 0.5f, ImGuiCond.Appearing, 0.5f, 0.5f)
        ImGui.setNextWindowSize(420f, 0f, ImGuiCond.Appearing)

        val flags = ImGuiWindowFlags.AlwaysAutoResize or ImGuiWindowFlags.NoMove or ImGuiWindowFlags.NoCollapse

        if (ImGui.beginPopupModal(POPUP_ID, flags)) {
            // Title row
            val title = when (ctx) {
                is NoteContext.Param  -> "Parameter note: ${ctx.displayLabel}"
                is NoteContext.Source -> "Source note: ${ctx.displayName}"
                is NoteContext.Preset -> "Preset note: ${ctx.presetName}"
            }
            ImGui.text(title)
            ImGui.separator()
            ImGui.spacing()

            ImGui.inputTextMultiline(
                "##note_text",
                textBuffer,
                400f,
                120f,
                ImGuiInputTextFlags.None
            )

            ImGui.spacing()

            // Footer hint
            when (ctx) {
                is NoteContext.Source ->
                    ImGui.textDisabled("Saved globally — persists across all presets and app restarts.")
                is NoteContext.Param, is NoteContext.Preset ->
                    ImGui.textDisabled("Saved with this preset — included next time you save.")
            }

            ImGui.spacing()
            ImGui.separator()
            ImGui.spacing()

            if (ImGui.button("Save", 100f, 0f)) {
                val text = textBuffer.get()
                when (ctx) {
                    is NoteContext.Param  -> NotesManager.setParamNote(ctx.deckLabel, ctx.paramKey, text)
                    is NoteContext.Source -> NotesManager.setSourceNote(ctx.sourceId, text)
                    is NoteContext.Preset -> NotesManager.setPresetNote(ctx.deckLabel, text)
                }
                pendingContext = null
                ImGui.closeCurrentPopup()
            }
            ImGui.sameLine()
            if (ImGui.button("Cancel", 100f, 0f)) {
                pendingContext = null
                ImGui.closeCurrentPopup()
            }

            ImGui.endPopup()
        }
    }
}
