package llm.slop.liquidlsd.presets

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import llm.slop.liquidlsd.macro.MacroBank
import llm.slop.liquidlsd.macro.MacroBankSerializer
import llm.slop.liquidlsd.macro.MacroBinding
import llm.slop.liquidlsd.macro.MacroControl
import llm.slop.liquidlsd.macro.MacroCurve
import llm.slop.liquidlsd.macro.MacroCurveType
import llm.slop.liquidlsd.macro.MacroEngine
import llm.slop.liquidlsd.macro.MacroLinkMode
import llm.slop.liquidlsd.macro.MacroTargetType
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import llm.slop.liquidlsd.models.GeneratorDefaultDto
import llm.slop.liquidlsd.models.ParameterDto
import llm.slop.liquidlsd.models.applyDto
import llm.slop.liquidlsd.models.toGeneratorDefaultDto
import llm.slop.liquidlsd.rendering.Deck
import llm.slop.liquidlsd.rendering.DynamicVisualSource
import llm.slop.liquidlsd.rendering.ExternalVideoSource
import llm.slop.liquidlsd.rendering.VisualSource
import llm.slop.liquidlsd.rendering.isf.ISFVisualSource
import llm.slop.liquidlsd.ui.FileSystemManager
import mu.KotlinLogging
import java.io.File

/**
 * Central resolution and persistence engine for visual generator defaults.
 *
 * Provides a 3-tier fallback strategy:
 *   1. User-saved default (stored in library/generator_defaults/<sourceId>.json)
 *   2. The default shipped for a stock generator ([StockDefaults], resources/stock_defaults/sources/<sourceId>.json)
 *   3. Automated heuristic fallback for third-party shaders
 */
object GeneratorDefaults {
    private val logger = KotlinLogging.logger {}

    val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    var storageDir: File = FileSystemManager.getGeneratorDefaultsRoot()

    private val SELECTOR_REGEX = Regex("mode|type|select|shape|method|invert|wireframe", RegexOption.IGNORE_CASE)
    private val EXPONENTIAL_REGEX = Regex("speed|rate|frequency|decay|feedback", RegexOption.IGNORE_CASE)

    private val PRIORITY_NAMES = listOf(
        "speed", "rate", "zoom", "scale", "morph", "amount",
        "intensity", "density", "depth", "detail", "complexity", "iterations"
    )

    /**
     * The deck-agnostic path of [source]'s parameter [paramName]: `Deck/<displayName>/<param>`, the form a source
     * registers its parameters under once a deck label is put in front (see `VisualSource.getParameterPaths`).
     */
    fun deckParamId(source: VisualSource, paramName: String): String = "Deck/${source.displayName}/$paramName"

    fun sourceIdFor(source: VisualSource): String = when (source) {
        is DynamicVisualSource -> source.id
        else -> source.id.ifEmpty { source.displayName.lowercase().replace(" ", "_") }
    }

    /**
     * Resolves the default configuration for [source].
     * Resolves in order: user-saved default -> shipped default -> heuristic default. Nothing is written to disk here.
     */
    fun resolve(source: VisualSource): GeneratorDefaultDto {
        val sourceId = sourceIdFor(source)

        // 1. User-saved default
        if (hasUserDefault(sourceId)) {
            val file = File(storageDir, "$sourceId.json")
            try {
                return json.decodeFromString<GeneratorDefaultDto>(file.readText())
            } catch (e: Exception) {
                logger.warn(e) { "Failed to load generator default for $sourceId from ${file.path}, falling back to curated/heuristic" }
            }
        }

        // Base parameter baselines directly from the source instance
        val baseParams = source.parameters.mapValues { it.value.toGeneratorDefaultDto() }
        val baseAlpha = source.globalAlpha.toGeneratorDefaultDto()

        // 2. The default shipped with the app (resources/stock_defaults/sources/<sourceId>.json), laid over the shader's own
        val shipped = StockDefaults.forId("sources", sourceId)
        if (shipped != null) {
            val params = baseParams + StockDefaults.overlayParameters(shipped, "parameters", baseParams, "sources/$sourceId")
            val bank = shippedBank(shipped, source, params)
                ?: return resolveHeuristic(source, sourceId, params, baseAlpha)
            return GeneratorDefaultDto(
                version = 1,
                sourceId = sourceId,
                parameters = params,
                globalAlpha = baseAlpha,
                macroBank = bank
            )
        }

        // 3. Automated heuristic fallback
        return resolveHeuristic(source, sourceId, baseParams, baseAlpha)
    }

