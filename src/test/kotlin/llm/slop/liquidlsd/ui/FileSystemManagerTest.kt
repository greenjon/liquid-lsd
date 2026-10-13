package llm.slop.liquidlsd.ui

import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FileSystemManagerTest {
    @Test
    fun testManagedAssetPathAllowsPresetAndPlaylistRootsOnly() {
        assertTrue(FileSystemManager.isManagedAssetPath(File("library/presets/test.lsd")))
        assertTrue(FileSystemManager.isManagedAssetPath(File("library/playlists/test.lsdplay")))
        assertFalse(FileSystemManager.isManagedAssetPath(File("library/midi/test.json")))
        assertFalse(FileSystemManager.isManagedAssetPath(File(createTempDirectory().toFile(), "outside.lsd")))
        // A sibling that merely shares the root's name as a prefix is not inside it.
        val root = FileSystemManager.getPresetsRoot()
        assertFalse(FileSystemManager.isManagedAssetPath(File(root.parentFile, "${root.name}_other/x.lsd")))
        assertFalse(FileSystemManager.isManagedAssetPath(File(root, "../midi/escape.json")))
    }

    @Test
    fun testRenameRefusesNewNameThatEscapesManagedRoots() {
        val presetDir = FileSystemManager.getPresetsRoot()
        val source = File(presetDir, "rename-escape-test.lsd").apply { writeText("{}") }
        try {
            val result = FileSystemManager.renameFile(source.absolutePath, "../../evil")
            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull() is IllegalArgumentException)
            assertTrue(source.exists(), "a refused rename must leave the source where it was")
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

    @Test
    fun testEnsureDefaultLibraryInstallsDefaultsAndRespectsMarker() {
        val marker = File("library/.defaults_installed")
        val existedBefore = marker.exists()
        val originalContent = if (existedBefore) marker.readText() else null

        try {
            // First call with forceRestore = true
            val result = FileSystemManager.ensureDefaultLibrary(forceRestore = true)
            assertTrue(marker.exists())

            // Calling without forceRestore should recognize it was already installed
            val secondResult = FileSystemManager.ensureDefaultLibrary(forceRestore = false)
            assertTrue(secondResult.wasAlreadyInstalled)
            assertEquals(0, secondResult.presetsExtracted)
            assertEquals(0, secondResult.playlistsExtracted)
        } finally {
            if (existedBefore && originalContent != null) {
                marker.writeText(originalContent)
            } else {
                marker.delete()
            }
        }
    }

    @Test
    fun testScanAllFxPlaylistsFlagsMissingItemsInvalid() {
        val fxPresetsRoot = FileSystemManager.getFxPresetsRoot()
        val fxPlaylistsRoot = FileSystemManager.getFxPlaylistsRoot()
        val realFxItem = File(fxPresetsRoot, "fx-playlist-test-item.lsdfx").apply { writeText("{}") }
        val validPlaylist = File(fxPlaylistsRoot, "fx-playlist-test-valid.lsdfxplay").apply {
            writeText("""{"version":1,"name":"valid","items":["${realFxItem.name}"]}""")
        }
        val brokenPlaylist = File(fxPlaylistsRoot, "fx-playlist-test-broken.lsdfxplay").apply {
            writeText("""{"version":1,"name":"broken","items":["does-not-exist.lsdfx"]}""")
        }

        try {
            val items = FileSystemManager.scanAllFxPlaylists()
            val validEntry = items.first { it.name == validPlaylist.nameWithoutExtension }
            val brokenEntry = items.first { it.name == brokenPlaylist.nameWithoutExtension }

            assertTrue(validEntry.isValid, "Playlist referencing an existing FX item should be valid")
            assertFalse(brokenEntry.isValid, "Playlist referencing a missing FX item should be invalid")
            assertEquals("Missing 1 item(s)", brokenEntry.errorMessage)
        } finally {
            realFxItem.delete()
            validPlaylist.delete()
            brokenPlaylist.delete()
            FileSystemManager.clearScanCache()
        }
    }

    @Test
    fun testCreateDirectoryRefusesNameThatEscapesParent() {
        val parent = FileSystemManager.getPresetsRoot()
        val escaped = File(parent.parentFile, "escape-test-dir")
        try {
            val result = FileSystemManager.createDirectory(parent.absolutePath, "../escape-test-dir")
            assertTrue(result.isFailure)
            assertFalse(escaped.exists())
        } finally {
            escaped.delete()
        }
    }

    @Test
    fun testScanDirectorySeesNewFilesAfterCacheClear() {
        val directory = createTempDirectory().toFile()
        File(directory, "one.lsd").writeText("{}")
        assertEquals(1, FileSystemManager.scanDirectory(directory).size)

        File(directory, "two.lsd").writeText("{}")
        FileSystemManager.clearScanCache()
        assertEquals(2, FileSystemManager.scanDirectory(directory).size)
    }

    @Test
    fun testScanDirectoryOfMissingPathIsEmpty() {
        assertTrue(FileSystemManager.scanDirectory(File(createTempDirectory().toFile(), "nope")).isEmpty())
    }
}
