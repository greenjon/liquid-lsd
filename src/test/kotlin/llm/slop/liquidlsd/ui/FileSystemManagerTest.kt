package llm.slop.liquidlsd.ui

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import java.io.File
import kotlin.io.path.createTempDirectory

class FileSystemManagerTest {

    @AfterTest
    fun tearDown() {
        FileSystemManager.clearScanCache()
    }

    @Test
    fun testScanDirectoryRefreshesWhenDirectoryContentsChange() {
        val directory = createTempDirectory().toFile()

        assertEquals(emptyList(), FileSystemManager.scanDirectory(directory))

        File(directory, "first.lsd").writeText("{}")
        val firstScan = FileSystemManager.scanDirectory(directory)
        assertEquals(listOf("first"), firstScan.map { it.name })

        File(directory, "second.lsd").writeText("{}")
        val secondScan = FileSystemManager.scanDirectory(directory)
        assertEquals(listOf("first", "second"), secondScan.map { it.name })
    }

    @Test
    fun testManagedAssetPathAllowsPresetAndPlaylistRootsOnly() {
        assertTrue(FileSystemManager.isManagedAssetPath(File("library/presets/test.lsd")))
        assertTrue(FileSystemManager.isManagedAssetPath(File("library/playlists/test.lsdset")))
        assertFalse(FileSystemManager.isManagedAssetPath(File("library/midi/test.json")))
        assertFalse(FileSystemManager.isManagedAssetPath(File("build/outside.lsd")))
    }

    @Test
    fun testRenameRejectsTargetOutsideManagedRoots() {
        val presetDir = FileSystemManager.getPresetsRoot()
        val source = File(presetDir, "rename-escape-test.lsd").apply { writeText("{}") }

        try {
            val result = FileSystemManager.renameFile(source.absolutePath, "../midi/escaped")

            assertTrue(result.isFailure)
            assertTrue(source.exists())
            assertFalse(File("presets/midi/escaped.lsd").exists())
        } finally {
            source.delete()
        }
    }
    @Test
    fun testDeleteRejectsTargetOutsideManagedRoots() {
        val result = FileSystemManager.deleteFile("../../etc/passwd")
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun testDeleteAllowsTargetInsideManagedRoots() {
        val presetDir = FileSystemManager.getPresetsRoot()
        val source = File(presetDir, "delete-test.lsd").apply { writeText("{}") }
        val result = FileSystemManager.deleteFile(source.absolutePath)
        assertTrue(result.isSuccess)
        assertFalse(source.exists())
    }
    @Test
    fun testConcurrentScanDoesNotDuplicateTasks() {
        val directory = createTempDirectory().toFile()
        File(directory, "concurrent1.lsd").writeText("{}")
        File(directory, "concurrent2.lsd").writeText("{}")

        val threads = (1..10).map {
            Thread {
                FileSystemManager.scanDirectory(directory)
            }
        }
        
        threads.forEach { it.start() }
        threads.forEach { it.join() }
        
        // Wait for debounce and execution
        Thread.sleep(200)
        
        val finalScan = FileSystemManager.scanDirectory(directory)
        assertEquals(2, finalScan.size)
    }

    @Test
    fun testAsyncValidationUpdatesState() {
        val directory = createTempDirectory().toFile()
        File(directory, "valid.lsd").writeText("{}")
        
        val initialScan = FileSystemManager.scanDirectory(directory)
        assertEquals(1, initialScan.size)
        assertTrue(initialScan[0].isValid)
        
        Thread.sleep(200)
        
        val secondScan = FileSystemManager.scanDirectory(directory)
        assertEquals(1, secondScan.size)
        assertTrue(secondScan[0].isValid)
    }

    @Test
    fun testDirectorySignatureChangesWhenFileAddedOrRemoved() {
        val directory = createTempDirectory().toFile()
        val initialSig = FileSystemManager.getDirectorySignature(directory)

        val file = File(directory, "new_preset.lsd").apply { writeText("{}") }
        val addedSig = FileSystemManager.getDirectorySignature(directory)
        assertTrue(initialSig != addedSig, "Directory signature should change when file is added")

        file.delete()
        val deletedSig = FileSystemManager.getDirectorySignature(directory)
        assertTrue(addedSig != deletedSig, "Directory signature should change when file is deleted")
    }
}