    /**
     * The 4-knob bank of a shipped default. A binding's `parameterId` is the bare parameter name (expanded to
     * [deckParamId]); a missing `targetType` means the base value and a missing range the parameter's own. Each knob
     * starts where it reproduces its parameter's starting value. A knob whose parameter the source lacks is left blank.
     */
    private fun shippedBank(file: JsonObject, source: VisualSource, params: Map<String, ParameterDto>): MacroBank? {
        val knobs = (file["macroBank"] as? JsonObject)?.get("knobs") as? JsonArray ?: return null
        return MacroBank(knobs = List(4) { i ->
            val k = knobs.getOrNull(i) as? JsonObject
            val b = (k?.get("bindings") as? JsonArray)?.firstOrNull() as? JsonObject
            val name = (b?.get("parameterId") as? JsonPrimitive)?.contentOrNull
            val param = name?.let { source.parameters[it] }
            if (k == null || b == null || param == null) {
                if (name != null) logger.warn { "Shipped default for ${sourceIdFor(source)}: knob ${i + 1} names unknown parameter '$name'" }
                return@List MacroControl(label = "", value = 0f, bindings = mutableListOf())
            }
            val filled = JsonObject(mapOf(
                "targetType" to JsonPrimitive(MacroTargetType.PARAM_BASE_VALUE.name),
                "minVal" to JsonPrimitive(param.minClamp),
                "maxVal" to JsonPrimitive(param.maxClamp)
            ) + b + ("parameterId" to JsonPrimitive(deckParamId(source, name))))
            val binding = json.decodeFromJsonElement(MacroBinding.serializer(), filled)
            val label = (k["label"] as? JsonPrimitive)?.contentOrNull ?: ""
            val start = params[name]?.baseValue ?: param.baseValue
            MacroControl(label = label, value = MacroCurve.inverse(start, binding), bindings = mutableListOf(binding))
        })
    }

    private fun resolveHeuristic(
        source: VisualSource,
        sourceId: String,
        baseParams: Map<String, llm.slop.liquidlsd.models.ParameterDto>,
        baseAlpha: llm.slop.liquidlsd.models.ParameterDto
    ): GeneratorDefaultDto {
        val candidateNames = if (source is ISFVisualSource) {
            source.header.INPUTS
                .filter { it.TYPE.equals("float", ignoreCase = true) && source.parameters.containsKey(it.NAME) }
                .map { it.NAME }
        } else {
            source.parameters.keys.toList()
        }

        val eligible = candidateNames.filterNot { SELECTOR_REGEX.containsMatchIn(it) }

        // Prioritize continuous floats
        val selected = mutableListOf<String>()
        for (keyword in PRIORITY_NAMES) {
            val match = eligible.firstOrNull {
                !selected.contains(it) && (it.equals(keyword, ignoreCase = true) || it.contains(keyword, ignoreCase = true))
            }
            if (match != null) {
                selected.add(match)
                if (selected.size == 4) break
            }
        }
        if (selected.size < 4) {
            for (cand in eligible) {
                if (!selected.contains(cand)) {
                    selected.add(cand)
                    if (selected.size == 4) break
                }
            }
        }

        val knobs = mutableListOf<MacroControl>()
        for (i in 0 until 4) {
            if (i < selected.size) {
                val paramName = selected[i]
                val param = source.parameters[paramName]!!
                val isExp = EXPONENTIAL_REGEX.containsMatchIn(paramName)
                val curve = if (isExp) MacroCurveType.EXPONENTIAL else MacroCurveType.LINEAR
                val binding = MacroBinding(
                    parameterId = deckParamId(source, paramName),
                    targetType = MacroTargetType.PARAM_BASE_VALUE,
                    minVal = param.minClamp,
                    maxVal = param.maxClamp,
                    curve = curve,
                    linkMode = MacroLinkMode.FULL
                )
                val knobVal = MacroCurve.inverse(param.baseValue, binding)
                // The whole readable name: the knob caption ellipsizes it to fit its strip, so nothing is cut here.
                val label = llm.slop.liquidlsd.ui.ParamLabels.of((source as? ISFVisualSource)?.header, paramName).uppercase()
                knobs.add(MacroControl(label = label, value = knobVal, bindings = mutableListOf(binding)))
            } else {
                knobs.add(MacroControl(label = "", value = 0f, bindings = mutableListOf()))
            }
        }

        return GeneratorDefaultDto(
            version = 1,
            sourceId = sourceId,
            parameters = baseParams,
            globalAlpha = baseAlpha,
            macroBank = MacroBank(knobs = knobs)
        )
    }

