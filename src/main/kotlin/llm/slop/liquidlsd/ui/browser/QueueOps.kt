package llm.slop.liquidlsd.ui.browser

import llm.slop.liquidlsd.SessionContext
import llm.slop.liquidlsd.presets.BgQueueManager
import llm.slop.liquidlsd.presets.FXBgQueueManager
import llm.slop.liquidlsd.presets.FXQueueManager
import llm.slop.liquidlsd.presets.PlayQueueManager
import llm.slop.liquidlsd.presets.QueueEngine
import llm.slop.liquidlsd.presets.TransitionQueueManager
import llm.slop.liquidlsd.rendering.Mixer
import llm.slop.liquidlsd.ui.ParametersState
import llm.slop.liquidlsd.ui.ParametersUndoSnapshot
import llm.slop.liquidlsd.ui.ToastOverlay
import llm.slop.liquidlsd.ui.browser.QueueDrop.Queue
import java.io.File

/**
 * Edits of one queue by index, the same for all five: its cursor (the panel's highlighted row), play now, move, remove, insert and
 * clear. Every edit pushes an undo step (Ctrl+Z puts the items, the playing position and the shuffle history back), so the mouse, the
 * keyboard and the controller all edit queues the same way.
 */
object QueueOps {
    fun items(queue: Queue): List<File> = when (queue) {
        Queue.AB -> PlayQueueManager.queue
        Queue.BG -> BgQueueManager.queue
        Queue.FX_AB -> FXQueueManager.queue
        Queue.FX_BG -> FXBgQueueManager.queue
        Queue.TRANS -> TransitionQueueManager.queue
    }

    /** The queue's highlighted row (its panel's selection), or -1. */
    fun cursor(queue: Queue): Int = when (queue) {
        Queue.AB -> QueueActionsPanel.selectedIndex
        Queue.BG -> BgQueueActionsPanel.selectedIndex
        Queue.FX_AB -> FXQueueActionsPanel.selectedIndex
        Queue.FX_BG -> FXBgQueueActionsPanel.selectedIndex
        Queue.TRANS -> TransitionQueuePanel.selectedIndex
    }.takeIf { it in items(queue).indices } ?: -1

    /** Highlights row [index] (clamped; -1 clears). Doesn't touch [llm.slop.liquidlsd.ui.LibraryPanel.activeSelectionSource]. */
    fun setCursor(queue: Queue, index: Int) {
        val i = if (index < 0 || items(queue).isEmpty()) -1 else index.coerceIn(0, items(queue).lastIndex)
        when (queue) {
            Queue.AB -> QueueActionsPanel.selectedIndex = i
            Queue.BG -> BgQueueActionsPanel.selectedIndex = i
            Queue.FX_AB -> FXQueueActionsPanel.selectedIndex = i
            Queue.FX_BG -> FXBgQueueActionsPanel.selectedIndex = i
            Queue.TRANS -> TransitionQueuePanel.selectedIndex = i
        }
    }

    /** Uses row [index] now, as a double-click on it does. */
    fun play(queue: Queue, index: Int, session: SessionContext, mixer: Mixer) {
        val file = items(queue).getOrNull(index) ?: return
        when (queue) {
            // Moves the queue position, fades to the loaded deck and advances the transition queue.
            Queue.AB -> PlayQueueManager.playIndex(index, mixer)
            Queue.BG -> BrowserDeckButtons.loadPresetToDeck(session, mixer, file, 3)
            Queue.FX_AB -> FXQueueManager.jumpToIndex(index, session, mixer)
            Queue.FX_BG -> FXBgQueueManager.jumpToIndex(index, session, mixer)
            Queue.TRANS -> TransitionQueueManager.applyTransitionItem(file, mixer)
        }
    }

    /** A copy of everything an edit can change, and how to put it back. */
    internal class Snapshot(val queue: Queue, val files: List<File>, val active: Int, val played: Set<Int>, val history: List<Int>, val cursor: Int)

    internal fun snapshot(queue: Queue): Snapshot {
        val (active, played, history) = when (queue) {
            Queue.AB -> Triple(PlayQueueManager.activeIndex, PlayQueueManager.playedIndices.toSet(), PlayQueueManager.playbackHistory.toList())
            Queue.BG -> Triple(BgQueueManager.activeIndex, BgQueueManager.playedIndices.toSet(), BgQueueManager.playbackHistory.toList())
            else -> engine(queue).let { Triple(it.activeIndex, it.playedIndices.toSet(), it.playbackHistory.toList()) }
        }
        return Snapshot(queue, items(queue).toList(), active, played, history, cursor(queue))
    }

