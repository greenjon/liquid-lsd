package llm.slop.liquidlsd.presets

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

class SaveFailureReasonTest {
    private fun reasonOf(block: () -> Unit): String = try { block(); "no failure" } catch (e: Exception) { PresetRepository.failureReason(e) }

    @Test
    fun `a read-only preset says so`() {
        val f = File(Files.createTempDirectory("ro").toFile(), "p.lsd").apply { writeText("{}"); setWritable(false) }
        try {
            assertEquals("the file is read-only", reasonOf { llm.slop.liquidlsd.utils.AtomicFile.write(f, "x") })
        } finally { f.setWritable(true) }
    }

    @Test
    fun `reasons drop the path`() {
        assertEquals("No space left on device", PresetRepository.failureReason(java.io.IOException("No space left on device")))
        assertEquals("Permission denied", PresetRepository.failureReason(java.io.FileNotFoundException("/a/b/p.lsd (Permission denied)")))
        assertEquals("permission denied", PresetRepository.failureReason(java.nio.file.AccessDeniedException("/a/b/p.lsd")))
        assertEquals("see log", PresetRepository.failureReason(RuntimeException()))
    }
}
