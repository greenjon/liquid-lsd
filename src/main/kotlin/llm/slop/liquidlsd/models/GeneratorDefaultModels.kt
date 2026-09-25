package llm.slop.liquidlsd.models

import kotlinx.serialization.Serializable
import llm.slop.liquidlsd.macro.MacroBank
import llm.slop.liquidlsd.parameters.ModulatableParameter

/**
 * Serializable DTO representing default parameters, alpha, and macro knob bindings for a visual generator.
 *
 * Parameters are keyed by parameter name (e.g. "Lobes", "Speed") rather than deck-specific paths.
 * The bundled [macroBank] uses deck-agnostic paths (e.g. "Deck/Lobes") that are dynamically remapped
 * to whichever deck the generator is instantiated on.
 */
@Serializable
data class GeneratorDefaultDto(
    val version: Int = 1,
    val sourceId: String,
    val parameters: Map<String, ParameterDto> = emptyMap(),
    val globalAlpha: ParameterDto? = null,
    val macroBank: MacroBank? = null
)

/** Strips hardware MIDI mappings so defaults can be reused safely on any deck without collision. */
fun ParameterDto.stripMidi(): ParameterDto =
    copy(mappedMidiId = null, midiMapMin = 0f, midiMapMax = 1f)

/** Converts a [ModulatableParameter] to [ParameterDto] stripped of MIDI mappings for default persistence. */
fun ModulatableParameter.toGeneratorDefaultDto(): ParameterDto =
    toDto().stripMidi()
