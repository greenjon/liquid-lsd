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

    private val filterFiles get() = files("filters")

    /** (min, default, max) of each float input of a bundled filter. */
    private fun filterRanges(id: String): Map<String, Triple<Float, Float, Float>> =
        isfInputs(File(root, "src/main/resources/default_filters/$id.fs")).associate {
            it["NAME"]!!.jsonPrimitive.content to Triple(
                it["MIN"]!!.jsonPrimitive.content.toFloat(), it["DEFAULT"]!!.jsonPrimitive.content.toFloat(), it["MAX"]!!.jsonPrimitive.content.toFloat())
        }

    private fun shippedFilterParams(file: File) = StockDefaults.overlayParameters(
        parse(file), "params",
        filterRanges(file.nameWithoutExtension).mapValues { (_, r) -> ParameterDto(r.second, r.first, r.third, false, emptyList()) },
        file.name)

    /** Every modulator entry under [raw] survived validation, at most one runs per parameter, and no depth exceeds 1. */
    private fun assertModulatorsValid(id: String, raw: JsonObject, parsed: Map<String, ParameterDto>) {
        assertEquals(raw.keys, parsed.keys, "$id: a parameter is not a float input")
        for ((name, p) in parsed) {
            val rawCount = (raw[name]!!.jsonObject["modulators"] as? JsonArray)?.size ?: 0
            assertEquals(rawCount, p.modulators.size, "$id.$name: a modulator failed validation")
            assertTrue(p.modulators.count { !it.bypassed } <= 1, "$id.$name: more than one active modulator")
            p.modulators.forEach { assertTrue(kotlin.math.abs(it.depth) <= 1f, "$id.$name: depth ${it.depth} out of range") }
        }
    }

    /**
     * A bipolar source (LFO, sample-and-hold) swings dcOffset +- depth. An ADD swing must stay inside the parameter, or
     * the clamp flattens part of every cycle; a MUL factor (1 + swing) must never go negative.
     */
    private fun assertLfoSwingsFit(id: String, parsed: Map<String, ParameterDto>, ranges: Map<String, Triple<Float, Float, Float>>) {
        for ((name, p) in parsed) {
            val (min, _, max) = ranges.getValue(name)
            val scalar = if (min < 0f) (max - min) / 2f else max - min
            for (m in p.modulators.filter { it.sourceId == "lfo" }) {
                if (m.operator == "MUL") {
                    assertTrue(1f + m.dcOffset - kotlin.math.abs(m.depth) >= 0f, "$id.$name (${m.waveform}): MUL factor goes negative")
                    continue
                }
                val lo = p.baseValue + (m.dcOffset - kotlin.math.abs(m.depth)) * scalar
                val hi = p.baseValue + (m.dcOffset + kotlin.math.abs(m.depth)) * scalar
                val eps = 1e-4f * (max - min)
                assertTrue(lo >= min - eps && hi <= max + eps, "$id.$name (${m.waveform}): swings $lo..$hi, outside $min..$max")
            }
        }
    }

    @Test
    fun everyShippedSourceDefaultIsValid() {
        assertTrue(sourceFiles.isNotEmpty())
        for (file in sourceFiles) {
            val id = file.nameWithoutExtension
            val obj = parse(file)
            assertEquals(id, obj["sourceId"]?.jsonPrimitive?.content, "$id: sourceId")
            val inputs = ranges(id).keys
            assertModulatorsValid(id, obj["parameters"] as? JsonObject ?: JsonObject(emptyMap()), shippedParams(file))
            val knobs = (obj["macroBank"] as? JsonObject)?.get("knobs")?.jsonArray.orEmpty()
            assertTrue(knobs.size <= 4, "$id: more than 4 knobs")
            for (k in knobs) {
                val target = k.jsonObject["bindings"]!!.jsonArray.first().jsonObject["parameterId"]!!.jsonPrimitive.content
                assertTrue(target in inputs, "$id: knob bound to unknown parameter '$target'")
            }
        }
    }

    /**
     * A randomized base range lies inside the parameter and leaves room for the parameter's running ADD responses: an
     * LFO's whole swing, a follower's full depth. Otherwise the clamp flattens the response at one end of the range.
     */
    @Test
    fun shippedRandomRangesFitTheirParameters() {
        var randomized = 0
        for (file in sourceFiles) {
            val id = file.nameWithoutExtension
            if (id == "fractal_flame" || id == "mandala") continue // genome / meta.json ranges, not shipped here
            val ranges = ranges(id)
            for ((name, p) in shippedParams(file)) {
                if (!p.randomizeBase) continue
                randomized++
                val (min, _, max) = ranges.getValue(name)
                val eps = 1e-4f * (max - min)
                assertTrue(p.baseMin < p.baseMax, "$id.$name: empty random range")
                assertTrue(p.baseMin >= min - eps && p.baseMax <= max + eps, "$id.$name: random range ${p.baseMin}..${p.baseMax} outside $min..$max")
                val scalar = if (min < 0f) (max - min) / 2f else max - min
                for (m in p.modulators.filter { !it.bypassed && it.operator == "ADD" }) {
                    val (down, up) = if (m.sourceId == "lfo") (m.dcOffset - kotlin.math.abs(m.depth)) to (m.dcOffset + kotlin.math.abs(m.depth))
                        else minOf(m.depth, 0f) to maxOf(m.depth, 0f)
                    assertTrue(p.baseMin + down * scalar >= min - eps && p.baseMax + up * scalar <= max + eps,
                        "$id.$name: '${m.sourceId}' pushes the random range ${p.baseMin}..${p.baseMax} outside $min..$max")
                }
            }
        }
        assertTrue(randomized > 0)
    }

    @Test
    fun everyShippedFilterDefaultTargetsRealInputs() {
        assertTrue(filterFiles.isNotEmpty())
        for (file in filterFiles) {
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
    fun everyShippedFilterResponseIsValid() {
        for (file in filterFiles) {
            val id = file.nameWithoutExtension
            val raw = parse(file)["params"] as? JsonObject ?: continue
            val parsed = shippedFilterParams(file)
            assertModulatorsValid(id, raw, parsed)
            assertLfoSwingsFit(id, parsed, filterRanges(id))
        }
    }

    private val transitionFiles get() = files("transitions")

    /** (min, default, max) of each float input of a bundled transition. */
    private fun transitionRanges(id: String): Map<String, Triple<Float, Float, Float>> =
        isfInputs(File(root, "src/main/resources/default_transitions/$id.fs")).associate {
            it["NAME"]!!.jsonPrimitive.content to Triple(
                it["MIN"]!!.jsonPrimitive.content.toFloat(), it["DEFAULT"]!!.jsonPrimitive.content.toFloat(), it["MAX"]!!.jsonPrimitive.content.toFloat())
        }

    private fun shippedTransitionParams(id: String) = StockDefaults.overlayParameters(
        parse(File(root, "src/main/resources/stock_defaults/transitions/$id.json")), "params",
        transitionRanges(id).mapValues { (_, r) -> ParameterDto(r.second, r.first, r.third, false, emptyList()) },
        "transitions/$id")

    /** The shipped transition presets, the `.lsdtrans` files in `defaults/transitions`. */
    private val transitionPresets get() =
        File(root, "defaults/transitions").listFiles { f -> f.extension == "lsdtrans" }!!.sortedBy { it.name }
            .map { it.name to json.decodeFromString(llm.slop.liquidlsd.models.TransitionPresetDto.serializer(), it.readText()) }

    /**
     * Inputs that make the picture pop the instant a fade leaves rest when pushed past a limit: the flash or the burn
     * edge leaks to the ends of the fade (see `.planning/transition-reactivity-plan.md`). Checked against the highest
     * value any modulator, running or bypassed, can push the input to.
     */
    private val atRestLimits = mapOf("luminous_flash" to mapOf("spread" to 0.45f), "film_burn" to mapOf("burnSpread" to 0.45f))

    /** The highest value [p]'s modulators can push it to: unipolar followers add `depth`, bipolar LFOs `dcOffset + |depth|`. */
    private fun reach(p: ParameterDto, min: Float, max: Float): Float {
        val scalar = if (min < 0f) (max - min) / 2f else max - min
        return p.modulators.fold(p.baseValue) { top, m ->
            val swing = if (m.sourceId == "lfo") m.dcOffset + kotlin.math.abs(m.depth) else maxOf(m.depth, 0f)
            maxOf(top, if (m.operator == "MUL") p.baseValue * (1f + swing) else p.baseValue + swing * scalar)
        }
    }

    @Test
    fun everyShippedTransitionDefaultIsValid() {
        assertTrue(transitionFiles.isNotEmpty())
        for (file in transitionFiles) {
            val id = file.nameWithoutExtension
            assertTrue(File(root, "src/main/resources/default_transitions/$id.fs").exists(), "$id: no bundled transition with this id")
            val raw = parse(file)["params"] as JsonObject
            assertTrue("progress" !in raw.keys, "$id: progress is the crossfader and must never be modulated")
            val parsed = shippedTransitionParams(id)
            assertModulatorsValid(id, raw, parsed)
            assertLfoSwingsFit(id, parsed, transitionRanges(id))
        }
    }

    @Test
    fun transitionResponsesStayInsideTheAtRestLimits() {
        for ((id, limits) in atRestLimits) {
            val ranges = transitionRanges(id)
            val shipped = shippedTransitionParams(id)
            for ((name, limit) in limits) {
                val (min, default, max) = ranges.getValue(name)
                val p = shipped[name] ?: ParameterDto(default, min, max, false, emptyList())
                assertTrue(reach(p, min, max) <= limit, "$id.$name: the shipped default reaches ${reach(p, min, max)}, over $limit")
            }
        }
        for ((file, preset) in transitionPresets) {
            val limits = atRestLimits[preset.slot.filterId] ?: continue
            val ranges = transitionRanges(preset.slot.filterId)
            for ((name, limit) in limits) {
                val p = preset.slot.parameters[name] ?: continue
                val (min, _, max) = ranges.getValue(name)
                assertTrue(reach(p, min, max) <= limit, "$file: $name reaches ${reach(p, min, max)}, over $limit")
            }
        }
    }

    /** A preset stores full parameters, so it must carry the shipped response itself or AutoVJ and playlists never show it. */
    @Test
    fun shippedTransitionPresetsCarryTheShippedResponse() {
        assertTrue(transitionPresets.isNotEmpty())
        for ((file, preset) in transitionPresets) {
            val id = preset.slot.filterId
            val ranges = transitionRanges(id)
            for ((name, p) in preset.slot.parameters) {
                val (min, _, max) = ranges[name] ?: continue
                assertTrue(p.baseValue in min..max, "$file: $name = ${p.baseValue} is outside $min..$max")
            }
            if (!File(root, "src/main/resources/stock_defaults/transitions/$id.json").exists()) continue
            for ((name, shipped) in shippedTransitionParams(id)) {
                assertEquals(shipped.modulators, preset.slot.parameters[name]?.modulators, "$file: $name does not carry the shipped response")
            }
        }
    }

    @Test
    fun overlayReplacesModulatorsAndKeepsOtherFields() {
        val file = sourceFiles.first { (parse(it)["parameters"] as? JsonObject)?.isNotEmpty() == true }
        val id = file.nameWithoutExtension
        val obj = parse(file)
        val name = (obj["parameters"] as JsonObject).keys.first()
        val stray = ModulatorDto(sourceId = "audio_high", operator = "ADD", depth = 0.123f)
        val base = baseline(id).getValue(name).copy(midiMapMin = 0.1f, modulators = listOf(stray))
        val once = StockDefaults.overlayParameters(obj, "parameters", mapOf(name to base), "t").getValue(name)
        val twice = StockDefaults.overlayParameters(obj, "parameters", mapOf(name to once), "t").getValue(name)
        assertEquals(once.modulators, twice.modulators)
        assertTrue(stray !in once.modulators)
        assertEquals(0.1f, once.midiMapMin) // shipped files name base values and random ranges, never MIDI fields
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

    @Test
    fun lfoSwingsFitTheirParameters() {
        for (file in sourceFiles) assertLfoSwingsFit(file.nameWithoutExtension, shippedParams(file), ranges(file.nameWithoutExtension))
    }
}
