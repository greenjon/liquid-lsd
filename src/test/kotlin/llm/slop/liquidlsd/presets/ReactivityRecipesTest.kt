package llm.slop.liquidlsd.presets

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import llm.slop.liquidlsd.models.ModulatorDto
import llm.slop.liquidlsd.models.ParameterDto
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReactivityRecipesTest {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val root: File by lazy {
        var dir = File(".").canonicalFile
        while (dir.parentFile != null && !File(dir, "web/sync_manifest.json").exists()) dir = dir.parentFile
        dir
    }
    private val recipeFiles get() = File(root, "src/main/resources/reactivity/sources").listFiles { f -> f.extension == "json" }!!.sortedBy { it.name }

    /** Float input names of a bundled ISF source (the JSON header comment). */
    private fun floatInputs(id: String): Set<String> {
        if (id == "fractal_flame") return llm.slop.liquidlsd.rendering.FlameGenome.buildParameters().keys
        val meta = File(root, "src/main/resources/default_sources/$id/meta.json")
        if (meta.exists()) {
            return json.parseToJsonElement(meta.readText()).jsonObject["parameters"]!!.jsonArray
                .map { it.jsonObject["name"]!!.jsonPrimitive.content }.toSet()
        }
        val text = File(root, "src/main/resources/default_sources/$id/$id.fs").readText()
        val header = text.substringAfter("/*").substringBefore("*/")
        return json.parseToJsonElement(header).jsonObject["INPUTS"]!!.jsonArray
            .map { it.jsonObject }.filter { it["TYPE"]?.jsonPrimitive?.content.equals("float", true) }
            .map { it["NAME"]!!.jsonPrimitive.content }.toSet()
    }

    @Test
    fun everyShippedSourceRecipeIsValid() {
        assertTrue(recipeFiles.isNotEmpty())
        for (file in recipeFiles) {
            val id = file.nameWithoutExtension
            val raw = json.parseToJsonElement(file.readText()).jsonObject["params"]!!.jsonObject
            val parsed = ReactivityRecipes.parse(file.readText(), id)
            val inputs = floatInputs(id)
            assertEquals(raw.keys, parsed.keys, "$id")
            for ((name, recipe) in parsed) {
                assertTrue(name in inputs, "$id: '$name' is not a float input")
                val rawCount = raw[name]!!.jsonObject["stack"]!!.jsonArray.size
                assertEquals(rawCount, recipe.stack.size, "$id.$name: an entry failed validation")
                assertTrue(recipe.stack.count { !it.bypassed } <= 1, "$id.$name: more than one active modulator")
                assertTrue(recipe.stack.any { !it.bypassed } || recipe.stack.isNotEmpty(), "$id.$name: empty")
                recipe.stack.forEach {
                    assertTrue(kotlin.math.abs(it.depth) <= 1f, "$id.$name: depth ${it.depth} out of range")
                }
            }
        }
    }

    @Test
    fun overlayReplacesTheModulatorsOfRecipeParameters() {
        val file = recipeFiles.first()
        val id = file.nameWithoutExtension
        val name = ReactivityRecipes.forSource(id).keys.first()
        val base = ParameterDto(0.3f, 0f, 1f, false, emptyList())
        val once = ReactivityRecipes.overlay(id, mapOf(name to base))
        val twice = ReactivityRecipes.overlay(id, once)
        assertEquals(once[name]!!.modulators.size, twice[name]!!.modulators.size)
        assertEquals(0.3f, once[name]!!.baseValue)
        val stray = ModulatorDto(sourceId = "audio_high", operator = "ADD", depth = 0.123f)
        val withStray = ParameterDto(0.3f, 0f, 1f, false, listOf(stray))
        val replaced = ReactivityRecipes.overlay(id, mapOf(name to withStray))[name]!!.modulators
        assertEquals(once[name]!!.modulators, replaced)
    }

    @Test
    fun keepsDcOffsetAndRejectsUnknownSources() {
        fun entries(entry: String) = ReactivityRecipes.parse("""{"params":{"X":{"stack":[$entry]}}}""", "t")["X"]!!.stack
        assertEquals(0.1f, entries("""{"sourceId":"lfo","depth":0.2,"dcOffset":0.1}""").single().dcOffset)
        assertEquals("RANDOM", entries("""{"sourceId":"lfo","depth":0.2,"waveform":"RANDOM","hold":0.5}""").single().waveform)
        assertTrue(entries("""{"sourceId":"nope","depth":0.2}""").isEmpty())
        val ok = entries("""{"sourceId":"lfo","depth":0.2,"subdivision":8}""").single()
        assertEquals("BEAT", ok.genUnit)
        assertTrue(ok.bypassed)
        assertFalse(entries("""{"sourceId":"audio_bass","depth":0.2,"active":true,"attackMs":5,"decayMs":100}""").single().bypassed)
    }

    /** (min, default, max) of each parameter of a bundled source. */
    private fun ranges(id: String): Map<String, Triple<Float, Float, Float>> {
        if (id == "fractal_flame") {
            return llm.slop.liquidlsd.rendering.FlameGenome.buildParameters()
                .mapValues { Triple(it.value.minClamp, it.value.baseValue, it.value.maxClamp) }
        }
        val meta = File(root, "src/main/resources/default_sources/$id/meta.json")
        if (meta.exists()) {
            return json.parseToJsonElement(meta.readText()).jsonObject["parameters"]!!.jsonArray.associate {
                val o = it.jsonObject
                o["name"]!!.jsonPrimitive.content to Triple(
                    o["min"]!!.jsonPrimitive.content.toFloat(), o["default"]!!.jsonPrimitive.content.toFloat(), o["max"]!!.jsonPrimitive.content.toFloat())
            }
        }
        val header = File(root, "src/main/resources/default_sources/$id/$id.fs").readText().substringAfter("/*").substringBefore("*/")
        return json.parseToJsonElement(header).jsonObject["INPUTS"]!!.jsonArray.map { it.jsonObject }
            .filter { it["TYPE"]?.jsonPrimitive?.content.equals("float", true) }
            .associate {
                it["NAME"]!!.jsonPrimitive.content to Triple(
                    it["MIN"]!!.jsonPrimitive.content.toFloat(), it["DEFAULT"]!!.jsonPrimitive.content.toFloat(), it["MAX"]!!.jsonPrimitive.content.toFloat())
            }
    }

    /** A bipolar source (LFO, sample-and-hold) swings dcOffset +- depth; that must stay inside the parameter, or the clamp flattens part of every cycle. */
    @Test
    fun lfoSwingsFitTheirParameters() {
        for (file in recipeFiles) {
            val id = file.nameWithoutExtension
            val ranges = ranges(id)
            for ((name, recipe) in ReactivityRecipes.parse(file.readText(), id)) {
                val (min, default, max) = ranges.getValue(name)
                val base = recipe.base ?: default
                val scalar = if (min < 0f) (max - min) / 2f else max - min
                for (m in recipe.stack.filter { it.sourceId == "lfo" }) {
                    val lo = base + (m.dcOffset - kotlin.math.abs(m.depth)) * scalar
                    val hi = base + (m.dcOffset + kotlin.math.abs(m.depth)) * scalar
                    val eps = 1e-4f * (max - min)
                    assertTrue(lo >= min - eps && hi <= max + eps, "$id.$name (${m.waveform}): swings $lo..$hi, outside $min..$max")
                }
            }
        }
    }
}
