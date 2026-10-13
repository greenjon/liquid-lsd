package llm.slop.liquidlsd.presets

import kotlinx.serialization.decodeFromString
import llm.slop.liquidlsd.models.DeckPresetDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The curated flames bundled as default presets: every manifest entry exists, and each preset is a readable Fractal Flame. */
class CuratedGardenBundleTest {
    private val loader = CuratedGardenBundleTest::class.java.classLoader

    @Test fun everyManifestEntryExistsAndEveryFlameIsReadable() {
        val entries = loader.getResourceAsStream("default_presets/manifest.txt")!!.bufferedReader().readLines().filter { it.isNotBlank() && !it.startsWith("#") }
        val flames = entries.filter { it.startsWith("garden/curated/") && it.endsWith(".lsd") }
        assertTrue(flames.size > 100, "expected the curated set, got ${flames.size}")
        for (e in entries) assertTrue(loader.getResource("default_presets/$e") != null, "missing resource $e")
        for (f in flames) {
            val dto = PresetManager.json.decodeFromString<DeckPresetDto>(loader.getResourceAsStream("default_presets/$f")!!.bufferedReader().readText())
            assertEquals("fractal_flame", dto.visualSourceType, f)
            assertTrue(loader.getResource("default_presets/" + f.removeSuffix(".lsd") + ".png") != null, "no thumbnail for $f")
        }
    }
}
