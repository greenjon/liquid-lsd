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

            assertTrue(metaFile.exists(), "Source folder '${folder.name}' missing meta.json")
            assertTrue(fragFile.exists(), "Source folder '${folder.name}' missing shader.frag")

            val metaText = metaFile.readText()
            val meta = json.decodeFromString<SourceMeta>(metaText)

            assertEquals(folder.name, meta.id, "Source folder name and meta.id should match")
            assertTrue(meta.name.isNotBlank(), "Source display name must not be blank")
            assertTrue(meta.parameters.isNotEmpty(), "Source should define parameters")

            discoveredIds.add(meta.id)
        }

        assertTrue(discoveredIds.contains("icosa_dodeca"), "icosa_dodeca visual source must be discovered")
    }

    @Test
    fun testIcosaDodecaSourceParametersAndDocs() {
        val metaFile = File("library/sources/icosa_dodeca/meta.json")
        assertTrue(metaFile.exists(), "icosa_dodeca/meta.json must exist")

        val meta = json.decodeFromString<SourceMeta>(metaFile.readText())
        assertEquals("icosa_dodeca", meta.id)
        assertEquals("Icosa-Dodeca", meta.name)

        val paramNames = meta.parameters.map { it.name }.toSet()
        val expectedParams = setOf(
            "Morph",
            "Stellation",
            "Color Method",
            "Depth Frame",
            "Hue Offset",
            "Saturation",
            "Brightness",
            "Opacity",
            "Edge Thickness",
            "Edge Brightness",
            "Zoom",
            "Rotate X",
            "Rotate Y",
            "Rotate Z"
        )

        for (expected in expectedParams) {
            assertTrue(paramNames.contains(expected), "Missing expected parameter: $expected")
            val doc = SourceDocRegistry.getParamDescription("icosa_dodeca", expected)
            assertTrue(doc.isNotBlank(), "Missing documentation in SourceDocRegistry for icosa_dodeca/$expected")
        }

        val sourceDoc = SourceDocRegistry.getSourceDescription("icosa_dodeca")
        assertTrue(sourceDoc.isNotBlank(), "Missing source description in SourceDocRegistry for icosa_dodeca")
    }
}
