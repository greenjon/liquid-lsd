package llm.slop.liquidlsd.presets

import llm.slop.liquidlsd.SessionContext
import llm.slop.liquidlsd.models.DeckPresetDto
import llm.slop.liquidlsd.models.ModulatorDto
import llm.slop.liquidlsd.models.ParameterDto
import llm.slop.liquidlsd.parameters.CvModulator
import llm.slop.liquidlsd.parameters.ModulatableParameter
import llm.slop.liquidlsd.rendering.Deck
import llm.slop.liquidlsd.ui.AppSettings
import llm.slop.liquidlsd.ui.UITheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PresetDependencyAnalyzerTest {

    private fun createSession(
        audioEngineEnabled: Boolean = true,
        randomizationEnabled: Boolean = true,
        midiEnabled: Boolean = true,
        sequencerEnabled: Boolean = true,
        showLfoCol: Boolean = true
    ): SessionContext {
        UITheme.audioEngineEnabled = audioEngineEnabled
        UITheme.randomizationEnabled = randomizationEnabled
        UITheme.midiEnabled = midiEnabled
        UITheme.sequencerEnabled = sequencerEnabled
        UITheme.showLfoCol = showLfoCol
        return SessionContext()
    }

    @Test
    fun testAnalyzeDtoWithNoDependencies() {
        val dto = DeckPresetDto(
            name = "Plain",
            visualSourceType = "mandala",
            parameters = mapOf(
                "speed" to ParameterDto(baseValue = 1f, baseMin = 1f, baseMax = 1f, randomizeBase = false, modulators = emptyList())
            ),
            feedbackParameters = emptyMap()
        )

        val deps = PresetDependencyAnalyzer.analyze(dto)
        assertTrue(deps.isEmpty)
        assertFalse(deps.usesAudio)
        assertFalse(deps.usesMidi)
        assertFalse(deps.usesLfo)
        assertFalse(deps.usesSeq)
        assertFalse(deps.usesRandomization)
    }

    @Test
    fun testAnalyzeDtoWithAudioAndFluxModulators() {
        val dto = DeckPresetDto(
            name = "AudioPulse",
            visualSourceType = "mandala",
            parameters = mapOf(
                "zoom" to ParameterDto(
                    baseValue = 1f, baseMin = 1f, baseMax = 1f, randomizeBase = false,
                    modulators = listOf(
                        ModulatorDto(sourceId = "audio_bass", operator = "ADD", depth = 0.5f)
                    )
                ),
                "flash" to ParameterDto(
                    baseValue = 0f, baseMin = 0f, baseMax = 0f, randomizeBase = false,
                    modulators = listOf(
                        ModulatorDto(sourceId = "audio_flux_amp", operator = "ADD", depth = 1f)
                    )
                )
            ),
            feedbackParameters = emptyMap()
        )

        val deps = PresetDependencyAnalyzer.analyze(dto)
        assertTrue(deps.usesAudio)
        assertFalse(deps.usesMidi)
        assertFalse(deps.usesLfo)
        assertFalse(deps.usesSeq)
    }

    @Test
    fun testAnalyzeDtoWithMidiAndLfoAndSeq() {
        val dto = DeckPresetDto(
            name = "ModCity",
            visualSourceType = "mandala",
            parameters = mapOf(
                "color" to ParameterDto(
                    baseValue = 0.5f, baseMin = 0.5f, baseMax = 0.5f, randomizeBase = false,
                    modulators = listOf(
                        ModulatorDto(sourceId = "midi_cc_16", operator = "ADD", depth = 1f),
                        ModulatorDto(sourceId = "lfo", operator = "MUL", depth = 0.2f),
                        ModulatorDto(sourceId = "seq", operator = "ADD", depth = 0.3f)
                    )
                )
            ),
            feedbackParameters = emptyMap()
        )

        val deps = PresetDependencyAnalyzer.analyze(dto)
        assertTrue(deps.usesMidi)
        assertTrue(deps.usesLfo)
        assertTrue(deps.usesSeq)
        assertFalse(deps.usesAudio)
    }

    @Test
    fun testAnalyzeDtoWithRandomization() {
        val dtoWithBaseRand = DeckPresetDto(
            name = "RandBase",
            visualSourceType = "mandala",
            parameters = mapOf(
                "zoom" to ParameterDto(baseValue = 1f, baseMin = 0.5f, baseMax = 2f, randomizeBase = true, modulators = emptyList())
            ),
            feedbackParameters = emptyMap()
        )
        val depsBase = PresetDependencyAnalyzer.analyze(dtoWithBaseRand)
        assertTrue(depsBase.usesRandomization)

        val dtoWithModRand = DeckPresetDto(
            name = "RandMod",
            visualSourceType = "mandala",
            parameters = mapOf(
                "zoom" to ParameterDto(
                    baseValue = 1f, baseMin = 1f, baseMax = 1f, randomizeBase = false,
                    modulators = listOf(
                        ModulatorDto(sourceId = "lfo", operator = "ADD", depth = 0.5f, depthMin = 0.1f, depthMax = 0.9f, randomizeDepth = true)
                    )
                )
            ),
            feedbackParameters = emptyMap()
        )
        val depsMod = PresetDependencyAnalyzer.analyze(dtoWithModRand)
        assertTrue(depsMod.usesRandomization)
        assertTrue(depsMod.usesLfo)
    }

    @Test
    fun testBypassedModulatorsIgnored() {
        val dto = DeckPresetDto(
            name = "Bypassed",
            visualSourceType = "mandala",
            parameters = mapOf(
                "zoom" to ParameterDto(
                    baseValue = 1f, baseMin = 1f, baseMax = 1f, randomizeBase = false,
                    modulators = listOf(
                        ModulatorDto(sourceId = "audio_bass", operator = "ADD", depth = 0.5f, bypassed = true)
                    )
                )
            ),
            feedbackParameters = emptyMap()
        )

        val deps = PresetDependencyAnalyzer.analyze(dto)
        assertFalse(deps.usesAudio)
    }

    @Test
    fun testGetIssuesWhenAllEnabled() {
        val session = createSession()
        val deps = PresetDependencies(usesAudio = true, usesMidi = true, usesLfo = true, usesSeq = true, usesRandomization = true)

        val issues = PresetDependencyAnalyzer.getIssues(deps, session)
        assertTrue(issues.isEmpty(), "When everything is enabled and columns visible, there should be no issues")
    }

    @Test
    fun testGetIssuesWhenAudioEngineDisabled() {
        val session = createSession(audioEngineEnabled = false)
        val deps = PresetDependencies(usesAudio = true)

        val issues = PresetDependencyAnalyzer.getIssues(deps, session)
        assertEquals(1, issues.size)
        assertEquals("Audio Engine Disabled", issues[0].title)
        assertTrue(issues[0].isAudioEngineIssue)
    }

    @Test
    fun testGetIssuesWhenLfoColumnHidden() {
        val session = createSession(showLfoCol = false)
        val deps = PresetDependencies(usesLfo = true)

        val issues = PresetDependencyAnalyzer.getIssues(deps, session)
        assertEquals(1, issues.size)
        assertEquals("LFO Column Hidden", issues[0].title)
        assertEquals("lfo", issues[0].affectedColumn)
    }

    @Test
    fun testGetIssuesWhenRandomizationDisabled() {
        val session = createSession(randomizationEnabled = false)
        val deps = PresetDependencies(usesRandomization = true)

        val issues = PresetDependencyAnalyzer.getIssues(deps, session)
        assertEquals(1, issues.size)
        assertEquals("Randomization Disabled", issues[0].title)
    }

    @Test
    fun testAnalyzeDtoWithFluxTransientAudioSourcesOnly() {
        val dto = DeckPresetDto(
            name = "FluxOnly",
            visualSourceType = "mandala",
            parameters = mapOf(
                "bass" to ParameterDto(
                    baseValue = 1f, baseMin = 1f, baseMax = 1f, randomizeBase = false,
                    modulators = listOf(
                        ModulatorDto(sourceId = "audio_flux_bass", operator = "ADD", depth = 0.8f)
                    )
                )
            ),
            feedbackParameters = emptyMap()
        )

        val deps = dto.analyzeDependencies()
        assertTrue(deps.usesAudio, "Spectral flux transient modulator should be recognized as usesAudio")
    }

    @Test
    fun testGetIssuesWhenMidiAndSequencerSubsystemsDisabled() {
        val session = createSession(midiEnabled = false, sequencerEnabled = false)
        val deps = PresetDependencies(usesMidi = true, usesSeq = true)

        val issues = deps.getIssues(session)
        assertEquals(2, issues.size)
        assertTrue(issues.any { it.title == "MIDI Disabled" })
        assertTrue(issues.any { it.title == "Sequencer Disabled" })
    }

    @Test
    fun testGetIssuesZeroAllocationCaching() {
        PresetDependencyAnalyzer.clearCache()
        val session = createSession(audioEngineEnabled = false)
        val deps = PresetDependencies(usesAudio = true)

        val firstCall = deps.getIssues(session)
        val secondCall = deps.getIssues(session)

        // Verify referential identity (cached immutable instance reused)
        kotlin.test.assertSame(firstCall, secondCall, "getIssues must return cached instance to prevent GC allocations")
    }
}
