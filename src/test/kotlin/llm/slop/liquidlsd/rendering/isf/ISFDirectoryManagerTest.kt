package llm.slop.liquidlsd.rendering.isf

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.*

class ISFDirectoryManagerTest {

    @TempDir
    lateinit var tempDir: File

    private lateinit var tempConfigFile: File

    @BeforeEach
    fun setUp() {
        tempConfigFile = File(tempDir, "isf_directories.json")
        ISFDirectoryManager.setConfigFile(tempConfigFile)
    }

    @AfterEach
    fun tearDown() {
        if (tempConfigFile.exists()) {
            tempConfigFile.delete()
        }
    }

    @Test
    fun testPathExpansionHome() {
        val expanded = ISFDirectoryManager.expandPath("~/Library/Graphics/ISF", userHome = "/home/testuser")
        assertEquals(File("/home/testuser/Library/Graphics/ISF").canonicalPath, expanded)
    }

    @Test
    fun testPathExpansionWindowsEnv() {
        val envMap = mapOf("ProgramData" to "C:\\ProgramData", "LOCALAPPDATA" to "C:\\Users\\test\\AppData\\Local")
        val expandedProgramData = ISFDirectoryManager.expandPath("%ProgramData%\\ISF", envLookup = { envMap[it] })
        assertEquals(File("C:\\ProgramData\\ISF").canonicalPath, expandedProgramData)

        val expandedLocalAppData = ISFDirectoryManager.expandPath("%LOCALAPPDATA%\\ISF", envLookup = { envMap[it] })
        assertEquals(File("C:\\Users\\test\\AppData\\Local\\ISF").canonicalPath, expandedLocalAppData)
    }

    @Test
    fun testPathExpansionUnixEnv() {
        val envMap = mapOf("XDG_DATA_HOME" to "/home/testuser/.local/share", "CUSTOM_DIR" to "/mnt/shaders")
        val expandedXdg = ISFDirectoryManager.expandPath("\$XDG_DATA_HOME/isf", userHome = "/home/testuser", envLookup = { envMap[it] })
        assertEquals(File("/home/testuser/.local/share/isf").canonicalPath, expandedXdg)

        val expandedCustom = ISFDirectoryManager.expandPath("\$CUSTOM_DIR/packs", userHome = "/home/testuser", envLookup = { envMap[it] })
        assertEquals(File("/mnt/shaders/packs").canonicalPath, expandedCustom)
    }

    @Test
    fun testPlatformDefaultSourcesMac() {
        val defaults = ISFDirectoryManager.getPlatformDefaultSources(
            osName = "Mac OS X",
            userHome = "/Users/macuser",
            envLookup = { null }
        )
        assertTrue(defaults.any { it.type == DirectorySourceType.BUILT_IN && it.path == "library/sources" })
        assertTrue(defaults.any { it.type == DirectorySourceType.SYSTEM_STANDARD && it.path == "/Library/Graphics/ISF/" })
        assertTrue(defaults.any { it.type == DirectorySourceType.USER_STANDARD && it.path == "~/Library/Graphics/ISF/" })
    }

    @Test
    fun testPlatformDefaultSourcesWindows() {
        val defaults = ISFDirectoryManager.getPlatformDefaultSources(
            osName = "Windows 11",
            userHome = "C:\\Users\\winuser",
            envLookup = { null }
        )
        assertTrue(defaults.any { it.type == DirectorySourceType.BUILT_IN })
        assertTrue(defaults.any { it.type == DirectorySourceType.SYSTEM_STANDARD && it.path.contains("ProgramData") })
        assertTrue(defaults.any { it.type == DirectorySourceType.USER_STANDARD && it.path.contains("LOCALAPPDATA") })
    }

    @Test
    fun testPlatformDefaultSourcesLinux() {
        val defaults = ISFDirectoryManager.getPlatformDefaultSources(
            osName = "Linux",
            userHome = "/home/linuxuser",
            envLookup = { null }
        )
        assertTrue(defaults.any { it.type == DirectorySourceType.BUILT_IN })
        assertTrue(defaults.any { it.type == DirectorySourceType.SYSTEM_STANDARD && it.path == "/usr/share/isf/" })
        assertTrue(defaults.any { it.type == DirectorySourceType.USER_STANDARD && it.path.contains(".local/share") })
    }

    @Test
    fun testEvaluateStatusActiveMissingUnreadable() {
        val activeFolder = File(tempDir, "active_isf").apply { mkdirs() }
        val missingFolder = File(tempDir, "missing_isf")
        val fileAsDir = File(tempDir, "regular_file.txt").apply { writeText("not a dir") }

        val activeConfig = DirectorySourceConfig(activeFolder.absolutePath, DirectorySourceType.CUSTOM)
        val missingConfig = DirectorySourceConfig(missingFolder.absolutePath, DirectorySourceType.CUSTOM)
        val unreadableConfig = DirectorySourceConfig(fileAsDir.absolutePath, DirectorySourceType.CUSTOM)

        assertEquals(DirectoryStatus.ACTIVE, ISFDirectoryManager.evaluateStatus(activeConfig))
        assertEquals(DirectoryStatus.MISSING, ISFDirectoryManager.evaluateStatus(missingConfig))
        assertEquals(DirectoryStatus.UNREADABLE, ISFDirectoryManager.evaluateStatus(unreadableConfig))
    }

    @Test
    fun testPersistenceAndLifecycle() {
        val sources = ISFDirectoryManager.loadSettings(osName = "Linux", userHome = "/home/test")
        assertTrue(sources.isNotEmpty())

        val customPath = File(tempDir, "my_custom_shaders").absolutePath
        val added = ISFDirectoryManager.addCustomDirectory(customPath)
        assertTrue(added)

        val registered = ISFDirectoryManager.getRegisteredDirectories()
        assertTrue(registered.any { it.path == customPath && it.type == DirectorySourceType.CUSTOM })

        // Toggle enabled
        val toggled = ISFDirectoryManager.toggleDirectoryEnabled(customPath, false)
        assertTrue(toggled)
        val updated = ISFDirectoryManager.getRegisteredDirectories().find { it.path == customPath }
        assertNotNull(updated)
        assertFalse(updated.isEnabled)

        // Prevent removing built-in source
        val builtinPath = "library/sources"
        val removedBuiltin = ISFDirectoryManager.removeDirectory(builtinPath)
        assertFalse(removedBuiltin)

        // Remove custom source
        val removedCustom = ISFDirectoryManager.removeDirectory(customPath)
        assertTrue(removedCustom)
        assertFalse(ISFDirectoryManager.getRegisteredDirectories().any { it.path == customPath })
    }
}
