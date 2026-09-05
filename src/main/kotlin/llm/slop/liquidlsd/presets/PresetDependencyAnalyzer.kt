package llm.slop.liquidlsd.presets

import llm.slop.liquidlsd.SessionContext
import llm.slop.liquidlsd.models.DeckPresetDto
import llm.slop.liquidlsd.models.ModulatorDto
import llm.slop.liquidlsd.models.ParameterDto
import llm.slop.liquidlsd.parameters.CvModulator
import llm.slop.liquidlsd.parameters.ModulatableParameter
import llm.slop.liquidlsd.rendering.Deck

data class PresetDependencies(
    val usesAudio: Boolean = false,
    val usesMidi: Boolean = false,
    val usesLfo: Boolean = false,
    val usesSeq: Boolean = false,
    val usesRandomization: Boolean = false
) {
    val isEmpty: Boolean
        get() = !usesAudio && !usesMidi && !usesLfo && !usesSeq && !usesRandomization
}

enum class DependencySeverity {
    WARNING,
    INFO
}

data class DependencyIssue(
    val title: String,
    val description: String,
    val severity: DependencySeverity = DependencySeverity.WARNING,
    val affectedColumn: String? = null,
    val isAudioEngineIssue: Boolean = false
)

object PresetDependencyAnalyzer {

    private val AUDIO_SOURCES = setOf(
        "audio_amp", "audio_bass", "audio_mid", "audio_high",
        "trigger_onset", "trigger_accent"
    )
    private val LFO_SOURCES = setOf("lfo", "beatPhase", "sampleAndHold")

    /**
     * Inspects a [DeckPresetDto] and summarizes all modulator types and features it utilizes.
     */
    fun analyze(dto: DeckPresetDto): PresetDependencies {
        var usesAudio = false
        var usesMidi = false
        var usesLfo = false
        var usesSeq = false
        var usesRandomization = false

        fun inspectParam(param: ParameterDto) {
            if (param.randomizeBase && param.baseMin != param.baseMax) {
                usesRandomization = true
            }
            if (!param.mappedMidiId.isNullOrBlank()) {
                usesMidi = true
            }

            for (mod in param.modulators) {
                if (mod.bypassed) continue

                val src = mod.sourceId
                when {
                    src in AUDIO_SOURCES -> usesAudio = true
                    src.startsWith("midi_cc_") -> usesMidi = true
                    src in LFO_SOURCES -> usesLfo = true
                    src == "seq" -> usesSeq = true
                }

                if (isModulatorRandomized(mod)) {
                    usesRandomization = true
                }
            }
        }

        dto.parameters.values.forEach(::inspectParam)
        dto.feedbackParameters.values.forEach(::inspectParam)
        dto.viewParameters.values.forEach(::inspectParam)
        dto.globalAlpha?.let(::inspectParam)

        return PresetDependencies(
            usesAudio = usesAudio,
            usesMidi = usesMidi,
            usesLfo = usesLfo,
            usesSeq = usesSeq,
            usesRandomization = usesRandomization
        )
    }

    /**
     * Inspects a live [Deck] instance and summarizes its active dependencies.
     */
    fun analyze(deck: Deck): PresetDependencies {
        if (deck.isEmpty) return PresetDependencies()

        var usesAudio = false
        var usesMidi = false
        var usesLfo = false
        var usesSeq = false
        var usesRandomization = false

        fun inspectParam(param: ModulatableParameter) {
            if (param.randomizeBase && param.baseMin != param.baseMax) {
                usesRandomization = true
            }
            @Suppress("DEPRECATION")
            if (!param.mappedMidiId.isNullOrBlank()) {
                usesMidi = true
            }

            for (mod in param.modulators) {
                if (mod.bypassed) continue

                val src = mod.sourceId
                when {
                    src in AUDIO_SOURCES -> usesAudio = true
                    src.startsWith("midi_cc_") -> usesMidi = true
                    src in LFO_SOURCES -> usesLfo = true
                    src == "seq" -> usesSeq = true
                }

                if (isModulatorRandomized(mod)) {
                    usesRandomization = true
                }
            }
        }

        deck.getAllRandomizableParameters().forEach(::inspectParam)

        return PresetDependencies(
            usesAudio = usesAudio,
            usesMidi = usesMidi,
            usesLfo = usesLfo,
            usesSeq = usesSeq,
            usesRandomization = usesRandomization
        )
    }

    private fun isModulatorRandomized(mod: ModulatorDto): Boolean {
        return (mod.randomizeDepth && mod.depthMin != mod.depthMax) ||
            (mod.randomizeSubdivision && mod.subdivisionMin != mod.subdivisionMax) ||
            (mod.randomizePhaseOffset && mod.phaseOffsetMin != mod.phaseOffsetMax) ||
            (mod.randomizeSlope && mod.slopeMin != mod.slopeMax) ||
            (mod.randomizeMorph && mod.morphMin != mod.morphMax) ||
            (mod.randomizeHold && mod.holdMin != mod.holdMax) ||
            (mod.randomizeDcOffset && mod.dcOffsetMin != mod.dcOffsetMax) ||
            (mod.randomizeAttackMs && mod.attackMsMin != mod.attackMsMax) ||
            (mod.randomizeDecayMs && mod.decayMsMin != mod.decayMsMax) ||
            (mod.randomizeSeqHold && mod.seqHoldMin != mod.seqHoldMax)
    }

