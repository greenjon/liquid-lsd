package llm.slop.liquidlsd.ui.browser

import llm.slop.liquidlsd.presets.BgQueueManager
import llm.slop.liquidlsd.presets.FXBgQueueManager
import llm.slop.liquidlsd.presets.FXQueueManager
import llm.slop.liquidlsd.presets.PlayQueueManager
import llm.slop.liquidlsd.presets.TransitionQueueManager
import llm.slop.liquidlsd.ui.ParametersState
import llm.slop.liquidlsd.ui.ToastOverlay
import llm.slop.liquidlsd.ui.browser.QueueDrop.Queue
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.io.File

class QueueOpsTest {
    private val state = ParametersState()

    @AfterEach
    fun clear() {
        PlayQueueManager.clearQueue()
        BgQueueManager.clearQueue()
        FXQueueManager.clearQueue()
        FXBgQueueManager.clearQueue()
        TransitionQueueManager.clearQueue()
        Queue.entries.forEach { QueueOps.setCursor(it, -1) }
    }

    private fun fill(queue: Queue, vararg names: String) = QueueDrop.insertFiles(queue, 0, names.map { File("/l/$it.${ext(queue)}") })
    private fun ext(queue: Queue) = when (queue.kind) { BrowseKind.SRC -> "lsd"; BrowseKind.FX -> "lsdfx"; BrowseKind.TRANS -> "lsdtrans" }
    private fun names(queue: Queue) = QueueOps.items(queue).map { it.nameWithoutExtension }

    private fun undo() = state.popUndoState()!!.restore!!.invoke()

    @Test
    fun `move puts the row at the index, both ways, in every queue, and the cursor follows it`() {
        for (q in Queue.entries) {
            fill(q, "a", "b", "c", "d")
            QueueOps.setCursor(q, 0)
            QueueOps.move(q, 0, 2, state)
            assertEquals(listOf("b", "c", "a", "d"), names(q), "$q down")
            assertEquals(2, QueueOps.cursor(q), "$q cursor")
            QueueOps.move(q, 3, 0, state)
            assertEquals(listOf("d", "b", "c", "a"), names(q), "$q up")
        }
    }

    @Test
    fun `move keeps the playing item playing and undo puts everything back`() {
        fill(Queue.AB, "a", "b", "c")
        PlayQueueManager.restoreSnapshot(PlayQueueManager.queue.toList(), 1, setOf(0, 1), listOf(0, 1))
        QueueOps.setCursor(Queue.AB, 2)
        QueueOps.move(Queue.AB, 2, 0, state)
        assertEquals(listOf("c", "a", "b"), names(Queue.AB))
        assertEquals("b", PlayQueueManager.queue[PlayQueueManager.activeIndex].nameWithoutExtension)

        undo()
        assertEquals(listOf("a", "b", "c"), names(Queue.AB))
        assertEquals(1, PlayQueueManager.activeIndex)
        assertEquals(setOf(0, 1), PlayQueueManager.playedIndices.toSet())
        assertEquals(listOf(0, 1), PlayQueueManager.playbackHistory.toList())
        assertEquals(2, QueueOps.cursor(Queue.AB))
    }

    @Test
    fun `remove is one undo step, toasts, and the cursor stays on the next row`() {
        fill(Queue.BG, "a", "b", "c", "d")
        QueueOps.setCursor(Queue.BG, 2)
        QueueOps.remove(Queue.BG, listOf(0, 2), state)
        assertEquals(listOf("b", "d"), names(Queue.BG))
        assertEquals(1, QueueOps.cursor(Queue.BG), "on d, the row after the removed c")
        assertEquals("Removed 2 items from the BG queue (Ctrl+Z)", ToastOverlay.active())

        undo()
        assertEquals(listOf("a", "b", "c", "d"), names(Queue.BG))
    }

    @Test
    fun `removing the playing item and emptying a queue`() {
        fill(Queue.FX_AB, "a")
        QueueOps.setCursor(Queue.FX_AB, 0)
        QueueOps.remove(Queue.FX_AB, listOf(0), state)
        assertEquals(emptyList<String>(), names(Queue.FX_AB))
        assertEquals(-1, QueueOps.cursor(Queue.FX_AB))
        assertEquals(-1, FXQueueManager.activeIndex)
        assertEquals("Removed a from the A/B FX queue (Ctrl+Z)", ToastOverlay.active())
    }

    @Test
    fun `insertAfter goes after the row, the cursor lands on the last new item, and the top and the end work`() {
        fill(Queue.TRANS, "a", "b")
        assertEquals(2, QueueOps.insertAfter(Queue.TRANS, 0, listOf(File("/l/x.lsdtrans"), File("/l/y.lsdtrans")), state))
        assertEquals(listOf("a", "x", "y", "b"), names(Queue.TRANS))
        assertEquals(2, QueueOps.cursor(Queue.TRANS))

        QueueOps.insertAfter(Queue.TRANS, -1, listOf(File("/l/top.lsdtrans")), state)
        assertEquals("top", names(Queue.TRANS).first())
        QueueOps.insertAfter(Queue.TRANS, 99, listOf(File("/l/end.lsdtrans")), state)
        assertEquals("end", names(Queue.TRANS).last())

        undo()
        undo()
        undo()
        assertEquals(listOf("a", "b"), names(Queue.TRANS), "each insert is one undo step")
    }

    @Test
    fun `clear empties every queue as one undo step that puts the items and the playing row back`() {
        for (q in Queue.entries) {
            fill(q, "a", "b", "c")
            QueueOps.setCursor(q, 1)
            QueueOps.clear(q, state)
            assertEquals(emptyList<String>(), names(q), "$q")
            assertEquals(-1, QueueOps.cursor(q))
            undo()
            assertEquals(listOf("a", "b", "c"), names(q), "$q")
            assertEquals(1, QueueOps.cursor(q))
        }
        assertEquals("Cleared the ${Queue.TRANS.label} (Ctrl+Z)", ToastOverlay.active())

        QueueOps.clear(Queue.AB, state)
        QueueOps.clear(Queue.AB, state)
        val depth = generateSequence { state.popUndoState() }.count()
        assertEquals(1, depth, "clearing an already empty queue pushes nothing")
    }

    @Test
    fun `a dropped payload goes in as one undo step`() {
        fill(Queue.FX_AB, "a")
        QueueDrop.insert(Queue.FX_AB, 0, "/l/x.lsdfx\n/l/y.lsdfx", state)
        assertEquals(listOf("x", "y", "a"), names(Queue.FX_AB))
        undo()
        assertEquals(listOf("a"), names(Queue.FX_AB))
    }

    @Test
    fun `edit pushes undo only when the manager call changed the items`() {
        fill(Queue.BG, "a")
        QueueOps.edit(Queue.BG, state) { }
        assertEquals(null, state.popUndoState())
        QueueOps.edit(Queue.BG, state) { BgQueueManager.appendToQueue(File("/l/b.lsd")) }
        assertEquals(listOf("a", "b"), names(Queue.BG))
        undo()
        assertEquals(listOf("a"), names(Queue.BG))
    }
}
