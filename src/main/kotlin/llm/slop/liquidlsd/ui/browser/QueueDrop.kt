package llm.slop.liquidlsd.ui.browser

import imgui.ImGui
import llm.slop.liquidlsd.presets.BgQueueManager
import llm.slop.liquidlsd.presets.FXBgQueueManager
import llm.slop.liquidlsd.presets.FXQueueManager
import llm.slop.liquidlsd.presets.PlayQueueManager
import llm.slop.liquidlsd.presets.TransitionQueueManager
import llm.slop.liquidlsd.ui.ParametersState
import llm.slop.liquidlsd.ui.ToastOverlay
import llm.slop.liquidlsd.utils.TimeSource
import java.io.File

/**
 * What every queue column does with a drag from the browser: which files it takes, inserting them at the drop line (or the end),
 * dimming while the drag holds nothing it takes, scrolling while the mouse is near its top or bottom edge, and a short border flash
 * when items arrive (also from Q / Shift+Q), so you see where they went.
 */
object QueueDrop {
    enum class Queue(val kind: BrowseKind, val label: String, val reorderPayload: String) {
        AB(BrowseKind.SRC, "A/B queue", "QUEUE_ITEM"),
        BG(BrowseKind.SRC, "BG queue", "BG_QUEUE_ITEM"),
        FX_AB(BrowseKind.FX, "A/B FX queue", "FX_QUEUE_ITEM"),
        FX_BG(BrowseKind.FX, "BG FX queue", "FX_BG_QUEUE_ITEM"),
        TRANS(BrowseKind.TRANS, "Transition queue", "TRANS_QUEUE_ITEM")
    }

    const val PAYLOAD_ASSET = "ASSET_ITEM"

    /** The kind a dropped file is, beyond [LibraryRefs.kindOf]: older source extensions, and stock transitions (their `.fs`, or a bare id). */
    internal fun kindOf(file: File): BrowseKind? = LibraryRefs.kindOf(file) ?: LibraryRefs.playlistKindOf(file) ?: when (file.extension.lowercase()) {
        "patch", "json", "playlist" -> BrowseKind.SRC
        "fs", "" -> BrowseKind.TRANS
        else -> null
    }

    fun fits(queue: Queue, file: File): Boolean = kindOf(file) == queue.kind

    class Sorted(val files: List<File>, val skipped: Int)

    /** The payload's files that [queue] takes, in order (playlists not yet expanded), and how many it doesn't. */
    fun sort(queue: Queue, payload: String): Sorted {
        val files = payload.lines().map { it.trim() }.filter { it.isNotEmpty() }.map(::File)
        val (fit, rest) = files.partition { fits(queue, it) }
        return Sorted(fit, rest.size)
    }

    private fun isPlaylist(file: File) = LibraryRefs.playlistKindOf(file) != null || file.extension.equals("playlist", ignoreCase = true)

    /** [file] as the items it puts in [queue]: a playlist its entries, anything else itself. */
    internal fun expandForQueue(queue: Queue, file: File): List<File> {
        if (!isPlaylist(file)) return listOf(file)
        return when (queue) {
            Queue.AB, Queue.BG -> PlayQueueManager.parsePlaylist(file)
            Queue.FX_AB -> FXQueueManager.parsePlaylist(file)
            Queue.FX_BG -> FXBgQueueManager.parsePlaylist(file)
            Queue.TRANS -> TransitionQueueManager.parsePlaylist(file)
        }
    }

    fun size(queue: Queue): Int = when (queue) {
        Queue.AB -> PlayQueueManager.queue.size
        Queue.BG -> BgQueueManager.queue.size
        Queue.FX_AB -> FXQueueManager.queue.size
        Queue.FX_BG -> FXBgQueueManager.queue.size
        Queue.TRANS -> TransitionQueueManager.queue.size
    }

    /** Inserts [files] (already expanded) before [index], clamped; past the end appends. */
    internal fun insertFiles(queue: Queue, index: Int, files: List<File>) {
        if (files.isEmpty()) return
        when (queue) {
            Queue.AB -> PlayQueueManager.insertAt(index, files)
            Queue.BG -> files.forEachIndexed { i, f -> BgQueueManager.insertAt(index + i, f) }
            Queue.FX_AB -> FXQueueManager.insertAt(index, files)
            Queue.FX_BG -> FXBgQueueManager.insertAt(index, files)
            Queue.TRANS -> TransitionQueueManager.insertAt(index, files)
        }
    }