    private fun isModulatorRandomized(mod: CvModulator): Boolean {
        return (mod.randomizeDepth && mod.depthMin != mod.depthMax) ||
            (mod.randomizeSubdivision && mod.subdivisionMin != mod.subdivisionMax) ||
            (mod.randomizePhaseOffset && mod.phaseOffsetMin != mod.phaseOffsetMax) ||
            (mod.randomizeSlope && mod.slopeMin != mod.slopeMax) ||
            (mod.randomizeMorph && mod.morphMin != mod.morphMax) ||
            (mod.randomizeHold && mod.holdMin != mod.holdMax) ||
            (mod.randomizeDcOffset && mod.dcOffsetMin != mod.dcOffsetMax) ||
            (mod.randomizeAttackMs && mod.attackMsMin != mod.attackMsMax) ||
            (mod.randomizeDecayMs && mod.decayMsMin != mod.decayMsMax) ||
            (mod.randomizeSeqHold && mod.seqHoldMin != mod.seqHoldMax)
    }

    /**
     * Compares the dependencies required by a preset against the active [SessionContext]
     * and returns any inactive engines or hidden columns affecting it.
     */
    fun getIssues(deps: PresetDependencies, session: SessionContext): List<DependencyIssue> {
        val issues = mutableListOf<DependencyIssue>()

        // 1. Audio Engine disabled check
        if (deps.usesAudio && !session.uiTheme.audioEngineEnabled) {
            issues.add(
                DependencyIssue(
                    title = "Audio Engine Disabled",
                    description = "Audio & Trigger modulators are inactive (0.0). Enable in Settings > Audio Engine.",
                    severity = DependencySeverity.WARNING,
                    isAudioEngineIssue = true
                )
            )
        }

        // 2. MIDI disabled check
        if (deps.usesMidi && !session.uiTheme.midiEnabled) {
            issues.add(
                DependencyIssue(
                    title = "MIDI Disabled",
                    description = "Preset uses MIDI CC modulation, but MIDI is disabled in Settings.",
                    severity = DependencySeverity.WARNING,
                    affectedColumn = "midi"
                )
            )
        }

        // 3. Sequencer disabled check
        if (deps.usesSeq && !session.uiTheme.sequencerEnabled) {
            issues.add(
                DependencyIssue(
                    title = "Sequencer Disabled",
                    description = "Preset uses Step Sequencer modulation, but Sequencer is disabled in Settings.",
                    severity = DependencySeverity.WARNING,
                    affectedColumn = "seq"
                )
            )
        }

        // 4. Hidden columns in Preset Grid
        if (deps.usesMidi && session.uiTheme.midiEnabled && !session.uiTheme.showMidiCol) {
            issues.add(
                DependencyIssue(
                    title = "MIDI Column Hidden",
                    description = "Preset uses MIDI CC modulation, but MIDI column is hidden in Preset Grid.",
                    severity = DependencySeverity.INFO,
                    affectedColumn = "midi"
                )
            )
        }
        if (deps.usesLfo && !session.uiTheme.showLfoCol) {
            issues.add(
                DependencyIssue(
                    title = "LFO Column Hidden",
                    description = "Preset uses LFO modulation, but LFO column is hidden in Preset Grid.",
                    severity = DependencySeverity.INFO,
                    affectedColumn = "lfo"
                )
            )
        }
        if (deps.usesSeq && session.uiTheme.sequencerEnabled && !session.uiTheme.showSeqCol) {
            issues.add(
                DependencyIssue(
                    title = "SEQ Column Hidden",
                    description = "Preset uses Step Sequencer modulation, but SEQ column is hidden in Preset Grid.",
                    severity = DependencySeverity.INFO,
                    affectedColumn = "seq"
                )
            )
        }
        if (deps.usesAudio && session.uiTheme.audioEngineEnabled && !session.uiTheme.showAudioCol) {
            issues.add(
                DependencyIssue(
                    title = "Audio Column Hidden",
                    description = "Preset uses Audio modulation, but AUD column is hidden in Preset Grid.",
                    severity = DependencySeverity.INFO,
                    affectedColumn = "audio"
                )
            )
        }

        // 5. Randomization disabled
        if (deps.usesRandomization && !session.uiTheme.randomizationEnabled) {
            issues.add(
                DependencyIssue(
                    title = "Randomization Disabled",
                    description = "Preset uses parameter randomization, but Randomization is disabled in Settings.",
                    severity = DependencySeverity.INFO
                )
            )
        }

        return issues
    }
}
