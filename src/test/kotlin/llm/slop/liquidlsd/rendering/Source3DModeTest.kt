package llm.slop.liquidlsd.rendering

import io.mockk.mockk
import kotlinx.serialization.json.Json
import llm.slop.liquidlsd.parameters.ModulatableParameter
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class Source3DModeTest {

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    private val expected3DSources = setOf(
        "icosahedron",
        "icosa-v3",
        "hyper_mesh",
        "icosa_dodeca",
        "chladni",
        "gyroid",
        "hyper_slice"
    )

    private val expected2DSources = setOf(
        "mandala",
        "colors",
        "dynamic_spiral",
        "attractor_feedback"
    )

    @Test
    fun testAllSourcesHaveCorrect3DClassificationInMeta() {
        val sourcesDir = File("library/sources")
        assertTrue(sourcesDir.exists() && sourcesDir.isDirectory, "library/sources must exist")

        val folders = sourcesDir.listFiles { file -> file.isDirectory } ?: emptyArray()
        assertTrue(folders.isNotEmpty(), "There should be visual source folders")

        for (folder in folders) {
            val metaFile = File(folder, "meta.json")
            assertTrue(metaFile.exists(), "Source folder '${folder.name}' missing meta.json")

            val meta = json.decodeFromString<SourceMeta>(metaFile.readText())
            if (expected3DSources.contains(meta.id)) {
                assertTrue(meta.is3D, "Expected 3D source '${meta.id}' to have is3D == true in meta.json")
            } else if (expected2DSources.contains(meta.id)) {
                assertFalse(meta.is3D, "Expected 2D source '${meta.id}' to have is3D == false in meta.json")
            }
        }
    }

    @Test
    fun testAutoDetectionOf3DSourcesFromParameters() {
        // Auto-detection triggers if parameters contain both Rotate X and Rotate Y even if is3D is false in meta
        val paramsWithRotateXY = linkedMapOf(
            "Rotate X" to ModulatableParameter(0f),
            "Rotate Y" to ModulatableParameter(0f),
            "Zoom" to ModulatableParameter(1f)
        )
        val isAuto3D = (paramsWithRotateXY.containsKey("Rotate X") && paramsWithRotateXY.containsKey("Rotate Y"))
        assertTrue(isAuto3D, "Sources with Rotate X and Rotate Y should auto-detect as 3D")

        val paramsWithoutRotateXY = linkedMapOf(
            "Scale" to ModulatableParameter(1f),
            "Speed" to ModulatableParameter(1f)
        )
        val isAuto2D = (paramsWithoutRotateXY.containsKey("Rotate X") && paramsWithoutRotateXY.containsKey("Rotate Y"))
        assertFalse(isAuto2D, "Sources without Rotate X and Rotate Y should not auto-detect as 3D")
    }

    @Test
    fun test3DSourceProperties() {
        val dummy2DSource = DynamicVisualSource(
            id = "test_2d",
            displayName = "Test 2D",
            shader = mockk(relaxed = true),
            parameters = linkedMapOf(),
            is3D = false
        )
        val dummy3DSource = DynamicVisualSource(
            id = "test_3d",
            displayName = "Test 3D",
            shader = mockk(relaxed = true),
            parameters = linkedMapOf(),
            is3D = true
        )

        assertFalse(dummy2DSource.is3D)
        assertTrue(dummy3DSource.is3D)

        val cloned2D = dummy2DSource.clone()
        val cloned3D = dummy3DSource.clone()
        assertFalse(cloned2D.is3D)
        assertTrue(cloned3D.is3D)
    }
}
