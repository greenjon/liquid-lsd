package llm.slop.liquidlsd.ui.browser

import imgui.ImGui
import imgui.flag.ImGuiCol
import kotlinx.serialization.json.Json
import llm.slop.liquidlsd.models.FXPlaylistDto
import llm.slop.liquidlsd.models.TransitionPlaylistDto
import llm.slop.liquidlsd.ui.LibraryPanel
import llm.slop.liquidlsd.ui.PlaylistManager
import mu.KotlinLogging
import java.io.File

/** Pure list edits shared by the token-list playlists (FX, transitions); unit tested. */
object PlaylistItems {
    /** Moves the item at [from] so it ends up at index [to] (both clamped into range). */
    fun move(items: List<String>, from: Int, to: Int): List<String> {
        if (from !in items.indices) return items
        val list = items.toMutableList()
        val moved = list.removeAt(from)
        list.add(to.coerceIn(0, list.size), moved)
        return list
    }

    fun insert(items: List<String>, tokens: List<String>, at: Int): List<String> =
        items.toMutableList().also { it.addAll(at.coerceIn(0, it.size), tokens) }

    fun remove(items: List<String>, indices: Collection<Int>): List<String> {
        val drop = indices.toSet()
        return items.filterIndexed { i, _ -> i !in drop }
    }

    /** The playlist token for a dragged file path: a stock transition's bundled `.fs` becomes its shader id, anything else is kept. */
    fun tokenForPayload(kind: BrowseKind, path: String): String =
        if (kind == BrowseKind.TRANS && path.endsWith(".fs", ignoreCase = true)) File(path).nameWithoutExtension else path
}

/** What the list needs to edit one playlist file, whatever its storage format. */
interface PlaylistEdit {
    val size: Int
    fun move(from: Int, to: Int)

    /** Inserts the dragged paths (one per entry) at [at]. */
    fun insertPaths(paths: List<String>, at: Int)
    fun remove(indices: Collection<Int>)
}

/** `.lsdplay` playlists, edited through [PlaylistManager] (which saves on every change). */
class PresetPlaylistEdit(private val playlist: PlaylistManager.Playlist) : PlaylistEdit {
    override val size get() = playlist.presets.size
    override fun move(from: Int, to: Int) { PlaylistManager.movePreset(playlist, from, to) }
    override fun insertPaths(paths: List<String>, at: Int) {
        var slot = at
        for (path in paths) {
            if (File(path).extension == "lsdplay") {
                PlaylistManager.unpackPlaylistInto(playlist, path, slot)
            } else {
                PlaylistManager.insertPreset(playlist, path, slot)
                slot++
            }
        }
    }
    override fun remove(indices: Collection<Int>) { indices.sortedDescending().forEach { PlaylistManager.removePreset(playlist, it) } }
}

/** `.lsdfxplay` / `.lsdtransplay` playlists: a JSON file holding a token list, rewritten on every change. */
class TokenPlaylistEdit(private val kind: BrowseKind, private val file: File, private var items: List<String>) : PlaylistEdit {
    override val size get() = items.size
    override fun move(from: Int, to: Int) = save(PlaylistItems.move(items, from, to))
    override fun insertPaths(paths: List<String>, at: Int) = save(PlaylistItems.insert(items, paths.map { PlaylistItems.tokenForPayload(kind, it) }, at))
    override fun remove(indices: Collection<Int>) = save(PlaylistItems.remove(items, indices))

    private fun save(updated: List<String>) {
        try {
            val text = when (kind) {
                BrowseKind.FX -> json.encodeToString(FXPlaylistDto.serializer(), json.decodeFromString<FXPlaylistDto>(file.readText()).copy(items = updated))
                else -> json.encodeToString(TransitionPlaylistDto.serializer(), json.decodeFromString<TransitionPlaylistDto>(file.readText()).copy(items = updated))
            }
            file.writeText(text)
            items = updated
            LibraryPanel.refreshAssets()
        } catch (e: Exception) {
            logger.error(e) { "Failed to save playlist ${file.name}" }
        }
    }

