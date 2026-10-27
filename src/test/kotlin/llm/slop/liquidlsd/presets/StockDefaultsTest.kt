package llm.slop.liquidlsd.presets

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import llm.slop.liquidlsd.models.ModulatorDto
import llm.slop.liquidlsd.models.ParameterDto
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StockDefaultsTest {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val root: File by lazy {
        var dir = File(".").canonicalFile
        while (dir.parentFile != null && !File(dir, "web/sync_manifest.json").exists()) dir = dir.parentFile
        dir
    }
    private fun files(kind: String) =
        File(root, "src/main/resources/stock_defaults/$kind").listFiles { f -> f.extension == "json" }!!.sortedBy { it.name }
    private val sourceFiles get() = files("sources")

    private fun parse(file: File) = json.parseToJsonElement(file.readText()).jsonObject

    /** The ISF header (the leading comment) of a shader file. */
    private fun isfInputs(file: File): List<JsonObject> =
        json.parseToJsonElement(file.readText().substringAfter("/*").substringBefore("*/")).jsonObject["INPUTS"]!!.jsonArray
            .map { it.jsonObject }.filter { it["TYPE"]?.jsonPrimitive?.content.equals("float", true) }

    /** (min, default, max) of each float parameter of a bundled source. */
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
        return isfInputs(File(root, "src/main/resources/default_sources/$id/$id.fs")).associate {
            it["NAME"]!!.jsonPrimitive.content to Triple(
                it["MIN"]!!.jsonPrimitive.content.toFloat(), it["DEFAULT"]!!.jsonPrimitive.content.toFloat(), it["MAX"]!!.jsonPrimitive.content.toFloat())
        }
    }

    /** Placeholder baseline for every input of source [id], so an overlay can be checked without loading the shader. */
    private fun baseline(id: String) = ranges(id).mapValues { (_, r) -> ParameterDto(r.second, r.first, r.third, false, emptyList()) }

    private fun shippedParams(file: File) = StockDefaults.overlayParameters(parse(file), "parameters", baseline(file.nameWithoutExtension), file.name)

    @Test
    fun everyShippedSourceDefaultIsValid() {
        assertTrue(sourceFiles.isNotEmpty())
        for (file in sourceFiles) {
            val id = file.nameWithoutExtension
            val obj = parse(file)
            assertEquals(id, obj["sourceId"]?.jsonPrimitive?.content, "$id: sourceId")
            val inputs = ranges(id).keys
            val raw = obj["parameters"] as? JsonObject ?: JsonObject(emptyMap())
            val parsed = shippedParams(file)
            assertEquals(raw.keys, parsed.keys, "$id: a parameter is not a float input")
            for ((name, p) in parsed) {
                val rawCount = (raw[name]!!.jsonObject["modulators"] as? JsonArray)?.size ?: 0
                assertEquals(rawCount, p.modulators.size, "$id.$name: a modulator failed validation")
                assertTrue(p.modulators.count { !it.bypassed } <= 1, "$id.$name: more than one active modulator")
                p.modulators.forEach { assertTrue(kotlin.math.abs(it.depth) <= 1f, "$id.$name: depth ${it.depth} out of range") }
            }
            val knobs = (obj["macroBank"] as? JsonObject)?.get("knobs")?.jsonArray.orEmpty()
            assertTrue(knobs.size <= 4, "$id: more than 4 knobs")
            for (k in knobs) {
                val target = k.jsonObject["bindings"]!!.jsonArray.first().jsonObject["parameterId"]!!.jsonPrimitive.content
                assertTrue(target in inputs, "$id: knob bound to unknown parameter '$target'")
            }
        }
    }

    @Test
    fun everyShippedFilterDefaultTargetsRealInputs() {
        val fxFiles = files("filters")
        assertTrue(fxFiles.isNotEmpty())
        for (file in fxFiles) {
            val id = file.nameWithoutExtension
            val shader = File(root, "src/main/resources/default_filters/$id.fs")
            assertTrue(shader.exists(), "$id: no bundled filter with this id")
            val inputs = isfInputs(shader).map { it["NAME"]!!.jsonPrimitive.content }.toSet()
            val obj = parse(file)
            for (b in obj["metaBindings"]!!.jsonArray) {
                val target = b.jsonObject["targetParamName"]?.jsonPrimitive?.content ?: continue
                assertTrue(target in inputs, "$id: Metaknob bound to unknown input '$target'")
            }
            (obj["params"] as? JsonObject)?.keys?.forEach { assertTrue(it in inputs, "$id: unknown input '$it'") }
        }
    }

    @Test
    fun overlayReplacesModulatorsAndKeepsOtherFields() {
        val file = sourceFiles.first { (parse(it)["parameters"] as? JsonObject)?.isNotEmpty() == true }
        val id = file.nameWithoutExtension
        val obj = parse(file)
        val name = (obj["parameters"] as JsonObject).keys.first()
        val stray = ModulatorDto(sourceId = "audio_high", operator = "ADD", depth = 0.123f)
        val base = baseline(id).getValue(name).copy(baseMin = 0.1f, modulators = listOf(stray))
        val once = StockDefaults.overlayParameters(obj, "parameters", mapOf(name to base), "t").getValue(name)
        val twice = StockDefaults.overlayParameters(obj, "parameters", mapOf(name to once), "t").getValue(name)
        assertEquals(once.modulators, twice.modulators)
        assertTrue(stray !in once.modulators)
        assertEquals(0.1f, once.baseMin)
        assertTrue(once.modulators.all { it.id == null })
    }

    @Test
    fun keepsDcOffsetAndRejectsBadModulators() {
        fun mod(entry: String) = StockDefaults.modulator(json.parseToJsonElement(entry), "t")
        assertEquals(0.1f, mod("""{"sourceId":"lfo","operator":"ADD","depth":0.2,"dcOffset":0.1}""")!!.dcOffset)
        assertEquals("RANDOM", mod("""{"sourceId":"lfo","operator":"ADD","depth":0.2,"waveform":"RANDOM","hold":0.5}""")!!.waveform)
        assertNull(mod("""{"sourceId":"nope","operator":"ADD","depth":0.2}"""))
        assertNull(mod("""{"sourceId":"lfo","depth":0.2}"""))
        assertNull(mod("""{"sourceId":"lfo","operator":"ADD","depth":0}"""))
        assertTrue(mod("""{"name":"x","sourceId":"lfo","operator":"ADD","depth":0.2,"bypassed":true}""")!!.bypassed)
    }

    /** A bipolar source (LFO, sample-and-hold) swings dcOffset +- depth; that must stay inside the parameter, or the clamp flattens part of every cycle. */
    @Test
    fun lfoSwingsFitTheirParameters() {
        for (file in sourceFiles) {
            val id = file.nameWithoutExtension
            val ranges = ranges(id)
            for ((name, p) in shippedParams(file)) {
                val (min, _, max) = ranges.getValue(name)
                val scalar = if (min < 0f) (max - min) / 2f else max - min
                for (m in p.modulators.filter { it.sourceId == "lfo" }) {
                    val lo = p.baseValue + (m.dcOffset - kotlin.math.abs(m.depth)) * scalar
                    val hi = p.baseValue + (m.dcOffset + kotlin.math.abs(m.depth)) * scalar
                    val eps = 1e-4f * (max - min)
                    assertTrue(lo >= min - eps && hi <= max + eps, "$id.$name (${m.waveform}): swings $lo..$hi, outside $min..$max")
                }
            }
        }
    }
}
