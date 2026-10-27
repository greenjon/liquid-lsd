package llm.slop.liquidlsd.presets

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import llm.slop.liquidlsd.cv.CVRegistry
import llm.slop.liquidlsd.cv.isAudioSource
import llm.slop.liquidlsd.models.ModulatorDto
import llm.slop.liquidlsd.models.ParameterDto
import llm.slop.liquidlsd.parameters.ModulationOperator
import llm.slop.liquidlsd.parameters.Waveform
import mu.KotlinLogging
import java.util.concurrent.ConcurrentHashMap

/**
 * Hand-curated audio / beat response ("reactivity recipes") shipped per shader as
 * `reactivity/<kind>/<id>.json` on the classpath. Per parameter an ordered `stack` of modulators; entries with
 * `"active": true` run, the rest are wired but bypassed so a click turns them on. Every recipe modulator is
 * flagged `recipe` so the global [llm.slop.liquidlsd.parameters.Reactivity] amount scales it.
 *
 * Entry keys are [ModulatorDto] fields plus `active`, `name` and `note` (ignored). Defaults: operator ADD,
 * LFO unit BEAT, follower PUNCHY when an attack/decay is given. Depth and
 * `dcOffset` both scale with the reactivity amount, so 0 reproduces the base value exactly. A bipolar source (LFO,
 * sample-and-hold) swings `dcOffset` +- `depth`: on a parameter whose set value is its minimum, `dcOffset = depth`
 * swings it upward from there. A parameter may also carry `base` to move its set value.
 *
 * Format and rationale: .planning/reactivity-recipes-plan.md.
 */
object ReactivityRecipes {
    private val logger = KotlinLogging.logger {}
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    class ParamRecipe(val base: Float?, val stack: List<ModulatorDto>)

    private val cache = ConcurrentHashMap<String, Map<String, ParamRecipe>>()

    /** The recipe for source [sourceId] (empty when it has none). */
    fun forSource(sourceId: String): Map<String, ParamRecipe> = forId("sources", sourceId)

    fun forId(kind: String, id: String): Map<String, ParamRecipe> =
        cache.getOrPut("$kind/$id") {
            val text = ReactivityRecipes::class.java.getResourceAsStream("/reactivity/$kind/$id.json")
                ?.bufferedReader()?.use { it.readText() } ?: return@getOrPut emptyMap()
            try {
                parse(text, "$kind/$id")
            } catch (e: Exception) {
                logger.warn(e) { "Ignoring unreadable reactivity recipe $kind/$id" }
                emptyMap()
            }
        }

    /** Parses recipe JSON; entries that fail validation are skipped with a warning. Exposed for tests. */
    fun parse(text: String, label: String): Map<String, ParamRecipe> {
        val params = json.parseToJsonElement(text).jsonObject["params"]?.jsonObject ?: return emptyMap()
        val out = LinkedHashMap<String, ParamRecipe>()
        for ((name, spec) in params) {
            val obj = spec.jsonObject
            val stack = (obj["stack"] as? JsonArray).orEmpty().mapNotNull { entry ->
                try {
                    toModulator(entry.jsonObject)
                } catch (e: Exception) {
                    logger.warn { "Reactivity recipe $label: skipping a '$name' entry: ${e.message}" }
                    null
                }
            }
            out[name] = ParamRecipe(obj["base"]?.jsonPrimitive?.floatOrNull, stack)
        }
        return out
    }

    private fun toModulator(entry: JsonObject): ModulatorDto {
        val m = LinkedHashMap(entry)
        val active = (m.remove("active") as? JsonPrimitive)?.booleanOrNull ?: false
        m.remove("name"); m.remove("note")
        val source = m["sourceId"]?.jsonPrimitive?.contentOrNull ?: error("missing sourceId")
        require(CVRegistry.exists(source)) { "unknown source '$source'" }
        m.getOrPut("operator") { JsonPrimitive(ModulationOperator.ADD.name) }
        require(m["operator"]!!.jsonPrimitive.content in ModulationOperator.values().map { it.name }) { "bad operator" }
        m["waveform"]?.let { require(it.jsonPrimitive.content in Waveform.values().map { w -> w.name }) { "bad waveform" } }
        if (source == "lfo") m.getOrPut("genUnit") { JsonPrimitive("BEAT") }
        if (isAudioSource(source) && "followerMode" !in m &&
            ((m["attackMs"]?.jsonPrimitive?.floatOrNull ?: 0f) > 0f || (m["decayMs"]?.jsonPrimitive?.floatOrNull ?: 0f) > 0f)
        ) m["followerMode"] = JsonPrimitive("PUNCHY")
        m["bypassed"] = JsonPrimitive(!active)
        m["recipe"] = JsonPrimitive(true)
        val dto = json.decodeFromJsonElement(ModulatorDto.serializer(), JsonObject(m))
        require(dto.depth != 0f) { "depth is 0" }
        return dto
    }

    /**
     * [params] with the recipe for [sourceId] laid over it: each recipe parameter keeps its non-recipe
     * modulators, replaces its old recipe ones with fresh copies, and takes the recipe's `base` if it has one.
     */
    fun overlay(sourceId: String, params: Map<String, ParameterDto>): Map<String, ParameterDto> {
        val recipe = forSource(sourceId)
        if (recipe.isEmpty()) return params
        val out = LinkedHashMap(params)
        for ((name, r) in recipe) {
            val p = params[name]
            if (p == null) {
                logger.warn { "Reactivity recipe for $sourceId names unknown parameter '$name'" }
                continue
            }
            out[name] = p.copy(
                baseValue = r.base ?: p.baseValue,
                modulators = p.modulators.filterNot { it.recipe } + r.stack.map { it.copy(id = null) }
            )
        }
        return out
    }
}
