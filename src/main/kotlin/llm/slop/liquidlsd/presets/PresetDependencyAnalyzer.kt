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

    private val LFO_SOURCES = setOf("lfo", "beatPhase", "sampleAndHold")

    private fun isAudioSource(sourceId: String): Boolean {
        return sourceId.startsWith("audio_") || sourceId.startsWith("trigger_")
    }

    // Zero-allocation issue cache: 10-bit key space (5 deps bits + 5 settings bits = 1024 slots)
    private val issueCache = arrayOfNulls<List<DependencyIssue>>(1024)

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
                    isAudioSource(src) -> usesAudio = true
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
                    isAudioSource(src) -> usesAudio = true
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
     *
     * Results are memoized in an internal zero-allocation lookup table across frames.
     */
    fun getIssues(deps: PresetDependencies, session: SessionContext): List<DependencyIssue> {
        val theme = session.uiTheme
        val depsKey = (if (deps.usesAudio) 1 else 0) or
            (if (deps.usesMidi) 2 else 0) or
            (if (deps.usesLfo) 4 else 0) or
            (if (deps.usesSeq) 8 else 0) or
            (if (deps.usesRandomization) 16 else 0)

        val settingsKey = (if (theme.audioEngineEnabled) 1 else 0) or
            (if (theme.midiEnabled) 2 else 0) or
            (if (theme.sequencerEnabled) 4 else 0) or
            (if (theme.showLfoCol) 8 else 0) or
            (if (theme.randomizationEnabled) 16 else 0)

        val cacheIndex = depsKey or (settingsKey shl 5)
        val cached = issueCache[cacheIndex]
        if (cached != null) return cached

        val issues = mutableListOf<DependencyIssue>()

        // 1. Audio Engine disabled check
        if (deps.usesAudio && !theme.audioEngineEnabled) {
            issues.add(
                DependencyIssue(
                    title = "Audio Engine Disabled",
                    description = "Audio modulators are inactive (0.0). Enable in Settings > Audio Engine.",
                    severity = DependencySeverity.WARNING,
                    affectedColumn = "audio",
                    isAudioEngineIssue = true
                )
            )
        }

        // 2. MIDI disabled check
        if (deps.usesMidi && !theme.midiEnabled) {
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
        if (deps.usesSeq && !theme.sequencerEnabled) {
            issues.add(
                DependencyIssue(
                    title = "Sequencer Disabled",
                    description = "Preset uses Step Sequencer modulation, but Sequencer is disabled in Settings.",
                    severity = DependencySeverity.WARNING,
                    affectedColumn = "seq"
                )
            )
        }

        // 4. LFO column hidden check
        if (deps.usesLfo && !theme.showLfoCol) {
            issues.add(
                DependencyIssue(
                    title = "LFO Column Hidden",
                    description = "Preset uses LFO modulation, but LFO column is hidden in Preset Grid.",
                    severity = DependencySeverity.INFO,
                    affectedColumn = "lfo"
                )
            )
        }

        // 5. Randomization disabled
        if (deps.usesRandomization && !theme.randomizationEnabled) {
            issues.add(
                DependencyIssue(
                    title = "Randomization Disabled",
                    description = "Preset uses parameter randomization, but Randomization is disabled in Settings.",
                    severity = DependencySeverity.INFO
                )
            )
        }

        val result = if (issues.isEmpty()) emptyList() else java.util.Collections.unmodifiableList(issues)
        issueCache[cacheIndex] = result
        return result
    }

    /** Clears the memoized issue cache. */
    fun clearCache() {
        issueCache.fill(null)
    }
}

/** Extension function to analyze dependencies of a [DeckPresetDto]. */
fun DeckPresetDto.analyzeDependencies(): PresetDependencies = PresetDependencyAnalyzer.analyze(this)

/** Extension function to analyze active dependencies of a [Deck]. */
fun Deck.analyzeDependencies(): PresetDependencies = PresetDependencyAnalyzer.analyze(this)

/** Extension function to resolve active dependency issues against a [SessionContext]. */
fun PresetDependencies.getIssues(session: SessionContext): List<DependencyIssue> = PresetDependencyAnalyzer.getIssues(this, session)
