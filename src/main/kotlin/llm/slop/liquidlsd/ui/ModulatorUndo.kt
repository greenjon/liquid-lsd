package llm.slop.liquidlsd.ui

import imgui.ImGui

/**
 * Makes edits on the modulator panels (LFO, Audio, MIDI CC, Step Sequencer) undoable with Ctrl+Z, one undo
 * step per gesture: [beforeChange] snapshots just before the first change of a gesture, and not again until
 * the mouse button has been released (a slider or paint drag is many changes but one step).
 */
object ModulatorUndo {
    private var gestureOpen = false

    /** Call once per frame before the panel draws. */
    fun frame() {
        if (!ImGui.isMouseDown(0)) gestureOpen = false
    }

    /** Call right before applying a change; [push] records the undo snapshot the first time in a gesture. */
    fun beforeChange(push: () -> Unit) {
        if (!gestureOpen) {
            push()
            gestureOpen = true
        }
    }
}
