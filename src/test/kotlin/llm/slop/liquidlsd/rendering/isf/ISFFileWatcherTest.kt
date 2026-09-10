package llm.slop.liquidlsd.rendering.isf

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.assertTrue

class ISFFileWatcherTest {

    @TempDir
    lateinit var tempDir: File

    @Test
    fun testFileWatcherTriggersReloadOnModification() {
        val latch = CountDownLatch(1)
        val watcher = ISFFileWatcher(debounceMillis = 100L) {
            latch.countDown()
        }

        watcher.watchDirectories(listOf(tempDir))

        // Create a shader file
        val shaderFile = File(tempDir, "watch_test.fs")
        shaderFile.writeText("/*{ \"DESCRIPTION\": \"Watch Test\" }*/\nvoid main() {}")

        val triggered = latch.await(3, TimeUnit.SECONDS)
        watcher.stop()

        assertTrue(triggered, "File watcher failed to trigger reload callback upon file creation/modification.")
    }
}