    companion object {
        private val logger = KotlinLogging.logger {}
        private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

        fun open(kind: BrowseKind, file: File): TokenPlaylistEdit? = try {
            val items = when (kind) {
                BrowseKind.FX -> json.decodeFromString<FXPlaylistDto>(file.readText()).items
                else -> json.decodeFromString<TransitionPlaylistDto>(file.readText()).items
            }
            TokenPlaylistEdit(kind, file, items)
        } catch (e: Exception) {
            null
        }
    }
}

const val PAYLOAD_PLAYLIST_ITEM = "PLAYLIST_PATCH_ITEM"

/**
 * Makes the rows of a list renderer a playlist's reorderable contents. [rowToIndex] maps a visible row to its playlist position
 * (rows are the playlist minus missing items). Rows accept drops of other rows (move) and of library assets (insert) and draw an insertion line.
 */
class PlaylistRows(val edit: PlaylistEdit, private val rowToIndex: List<Int>, private val reorderEnabled: Boolean) {
    private var insertLineY = -1f
    private var moveFrom = -1
    private var moveTo = -1

    fun indexOfRow(row: Int): Int = rowToIndex[row]

    fun dropTarget(row: Int) {
        if (!reorderEnabled) return
        val idx = rowToIndex[row]
        val minY = ImGui.getItemRectMinY()
        val maxY = ImGui.getItemRectMaxY()
        ImGui.pushStyleColor(ImGuiCol.DragDropTarget, 0f, 0f, 0f, 0f)
        if (ImGui.beginDragDropTarget()) {
            val before = ImGui.getMousePosY() < (minY + maxY) * 0.5f
            val slot = if (before) idx else idx + 1
            insertLineY = if (before) minY else maxY
            ImGui.acceptDragDropPayload<Int>(PAYLOAD_PLAYLIST_ITEM)?.let { from ->
                moveFrom = from
                moveTo = (if (from < slot) slot - 1 else slot).coerceIn(0, edit.size - 1)
            }
            ImGui.acceptDragDropPayload<String>("ASSET_ITEM")?.let { insertPayload(it, slot) }
            ImGui.endDragDropTarget()
        }
        ImGui.popStyleColor()
    }

    /** Draws the insertion line, the append-at-end drop zone, and applies a pending move. Call after the last row. */
    fun finish() {
        if (insertLineY > 0f) {
            val dl = ImGui.getWindowDrawList()
            val color = (255 shl 24) or (204 shl 16) or (255 shl 8) or 102
            val x0 = ImGui.getWindowPosX() + 4f
            val x1 = ImGui.getWindowPosX() + ImGui.getWindowWidth() - 4f
            dl.addCircleFilled(x0 + 2f, insertLineY, 3f, color)
            dl.addLine(x0 + 5f, insertLineY, x1, insertLineY, color, 2f)
        }
        if (reorderEnabled) {
            ImGui.dummy(ImGui.getContentRegionAvailX(), ImGui.getContentRegionAvailY().coerceAtLeast(30f))
            ImGui.pushStyleColor(ImGuiCol.DragDropTarget, 0f, 0f, 0f, 0f)
            if (ImGui.beginDragDropTarget()) {
                ImGui.acceptDragDropPayload<String>("ASSET_ITEM")?.let { insertPayload(it, edit.size) }
                ImGui.acceptDragDropPayload<Int>(PAYLOAD_PLAYLIST_ITEM)?.let { from ->
                    moveFrom = from
                    moveTo = edit.size - 1
                }
                ImGui.endDragDropTarget()
            }
            ImGui.popStyleColor()
        }
        if (moveFrom != -1 && moveTo != -1 && moveFrom != moveTo) edit.move(moveFrom, moveTo)
    }

    private fun insertPayload(payload: String, slot: Int) =
        edit.insertPaths(payload.lines().map { it.trim() }.filter { it.isNotBlank() }, slot)
}