    /**
     * Applies the resolved default for [deck.source] to [deck] and [canonicalBankId].
     *
     * @return true when the bank already held bindings and the default replaced them with different ones.
     */
    fun applyToDeck(deck: Deck, deckLabel: String, canonicalBankId: String): Boolean {
        val defaultDto = resolve(deck.source)

        for ((key, pDto) in defaultDto.parameters) {
            deck.source.parameters[key]?.applyDto(pDto)
        }
        defaultDto.globalAlpha?.let { deck.source.globalAlpha.applyDto(it) }

        return installDefaultBank(deck, deckLabel, canonicalBankId)
    }

    /**
     * Replaces [canonicalBankId]'s knobs with the resolved default bank for [deck.source], leaving
     * parameter values alone (used for presets that carry no bank of their own).
     *
     * @return true when the bank already held bindings and the default replaced them with different ones.
     */
    fun installDefaultBank(deck: Deck, deckLabel: String, canonicalBankId: String): Boolean {
        val targetBank = MacroEngine.getBank(canonicalBankId)
            ?: MacroEngine.newBankFor(canonicalBankId).also { MacroEngine.registerBank(canonicalBankId, it) }
        val before = targetBank.knobs.map { it.bindings.toList() }
        MacroBankSerializer.installBankForDeck(resolve(deck.source).macroBank, targetBank, deckLabel)
        return before.any { it.isNotEmpty() } && before != targetBank.knobs.map { it.bindings.toList() }
    }

    /**
     * Persists the current parameters, globalAlpha, and macro bank of [deck] as the user default.
     */
    fun saveDefault(deck: Deck, canonicalBankId: String) {
        if (deck.source is ExternalVideoSource) return

        val sourceId = sourceIdFor(deck.source)
        val params = deck.source.parameters.mapValues { it.value.toGeneratorDefaultDto() }
        val alpha = deck.source.globalAlpha.toGeneratorDefaultDto()

        val residentBank = MacroEngine.getBank(canonicalBankId)
        val agnosticBank = residentBank?.let { b ->
            MacroBank(
                knobs = b.knobs.map { knob ->
                    knob.copy(
                        bindings = knob.bindings.map { binding ->
                            binding.copy(parameterId = toDeckAgnosticParamId(binding.parameterId))
                        }.toMutableList()
                    )
                }
            )
        }

        val dto = GeneratorDefaultDto(
            version = 1,
            sourceId = sourceId,
            parameters = params,
            globalAlpha = alpha,
            macroBank = agnosticBank
        )

        try {
            if (!storageDir.exists()) storageDir.mkdirs()
            File(storageDir, "$sourceId.json").writeText(json.encodeToString(dto))
            logger.info { "Saved generator default for $sourceId to ${storageDir.path}" }
        } catch (e: Exception) {
            logger.error(e) { "Failed to save generator default for $sourceId" }
        }
    }

    fun deleteDefault(sourceId: String) {
        val file = File(storageDir, "$sourceId.json")
        if (file.exists()) {
            file.delete()
            logger.info { "Deleted generator default for $sourceId" }
        }
    }

    fun hasUserDefault(sourceId: String): Boolean =
        File(storageDir, "$sourceId.json").exists()

    fun toDeckAgnosticParamId(id: String): String {
        val slashIdx = id.indexOf('/')
        return if (slashIdx > 0 && id.substring(0, slashIdx).startsWith("Deck")) {
            "Deck/" + id.substring(slashIdx + 1)
        } else {
            id
        }
    }
}
