package llm.slop.liquidlsd.ui.browser

import io.mockk.mockk
import llm.slop.liquidlsd.presets.BgQueueManager
import llm.slop.liquidlsd.presets.FXBgQueueManager
import llm.slop.liquidlsd.presets.FXQueueManager
import llm.slop.liquidlsd.presets.PlayQueueManager
import llm.slop.liquidlsd.presets.TransitionQueueManager
import llm.slop.liquidlsd.ui.LibraryPanel
import llm.slop.liquidlsd.ui.LibraryPanel.LibraryViewMode
import llm.slop.liquidlsd.ui.ParametersState
import llm.slop.liquidlsd.ui.UITheme
import llm.slop.liquidlsd.ui.browser.QueueDrop.Queue
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File

class QueueKnobsTest {
    private val state = ParametersState()
    private val a = QueueKnobs.A_SIDE_KNOB
    private val bg = QueueKnobs.BG_SIDE_KNOB
    private var savedMode = UITheme.libraryMode
    private var savedSplit = UITheme.libraryQueueSplit

    @BeforeEach
    fun libraryViewWithTheSplit() {
        savedMode = UITheme.libraryMode
        savedSplit = UITheme.libraryQueueSplit
        UITheme.libraryMode = UITheme.LibraryMode.FULL
        UITheme.libraryQueueSplit = true
        LibraryPanel.viewMode = LibraryViewMode.PRESETS
    }

    @AfterEach
    fun clear() {
        UITheme.libraryMode = savedMode
        UITheme.libraryQueueSplit = savedSplit
        LibraryPanel.viewMode = LibraryViewMode.PRESETS
        PlayQueueManager.clearQueue()
        BgQueueManager.clearQueue()
        FXQueueManager.clearQueue()
        FXBgQueueManager.clearQueue()
        TransitionQueueManager.clearQueue()
        Queue.entries.forEach { QueueOps.setCursor(it, -1) }
        QueueKnobs.reset()
    }

    private fun fill(queue: Queue, vararg names: String) = QueueDrop.insertFiles(queue, 0, names.map { File("/l/$it.lsd") })
    private fun names(queue: Queue) = QueueOps.items(queue).map { it.nameWithoutExtension }

    @Test
    fun `the knobs follow the tab`() {
        assertEquals(Queue.AB to Queue.BG, QueueKnobs.queuesFor(LibraryViewMode.PRESETS))
        assertEquals(Queue.FX_AB to Queue.FX_BG, QueueKnobs.queuesFor(LibraryViewMode.FX))
        assertEquals(Queue.TRANS to null, QueueKnobs.queuesFor(LibraryViewMode.TRANS))
        assertEquals(null to null, QueueKnobs.queuesFor(LibraryViewMode.QUEUES))
        assertNull(QueueKnobs.queueFor(bg, LibraryViewMode.TRANS))
        assertNull(QueueKnobs.queueFor(12, LibraryViewMode.PRESETS))
    }

    @Test
    fun `the first turn lands on the playing row, then steps and clamps`() {
        fill(Queue.AB, "a", "b", "c")
        PlayQueueManager.restoreSnapshot(PlayQueueManager.queue.toList(), 1, emptySet(), emptyList())
        assertTrue(QueueKnobs.turn(a, 1, held = false, session = 1, state = state))
        assertEquals(1, QueueOps.cursor(Queue.AB))
        QueueKnobs.turn(a, 1, false, 1, state)
        QueueKnobs.turn(a, 1, false, 1, state)
        assertEquals(2, QueueOps.cursor(Queue.AB))
    }

    @Test
    fun `hold and turn carries the row and is undoable`() {
        fill(Queue.BG, "a", "b", "c")
        QueueOps.setCursor(Queue.BG, 0)
        QueueKnobs.turn(bg, 1, held = true, session = 1, state = state)
        QueueKnobs.turn(bg, 1, held = true, session = 1, state = state)
        assertEquals(listOf("b", "c", "a"), names(Queue.BG))
        assertEquals(2, QueueOps.cursor(Queue.BG))
        state.popUndoState()!!.restore!!.invoke()
        assertEquals(listOf("b", "a", "c"), names(Queue.BG), "one step per row")
    }

    @Test
    fun `shift tap removes the cursor row`() {
        fill(Queue.AB, "a", "b")
        QueueOps.setCursor(Queue.AB, 0)
        val app = mockk<llm.slop.liquidlsd.SessionContext>(relaxed = true)
        io.mockk.every { app.parametersState } returns state
        assertTrue(QueueKnobs.tap(a, shifted = true, session = 1, app = app, mixer = mockk(relaxed = true)))
        assertEquals(listOf("b"), names(Queue.AB))
    }

    @Test
    fun `the browser item goes after the touched queue's cursor, only in that session and while the tab fits`() {
        fill(Queue.BG, "a", "b")
        assertFalse(QueueKnobs.insertFromBrowser(listOf(File("/l/x.lsd")), session = 1), "nothing touched")
        QueueOps.setCursor(Queue.BG, 0)
        QueueKnobs.turn(bg, 0, false, session = 1, state = state)
        assertTrue(QueueKnobs.insertFromBrowser(listOf(File("/l/x.lsd"), File("/l/fx.lsdfx")), session = 1))
        assertEquals(listOf("a", "x", "b"), names(Queue.BG))
        assertEquals(1, QueueOps.cursor(Queue.BG), "the cursor is on the new item, so the next one follows it")

        assertFalse(QueueKnobs.insertFromBrowser(listOf(File("/l/y.lsd")), session = 2), "a new browse session")
        LibraryPanel.viewMode = LibraryViewMode.FX
        assertFalse(QueueKnobs.insertFromBrowser(listOf(File("/l/y.lsd")), session = 1), "the BG queue isn't this tab's")
    }

    @Test
    fun `the knobs are dead without the split`() {
        fill(Queue.AB, "a")
        UITheme.libraryQueueSplit = false
        assertFalse(QueueKnobs.turn(a, 1, false, 1, state))
        assertEquals(-1, QueueOps.cursor(Queue.AB))
    }
}
