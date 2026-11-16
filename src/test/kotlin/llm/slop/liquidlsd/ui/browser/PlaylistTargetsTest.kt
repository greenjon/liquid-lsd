package llm.slop.liquidlsd.ui.browser

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import llm.slop.liquidlsd.models.FXPlaylistDto
import llm.slop.liquidlsd.models.TransitionPlaylistDto
import llm.slop.liquidlsd.ui.PlaylistManager
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class PlaylistTargetsTest {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    @Test
    fun `a level lists its playlists then its folders, sorted, skipping hidden folders and other files`(@TempDir dir: File) {
        File(dir, "zeta.lsdfxplay").writeText("{}")
        File(dir, "Alpha.lsdfxplay").writeText("{}")
        File(dir, "notes.txt").writeText("")
        File(dir, "set.lsdplay").writeText("")
        File(dir, "gigs").mkdirs()
        File(dir, "Archive").mkdirs()
        File(dir, ".trash").mkdirs()

        val level = PlaylistTargets.level(dir, "lsdfxplay")
        assertEquals(listOf("Alpha.lsdfxplay", "zeta.lsdfxplay"), level.playlists.map { it.name })
        assertEquals(listOf("Archive", "gigs"), level.folders.map { it.name })
        assertTrue(PlaylistTargets.level(File(dir, "gigs"), "lsdfxplay").isEmpty)
    }

    @Test
    fun `FX adds store the short path a drag stores, after what is there`(@TempDir dir: File) {
        val singles = File(dir, "fx").apply { mkdirs() }
        val pl = File(dir, "set.lsdfxplay").apply { writeText(json.encodeToString(FXPlaylistDto.serializer(), FXPlaylistDto(name = "set", items = listOf("old")))) }

        val added = PlaylistTargets.appendTokens(BrowseKind.FX, pl, listOf(File(singles, "dark/glow.lsdfx").path, "/elsewhere/x.lsdfx"), listOf(singles))

        assertEquals(2, added.getOrThrow())
        assertEquals(listOf("old", "dark/glow.lsdfx", "/elsewhere/x.lsdfx"), json.decodeFromString<FXPlaylistDto>(pl.readText()).items)
        assertEquals("set", json.decodeFromString<FXPlaylistDto>(pl.readText()).name)
    }

    @Test
    fun `an add keeps fields this build doesn't know`(@TempDir dir: File) {
        val pl = File(dir, "set.lsdfxplay").apply {
            writeText("""{"version":3,"name":"set","tags":["x"],"items":["old"],"futureField":{"a":1},"color":"red"}""")
        }

        PlaylistTargets.appendTokens(BrowseKind.FX, pl, listOf("/elsewhere/x.lsdfx"), emptyList()).getOrThrow()

        val obj = json.parseToJsonElement(pl.readText()).jsonObject
        assertEquals("""{"a":1}""", obj["futureField"].toString())
        assertEquals("\"red\"", obj["color"].toString())
        assertEquals("3", obj["version"].toString())
        assertEquals(listOf("old", "/elsewhere/x.lsdfx"), json.decodeFromString<FXPlaylistDto>(pl.readText()).items)
    }

    @Test
    fun `withItems rewrites only the items of a transition playlist`() {
        val text = """{"name":"t","items":["a","b"],"extra":true}"""
        val out = json.parseToJsonElement(PlaylistItems.withItems(BrowseKind.TRANS, text) { it.reversed() }).jsonObject
        assertEquals("true", out["extra"].toString())
        assertEquals("""["b","a"]""", out["items"].toString())
        assertTrue(runCatching { PlaylistItems.withItems(BrowseKind.TRANS, "[1,2]") { it } }.isFailure)
    }

    @Test
    fun `a stock transition goes in by shader id, a saved one by short path`(@TempDir dir: File) {
        val saved = File(dir, "trans").apply { mkdirs() }
        val pl = File(dir, "t.lsdtransplay").apply { writeText(json.encodeToString(TransitionPlaylistDto.serializer(), TransitionPlaylistDto(name = "t"))) }

        PlaylistTargets.appendTokens(BrowseKind.TRANS, pl, listOf("/app/isf/wipe.fs", File(saved, "soft.lsdtrans").path), listOf(saved)).getOrThrow()

        assertEquals(listOf("wipe", "soft.lsdtrans"), json.decodeFromString<TransitionPlaylistDto>(pl.readText()).items)
    }

    @Test
    fun `a broken or read-only playlist fails and is left as it was`(@TempDir dir: File) {
        val broken = File(dir, "b.lsdfxplay").apply { writeText("not json") }
        assertTrue(PlaylistTargets.appendTokens(BrowseKind.FX, broken, listOf("a.lsdfx"), emptyList()).isFailure)
        assertEquals("not json", broken.readText())

        val locked = File(dir, "l.lsdfxplay").apply { writeText(json.encodeToString(FXPlaylistDto.serializer(), FXPlaylistDto(name = "l"))) }
        val before = locked.readText()
        locked.setWritable(false)
        try {
            assertTrue(PlaylistTargets.appendTokens(BrowseKind.FX, locked, listOf("a.lsdfx"), emptyList()).isFailure)
            assertEquals(before, locked.readText())
        } finally {
            locked.setWritable(true)
        }
    }

    @Test
    fun `preset adds append in order and save once`(@TempDir dir: File) {
        val file = File(dir, "set.lsdplay")
        val playlist = PlaylistManager.Playlist("set", file.absolutePath, mutableListOf("first.lsd"))

        assertEquals(2, PlaylistTargets.appendPresets(playlist, listOf("/outside/a.lsd", "/outside/b.lsd")).getOrThrow())

        assertEquals(listOf("first.lsd", "/outside/a.lsd", "/outside/b.lsd"), playlist.presets)
        assertEquals(listOf("first.lsd", "/outside/a.lsd", "/outside/b.lsd"), PlaylistManager.loadPlaylist(file).getOrThrow().presets)
    }

    @Test
    fun `a failed preset save leaves the open playlist unchanged`(@TempDir dir: File) {
        val missingDir = File(dir, "gone/set.lsdplay")
        File(dir, "gone").writeText("a file where the folder should be")
        val playlist = PlaylistManager.Playlist("set", missingDir.absolutePath, mutableListOf("first.lsd"))

        assertTrue(PlaylistTargets.appendPresets(playlist, listOf("/outside/a.lsd")).isFailure)
        assertEquals(listOf("first.lsd"), playlist.presets)
    }
}
