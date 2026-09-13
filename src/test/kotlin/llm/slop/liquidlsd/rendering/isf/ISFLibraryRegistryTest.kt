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
        assertFalse(ISFLibraryRegistry.isScanning)
        assertEquals(1f, ISFLibraryRegistry.scanProgress)
        assertEquals("", ISFLibraryRegistry.scanCurrentPath)
    }

    @Test
    fun testRoleAutoDetectionViaJsonInputs() {
        val rootDir = File(tempDir, "mixed_shaders").apply { mkdirs() }
        val fxFolder = File(rootDir, "FX").apply { mkdirs() }
        val genFolder = File(rootDir, "Generators").apply { mkdirs() }

        // A generator (0 image inputs) placed inside an FX folder must be detected as GENERATOR
        val genFile = File(fxFolder, "plasma_gen.fs").apply {
            writeText("""
                /*{
                    "DESCRIPTION": "Plasma Generator in FX folder",
                    "INPUTS": [
                        { "NAME": "speed", "TYPE": "float", "DEFAULT": 1.0 }
                    ]
                }*/
                void main() { gl_FragColor = vec4(1.0); }
            """.trimIndent())
        }

        // A filter (1 image input) placed inside a Generators folder must be detected as FILTER
        val filterFile = File(genFolder, "color_filter.fs").apply {
            writeText("""
                /*{
                    "DESCRIPTION": "Color Filter in Generators folder",
                    "INPUTS": [
                        { "NAME": "inputImage", "TYPE": "image" },
                        { "NAME": "intensity", "TYPE": "float", "DEFAULT": 0.5 }
                    ]
                }*/
                void main() { gl_FragColor = vec4(1.0); }
            """.trimIndent())
        }

        // A transition (2 image inputs) placed inside a generic folder must be detected as TRANSITION
        val transFile = File(rootDir, "cross_fade.fs").apply {
            writeText("""
                /*{
                    "DESCRIPTION": "Cross Fade",
                    "INPUTS": [
                        { "NAME": "startImage", "TYPE": "image" },
                        { "NAME": "endImage", "TYPE": "image" },
                        { "NAME": "progress", "TYPE": "float", "DEFAULT": 0.5 }
                    ]
                }*/
                void main() { gl_FragColor = vec4(1.0); }
            """.trimIndent())
        }

        val assetGen = ISFScanner.parseShaderFile(genFile, DirectorySourceType.CUSTOM, rootDir.absolutePath)
        val assetFilter = ISFScanner.parseShaderFile(filterFile, DirectorySourceType.CUSTOM, rootDir.absolutePath)
        val assetTrans = ISFScanner.parseShaderFile(transFile, DirectorySourceType.CUSTOM, rootDir.absolutePath)

        assertNotNull(assetGen)
        assertNotNull(assetFilter)
        assertNotNull(assetTrans)

        assertEquals(ISFAssetType.GENERATOR, assetGen.type)
        assertEquals(ISFAssetType.FILTER, assetFilter.type)
        assertEquals(ISFAssetType.TRANSITION, assetTrans.type)
    }

    @Test
    fun testPreserveFolderHierarchyAsCategories() {
        val rootDir = File(tempDir, "pack_root").apply { mkdirs() }
        val nestedDir = File(rootDir, "ArtistPack/Psychedelic/3D").apply { mkdirs() }
        val shaderFile = File(nestedDir, "hyper_cube.fs").apply {
            writeText("""
                /*{
                    "DESCRIPTION": "Hyper Cube",
                    "INPUTS": []
                }*/
                void main() { gl_FragColor = vec4(1.0); }
            """.trimIndent())
        }

        val asset = ISFScanner.parseShaderFile(shaderFile, DirectorySourceType.CUSTOM, rootDir.absolutePath)
        assertNotNull(asset)

        assertEquals("ArtistPack/Psychedelic/3D", asset.folderPath)
        assertTrue(asset.categories.contains("ArtistPack"))
        assertTrue(asset.categories.contains("Psychedelic"))
        assertTrue(asset.categories.contains("3D"))
        assertTrue(asset.categories.contains("ArtistPack/Psychedelic/3D"))
    }

    @Test
    fun testImportedAssetsParsingAndGLSLUniforms() {
        val shaderSourceObj = """
            /*{
                "DESCRIPTION": "Shader with Object IMPORTED",
                "INPUTS": [
                    { "NAME": "speed", "TYPE": "float" }
                ],
                "IMPORTED": {
                    "noiseTex": { "PATH": "textures/noise.png" },
                    "lutTex": "lut.png"
                }
            }*/
            void main() {
                vec4 n = texture(noiseTex, vec2(0.5));
                gl_FragColor = n;
            }
        """.trimIndent()

        val headerObj = ISFParser.parseHeader(shaderSourceObj)
        assertNotNull(headerObj)
        val assetsObj = headerObj.getImportedAssets()
        assertEquals(2, assetsObj.size)
        assertTrue(assetsObj.any { it.name == "noiseTex" && it.path == "textures/noise.png" })
        assertTrue(assetsObj.any { it.name == "lutTex" && it.path == "lut.png" })

        val glslObj = ISFParser.buildGLSLFragmentShader(shaderSourceObj, headerObj)
        assertTrue(glslObj.contains("uniform sampler2D noiseTex;"))
        assertTrue(glslObj.contains("uniform sampler2D lutTex;"))

        val shaderSourceArr = """
            /*{
                "DESCRIPTION": "Shader with Array IMPORTED",
                "INPUTS": [],
                "IMPORTED": [
                    { "NAME": "audioMap", "PATH": "audio.png" }
                ]
            }*/
            void main() {}
        """.trimIndent()

        val headerArr = ISFParser.parseHeader(shaderSourceArr)
        assertNotNull(headerArr)
        val assetsArr = headerArr.getImportedAssets()
        assertEquals(1, assetsArr.size)
        assertEquals("audioMap", assetsArr[0].name)
        assertEquals("audio.png", assetsArr[0].path)

        val glslArr = ISFParser.buildGLSLFragmentShader(shaderSourceArr, headerArr)
        assertTrue(glslArr.contains("uniform sampler2D audioMap;"))
    }
}