    internal fun restore(s: Snapshot) {
        when (s.queue) {
            Queue.AB -> PlayQueueManager.restoreSnapshot(s.files, s.active, s.played, s.history)
            Queue.BG -> BgQueueManager.restoreSnapshot(s.files, s.active, s.played, s.history)
            else -> engine(s.queue).restoreSnapshot(s.files, s.active, s.played, s.history)
        }
        setCursor(s.queue, s.cursor)
    }

    private fun engine(queue: Queue): QueueEngine = when (queue) {
        Queue.FX_AB -> FXQueueManager
        Queue.FX_BG -> FXBgQueueManager
        Queue.TRANS -> TransitionQueueManager
        Queue.AB, Queue.BG -> error("$queue is not a QueueEngine")
    }

    private fun pushUndo(state: ParametersState?, before: Snapshot) {
        state?.pushUndoState(ParametersUndoSnapshot(emptyMap()) { restore(before) })
    }

    /**
     * Moves row [from] so it ends up at index [to] (both clamped into the queue); the playing item stays the playing item and the
     * cursor follows the moved row. One undo step per call, so a controller drag of several rows is several steps.
     */
    fun move(queue: Queue, from: Int, to: Int, state: ParametersState?) {
        val items = items(queue)
        if (from !in items.indices) return
        val target = to.coerceIn(0, items.lastIndex)
        if (target == from) return
        pushUndo(state, snapshot(queue))
        when (queue) {
            Queue.AB -> PlayQueueManager.moveQueueItem(from, target)
            // BgQueueManager.move takes the slot to insert before, counted in the list before the removal.
            Queue.BG -> BgQueueManager.move(from, if (target > from) target + 1 else target)
            else -> engine(queue).moveItem(from, target)
        }
        if (cursor(queue) == from) setCursor(queue, target)
    }

    /**
     * Removes [indices] (one undo step for all of them) and toasts what went. The playing item, if removed, keeps playing on its
     * deck. The cursor stays on the same place in the list (the next row), or clears with the queue.
     */
    fun remove(queue: Queue, indices: Collection<Int>, state: ParametersState?) {
        val items = items(queue)
        val valid = indices.filter { it in items.indices }.distinct().sortedDescending()
        if (valid.isEmpty()) return
        val firstName = items[valid.last()].nameWithoutExtension
        val cursorBefore = cursor(queue)
        pushUndo(state, snapshot(queue))
        for (i in valid) when (queue) {
            Queue.AB -> PlayQueueManager.removeFromQueue(i)
            Queue.BG -> BgQueueManager.removeAt(i)
            else -> engine(queue).removeFromQueue(i)
        }
        setCursor(queue, if (cursorBefore < 0 || items(queue).isEmpty()) -1 else (cursorBefore - valid.count { it < cursorBefore }).coerceAtLeast(0))
        val what = if (valid.size == 1) firstName else "${valid.size} items"
        ToastOverlay.show("Removed $what from the ${queue.label} (Ctrl+Z)")
    }

    /**
     * Puts [files] (already expanded, of the queue's kind) before row [index] (clamped; past the end: appended) as one undo step and
     * flashes the queue. Returns how many went in.
     */
    fun insert(queue: Queue, index: Int, files: List<File>, state: ParametersState?): Int {
        if (files.isEmpty()) return 0
        pushUndo(state, snapshot(queue))
        QueueDrop.insertFiles(queue, index.coerceIn(0, items(queue).size), files)
        QueueDrop.flash(queue)
        return files.size
    }

    /**
     * Puts [files] (already expanded, of the queue's kind) after row [index] (-1: at the top; past the end: appended), moves the
     * cursor onto the last of them and flashes the queue. With [index] on the playing item they play next. Returns how many went in.
     */
    fun insertAfter(queue: Queue, index: Int, files: List<File>, state: ParametersState?): Int {
        val at = (index + 1).coerceIn(0, items(queue).size)
        val n = insert(queue, at, files, state)
        if (n > 0) setCursor(queue, at + n - 1)
        return n
    }

    /** Empties [queue] as one undo step and toasts. The playing item keeps playing on its deck. */
    fun clear(queue: Queue, state: ParametersState?) {
        if (items(queue).isEmpty()) return
        pushUndo(state, snapshot(queue))
        when (queue) {
            Queue.AB -> PlayQueueManager.clearQueue()
            Queue.BG -> BgQueueManager.clearQueue()
            else -> engine(queue).clearQueue()
        }
        setCursor(queue, -1)
        ToastOverlay.show("Cleared the ${queue.label} (Ctrl+Z)")
    }

    /**
     * Runs [change], a queue manager edit with no QueueOps counterpart (a playlist's play now, insert or append), and pushes one undo
     * step if it changed [queue]'s items.
     */
    fun edit(queue: Queue, state: ParametersState?, change: () -> Unit) {
        val before = snapshot(queue)
        change()
        if (items(queue) != before.files) pushUndo(state, before)
    }
}
