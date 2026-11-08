package llm.slop.liquidlsd.utils

import java.io.File
import java.nio.file.AccessDeniedException
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class AtomicFileTest {
    private fun tempDir(): File = Files.createTempDirectory("atomic").toFile().apply { deleteOnExit() }

    @Test
    fun `replaces the file and leaves no temp file behind`() {
        val dir = tempDir()
        val f = File(dir, "my preset.lsd").apply { writeText("old") }
        AtomicFile.write(f, "new")
        assertEquals("new", f.readText())
        assertEquals(listOf("my preset.lsd"), dir.list()!!.toList())
    }

    @Test
    fun `creates missing folders`() {
        val f = File(tempDir(), "a/b/c.json")
        AtomicFile.write(f, "{}")
        assertEquals("{}", f.readText())
    }

    @Test
    fun `a read-only file is refused and kept`() {
        val dir = tempDir()
        val f = File(dir, "locked.lsdfxchain").apply { writeText("keep me"); setWritable(false) }
        try {
            assertFailsWith<AccessDeniedException> { AtomicFile.write(f, "overwritten") }
            assertEquals("keep me", f.readText())
            assertTrue(dir.list()!!.none { it.endsWith(".tmp") })
        } finally {
            f.setWritable(true)
        }
    }
}
