package llm.slop.liquidlsd.presets

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SessionPresetIndexTest {
    @Test
    fun `one walk finds each deck's preset file, and a name in two folders matches none`(@TempDir dir: File) {
        File(dir, "a.lsd").writeText("{}")
        File(dir, "sub").mkdirs()
        File(dir, "sub/b.lsd").writeText("{}")
        File(dir, "sub/a.lsd").writeText("{}")
        File(dir, "c.json").writeText("{}")
        File(dir, "only.lsd").writeText("{}")

        val index = SessionSerializer.presetFilesByName(dir)

        assertEquals(File(dir, "sub/b.lsd"), SessionSerializer.presetFileNamed("b", index))
        assertEquals(File(dir, "only.lsd"), SessionSerializer.presetFileNamed("only", index))
        assertNull(SessionSerializer.presetFileNamed("a", index), "two presets called a: neither is assumed")
        assertNull(SessionSerializer.presetFileNamed("c", index))
        assertTrue(SessionSerializer.presetFilesByName(File(dir, "missing")).isEmpty())
    }
}
