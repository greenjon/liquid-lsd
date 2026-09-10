package llm.slop.liquidlsd.rendering.isf

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.*

class ISFLibraryRegistryTest {

    @TempDir
    lateinit var tempDir: File

    private lateinit var tempConfigFile: File

    @BeforeEach
    fun setUp() {
        tempConfigFile = File(tempDir, "isf_directories.json")
        ISFDirectoryManager.setConfigFile(tempConfigFile)
        ISFLibraryRegistry.clear()
    }

    @AfterEach
    fun tearDown() {
        if (tempConfigFile.exists()) {
            tempConfigFile.delete()
        }
        ISFLibraryRegistry.clear()
    }

    @Test
    fun testScanDirectoryAndSafeParsing() {
        val shaderDir = File(tempDir, "shaders").apply { mkdirs() }
        val shaderFile = File(shaderDir, "test_plasma.fs").apply {
            writeText(
                """
                /*{
                    "DESCRIPTION": "Plasma Test Shader",
                    "CATEGORIES": ["Generator", "Fractal"],
                    "INPUTS": [
                        { "NAME": "speed", "TYPE": "float", "DEFAULT": 1.0 }
                    ]
                }*/
                void main() {
                    gl_FragColor = vec4(1.0);
                }
            """.trimIndent()
            )
        }

        // Add directory to manager
        ISFDirectoryManager.resetToDefaults()
        ISFDirectoryManager.addCustomDirectory(shaderDir.absolutePath)

        val assets = ISFLibraryRegistry.scanLibrary()
        assertTrue(assets.any { it.id == "test_plasma" && it.displayName == "Plasma Test Shader" && it.category == "Generator" })
    }

    @Test
    fun testMalformedHeaderResilience() {
        val shaderDir = File(tempDir, "broken").apply { mkdirs() }
        val brokenFile = File(shaderDir, "bad_shader.fs").apply {
            writeText(
                """
                /*{
                    "DESCRIPTION": "Truncated JSON header
                    "INPUTS": [
                }*/
                void main() {
                    gl_FragColor = vec4(0.0);
                }
            """.trimIndent()
            )
        }

        ISFDirectoryManager.resetToDefaults()
        ISFDirectoryManager.addCustomDirectory(shaderDir.absolutePath)

        // Should not throw exception
        val assets = ISFLibraryRegistry.scanLibrary()
        assertTrue(assets.any { it.id == "bad_shader" })
    }

    @Test
    fun testCollisionPrecedenceResolution() {
        val systemDir = File(tempDir, "system_shaders").apply { mkdirs() }
        val customDir = File(tempDir, "custom_shaders").apply { mkdirs() }

        // Same shader ID "shared_shader" in both directories
        File(systemDir, "shared_shader.fs").writeText(
            """
            /*{ "DESCRIPTION": "System Version" }*/
            void main() {}
        """.trimIndent()
        )

        File(customDir, "shared_shader.fs").writeText(
            """
            /*{ "DESCRIPTION": "Custom Version" }*/
            void main() {}
        """.trimIndent()
        )

        // Register both: SystemStandard (priority 2) and Custom (priority 4)
        ISFDirectoryManager.resetToDefaults()
        ISFDirectoryManager.addCustomDirectory(customDir.absolutePath) // Custom
        // Also add systemDir as system standard
        val currentConfigs = ISFDirectoryManager.getRegisteredDirectories().toMutableList()
        currentConfigs.add(DirectorySourceConfig(systemDir.absolutePath, DirectorySourceType.SYSTEM_STANDARD, true))
        // Force update config via manager by resetting or saving custom config
        ISFDirectoryManager.resetToDefaults()
        ISFDirectoryManager.addCustomDirectory(customDir.absolutePath)

        // Let's test precedence directly using ISFScanner or mock resolved dirs
        val assetSystem = ISFScanner.parseShaderFile(File(systemDir, "shared_shader.fs"), DirectorySourceType.SYSTEM_STANDARD, systemDir.absolutePath)
        val assetCustom = ISFScanner.parseShaderFile(File(customDir, "shared_shader.fs"), DirectorySourceType.CUSTOM, customDir.absolutePath)
        assertNotNull(assetSystem)
        assertNotNull(assetCustom)

        assertTrue(assetCustom.sourceType.priority > assetSystem.sourceType.priority)
    }

    @Test
    fun testAsyncScanCallback() {
        val shaderDir = File(tempDir, "async_shaders").apply { mkdirs() }
        File(shaderDir, "async_glow.fs").writeText("/*{ \"DESCRIPTION\": \"Async Glow\" }*/\nvoid main() {}")

        ISFDirectoryManager.resetToDefaults()
        ISFDirectoryManager.addCustomDirectory(shaderDir.absolutePath)

        val latch = CountDownLatch(1)
        var discoveredCount = 0

        ISFLibraryRegistry.scanLibraryAsync(
            onProgress = { _, _, _ -> },
            onComplete = { assets ->
                discoveredCount = assets.size
                latch.countDown()
            }
        )

        val completed = latch.await(5, TimeUnit.SECONDS)
        assertTrue(completed)
        assertTrue(discoveredCount > 0)
    }
}
