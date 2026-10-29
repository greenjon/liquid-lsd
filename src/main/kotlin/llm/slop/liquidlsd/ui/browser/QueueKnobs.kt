package llm.slop.liquidlsd.ui.browser

import llm.slop.liquidlsd.SessionContext
import llm.slop.liquidlsd.presets.BgQueueManager
import llm.slop.liquidlsd.presets.FXBgQueueManager
import llm.slop.liquidlsd.presets.FXQueueManager
import llm.slop.liquidlsd.presets.PlayQueueManager
import llm.slop.liquidlsd.presets.TransitionQueueManager
import llm.slop.liquidlsd.rendering.Mixer
import llm.slop.liquidlsd.ui.LibraryPanel
import llm.slop.liquidlsd.ui.LibraryPanel.LibraryViewMode
import llm.slop.liquidlsd.ui.ParametersState
import llm.slop.liquidlsd.ui.browser.QueueDrop.Queue
import java.io.File

/**
 * The controller's queue knobs in the Library view while the queues are under the browser ([QueueSplit]): knob 14 is the tab's
 * A-side queue (Sources: A/B, FX: A/B FX, Transitions: Transitions), knob 15 its BG-side queue (Sources: BG, FX: BG FX; none on
 * Transitions). Turn moves that queue's cursor, hold + turn carries the cursor row up or down, a tap uses it now, shift + tap removes
 * it (undoable). The queue knob touched last is where the cursor knob's shift + tap puts the browser item: after its cursor.
 */
object QueueKnobs {
    /** 0-based knob indices. */
    const val A_SIDE_KNOB = 13
    const val BG_SIDE_KNOB = 14

    /** The (A-side, BG-side) queues of [tab]; both null on a tab without a browser. */
    fun queuesFor(tab: LibraryViewMode): Pair<Queue?, Queue?> = when (tab) {
        LibraryViewMode.PRESETS -> Queue.AB to Queue.BG
        LibraryViewMode.FX -> Queue.FX_AB to Queue.FX_BG
        LibraryViewMode.TRANS -> Queue.TRANS to null
        LibraryViewMode.QUEUES, LibraryViewMode.MAPS -> null to null
    }

    /** The queue [knob] drives on [tab], or null (not a queue knob, or that side has no queue). */
    fun queueFor(knob: Int, tab: LibraryViewMode): Queue? = queuesFor(tab).let {
        when (knob) {
            A_SIDE_KNOB -> it.first
            BG_SIDE_KNOB -> it.second
            else -> null
        }
    }

    /** Whether the queue knobs are live: the Library view with the queues under the browser. */
    fun live(): Boolean = QueueSplit.shows(LibraryPanel.viewMode)

    private var lastTouched: Queue? = null
    private var lastTouchedSession = -1

    /** The queue whose knob was touched last in browse session [session] and still fits the current tab, or null. */
    fun lastTouched(session: Int): Queue? =
        lastTouched?.takeIf { lastTouchedSession == session && live() && it in queuesFor(LibraryPanel.viewMode).toList() }

    /** Forgets the touched knob (tests). */
    internal fun reset() {
        lastTouched = null
        lastTouchedSession = -1
    }

    private fun touch(queue: Queue, session: Int) {
        lastTouched = queue
        lastTouchedSession = session
    }

    /** The playing row of [queue], or -1. */
    fun playing(queue: Queue): Int = when (queue) {
        Queue.AB -> PlayQueueManager.activeIndex
        Queue.BG -> BgQueueManager.activeIndex
        Queue.FX_AB -> FXQueueManager.activeIndex
        Queue.FX_BG -> FXBgQueueManager.activeIndex
        Queue.TRANS -> TransitionQueueManager.activeIndex
    }.takeIf { it in QueueOps.items(queue).indices } ?: -1

    /**
     * A turn of [knob] by [steps] rows. Without a cursor yet, the first step lands on the playing row (else the first or last row),
     * so "insert after" right away means "play next". [held]: carry the cursor row instead. Returns false for a knob that isn't a
     * live queue knob.
     */
    fun turn(knob: Int, steps: Int, held: Boolean, session: Int, state: ParametersState?): Boolean {
        if (!live()) return false
        val queue = queueFor(knob, LibraryPanel.viewMode) ?: return false
        touch(queue, session)
        val size = QueueOps.items(queue).size
        if (size == 0 || steps == 0) return true
        val cursor = QueueOps.cursor(queue)
        when {
            cursor < 0 -> QueueOps.setCursor(queue, playing(queue).takeIf { it >= 0 } ?: if (steps > 0) 0 else size - 1)
            held -> QueueOps.move(queue, cursor, cursor + steps, state)
            else -> QueueOps.setCursor(queue, (cursor + steps).coerceIn(0, size - 1))
        }
        LibraryPanel.shouldScrollToSelection = true
        return true
    }

    /** A tap of [knob]: use the cursor row now ([shifted]: remove it). Returns false for a knob that isn't a live queue knob. */
    fun tap(knob: Int, shifted: Boolean, session: Int, app: SessionContext, mixer: Mixer): Boolean {
        if (!live()) return false
        val queue = queueFor(knob, LibraryPanel.viewMode) ?: return false
        touch(queue, session)
        val cursor = QueueOps.cursor(queue)
        if (cursor < 0) return true
        if (shifted) QueueOps.remove(queue, listOf(cursor), app.parametersState) else QueueOps.play(queue, cursor, app, mixer)
        return true
    }

    /**
     * The cursor knob's shift + tap: [files] go after the cursor of the queue knob touched last in [session] (no cursor: after the
     * playing row, else the end); with none touched, the end of the tab's A-side queue. Files of another kind are dropped. Returns
     * false when no queue knob was touched, so the caller can fall back to its plain enqueue.
     */
    fun insertFromBrowser(files: List<File>, session: Int, state: ParametersState?): Boolean {
        val queue = lastTouched(session) ?: return false
        val fitting = files.filter { QueueDrop.fits(queue, it) }.flatMap { QueueDrop.expandForQueue(queue, it) }
        val anchor = QueueOps.cursor(queue).takeIf { it >= 0 } ?: playing(queue).takeIf { it >= 0 } ?: QueueOps.items(queue).lastIndex
        QueueOps.insertAfter(queue, anchor, fitting, state)
        return true
    }
}
