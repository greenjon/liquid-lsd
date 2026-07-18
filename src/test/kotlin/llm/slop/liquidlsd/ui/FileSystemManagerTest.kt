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
    fun testManagedAssetPathAllowsPatchAndPlaylistRootsOnly() {
        assertTrue(FileSystemManager.isManagedAssetPath(File("presets/patches/test.lsd")))
        assertTrue(FileSystemManager.isManagedAssetPath(File("presets/playlists/test.lsdset")))
        assertFalse(FileSystemManager.isManagedAssetPath(File("presets/midi/test.json")))
        assertFalse(FileSystemManager.isManagedAssetPath(File("build/outside.lsd")))
    }

    @Test
    fun testRenameRejectsTargetOutsideManagedRoots() {
        val patchDir = FileSystemManager.getPatchesRoot()
        val source = File(patchDir, "rename-escape-test.lsd").apply { writeText("{}") }

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
        val patchDir = FileSystemManager.getPatchesRoot()
        val source = File(patchDir, "delete-test.lsd").apply { writeText("{}") }
        val result = FileSystemManager.deleteFile(source.absolutePath)
        assertTrue(result.isSuccess)
        assertFalse(source.exists())
    }
}
