package llm.slop.liquidlsd.ui.browser

import llm.slop.liquidlsd.presets.BgQueueManager
import llm.slop.liquidlsd.presets.FXBgQueueManager
import llm.slop.liquidlsd.presets.FXQueueManager
import llm.slop.liquidlsd.presets.PlayQueueManager
import llm.slop.liquidlsd.presets.TransitionQueueManager
import llm.slop.liquidlsd.ui.ToastOverlay
import llm.slop.liquidlsd.ui.browser.QueueDrop.Queue
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class QueueDropTest {
    @AfterEach
    fun clear() {
        PlayQueueManager.clearQueue()
        BgQueueManager.clearQueue()
        FXQueueManager.clearQueue()
        FXBgQueueManager.clearQueue()
        TransitionQueueManager.clearQueue()
    }

    @Test
    fun `each queue takes its own kind of file and playlist only`() {
        val takes = mapOf(
            Queue.AB to listOf("a.lsd", "a.patch", "a.json", "a.lsdplay", "a.playlist"),
            Queue.BG to listOf("a.lsd", "a.patch", "a.json", "a.lsdplay", "a.playlist"),
            Queue.FX_AB to listOf("a.lsdfx", "a.lsdfxchain", "a.lsdfxplay"),
            Queue.FX_BG to listOf("a.lsdfx", "a.lsdfxchain", "a.lsdfxplay"),
            Queue.TRANS to listOf("a.lsdtrans", "a.lsdtransplay", "stock/wipe.fs", "wipe")
        )
        val all = takes.values.flatten().toSet()
        for ((queue, own) in takes) {
            for (name in all) assertEquals(name in own, QueueDrop.fits(queue, File(name)), "$queue <- $name")
        }
        assertFalse(QueueDrop.fits(Queue.AB, File("notes.txt")))
    }

    @Test
    fun `a mixed drag keeps the queue's files in order and counts the rest`() {
        val sorted = QueueDrop.sort(Queue.FX_AB, " /l/a.lsdfx\n/l/s.lsd\n\n/l/c.lsdfxchain\n/l/t.lsdtrans ")
        assertEquals(listOf("/l/a.lsdfx", "/l/c.lsdfxchain"), sorted.files.map { it.path })
        assertEquals(2, sorted.skipped)
    }

    @Test
    fun `a drop lands at the line, skips other kinds with a toast, and appends when dropped below the list`() {
        listOf("p1", "p2").forEach { PlayQueueManager.appendToQueue(File("/l/$it.lsd")) }
        assertEquals(2, QueueDrop.insert(Queue.AB, 1, "/l/n1.lsd\n/l/fx.lsdfx\n/l/n2.lsd", null))
        assertEquals(listOf("p1", "n1", "n2", "p2"), PlayQueueManager.queue.map { it.nameWithoutExtension })
        assertEquals("1 skipped: the A/B queue takes sources", ToastOverlay.active())
        assertTrue(QueueDrop.flashAmount(Queue.AB) > 0f)

        assertEquals(1, QueueDrop.insert(Queue.AB, null, "/l/end.lsd", null))
        assertEquals("end", PlayQueueManager.queue.last().nameWithoutExtension)
    }

    @Test
    fun `every queue inserts in place`() {
        for ((queue, ext) in listOf(Queue.BG to "lsd", Queue.FX_AB to "lsdfx", Queue.FX_BG to "lsdfx", Queue.TRANS to "lsdtrans")) {
            QueueDrop.insert(queue, null, "/l/a.$ext\n/l/b.$ext", null)
            QueueDrop.insert(queue, 1, "/l/x.$ext\n/l/y.$ext", null)
            val names = when (queue) {
                Queue.BG -> BgQueueManager.queue
                Queue.FX_AB -> FXQueueManager.queue
                Queue.FX_BG -> FXBgQueueManager.queue
                else -> TransitionQueueManager.queue
            }.map { it.nameWithoutExtension }
            assertEquals(listOf("a", "x", "y", "b"), names, "$queue")
        }
    }

    @Test
    fun `a dropped playlist goes in as its items`(@TempDir dir: File) {
        val a = File(dir, "a.lsdfx").apply { writeText("{}") }
        val b = File(dir, "b.lsdfx").apply { writeText("{}") }
        val list = File(dir, "set.lsdfxplay").apply { writeText("${a.absolutePath}\n${b.absolutePath}\n") }
        FXQueueManager.appendToQueue(File(dir, "first.lsdfx"))
        assertEquals(2, QueueDrop.insert(Queue.FX_AB, 0, list.absolutePath, null))
        assertEquals(listOf("a", "b", "first"), FXQueueManager.queue.map { it.nameWithoutExtension })
    }

    @Test
    fun `the edge bands scroll toward their edge, faster nearer it, and the middle doesn't`() {
        assertEquals(0f, QueueDrop.edgeScrollStep(150f, 100f, 400f, 1f))
        assertTrue(QueueDrop.edgeScrollStep(101f, 100f, 400f, 1f) < QueueDrop.edgeScrollStep(120f, 100f, 400f, 1f))
        assertTrue(QueueDrop.edgeScrollStep(120f, 100f, 400f, 1f) < 0f)
        assertTrue(QueueDrop.edgeScrollStep(399f, 100f, 400f, 1f) > QueueDrop.edgeScrollStep(380f, 100f, 400f, 1f))
        assertTrue(QueueDrop.edgeScrollStep(380f, 100f, 400f, 1f) > 0f)
        assertEquals(0f, QueueDrop.edgeScrollStep(450f, 100f, 400f, 1f), "outside the list")
    }

    @Test
    fun `the flash fades out`() {
        QueueDrop.flash(Queue.TRANS)
        val t = llm.slop.liquidlsd.utils.TimeSource.getEpochMillis()
        assertTrue(QueueDrop.flashAmount(Queue.TRANS, t + 100) > QueueDrop.flashAmount(Queue.TRANS, t + 500))
        assertEquals(0f, QueueDrop.flashAmount(Queue.TRANS, t + 5000))
    }
}
