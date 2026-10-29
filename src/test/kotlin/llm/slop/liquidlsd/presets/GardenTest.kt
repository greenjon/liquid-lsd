package llm.slop.liquidlsd.presets

import java.nio.file.Files
import llm.slop.liquidlsd.models.toDto
import kotlin.test.Test
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import llm.slop.liquidlsd.models.DeckPresetDto
import llm.slop.liquidlsd.models.ParameterDto
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GardenTest {
    @Test fun lineageTagsRoundTrip() {
        val tags = Garden.lineageTags(3, listOf("flame-0001", "flame-0002"))
        assertEquals(listOf("garden", "gen:3", "parent:flame-0001", "parent:flame-0002"), tags)
        assertEquals(3, Garden.generationOf(tags))
        assertEquals(listOf("flame-0001", "flame-0002"), Garden.parentsOf(tags))
        assertEquals(0, Garden.generationOf(listOf("misc")))
    }

    @Test fun childGenerationIsOnePastTheYoungestParent() {
        assertEquals(1, Garden.childGeneration(emptyList()))
        assertEquals(4, Garden.childGeneration(listOf(listOf("gen:1"), listOf("gen:3"))))
    }

    @Test fun namesAreSluggedAndContinueAfterTheHighestExisting() {
        assertEquals("fractal-flame", Garden.slug("Fractal Flame"))
        val dir = Files.createTempDirectory("garden").toFile()
        try {
            assertEquals("fractal-flame-0001", Garden.nextName(dir, "Fractal Flame"))
            dir.resolve("fractal-flame-0001.lsd").writeText("{}")
            dir.resolve("fractal-flame-0007.lsd").writeText("{}")
            dir.resolve("other-0099.lsd").writeText("{}")
            assertEquals("fractal-flame-0008", Garden.nextName(dir, "Fractal Flame"))
        } finally { dir.deleteRecursively() }
    }

    private fun write(dir: java.io.File, name: String, source: String, zoom: Float, tags: List<String>) {
        val dto = DeckPresetDto(
            name = name, tags = tags, visualSourceType = source,
            parameters = mapOf("Zoom" to ParameterDto(zoom, zoom, zoom, false, emptyList()))
        )
        dir.resolve("$name.lsd").writeText(PresetManager.json.encodeToString(dto))
    }

    @Test fun poolReadsOnlyOffspringOfTheSourceAndSkipsBrokenFiles() {
        val dir = Files.createTempDirectory("garden").toFile()
        try {
            write(dir, "flame-0001", "fractal_flame", 0.7f, Garden.lineageTags(1, emptyList()))
            write(dir, "other-0001", "mandala", 0.1f, emptyList())
            dir.resolve("broken.lsd").writeText("{ not json")
            val pool = Garden.scan(dir).getValue("fractal_flame")
            assertEquals(listOf("flame-0001"), pool.map { it.name })
            assertEquals(0.7f, pool[0].values["Zoom"])
            assertEquals(1, Garden.generationOf(pool[0].tags))
            // a changed file is re-read, a deleted one disappears
            write(dir, "flame-0001", "fractal_flame", 0.9f, emptyList()); dir.resolve("flame-0001.lsd").setLastModified(System.currentTimeMillis() + 5000)
            assertEquals(0.9f, Garden.scan(dir).getValue("fractal_flame")[0].values["Zoom"])
            dir.resolve("flame-0001.lsd").delete()
            assertTrue(Garden.scan(dir)["fractal_flame"].isNullOrEmpty())
        } finally { dir.deleteRecursively() }
    }

    @Test fun poolReadsTheSnapshotThatARefreshBuildsOffThread() {
        val dir = Files.createTempDirectory("garden").toFile()
        try {
            write(dir, "flame-0001", "fractal_flame", 0.7f, emptyList())
            Garden.refreshPool(dir)
            PresetManager.awaitIo()
            assertEquals(listOf("flame-0001"), Garden.pool("fractal_flame").map { it.name })
            // a new file is not read by pool() itself: it shows up after the next scan
            write(dir, "flame-0002", "fractal_flame", 0.5f, emptyList())
            Garden.refreshPool(dir)
            PresetManager.awaitIo()
            assertEquals(listOf("flame-0001", "flame-0002"), Garden.pool("fractal_flame").map { it.name }.sorted())
        } finally {
            dir.deleteRecursively()
            PresetManager.awaitIo(); Garden.refreshPool(); PresetManager.awaitIo()
        }
    }

    @Test fun samplerStrategyFieldIsOptionalAndRoundTrips() {
        val old = """{"name":"x","visualSourceType":"mandala","parameters":{},"feedbackParameters":{}}"""
        assertEquals(null, PresetManager.json.decodeFromString<DeckPresetDto>(old).samplerStrategy)
        val dto = DeckPresetDto(name = "x", visualSourceType = "fractal_flame", parameters = emptyMap(), samplerStrategy = "BREED")
        assertEquals("BREED", PresetManager.json.decodeFromString<DeckPresetDto>(PresetManager.json.encodeToString(dto)).samplerStrategy)
    }

    @Test fun loadingSanitizerKeepsTheCodeBuiltGenome() {
        val params = llm.slop.liquidlsd.rendering.FlameGenome.buildParameters().mapValues { it.value.toDto() }
        val dto = DeckPresetDto(name = "x", visualSourceType = "fractal_flame", parameters = params)
        val (clean, _) = PresetMigrator.sanitizePresetDto(dto)
        assertEquals(params.keys, clean.parameters.keys)
    }

    @Test fun paletteFieldIsOptionalAndRoundTrips() {
        val old = """{"name":"x","visualSourceType":"fractal_flame","parameters":{},"feedbackParameters":{}}"""
        assertEquals(null, PresetManager.json.decodeFromString<DeckPresetDto>(old).palette)
        val dto = DeckPresetDto(name = "x", visualSourceType = "fractal_flame", parameters = emptyMap(), palette = "AQID")
        assertEquals("AQID", PresetManager.json.decodeFromString<DeckPresetDto>(PresetManager.json.encodeToString(dto)).palette)
    }

    @Test fun importingAFlameFileWritesLoadablePresetsAndNeverOverwrites() {
        val dir = Files.createTempDirectory("flames").toFile()
        try {
            val src = dir.resolve("My Flame.flame")
            src.writeText("""<flames><flame name="Alpha One" size="100 100" scale="50"><xform weight="1" coefs="0.5 0 0 0.5 0 0" sinusoidal="1" lazysusan="1"/></flame><flame name="Beta" size="100 100" scale="50"/></flames>""")
            val out = dir.resolve("out")
            val first = FlameImportOps.importNow(src, out)
            assertEquals(listOf("alpha-one.lsd", "beta.lsd"), first.files.map { it.name })
            assertTrue(first.notes.any { "lazysusan" in it })
            val dto = PresetManager.json.decodeFromString<DeckPresetDto>(first.files[0].readText())
            assertEquals("fractal_flame", dto.visualSourceType)
            assertEquals("alpha-one", dto.name)
            assertEquals(1f, llm.slop.liquidlsd.rendering.FlameUnits.real("X1 Weight", dto.parameters.getValue("X1 Weight").baseValue))
            val second = FlameImportOps.importNow(src, out)
            assertEquals(listOf("alpha-one-2.lsd", "beta-2.lsd"), second.files.map { it.name })
            // the browser's entry point returns at once and does the work on the preset IO thread
            val queued = FlameImportOps.importFile(src, out)
            assertTrue(queued.files.isEmpty())
            assertEquals(listOf("alpha-one-3.lsd", "beta-3.lsd"), queued.completion!!.get().files.map { it.name })
        } finally { dir.deleteRecursively() }
    }
}
