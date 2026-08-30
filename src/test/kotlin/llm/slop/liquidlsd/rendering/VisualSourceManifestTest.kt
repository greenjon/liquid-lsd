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
            "Support H",
            "Color Method",
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

    @Test
    fun testIcosahedronSourceParametersAndDocs() {
        val metaFile = File("library/sources/icosahedron/meta.json")
        assertTrue(metaFile.exists(), "icosahedron/meta.json must exist")

        val meta = json.decodeFromString<SourceMeta>(metaFile.readText())
        assertEquals("icosahedron", meta.id)
        assertEquals("Icosahedron 32-Stellation", meta.name)

        val paramNames = meta.parameters.map { it.name }.toSet()
        val expectedParams = setOf(
            "Control X",
            "Control Y",
            "Color Method",
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
            val doc = SourceDocRegistry.getParamDescription("icosahedron", expected)
            assertTrue(doc.isNotBlank(), "Missing documentation in SourceDocRegistry for icosahedron/$expected")
        }

        val sourceDoc = SourceDocRegistry.getSourceDescription("icosahedron")
        assertTrue(sourceDoc.isNotBlank(), "Missing source description in SourceDocRegistry for icosahedron")
    }

    @Test
    fun testHyperMeshSourceParametersAndDocs() {
        val metaFile = File("library/sources/hyper_mesh/meta.json")
        assertTrue(metaFile.exists(), "hyper_mesh/meta.json must exist")

        val meta = json.decodeFromString<SourceMeta>(metaFile.readText())
        assertEquals("hyper_mesh", meta.id)
        assertEquals("4D Hyper-Mesh", meta.name)

        val paramNames = meta.parameters.map { it.name }.toSet()
        val expectedParams = setOf(
            "Rotate XW",
            "Rotate YW",
            "Rotate ZW",
            "Rotate X",
            "Rotate Y",
            "Rotate Z",
            "Camera Dist",
            "Zoom",
            "Polytope",
            "Projection Mode",
            "Thickness",
            "Node Size",
            "Hue Offset",
            "Hue Spread",
            "Color Mode",
            "Saturation",
            "Brightness",
            "Glow",
            "Opacity",
            "Depth Fog"
        )

        for (expected in expectedParams) {
            assertTrue(paramNames.contains(expected), "Missing expected parameter: $expected")
            val doc = SourceDocRegistry.getParamDescription("hyper_mesh", expected)
            assertTrue(doc.isNotBlank(), "Missing documentation in SourceDocRegistry for hyper_mesh/$expected")
        }

        val sourceDoc = SourceDocRegistry.getSourceDescription("hyper_mesh")
        assertTrue(sourceDoc.isNotBlank(), "Missing source description in SourceDocRegistry for hyper_mesh")
    }

    @Test
    fun testHyperSliceSourceParametersAndDocs() {
        val metaFile = File("library/sources/hyper_slice/meta.json")
        assertTrue(metaFile.exists(), "hyper_slice/meta.json must exist")

        val meta = json.decodeFromString<SourceMeta>(metaFile.readText())
        assertEquals("hyper_slice", meta.id)
        assertEquals("4D Hyper-Slice", meta.name)

        val paramNames = meta.parameters.map { it.name }.toSet()
        val expectedParams = setOf(
            "Slice Offset",
            "Rotate XW",
            "Rotate YW",
            "Rotate ZW",
            "Rotate X",
            "Rotate Y",
            "Rotate Z",
            "Morph",
            "Support H",
            "Zoom",
            "Color Method",
            "Hue Offset",
            "Saturation",
            "Brightness",
            "Opacity",
            "Edge Thickness",
            "Edge Brightness",
            "Glow"
        )

        for (expected in expectedParams) {
            assertTrue(paramNames.contains(expected), "Missing expected parameter: $expected")
            val doc = SourceDocRegistry.getParamDescription("hyper_slice", expected)
            assertTrue(doc.isNotBlank(), "Missing documentation in SourceDocRegistry for hyper_slice/$expected")
        }

        val sourceDoc = SourceDocRegistry.getSourceDescription("hyper_slice")
        assertTrue(sourceDoc.isNotBlank(), "Missing source description in SourceDocRegistry for hyper_slice")
    }

    @Test
    fun testColorsSourceParametersAndDocs() {
        val metaFile = File("library/sources/colors/meta.json")
        assertTrue(metaFile.exists(), "colors/meta.json must exist")

        val meta = json.decodeFromString<SourceMeta>(metaFile.readText())
        assertEquals("colors", meta.id)
        assertEquals("Colors", meta.name)

        val paramNames = meta.parameters.map { it.name }.toSet()
        val expectedParams = setOf(
            "Style",
            "Hue",
            "Sat",
            "Val",
            "Sweep",
            "Speed",
            "Zoom"
        )

        for (expected in expectedParams) {
            assertTrue(paramNames.contains(expected), "Missing expected parameter: $expected")
            val doc = SourceDocRegistry.getParamDescription("colors", expected)
            assertTrue(doc.isNotBlank(), "Missing documentation in SourceDocRegistry for colors/$expected")
        }

        val sourceDoc = SourceDocRegistry.getSourceDescription("colors")
        assertTrue(sourceDoc.isNotBlank(), "Missing source description in SourceDocRegistry for colors")
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
}



