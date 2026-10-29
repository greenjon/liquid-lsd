package llm.slop.liquidlsd.ui.browser

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class BrowserPopupHandlerTest {
    @Test
    fun `rename and retag set only name and tags and keep fields this build doesn't know`(@TempDir dir: File) {
        val file = File(dir, "p.lsd").apply {
            writeText("""{"name":"p","visualSourceType":"mandala","fromTheFuture":{"keep":1.50},"tags":["old"]}""")
        }
        BrowserPopupHandler.updatePresetFileMeta(file, "q", listOf("dark", "slow"))

        val obj = Json.parseToJsonElement(file.readText()).jsonObject
        assertEquals("\"q\"", obj["name"].toString())
        assertEquals(listOf("dark", "slow"), LibraryTags.read(file))
        assertEquals(1.5, obj["fromTheFuture"]!!.jsonObject["keep"].toString().toDouble(), "unknown fields survive")
        assertEquals("\"mandala\"", obj["visualSourceType"].toString())
        assertEquals(listOf("p.lsd"), dir.list()!!.toList(), "no temp file is left beside it")
    }
}
