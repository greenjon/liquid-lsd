package llm.slop.liquidlsd.parameters

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import llm.slop.liquidlsd.cv.AudioFollowerTracker
import llm.slop.liquidlsd.cv.CVRegistry
import llm.slop.liquidlsd.models.ParameterDto
import llm.slop.liquidlsd.models.toDto
import llm.slop.liquidlsd.utils.TimeSource
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Golden vectors for web/evaluator.js: real [ModulatableParameter.evaluate] results under simulated time.
 * The web side replays them in `node --test web/tools/evaluator.test.mjs` (web/tools/evaluator.test.mjs).
 *
 * Fails when the committed web/tools/evaluator_vectors.json is stale. Regenerate with
 * `UPDATE_WEB_VECTORS=1 ./gradlew test --tests '*WebEvaluatorVectorsTest*'`.
 */
class WebEvaluatorVectorsTest {

    private val json = Json { encodeDefaults = true }

    private val projectRoot: File by lazy {
        var dir = File(".").canonicalFile
        while (dir.parentFile != null && !File(dir, "web/sync_manifest.json").exists()) dir = dir.parentFile
        dir
    }

    @BeforeEach
    fun setUp() {
        llm.slop.liquidlsd.ui.UITheme.sequencerEnabled = true
        AudioFollowerTracker.reset()
    }

    @AfterEach
    fun tearDown() {
        llm.slop.liquidlsd.ui.UITheme.sequencerEnabled = false
        AudioFollowerTracker.reset()
    }

    private class Spec(val min: Float, val max: Float, val steps: Int? = null)

    private class Point(val offsetSec: Double, val beats: Double, val frame: Long, val cv: Map<String, Float> = emptyMap())

    private val cvIds = listOf(
        "audio_amp", "audio_bass", "audio_mid", "audio_high",
        "audio_flux_amp", "audio_flux_bass", "audio_flux_mid", "audio_flux_high"
    )

    private val timeOffsets = listOf(0.0, 0.13, 0.37, 0.5, 0.77, 1.9, 3.21, 12.5)
    private val beatsList = listOf(0.0, 0.3, 1.1, 2.5, 3.999, 7.77, 31.2)
    private val framesList = listOf(0L, 3L, 17L, 100L, 1234L)

    /** Points that exercise a modulator whatever its clock unit. */
    private fun clockPoints(cv: Map<String, Float> = emptyMap()): List<Point> =
        timeOffsets.indices.map { Point(timeOffsets[it], beatsList[it % beatsList.size] + it * 0.173, framesList[it % framesList.size] + it, cv) }

    private fun param(base: Float, spec: Spec, vararg mods: CvModulator): ModulatableParameter {
        val p = ModulatableParameter(baseValue = base, minClamp = spec.min, maxClamp = spec.max, steps = spec.steps)
        mods.forEach { p.modulators.add(it) }
        return p
    }

    /** Sets the registry to the point and returns the context the web evaluator receives. */
    private fun apply(pt: Point, bpm: Float, dt: Double): JsonObject {
        TimeSource.setSimulatedTime(0.0, dt)
        val zeroElapsed = CVRegistry.getElapsedRealtimeSec() // = -startTime
        TimeSource.setSimulatedTime(-zeroElapsed + pt.offsetSec, dt)
        CVRegistry.resetBeatAnchor(pt.beats, bpm, TimeSource.getTimeNanos())
        CVRegistry.setRenderFrameCount(pt.frame)
        cvIds.forEach { CVRegistry.updatePushedValue(it, pt.cv[it] ?: 0f) }
        val time = CVRegistry.getElapsedRealtimeSec()
        val beats = CVRegistry.getSynchronizedTotalBeats()
        return buildJsonObject {
            // elapsed time carries JVM-start nanosecond noise; round so the committed file is reproducible
            put("time", Math.round(time * 1e6) / 1e6)
            put("beats", beats)
            put("frame", pt.frame)
            put("dt", dt)
            put("bpm", bpm.toDouble())
            put("cv", buildJsonObject { pt.cv.toSortedMap().forEach { (k, v) -> put(k, v) } })
        }
    }

    private fun paramJson(p: ModulatableParameter, extras: Map<String, JsonElement> = emptyMap()): JsonElement {
        val element = json.encodeToJsonElement(ParameterDto.serializer(), p.toDto()).let { it as JsonObject }
        if (extras.isEmpty()) return element
        // ModulatorDto cannot carry the LFO2 / generator-mod fields; the web evaluator still honours them.
        val mods = (element["modulators"] as JsonArray).map { m -> JsonObject((m as JsonObject) + extras) }
        return JsonObject(element + ("modulators" to JsonArray(mods)))
    }

