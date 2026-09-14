package llm.slop.liquidlsd.rendering

import kotlinx.serialization.json.Json
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class VisualSourceManifestTest {

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    @Test
    fun testAllSourceManifestsAreValid() {
        val sourcesDir = File("library/sources")
        assertTrue(sourcesDir.exists() && sourcesDir.isDirectory, "library/sources must exist")

        val folders = sourcesDir.listFiles { file -> file.isDirectory } ?: emptyArray()
        assertTrue(folders.isNotEmpty(), "There should be at least one visual source folder")

        val discoveredIds = mutableSetOf<String>()

        for (folder in folders) {
            val metaFile = File(folder, "meta.json")
            val fragFile = File(folder, "shader.frag")
            val isfFile = folder.listFiles { it.isFile && (it.extension == "fs" || it.extension == "isf" || it.extension == "frag") }?.firstOrNull()

            if (metaFile.exists()) {
                assertTrue(fragFile.exists(), "Source folder '${folder.name}' with meta.json missing shader.frag")

                val metaText = metaFile.readText()
                val meta = json.decodeFromString<SourceMeta>(metaText)

                assertEquals(folder.name, meta.id, "Source folder name and meta.id should match")
                assertTrue(meta.name.isNotBlank(), "Source display name must not be blank")
                assertTrue(meta.parameters.isNotEmpty(), "Source should define parameters")

                discoveredIds.add(meta.id)
            } else if (isfFile != null) {
                // ISF format source folder without meta.json
                val rawSource = isfFile.readText()
                val header = llm.slop.liquidlsd.rendering.isf.ISFParser.parseHeader(rawSource)
                assertTrue(header != null, "Source folder '${folder.name}' missing meta.json and valid ISF header in ${isfFile.name}")

                val displayName = header.DESCRIPTION ?: folder.name
                assertTrue(displayName.isNotBlank(), "ISF display name must not be blank")
                assertTrue(header.INPUTS.isNotEmpty(), "ISF source '${folder.name}' should define inputs")

                discoveredIds.add(folder.name)
            } else {
                assertTrue(false, "Source folder '${folder.name}' missing both meta.json and ISF shader file")
            }
        }

        assertTrue(discoveredIds.contains("mandala"), "mandala visual source must be discovered")
    }

    @Test
    fun testMandalaSourceHasNoBgParams() {
        val metaFile = File("library/sources/mandala/meta.json")
        assertTrue(metaFile.exists(), "mandala/meta.json must exist")

        val meta = json.decodeFromString<SourceMeta>(metaFile.readText())
        assertEquals("mandala", meta.id)

        val paramNames = meta.parameters.map { it.name }.toSet()
        val legacyBgParams = listOf(
            "Bg Style", "Bg Feedback", "Bg Hue", "Bg Sat", "Bg Val", "Bg Sweep", "Bg Speed", "Bg Zoom"
        )
        for (bgParam in legacyBgParams) {
            assertTrue(!paramNames.contains(bgParam), "Mandala should not contain legacy background parameter: $bgParam")
        }
    }

    @Test
    fun testEnsureDefaultSourcesExtractsBundledDefaults() {
        val tempDir = java.nio.file.Files.createTempDirectory("test_sources").toFile()
        try {
            VisualSourceRegistry.ensureDefaultSources(tempDir)
            val mandalaMeta = File(tempDir, "mandala/meta.json")
            val mandalaFrag = File(tempDir, "mandala/shader.frag")
            assertTrue(mandalaMeta.exists(), "ensureDefaultSources should extract mandala/meta.json")
            assertTrue(mandalaFrag.exists(), "ensureDefaultSources should extract mandala/shader.frag")
            assertTrue(mandalaMeta.length() > 0, "Extracted meta.json should not be empty")
            assertTrue(mandalaFrag.length() > 0, "Extracted shader.frag should not be empty")
        } finally {
            tempDir.deleteRecursively()
        }
    }
}




