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
        ),
        "mandelbox" to listOf(
            KnobSpec("Scale", "SCALE"),
            KnobSpec("FoldLimit", "FOLD"),
            KnobSpec("MinRadius", "RADIUS"),
            KnobSpec("FlightSpeed", "SPEED", MacroCurveType.EXPONENTIAL)
        ),
        "kifs" to listOf(
            KnobSpec("SurfaceType", "TYPE"),
            KnobSpec("Scale", "SCALE"),
            KnobSpec("TwistY", "TWIST"),
            KnobSpec("MorphSpeed", "MORPH", MacroCurveType.EXPONENTIAL)
        ),
        "mandelbulb" to listOf(
            KnobSpec("Power", "POWER"),
            KnobSpec("PowerSwing", "SWING"),
            KnobSpec("JuliaMix", "JULIA"),
            KnobSpec("FlightSpeed", "SPEED", MacroCurveType.EXPONENTIAL)
        ),
        "pseudo_kleinian" to listOf(
            KnobSpec("SlabSize", "SLAB"),
            KnobSpec("InversionRadius", "INVERT"),
            KnobSpec("CoreSize", "CORE"),
            KnobSpec("FlightSpeed", "SPEED", MacroCurveType.EXPONENTIAL)
        ),
        "apollonian" to listOf(
            KnobSpec("CellSize", "CELL"),
            KnobSpec("InversionRadius", "INVERT"),
            KnobSpec("Twist", "TWIST"),
            KnobSpec("FlightSpeed", "SPEED", MacroCurveType.EXPONENTIAL)
        ),
        "escape_time" to listOf(
            KnobSpec("JuliaAngle", "ANGLE"),
            KnobSpec("JuliaRadius", "RADIUS"),
            KnobSpec("Power", "POWER"),
            KnobSpec("MorphSpeed", "MORPH", MacroCurveType.EXPONENTIAL)
        ),
        "nebula_volume" to listOf(
            KnobSpec("Density", "DENSITY"),
            KnobSpec("Coverage", "COVER"),
            KnobSpec("Turbulence", "TURB"),
            KnobSpec("FlightSpeed", "SPEED", MacroCurveType.EXPONENTIAL)
        ),
        "reaction_diffusion" to listOf(
            KnobSpec("Regime", "REGIME"),
            KnobSpec("Diffusion", "DIFFUSE"),
            KnobSpec("SimSpeed", "SPEED", MacroCurveType.EXPONENTIAL),
            KnobSpec("Relief", "RELIEF")
        ),
        "fluid_ink" to listOf(
            KnobSpec("Force", "FORCE"),
            KnobSpec("Swirl", "SWIRL"),
            KnobSpec("Turbulence", "TURB"),
            KnobSpec("DyeFade", "FADE")
        ),
        "lenia" to listOf(
            KnobSpec("GrowthCenter", "CENTER"),
            KnobSpec("GrowthWidth", "WIDTH"),
            KnobSpec("Radius", "RADIUS"),
            KnobSpec("SimSpeed", "SPEED", MacroCurveType.EXPONENTIAL)
        ),
        "quasicrystal" to listOf(
            KnobSpec("Frequency", "FREQ"),
            KnobSpec("Sharpness", "SHARP"),
            KnobSpec("SpinSpeed", "SPIN"),
            KnobSpec("DriftSpeed", "DRIFT")
        ),
        "thin_film" to listOf(
            KnobSpec("FlowSpeed", "FLOW"),
            KnobSpec("Scale", "SCALE"),
            KnobSpec("FilmThickness", "FILM"),
            KnobSpec("Turbulence", "TURB")
        ),
        "tunnel_scenes" to listOf(
            KnobSpec("Speed", "SPEED"),
            KnobSpec("Twist", "TWIST"),
            KnobSpec("Glow", "GLOW"),
            KnobSpec("Sway", "SWAY")
        ),
        "video_feedback" to listOf(
            KnobSpec("Zoom", "ZOOM"),
            KnobSpec("Rotation", "ROT"),
            KnobSpec("HueShift", "HUE"),
            KnobSpec("Decay", "DECAY")
        ),
        "hyperbolic_tiling" to listOf(
            KnobSpec("Speed", "SPEED"),
            KnobSpec("Drift", "DRIFT"),
            KnobSpec("EdgeWidth", "EDGE"),
            KnobSpec("Glow", "GLOW")
        ),
        "cyclic_ca" to listOf(
            KnobSpec("States", "STATES"),
            KnobSpec("Threshold", "THRESH"),
            KnobSpec("Speed", "SPEED"),
            KnobSpec("Density", "SEED")
        ),
        "metaballs" to listOf(
            KnobSpec("BallSize", "SIZE"),
            KnobSpec("OrbitSpeed", "ORBIT"),
            KnobSpec("Merge", "MERGE"),
            KnobSpec("Softness", "SOFT")
        ),
        "wave_ripples" to listOf(
            KnobSpec("Wavelength", "WAVE"),
            KnobSpec("WaveSpeed", "SPEED"),
            KnobSpec("OrbitSpeed", "ORBIT"),
            KnobSpec("Falloff", "FALL")
        ),
        "harmonograph" to listOf(
            KnobSpec("Detune", "DETUNE"),
            KnobSpec("DriftSpeed", "DRIFT"),
            KnobSpec("Damping", "DAMP"),
            KnobSpec("Glow", "GLOW")
        ),
        "aurora_ribbons" to listOf(
            KnobSpec("CurtainHeight", "HEIGHT"),
            KnobSpec("WaveAmp", "AMP"),
            KnobSpec("WaveSpeed", "SPEED"),
            KnobSpec("Shimmer", "SHIMMER")
        ),
        "isometric_voxels" to listOf(
            KnobSpec("CellSize", "CELL"),
            KnobSpec("MaxHeight", "HEIGHT"),
            KnobSpec("Speed", "SPEED"),
            KnobSpec("Frequency", "SCALE")
        ),
        "halftone_matrix" to listOf(
            KnobSpec("Density", "DENSITY"),
            KnobSpec("Sharpness", "SHARP"),
            KnobSpec("PatternSpeed", "SPEED"),
            KnobSpec("PatternScale", "SCALE")
        ),
        "kali_set" to listOf(
            KnobSpec("OrbitRadius", "RADIUS"),
            KnobSpec("Speed", "SPEED"),
            KnobSpec("Zoom", "ZOOM"),
            KnobSpec("Twist", "TWIST")
        ),
        "girih_stars" to listOf(
            KnobSpec("Angle", "ANGLE"),
            KnobSpec("Sweep", "SWEEP"),
            KnobSpec("Speed", "SPEED"),
            KnobSpec("Interlace", "WEAVE")
        ),
        "glitch_blocks" to listOf(
            KnobSpec("Intensity", "GLITCH"),
            KnobSpec("BlockSize", "BLOCK"),
            KnobSpec("Rate", "RATE"),
            KnobSpec("Tear", "TEAR")
        ),
        "voronoi_cells" to listOf(
            KnobSpec("CellScale", "SCALE"),
            KnobSpec("Jitter", "JITTER"),
            KnobSpec("Speed", "SPEED"),
            KnobSpec("EdgeGlow", "GLOW")
        ),
        // Fractal Flame: the global handles that move every xform at once (one binding does what 6+ would otherwise)
        "fractal_flame" to listOf(
            KnobSpec("Magnify", "ZOOM"),   // not Zoom: that one morphs with the genome (stored 0..1, 0.5 = 1x, exponential already)
            KnobSpec("Spin", "SPIN"),
            KnobSpec("Warp", "WARP"),
            KnobSpec("Mutation", "EVOLVE")   // how far the next target drifts from the current genome
        )
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
                        parameterId = deckParamId(source, spec.paramName),
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
                    parameterId = deckParamId(source, paramName),
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