    private fun specJson(s: Spec) = buildJsonObject {
        put("min", s.min); put("max", s.max)
        s.steps?.let { put("steps", it) }
    }

    private val cases = mutableListOf<JsonElement>()
    private val sequences = mutableListOf<JsonElement>()

    private fun case(
        name: String, spec: Spec, p: ModulatableParameter, points: List<Point>,
        bpm: Float = 120f, extras: Map<String, JsonElement> = emptyMap()
    ) {
        val pts = points.map { pt ->
            val ctx = apply(pt, bpm, 1.0 / 60.0)
            buildJsonObject { put("ctx", ctx); put("expected", p.evaluate().toDouble()) }
        }
        cases.add(buildJsonObject {
            put("name", name); put("spec", specJson(spec)); put("param", paramJson(p, extras)); put("points", JsonArray(pts))
        })
    }

    /** Stateful: envelope followers carry state from frame to frame. */
    private fun sequence(name: String, spec: Spec, p: ModulatableParameter, inputs: List<Float>, cvId: String) {
        AudioFollowerTracker.reset()
        val frames = inputs.mapIndexed { i, v ->
            val ctx = apply(Point(i / 60.0, i / 30.0, i.toLong(), mapOf(cvId to v)), 120f, 1.0 / 60.0)
            buildJsonObject { put("ctx", ctx); put("expected", p.evaluate().toDouble()) }
        }
        sequences.add(buildJsonObject {
            put("name", name); put("spec", specJson(spec)); put("param", paramJson(p)); put("frames", JsonArray(frames))
        })
    }

    private fun lfo(
        waveform: Waveform = Waveform.SINE, unit: GenUnit = GenUnit.TIME, sub: Float = 1f, depth: Float = 0.5f,
        dc: Float = 0f, phase: Float = 0f, slope: Float = 0.5f, morph: Float = 0f, hold: Float = 0f,
        op: ModulationOperator = ModulationOperator.ADD, id: String = "mod-1", bypassed: Boolean = false, extra: CvModulator.() -> CvModulator = { this }
    ) = CvModulator(
        sourceId = "lfo", operator = op, depth = depth, waveform = waveform, genUnit = unit, subdivision = sub,
        phaseOffset = phase, slope = slope, morph = morph, hold = hold, dcOffset = dc, id = id, bypassed = bypassed
    ).extra()

    private fun audio(
        id: String = "audio_bass", op: ModulationOperator = ModulationOperator.ADD, depth: Float = 1f, dc: Float = 0f,
        mode: AudioFollowerMode = AudioFollowerMode.RAW, attack: Float = 0f, decay: Float = 0f, mid: String = "aud-1"
    ) = CvModulator(sourceId = id, operator = op, depth = depth, dcOffset = dc, followerMode = mode, attackMs = attack, decayMs = decay, id = mid)

