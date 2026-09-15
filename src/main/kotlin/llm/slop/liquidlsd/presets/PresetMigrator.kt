package llm.slop.liquidlsd.presets

import kotlinx.serialization.json.Json
import llm.slop.liquidlsd.models.*
import llm.slop.liquidlsd.rendering.SourceMeta
import mu.KotlinLogging
import java.io.File

/**
 * Preset schema migration/sanitization: normalizes a loaded [DeckPresetDto] against the
 * active visual source and feedback schemas, filling in defaults for any missing
 * parameters and stripping obsolete/legacy keys.
 *
 * Pure data transformation (with a read of the visual source's `meta.json` schema file to
 * determine expected parameters) - no preset file I/O or session serialization lives here.
 */
object PresetMigrator {
    private val logger = KotlinLogging.logger {}

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    private val LIBRARY_ROOT = File("library").absoluteFile

    /**
     * Sanitizes an incoming [DeckPresetDto] against the active visual source and feedback schemas.
     * Fills in defaults for any missing parameters and removes obsolete/legacy keys.
     * Returns a pair of the sanitized DTO and a boolean indicating whether any modifications occurred.
     */
    fun sanitizePresetDto(dto: DeckPresetDto): Pair<DeckPresetDto, Boolean> {
        if (dto.isEmpty) return Pair(dto, false)
        var modified = false

        val metaFile = File(LIBRARY_ROOT, "sources/${dto.visualSourceType}/meta.json")
        val sanitizedParams = LinkedHashMap<String, ParameterDto>()

        if (metaFile.exists()) {
            try {
                val meta = json.decodeFromString<SourceMeta>(metaFile.readText())
                val expectedNames = meta.parameters.map { it.name }.toSet()

                for (pMeta in meta.parameters) {
                    val existing = dto.parameters[pMeta.name]
                    if (existing != null) {
                        sanitizedParams[pMeta.name] = existing
                    } else {
                        modified = true
                        sanitizedParams[pMeta.name] = ParameterDto(
                            baseValue = pMeta.default,
                            baseMin = pMeta.defaultMin ?: pMeta.default,
                            baseMax = pMeta.defaultMax ?: pMeta.default,
                            randomizeBase = false,
                            modulators = emptyList()
                        )
                    }
                }

                if (dto.parameters.keys != expectedNames) {
                    modified = true
                }
            } catch (e: Exception) {
                logger.warn(e) { "Could not parse meta.json for source '${dto.visualSourceType}' during sanitization" }
                sanitizedParams.putAll(dto.parameters)
            }
        } else {
            sanitizedParams.putAll(dto.parameters)
        }

        // Canonical feedback parameters
        val canonicalFeedbackDefaults = mapOf(
            "fbDecay" to ParameterDto(0.0f, 0.0f, 0.0f, false, emptyList()),
            "fbGain" to ParameterDto(1.0f, 1.0f, 1.0f, false, emptyList()),
            "fbZoom" to ParameterDto(0.0f, 0.0f, 0.0f, false, emptyList()),
            "fbRotate" to ParameterDto(0.0f, 0.0f, 0.0f, false, emptyList()),
            "fbHueShift" to ParameterDto(0.0f, 0.0f, 0.0f, false, emptyList()),
            "fbBlur" to ParameterDto(0.0f, 0.0f, 0.0f, false, emptyList()),
            "fbChroma" to ParameterDto(0.0f, 0.0f, 0.0f, false, emptyList()),
            "fbMode" to ParameterDto(0.0f, 0.0f, 0.0f, false, emptyList()),
            "fbKaleido" to ParameterDto(1.0f, 1.0f, 1.0f, false, emptyList())
        )

        val sanitizedFeedback = LinkedHashMap<String, ParameterDto>()
        for ((key, defaultDto) in canonicalFeedbackDefaults) {
            val existing = dto.feedbackParameters[key]
            if (existing != null) {
                sanitizedFeedback[key] = existing
            } else {
                modified = true
                sanitizedFeedback[key] = defaultDto
            }
        }

        if (dto.feedbackParameters.keys != canonicalFeedbackDefaults.keys) {
            modified = true
        }

        val sanitizedGlobalAlpha = dto.globalAlpha ?: run {
            modified = true
            ParameterDto(1.0f, 1.0f, 1.0f, false, emptyList())
        }

        val sanitizedDto = if (modified) {
            dto.copy(
                parameters = sanitizedParams,
                feedbackParameters = sanitizedFeedback,
                globalAlpha = sanitizedGlobalAlpha
            )
        } else {
            dto
        }

        return Pair(sanitizedDto, modified)
    }
}
