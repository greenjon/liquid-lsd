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
import llm.slop.liquidlsd.models.GeneratorDefaultDto
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
 *   2. Hand-curated defaults for bundled stock generators
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

    private data class KnobSpec(
        val paramName: String,
        val label: String,
        val curve: MacroCurveType = MacroCurveType.LINEAR
    )

    private val CURATED_KNOBS: Map<String, List<KnobSpec>> = mapOf(
        "mandala" to listOf(
            KnobSpec("Lobes", "LOBES"),
            KnobSpec("Thickness", "THICK"),
            KnobSpec("Depth", "DEPTH"),
            KnobSpec("Hue Offset", "HUE")
        ),
        "dynamic_spiral" to listOf(
            KnobSpec("Speed", "SPEED", MacroCurveType.EXPONENTIAL),
            KnobSpec("Scale", "SCALE"),
            KnobSpec("WaveAmp", "WAVE AMP"),
            KnobSpec("Shear", "SHEAR")
        ),
        "icosa_h3" to listOf(
            KnobSpec("Morph", "MORPH"),
            KnobSpec("StellationBoost", "BOOST"),
            KnobSpec("Zoom", "ZOOM"),
            KnobSpec("HueOffset", "HUE")
        ),
        "domain_warp_fluid" to listOf(
            KnobSpec("WarpStrength", "WARP"),
            KnobSpec("Swirl", "SWIRL"),
            KnobSpec("Speed", "SPEED", MacroCurveType.EXPONENTIAL),
            KnobSpec("Zoom", "ZOOM")
        ),
        "gyroid_hyperspace" to listOf(
            KnobSpec("FlightSpeed", "SPEED", MacroCurveType.EXPONENTIAL),
            KnobSpec("WallThickness", "WALLS"),
            KnobSpec("Frequency", "FREQ", MacroCurveType.EXPONENTIAL),
            KnobSpec("CoreGlow", "GLOW")
        ),
        "celestial_engine" to listOf(
            KnobSpec("Speed", "SPEED", MacroCurveType.EXPONENTIAL),
            KnobSpec("Symmetries", "SYMMETRY"),
            KnobSpec("PhaseTwist", "TWIST"),
            KnobSpec("Glow", "GLOW")
        ),
        "hyper_slice" to listOf(
            KnobSpec("SliceOffset", "SLICE"),
            KnobSpec("RotateXW", "ROTATE XW"),
            KnobSpec("Morph", "MORPH"),
            KnobSpec("Zoom", "ZOOM")
        ),
        "chladni_cymatics" to listOf(
            KnobSpec("FrequencyM", "FREQ M", MacroCurveType.EXPONENTIAL),
            KnobSpec("FrequencyN", "FREQ N", MacroCurveType.EXPONENTIAL),
            KnobSpec("VibrationSpeed", "SPEED", MacroCurveType.EXPONENTIAL),
            KnobSpec("NodeSharpness", "NODES")
        )
    )

    fun sourceIdFor(source: VisualSource): String = when (source) {
        is DynamicVisualSource -> source.id
        else -> source.id.ifEmpty { source.displayName.lowercase().replace(" ", "_") }
    }

    /**
     * Resolves the default configuration for [source].
     * Resolves in order: User override -> Curated stock default -> Heuristic default.
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

        // 2. Curated stock defaults
        val curatedSpecs = CURATED_KNOBS[sourceId]
        if (curatedSpecs != null) {
            val knobs = mutableListOf<MacroControl>()
            for (i in 0 until 4) {
                val spec = curatedSpecs.getOrNull(i)
                val param = spec?.paramName?.let { source.parameters[it] }
                if (spec != null && param != null) {
                    val binding = MacroBinding(
                        parameterId = "Deck/${spec.paramName}",
                        targetType = MacroTargetType.PARAM_BASE_VALUE,
                        minVal = param.minClamp,
                        maxVal = param.maxClamp,
                        curve = spec.curve,
                        linkMode = MacroLinkMode.FULL
                    )
                    val knobVal = MacroCurve.inverse(param.baseValue, binding)
                    knobs.add(MacroControl(label = spec.label, value = knobVal, bindings = mutableListOf(binding)))
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

        // 3. Automated heuristic fallback
        return resolveHeuristic(source, sourceId, baseParams, baseAlpha)
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
                    parameterId = "Deck/$paramName",
                    targetType = MacroTargetType.PARAM_BASE_VALUE,
                    minVal = param.minClamp,
                    maxVal = param.maxClamp,
                    curve = curve,
                    linkMode = MacroLinkMode.FULL
                )
                val knobVal = MacroCurve.inverse(param.baseValue, binding)
                val rawLabel = if (source is ISFVisualSource) {
                    source.header.INPUTS.find { it.NAME == paramName }?.LABEL ?: paramName
                } else {
                    paramName
                }
                val label = rawLabel.uppercase().take(8)
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
     */
    fun applyToDeck(deck: Deck, deckLabel: String, canonicalBankId: String) {
        val defaultDto = resolve(deck.source)

        for ((key, pDto) in defaultDto.parameters) {
            deck.source.parameters[key]?.applyDto(pDto)
        }
        defaultDto.globalAlpha?.let { deck.source.globalAlpha.applyDto(it) }

        val targetBank = MacroEngine.getBank(canonicalBankId)
            ?: MacroEngine.newBankFor(canonicalBankId).also { MacroEngine.registerBank(canonicalBankId, it) }
        MacroBankSerializer.installBankForDeck(defaultDto.macroBank, targetBank, deckLabel)
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