    private fun build() {
        val uni = Spec(0f, 1f)
        val bi = Spec(-1f, 1f)
        val wide = Spec(0f, 12f)

        case("base only, in range", uni, param(0.4f, uni), clockPoints().take(1))
        case("base clamps high", uni, param(1.5f, uni), clockPoints().take(1))
        case("base clamps low bipolar", bi, param(-3f, bi), clockPoints().take(1))

        for (wf in listOf(Waveform.SINE, Waveform.TRIANGLE, Waveform.SQUARE)) {
            case("lfo time $wf unipolar", uni, param(0.5f, uni, lfo(wf, sub = 2f, depth = 0.3f)), clockPoints())
            case("lfo time $wf bipolar", bi, param(0f, bi, lfo(wf, sub = 2f, depth = 0.8f)), clockPoints())
        }
        case("lfo slope low", uni, param(0.5f, uni, lfo(Waveform.TRIANGLE, slope = 0.1f)), clockPoints())
        case("lfo slope extreme", uni, param(0.5f, uni, lfo(Waveform.TRIANGLE, slope = 0.9995f)), clockPoints())
        case("lfo square duty", bi, param(0f, bi, lfo(Waveform.SQUARE, slope = 0.25f, depth = 1f)), clockPoints())
        case("lfo morph", uni, param(0.5f, uni, lfo(morph = 0.6f, depth = 0.5f)), clockPoints())
        case("lfo morph 1", uni, param(0.5f, uni, lfo(morph = 1f, depth = 0.5f)), clockPoints())
        case("lfo hold", bi, param(0f, bi, lfo(hold = 0.5f, depth = 1f)), clockPoints())
        case("lfo hold+morph square", bi, param(0f, bi, lfo(Waveform.SQUARE, hold = 0.3f, morph = 0.4f, depth = 1f)), clockPoints())
        case("lfo phase offset", uni, param(0.5f, uni, lfo(phase = 0.35f)), clockPoints())
        case("lfo dc offset", uni, param(0.2f, uni, lfo(dc = 0.25f, depth = 0.2f)), clockPoints())
        case("lfo wide range scales ADD", wide, param(6f, wide, lfo(depth = 0.5f)), clockPoints())
        case("lfo beat", uni, param(0.5f, uni, lfo(unit = GenUnit.BEAT, sub = 2f)), clockPoints())
        case("lfo frame", uni, param(0.5f, uni, lfo(unit = GenUnit.FRAME, sub = 40f)), clockPoints())
        case("lfo MUL", uni, param(0.5f, uni, lfo(op = ModulationOperator.MUL, depth = 0.4f)), clockPoints())
        case("lfo SCALE", uni, param(0.8f, uni, lfo(op = ModulationOperator.SCALE, depth = 0.5f)), clockPoints())
        case("lfo bypassed", uni, param(0.5f, uni, lfo(bypassed = true)), clockPoints().take(2))

        for (unit in GenUnit.values()) {
            val sub = if (unit == GenUnit.FRAME) 30f else 1.5f
            case("lfo RANDOM $unit", bi, param(0f, bi, lfo(Waveform.RANDOM, unit, sub = sub, depth = 1f, morph = 0.3f, hold = 0.2f, id = "rand-uuid-1")), clockPoints())
        }
        case("lfo RANDOM other id", bi, param(0f, bi, lfo(Waveform.RANDOM, GenUnit.BEAT, sub = 1f, depth = 1f, id = "f3a9-77")), clockPoints())

        val am = mapOf<String, JsonElement>("generatorModMode" to JsonPrimitive("AM"), "modWaveform" to JsonPrimitive("TRIANGLE"),
            "modSubdivision" to JsonPrimitive(0.7), "modGenUnit" to JsonPrimitive("TIME"), "generatorModDepth" to JsonPrimitive(0.6), "modMorph" to JsonPrimitive(0.2))
        for (mode in listOf(GeneratorModMode.AM, GeneratorModMode.PM, GeneratorModMode.ADD)) {
            val m = lfo(sub = 1.3f, depth = 0.5f, unit = GenUnit.TIME) .copy(
                generatorModMode = mode, modWaveform = Waveform.TRIANGLE, modSubdivision = 0.7f,
                modGenUnit = GenUnit.TIME, generatorModDepth = 0.6f, modMorph = 0.2f
            )
            case("lfo gen-mod $mode", bi, param(0f, bi, m), clockPoints(), extras = am + ("generatorModMode" to JsonPrimitive(mode.name)))
        }
        val rm = lfo(Waveform.SINE, GenUnit.BEAT, sub = 2f, depth = 0.5f).copy(
            generatorModMode = GeneratorModMode.PM, modWaveform = Waveform.RANDOM, modSubdivision = 0.5f,
            modGenUnit = GenUnit.BEAT, generatorModDepth = 0.3f
        )
        case("lfo PM with RANDOM mod", bi, param(0f, bi, rm), clockPoints(), extras = mapOf(
            "generatorModMode" to JsonPrimitive("PM"), "modWaveform" to JsonPrimitive("RANDOM"), "modSubdivision" to JsonPrimitive(0.5),
            "modGenUnit" to JsonPrimitive("BEAT"), "generatorModDepth" to JsonPrimitive(0.3)))

        for ((unit, sub) in listOf(GenUnit.BEAT to 0.5f, GenUnit.FRAME to 7f, GenUnit.TIME to 0.25f)) {
            val steps = listOf(0.1f, 0.9f, -0.5f, 0.3f, 1.0f, 0.0f)
            for ((hold, smooth) in listOf(1f to false, 0.5f to false, 0.2f to true, 0f to true)) {
                val m = CvModulator(sourceId = "seq", genUnit = unit, subdivision = sub, seqStepCount = 6, seqSteps = steps,
                    seqHold = hold, seqCurveSmooth = smooth, depth = 1f, id = "seq-1")
                case("seq $unit hold=$hold smooth=$smooth", uni, param(0.5f, uni, m), clockPoints())
            }
        }
        case("seq depth+dc bipolar", bi, param(0f, bi, CvModulator(sourceId = "seq", genUnit = GenUnit.BEAT, subdivision = 1f, seqStepCount = 4,
            seqSteps = listOf(0.2f, -0.4f, 0.6f, 0.0f), depth = 0.5f, dcOffset = 0.1f, id = "seq-2")), clockPoints())
        case("seq step count past list", uni, param(0.2f, uni, CvModulator(sourceId = "seq", genUnit = GenUnit.BEAT, subdivision = 1f, seqStepCount = 32,
            seqSteps = listOf(0.1f, 0.2f), depth = 1f, id = "seq-3")), clockPoints())

        val cv = mapOf("audio_amp" to 0.8f, "audio_bass" to 0.65f, "audio_mid" to 0.3f, "audio_high" to 0.1f,
            "audio_flux_amp" to 0.9f, "audio_flux_bass" to 0.5f, "audio_flux_mid" to 0.2f, "audio_flux_high" to 0.05f)
        for (id in cvIds) {
            case("$id ADD", uni, param(0.1f, uni, audio(id, depth = 0.6f)), clockPoints(cv).take(1))
        }
        case("audio MUL", uni, param(0.5f, uni, audio(op = ModulationOperator.MUL, depth = 0.8f)), clockPoints(cv).take(1))
        case("audio SCALE", uni, param(0.9f, uni, audio(op = ModulationOperator.SCALE, depth = 0.7f)), clockPoints(cv).take(1))
        case("audio dc offset", bi, param(0f, bi, audio(depth = 0.5f, dc = -0.25f)), clockPoints(cv).take(1))
        case("audio wide range ADD", wide, param(1f, wide, audio(depth = 0.5f)), clockPoints(cv).take(1))
        case("audio overshoot clamps", uni, param(0.9f, uni, audio(depth = 1f)), clockPoints(cv).take(1))

        case("stepped snap", Spec(0f, 4f, 5), param(1.3f, Spec(0f, 4f, 5), lfo(sub = 3f, depth = 0.4f)), clockPoints())
        case("stepped base only", Spec(0f, 4f, 5), param(2.6f, Spec(0f, 4f, 5)), clockPoints().take(1))
        case("stepped fractional span", Spec(-1f, 1f, 5), param(0.3f, Spec(-1f, 1f, 5), lfo(sub = 3f, depth = 0.9f)), clockPoints())

        case("multi: lfo + audio", uni, param(0.4f, uni, lfo(depth = 0.2f), audio(depth = 0.3f)), clockPoints(cv))
        case("multi: ADD then MUL", uni, param(0.4f, uni, lfo(depth = 0.2f), audio(op = ModulationOperator.MUL, depth = 0.5f)), clockPoints(cv))
        case("multi: SCALE then ADD", uni, param(0.8f, uni, audio(op = ModulationOperator.SCALE, depth = 0.5f), lfo(depth = 0.1f)), clockPoints(cv))

        // beatPhase / sampleAndHold are not registered in CVRegistry, so ModulatableParameter skips them.
        case("legacy beatPhase is skipped", uni, param(0.3f, uni, CvModulator(sourceId = "beatPhase", depth = 0.5f, id = "bp")), clockPoints().take(2))
        case("legacy sampleAndHold is skipped", uni, param(0.3f, uni, CvModulator(sourceId = "sampleAndHold", depth = 0.5f, id = "sh")), clockPoints().take(2))
        case("unknown source is skipped", uni, param(0.3f, uni, CvModulator(sourceId = "nonsense", depth = 0.5f, id = "ns")), clockPoints().take(2))

        val ramp = List(60) { if (it in 10..29) 1f else if (it in 30..44) 0.4f else 0f }
        sequence("follower PUNCHY", uni, param(0f, uni, audio(mode = AudioFollowerMode.PUNCHY, attack = 5f, decay = 150f)), ramp, "audio_bass")
        sequence("follower SMOOTH", uni, param(0f, uni, audio(mode = AudioFollowerMode.SMOOTH, attack = 40f, decay = 400f)), ramp, "audio_bass")
        sequence("follower SNAP", uni, param(0f, uni, audio(mode = AudioFollowerMode.SNAP, attack = 0f, decay = 35f)), ramp, "audio_bass")
        sequence("follower RAW ignores times", uni, param(0f, uni, audio(mode = AudioFollowerMode.RAW, attack = 100f, decay = 100f)), ramp, "audio_bass")
        sequence("follower mode set but zero times", uni, param(0f, uni, audio(mode = AudioFollowerMode.CUSTOM, attack = 0f, decay = 0f)), ramp, "audio_bass")
    }

    @Test
    fun webVectorsMatchDesktop() {
        build()
        val text = buildString {
            append("{\n\"cases\": [\n")
            append(cases.joinToString(",\n") { json.encodeToString(JsonElement.serializer(), it) })
            append("\n],\n\"sequences\": [\n")
            append(sequences.joinToString(",\n") { json.encodeToString(JsonElement.serializer(), it) })
            append("\n]\n}\n")
        }
        val file = File(projectRoot, "web/tools/evaluator_vectors.json")
        if (System.getenv("UPDATE_WEB_VECTORS") == "1" || !file.exists()) {
            file.writeText(text)
        } else {
            assertEquals(file.readText(), text, "web/tools/evaluator_vectors.json is stale; regenerate with UPDATE_WEB_VECTORS=1")
        }
    }
}
