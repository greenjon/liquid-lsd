package llm.slop.liquidlsd.presets

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import llm.slop.liquidlsd.cv.CVRegistry
import llm.slop.liquidlsd.models.ModulatorDto
import llm.slop.liquidlsd.models.ParameterDto
import llm.slop.liquidlsd.parameters.ModulationOperator
import llm.slop.liquidlsd.parameters.Waveform
import mu.KotlinLogging
import java.util.concurrent.ConcurrentHashMap

private val logger = KotlinLogging.logger {}

/**
 * The defaults shipped with the app for stock sources and effects: `stock_defaults/<kind>/<id>.json` on the classpath
 * (`kind` = `sources`, `filters` or `transitions`). Each file has the shape of the default a user saves (`GeneratorDefaultDto`
 * for a source, `FxDefaultDto` for an effect or transition) but is sparse: it names only what differs from the shader (a
 * parameter's `baseValue`, its `modulators`, the knobs) and the shader supplies the rest. Keys the DTOs do not
 * know (`name`, `note`) are notes for whoever edits the file.
 *
 * A user-saved default replaces the shipped one wholesale; nothing is merged between the two.
 */
object StockDefaults {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true }
    private val NOTE_KEYS = setOf("note", "modulators")

    private val cache = ConcurrentHashMap<String, Result<JsonObject?>>()

    /** The shipped default file for [kind]/[id], or null when there is none (or it cannot be read). */
    fun forId(kind: String, id: String): JsonObject? =
        cache.getOrPut("$kind/$id") {
            runCatching {
                StockDefaults::class.java.getResourceAsStream("/stock_defaults/$kind/$id.json")
                    ?.bufferedReader()?.use { json.parseToJsonElement(it.readText()).jsonObject }
            }.onFailure { logger.warn(it) { "Ignoring unreadable shipped default $kind/$id" } }
        }.getOrNull()

    /**
     * The parameters [file] names under [key], each laid over its entry in [baseline]: the file's fields replace the
     * baseline's, and its `modulators` (if given) replace the baseline's modulators with fresh copies. Parameters the
     * file names but [baseline] lacks are skipped with a warning, as are modulator entries that fail validation.
     */
    fun overlayParameters(file: JsonObject, key: String, baseline: Map<String, ParameterDto>, label: String): Map<String, ParameterDto> {
        val entries = file[key] as? JsonObject ?: return emptyMap()
        val out = LinkedHashMap<String, ParameterDto>()
        for ((name, element) in entries) {
            val base = baseline[name]
            if (base == null) {
                logger.warn { "Shipped default $label names unknown parameter '$name'" }
                continue
            }
            val patch = element.jsonObject
            val merged = JsonObject(json.encodeToJsonElement(ParameterDto.serializer(), base).jsonObject + patch.filterKeys { it !in NOTE_KEYS })
            val dto = json.decodeFromJsonElement(ParameterDto.serializer(), merged)
            val mods = (patch["modulators"] as? JsonArray)?.mapNotNull { modulator(it, "$label.$name") }
            out[name] = if (mods != null) dto.copy(modulators = mods) else dto
        }
        return out
    }

    /** One shipped modulator entry, validated; null (with a warning) when it is unusable. Ids are never shipped. */
    fun modulator(entry: JsonElement, label: String): ModulatorDto? = try {
        val obj = entry.jsonObject
        val source = obj["sourceId"]?.jsonPrimitive?.contentOrNull ?: error("missing sourceId")
        require(CVRegistry.exists(source)) { "unknown source '$source'" }
        require(obj["operator"]?.jsonPrimitive?.content in ModulationOperator.values().map { it.name }) { "missing or bad operator" }
        obj["waveform"]?.let { require(it.jsonPrimitive.content in Waveform.values().map { w -> w.name }) { "bad waveform" } }
        val dto = json.decodeFromJsonElement(ModulatorDto.serializer(), obj)
        require(dto.depth != 0f) { "depth is 0" }
        dto.copy(id = null)
    } catch (e: Exception) {
        logger.warn { "Shipped default $label: skipping a modulator: ${e.message}" }
        null
    }
}
