package llm.slop.liquidlsd.presets

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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
                    assertTrue(it.recipe, "$id.$name: not flagged recipe")
                    assertTrue(kotlin.math.abs(it.depth) <= 1f, "$id.$name: depth ${it.depth} out of range")
                    assertEquals(0f, it.dcOffset, "$id.$name: dcOffset")
                }
            }
        }
    }

    @Test
    fun overlayReplacesRecipeModulatorsAndKeepsOthers() {
        val file = recipeFiles.first()
        val id = file.nameWithoutExtension
        val name = ReactivityRecipes.forSource(id).keys.first()
        val base = ParameterDto(0.3f, 0f, 1f, false, emptyList())
        val once = ReactivityRecipes.overlay(id, mapOf(name to base))
        val twice = ReactivityRecipes.overlay(id, once)
        assertEquals(once[name]!!.modulators.size, twice[name]!!.modulators.size)
        assertTrue(once[name]!!.modulators.all { it.recipe })
        assertEquals(0.3f, once[name]!!.baseValue)
        val user = ParameterDto(0.3f, 0f, 1f, false, listOf(base.modulators.firstOrNull() ?: once[name]!!.modulators.first().copy(recipe = false)))
        val mixed = ReactivityRecipes.overlay(id, mapOf(name to user))[name]!!.modulators
        assertEquals(1, mixed.count { !it.recipe })
    }

    @Test
    fun rejectsDcOffsetRandomAndUnknownSources() {
        fun entries(entry: String) = ReactivityRecipes.parse("""{"params":{"X":{"stack":[$entry]}}}""", "t")["X"]!!.stack
        assertTrue(entries("""{"sourceId":"lfo","depth":0.2,"dcOffset":0.1}""").isEmpty())
        assertTrue(entries("""{"sourceId":"lfo","depth":0.2,"waveform":"RANDOM"}""").isEmpty())
        assertTrue(entries("""{"sourceId":"nope","depth":0.2}""").isEmpty())
        val ok = entries("""{"sourceId":"lfo","depth":0.2,"subdivision":8}""").single()
        assertEquals("BEAT", ok.genUnit)
        assertTrue(ok.bypassed && ok.recipe)
        assertFalse(entries("""{"sourceId":"audio_bass","depth":0.2,"active":true,"attackMs":5,"decayMs":100}""").single().bypassed)
    }
}
