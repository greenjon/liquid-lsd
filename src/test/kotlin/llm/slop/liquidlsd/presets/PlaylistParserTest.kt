package llm.slop.liquidlsd.presets

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlaylistParserTest {
    private val root: File = Files.createTempDirectory("playlist-parser").toFile().apply { deleteOnExit() }

    private fun preset(name: String) = File(root, name).apply { writeText("{}"); deleteOnExit() }

    @Test
    fun jsonItemsKeepOrder() {
        assertEquals(listOf("b", "a"), PlaylistParser.parseItems("""{"name":"x","items":["b","a"]}"""))
    }

    @Test
    fun jsonWithoutItemsIsEmpty() {
        assertTrue(PlaylistParser.parseItems("""  {"name":"x"}""").isEmpty())
    }

    @Test
    fun textLinesSkipBlanksAndComments() {
        assertEquals(listOf("one", "two"), PlaylistParser.parseItems("# header\n\n  one \n#skip\ntwo\n"))
    }

    @Test
    fun bareNameGetsExtension() {
        val file = preset("alpha.lsd")
        assertEquals(file, PlaylistParser.resolveItem("alpha", listOf(root)))
    }

    @Test
    fun existingExtensionIsNotDoubled() {
        val file = preset("beta.lsd")
        assertEquals(file, PlaylistParser.resolveItem("beta.LSD".replace("LSD", "lsd"), listOf(root)))
        assertNull(PlaylistParser.resolveItem("beta.LSD.LSD", listOf(root)))
    }

    @Test
    fun missingNameIsDropped() {
        val file = preset("gamma.lsd")
        assertNull(PlaylistParser.resolveItem("nope", listOf(root)))
        assertEquals(listOf(file), PlaylistParser.resolveItems(listOf("nope", "gamma"), listOf(root)))
    }

    @Test
    fun directPathBeatsRootEntry() {
        val direct = preset("delta.lsd")
        val other = Files.createTempDirectory("playlist-parser-other").toFile().apply { deleteOnExit() }
        File(other, direct.absolutePath.removePrefix("/")).parentFile?.mkdirs()
        assertEquals(direct, PlaylistParser.resolveItem(direct.absolutePath, listOf(other)))
    }
}