    /**
     * Drops [payload] (one path per line) into [queue] before [index], or at the end when null. Files of another kind are skipped,
     * with a toast saying so. Returns how many items went in.
     */
    fun insert(queue: Queue, index: Int?, payload: String, state: ParametersState?): Int {
        val sorted = sort(queue, payload)
        val files = sorted.files.flatMap { expandForQueue(queue, it) }
        QueueOps.insert(queue, index ?: size(queue), files, state)
        if (sorted.skipped > 0) {
            val what = when (queue.kind) { BrowseKind.SRC -> "sources"; BrowseKind.FX -> "FX"; BrowseKind.TRANS -> "transitions" }
            ToastOverlay.show("${sorted.skipped} skipped: the ${queue.label} takes $what")
        }
        return files.size
    }

    /**
     * True while a drag is in progress that [queue] can't take: browser rows of another kind only, a stock source or FX (queues
     * hold saved files), or another queue's row. The column dims and has no drop target.
     */
    fun blocked(queue: Queue): Boolean {
        ImGui.getDragDropPayload<String>(PAYLOAD_ASSET)?.let { return sort(queue, it).files.isEmpty() }
        ImGui.getDragDropPayload<String>(BrowserPane.PAYLOAD_PLAYLIST)?.let { return !fits(queue, File(it)) }
        if (ImGui.getDragDropPayload<Any>(PresetListPanel.PAYLOAD_STOCK_SOURCE) != null) return true
        if (ImGui.getDragDropPayload<Any>(llm.slop.liquidlsd.ui.FxSlotCell.PAYLOAD_STOCK_FILTER) != null) return true
        return Queue.entries.any { it != queue && ImGui.getDragDropPayload<Any>(it.reorderPayload) != null }
    }

    /** Begins a drop target on the last item unless [queue] is [blocked]; end it with `ImGui.endDragDropTarget()` when true. */
    fun beginTarget(queue: Queue): Boolean = !blocked(queue) && ImGui.beginDragDropTarget()

    /** Inside a drop target: takes browser rows or a tree playlist dropped there, inserting before [index] (null: at the end). */
    fun acceptBrowserDrop(queue: Queue, index: Int?, state: ParametersState?) {
        ImGui.acceptDragDropPayload<String>(PAYLOAD_ASSET)?.let { insert(queue, index, it, state) }
        ImGui.acceptDragDropPayload<String>(BrowserPane.PAYLOAD_PLAYLIST)?.let { insert(queue, index, it, state) }
    }

    // --- scrolling at the edges while dragging -------------------------------------------------------------------------------

    private const val EDGE_ZONE = 28f
    private const val EDGE_SPEED = 900f // px per second at the very edge

    /** How far to scroll this frame for a mouse at [mouseY] over a list spanning [top]..[bottom]: negative up, 0 outside the edge bands. */
    internal fun edgeScrollStep(mouseY: Float, top: Float, bottom: Float, dt: Float): Float {
        if (mouseY < top || mouseY > bottom) return 0f
        val zone = EDGE_ZONE.coerceAtMost((bottom - top) / 3f)
        return when {
            mouseY < top + zone -> -EDGE_SPEED * dt * (1f - (mouseY - top) / zone)
            mouseY > bottom - zone -> EDGE_SPEED * dt * (1f - (bottom - mouseY) / zone)
            else -> 0f
        }
    }

    /** Call inside a scrolling child: while any drag is in progress and the mouse is near its top or bottom edge, scrolls it. */
    fun edgeScroll() {
        if (ImGui.getDragDropPayload<Any>() == null) return
        val mx = ImGui.getMousePosX()
        val left = ImGui.getWindowPosX()
        if (mx < left || mx > left + ImGui.getWindowWidth()) return
        val top = ImGui.getWindowPosY()
        val step = edgeScrollStep(ImGui.getMousePosY(), top, top + ImGui.getWindowHeight(), ImGui.getIO().deltaTime)
        if (step != 0f) ImGui.setScrollY((ImGui.getScrollY() + step).coerceIn(0f, ImGui.getScrollMaxY()))
    }

    // --- arrival flash -------------------------------------------------------------------------------------------------------

    private const val FLASH_MS = 700L
    private val flashedAt = LongArray(Queue.entries.size)

    fun flash(queue: Queue) {
        flashedAt[queue.ordinal] = TimeSource.getEpochMillis()
    }

    /** 1 right after [flash], fading to 0 over [FLASH_MS]. */
    fun flashAmount(queue: Queue, now: Long = TimeSource.getEpochMillis()): Float {
        val age = now - flashedAt[queue.ordinal]
        return if (age in 0 until FLASH_MS) 1f - age.toFloat() / FLASH_MS else 0f
    }
}
