package llm.slop.spirals.ui

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import java.io.File

class FileSystemManagerTest {

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
}
