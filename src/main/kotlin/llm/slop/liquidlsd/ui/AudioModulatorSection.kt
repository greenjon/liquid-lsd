package llm.slop.liquidlsd.ui

import imgui.ImGui
import imgui.type.ImInt
import llm.slop.liquidlsd.SessionContext
import llm.slop.liquidlsd.parameters.AudioFollowerMode
import llm.slop.liquidlsd.parameters.CvModulator
import llm.slop.liquidlsd.parameters.ModulatableParameter

/**
 * Dedicated UI section for Audio CV modulators (audio_amp, audio_bass, audio_mid, audio_high, their flux, and the
 * song-section signals audio_energy / audio_drop). Renders the dynamics Envelope Follower preset dropdown,
 * contextual Custom Attack/Decay sliders, DC Offset, and Depth. Section signals are already shaped, so they get
 * only a signal choice and the range.
 */
object AudioModulatorSection {

    private val BAND_LABELS = arrayOf(
        "Full Mix (Amp)",
        "Low / Bass (Kick)",
        "Mid (Snare)",
        "High (Hi-Hat)"
    )

    private val RMS_SOURCES = arrayOf("audio_amp", "audio_bass", "audio_mid", "audio_high")
    private val FLUX_SOURCES = arrayOf("audio_flux_amp", "audio_flux_bass", "audio_flux_mid", "audio_flux_high")
    private val SECTION_SOURCES = arrayOf("audio_energy", "audio_drop")
    private val SECTION_LABELS = arrayOf("Energy (vs. the song)", "Drop (4-bar hit)")

    /** Title for an Audio-tab source in modulator headers, or null for any other source. */
    fun sourceLabel(sourceId: String): String? = when (sourceId) {
        "audio_amp" -> "Full Mix (RMS)"
        "audio_bass" -> "Low / Bass (RMS)"
        "audio_mid" -> "Mid (RMS)"
        "audio_high" -> "High (RMS)"
        "audio_flux_amp" -> "Full Mix (Flux)"
        "audio_flux_bass" -> "Low / Kick (Flux)"
        "audio_flux_mid" -> "Mid / Snare (Flux)"
        "audio_flux_high" -> "High / Hat (Flux)"
        "audio_energy" -> "Energy (Section)"
        "audio_drop" -> "Drop (Section)"
        else -> null
    }

    fun draw(
        session: SessionContext,
        param: ModulatableParameter,
        existing: CvModulator,
        paramKey: String,
        modulatorIndex: Int,
        themeColor: Int,
        onReplace: (CvModulator) -> Unit
    ) {
        val bypassed = existing.bypassed
        val fontScale = 0.95f

        val isTransient = existing.sourceId.startsWith("audio_flux_")
        val isSection = llm.slop.liquidlsd.cv.isSectionSource(existing.sourceId)
        val currentBandIdx = when (existing.sourceId) {
            "audio_amp", "audio_flux_amp"   -> 0
            "audio_bass", "audio_flux_bass" -> 1
            "audio_mid", "audio_flux_mid"   -> 2
            "audio_high", "audio_flux_high" -> 3
            else                            -> 0
        }

        if (bypassed) ImGui.popStyleVar()

        // 1. Detection Mode (Continuous vs Transient)
        session.uiTheme.body("Detection Mode:")
        ImGui.sameLine(0f, 8f * fontScale)

        val btnW = 125f * fontScale
        val btnH = ImGui.getFrameHeight()

        // Continuous / Transient / Section toggles
        if (ButtonChrome.toggleButton("Continuous (RMS)##mode_cont_${existing.id}", !isTransient && !isSection, themeColor, btnW, btnH) && (isTransient || isSection)) {
            onReplace(existing.copy(sourceId = RMS_SOURCES[currentBandIdx]))
        }
        itemTooltip("Continuous Envelope: Tracks continuous volume and sustained body of audio frequencies.")

        ImGui.sameLine(0f, 4f * fontScale)

        if (ButtonChrome.toggleButton("Transient (Flux)##mode_flux_${existing.id}", isTransient, themeColor, btnW, btnH) && !isTransient) {
            onReplace(existing.copy(sourceId = FLUX_SOURCES[currentBandIdx]))
        }
        itemTooltip("Transient Trigger: Tracks sudden onsets, drum strikes, and energy growth (Spectral Flux).")

        ImGui.sameLine(0f, 4f * fontScale)

        if (ButtonChrome.toggleButton("Section##mode_section_${existing.id}", isSection, themeColor, btnW, btnH) && !isSection) {
            onReplace(existing.copy(sourceId = SECTION_SOURCES[0]))
        }
        itemTooltip("Song Section: Follows the shape of the song (breakdowns and drops) rather than its beats.")

        ImGui.spacing()

        if (isSection) {
            drawSectionSignal(session, existing, fontScale, onReplace)
            if (bypassed) ImGui.pushStyleVar(imgui.flag.ImGuiStyleVar.Alpha, 0.5f)
            ImGui.spacing()
            drawRange(session, param, existing, paramKey, modulatorIndex, themeColor, onReplace)
            return
        }

        // 2. Frequency Band Selector
        session.uiTheme.body("Frequency Band:")
        ImGui.sameLine(0f, 8f * fontScale)
        ImGui.pushItemWidth(180f * fontScale)
        val bandIdxWrapper = ImInt(currentBandIdx)
        val bandChanged = ImGui.combo("##band_${existing.id}", bandIdxWrapper, BAND_LABELS)
        llm.slop.liquidlsd.tour.WidgetRegistry.note { "aud_band" }
        if (bandChanged) {
            val selectedIdx = bandIdxWrapper.get().coerceIn(0, 3)
            val newSource = if (isTransient) FLUX_SOURCES[selectedIdx] else RMS_SOURCES[selectedIdx]
            onReplace(existing.copy(sourceId = newSource))
        }
        itemTooltip("Frequency Band:\nFull Mix: Entire frequency spectrum\nLow / Bass: Sub and kick frequencies (< 150Hz)\nMid: Vocals, synths, and snares (150Hz - 2.5kHz)\nHigh: Cymbals, hi-hats, and air (> 2.5kHz)")
        ImGui.popItemWidth()

        ImGui.spacing()

        // 3. Envelope / Response Preset Dropdown
        session.uiTheme.body("Response Profile:")
        ImGui.sameLine(0f, 8f * fontScale)

        val modes = AudioFollowerMode.values()
        val modeLabels = modes.map { it.label }.toTypedArray()
        val currentModeIdx = modes.indexOf(existing.followerMode).coerceAtLeast(0)
        val modeIdxWrapper = ImInt(currentModeIdx)

        ImGui.pushItemWidth(180f * fontScale)
        val profileChanged = ImGui.combo("##follower_mode_${existing.id}", modeIdxWrapper, modeLabels)
        llm.slop.liquidlsd.tour.WidgetRegistry.note { "aud_profile" }
        if (profileChanged) {
            val selectedMode = modes[modeIdxWrapper.get()]
            if (selectedMode == AudioFollowerMode.CUSTOM) {
                val curAtt = if (existing.attackMs > 0f || existing.decayMs > 0f) existing.attackMs else existing.followerMode.defaultAttackMs
                val curDec = if (existing.attackMs > 0f || existing.decayMs > 0f) existing.decayMs else existing.followerMode.defaultDecayMs
                onReplace(existing.copy(
                    followerMode = AudioFollowerMode.CUSTOM,
                    attackMs = curAtt,
                    decayMs = curDec,
                    attackMsMin = curAtt,
                    attackMsMax = curAtt,
                    decayMsMin = curDec,
                    decayMsMax = curDec
                ))
            } else {
                onReplace(existing.copy(
                    followerMode = selectedMode,
                    attackMs = selectedMode.defaultAttackMs,
                    decayMs = selectedMode.defaultDecayMs,
                    attackMsMin = selectedMode.defaultAttackMs,
                    attackMsMax = selectedMode.defaultAttackMs,
                    decayMsMin = selectedMode.defaultDecayMs,
                    decayMsMax = selectedMode.defaultDecayMs
                ))
            }
        }
        itemTooltip("Dynamics / Smoothing:\nInstant: 1-frame strobe or raw amplitude flutter\nStrobe / Snap: 0ms attack, 35ms decay snap\nPunchy (Accent): 5ms attack, 150ms decay\nSmooth Swell: 40ms attack, 400ms decay\nSlow Bloom: 100ms attack, 900ms decay\nAmbient Drift: 250ms attack, 1800ms decay\nCustom…: Freely adjust Attack and Decay sliders")
        ImGui.popItemWidth()
        if (bypassed) ImGui.pushStyleVar(imgui.flag.ImGuiStyleVar.Alpha, 0.5f)
        ImGui.spacing()

        // 2. Custom Attack and Decay Sliders
        if (existing.followerMode == AudioFollowerMode.CUSTOM) {
            // Attack (ms) Slider — uses asymmetric offset (-20/+50) so kept inline for onRandomizableChanged.
            val attackCbs = cvModulatorSlider(
                existing = existing,
                getValue = { attackMs }, getMin = { attackMsMin }, getMax = { attackMsMax },
                minLimit = 0f, maxLimit = 500f, defaultOffset = 20f,
                copyWithRandomize = { enabled, nMin, nMax -> copy(randomizeAttackMs = enabled, attackMsMin = nMin, attackMsMax = nMax) },
                copyWithRange   = { sMin, sMax, v -> copy(attackMsMin = sMin, attackMsMax = sMax, attackMs = v) },
                copyWithValue   = { v -> copy(attackMs = v, attackMsMin = v, attackMsMax = v) },
                randomizeNow    = { randomizeAttackMs() },
                onReplace = onReplace,
            )
            CustomRangeSlider.drawCustomRangeSlider(
                session = session,
                idPrefix = "${existing.id}_att",
                label = "Attack",
                themeColor = themeColor,
                paramKey = paramKey, modulatorIndex = modulatorIndex, propertyName = "attackMs",
                currentValue = existing.attackMs,
                currentMin = existing.attackMsMin,
                currentMax = existing.attackMsMax,
                minLimit = 0f, maxLimit = 500f, defaultValue = 0f,
                isRandomizable = existing.randomizeAttackMs,
                isRandomizeDisabled = param.isRandomizeDisabled,
                randomizeDisabledTooltip = llm.slop.liquidlsd.rendering.Mixer.FORBIDDEN_RANDOMIZE_TOOLTIP,
                formatValue = { "${it.toInt()}ms" },
                onRandomizableChanged = attackCbs.onRandomizableChanged,
                onRandomizeNow        = attackCbs.onRandomizeNow,
                onRangeChanged        = attackCbs.onRangeChanged,
                onValueChanged        = attackCbs.onValueChanged,
            )
            ImGui.spacing()

            // Decay (ms) Slider — uses asymmetric offset (-100/+200) so kept inline for onRandomizableChanged.
            val decayCbs = cvModulatorSlider(
                existing = existing,
                getValue = { decayMs }, getMin = { decayMsMin }, getMax = { decayMsMax },
                minLimit = 10f, maxLimit = 3000f, defaultOffset = 100f,
                copyWithRandomize = { enabled, nMin, nMax -> copy(randomizeDecayMs = enabled, decayMsMin = nMin, decayMsMax = nMax) },
                copyWithRange   = { sMin, sMax, v -> copy(decayMsMin = sMin, decayMsMax = sMax, decayMs = v) },
                copyWithValue   = { v -> copy(decayMs = v, decayMsMin = v, decayMsMax = v) },
                randomizeNow    = { randomizeDecayMs() },
                onReplace = onReplace,
            )
            CustomRangeSlider.drawCustomRangeSlider(
                session = session,
                idPrefix = "${existing.id}_dec",
                label = "Decay",
                themeColor = themeColor,
                paramKey = paramKey, modulatorIndex = modulatorIndex, propertyName = "decayMs",
                currentValue = existing.decayMs,
                currentMin = existing.decayMsMin,
                currentMax = existing.decayMsMax,
                minLimit = 10f, maxLimit = 3000f, defaultValue = 100f,
                isRandomizable = existing.randomizeDecayMs,
                isRandomizeDisabled = param.isRandomizeDisabled,
                randomizeDisabledTooltip = llm.slop.liquidlsd.rendering.Mixer.FORBIDDEN_RANDOMIZE_TOOLTIP,
                formatValue = { "${it.toInt()}ms" },
                onRandomizableChanged = decayCbs.onRandomizableChanged,
                onRandomizeNow        = decayCbs.onRandomizeNow,
                onRangeChanged        = decayCbs.onRangeChanged,
                onValueChanged        = decayCbs.onValueChanged,
            )
            ImGui.spacing()
        }

        drawRange(session, param, existing, paramKey, modulatorIndex, themeColor, onReplace)
    }

    private fun drawSectionSignal(
        session: SessionContext,
        existing: CvModulator,
        fontScale: Float,
        onReplace: (CvModulator) -> Unit
    ) {
        session.uiTheme.body("Signal:")
        ImGui.sameLine(0f, 8f * fontScale)
        ImGui.pushItemWidth(180f * fontScale)
        val idx = ImInt(SECTION_SOURCES.indexOf(existing.sourceId).coerceAtLeast(0))
        if (ImGui.combo("##section_${existing.id}", idx, SECTION_LABELS)) {
            onReplace(existing.copy(sourceId = SECTION_SOURCES[idx.get().coerceIn(0, SECTION_SOURCES.size - 1)]))
        }
        llm.slop.liquidlsd.tour.WidgetRegistry.note { "aud_section" }
        itemTooltip("Section Signal:\nEnergy: how loud the music is now against the last ~20 s. Rests mid-range;\n" +
            "falls in a breakdown, rises when the song gets louder. Needs ~20 s of music first.\n" +
            "Drop: jumps to full when a breakdown ends with the bass back, then falls away over 4 bars.\n" +
            "Dated back to the beat the drop landed on.")
        ImGui.popItemWidth()
    }

    private fun drawRange(
        session: SessionContext,
        param: ModulatableParameter,
        existing: CvModulator,
        paramKey: String,
        modulatorIndex: Int,
        themeColor: Int,
        onReplace: (CvModulator) -> Unit
    ) {
        // -- Modulation Range (Min/Max) ---------------------------
        CustomRangeSlider.drawMinMaxRangeSlider(
            session = session,
            label = "Modulation Range",
            currentMin = existing.getLfoMin(),
            currentMax = existing.getLfoMax(),
            minRangeMin = existing.dcOffsetMin,
            minRangeMax = existing.dcOffsetMax,
            maxRangeMin = existing.depthMin,
            maxRangeMax = existing.depthMax,
            minLimit = -1f,
            maxLimit = 1f,
            isRandomizable = existing.randomizeDcOffset || existing.randomizeDepth,
            isRandomizeDisabled = param.isRandomizeDisabled,
            themeColor = themeColor,
            idPrefix = existing.id,
            onRandomizableChanged = { enabled ->
                val rMin = existing.dcOffsetMin
                val rMax = existing.dcOffsetMax
                val dMin = existing.depthMin
                val dMax = existing.depthMax
                val curMin = existing.getLfoMin()
                val curMax = existing.getLfoMax()

                onReplace(existing.copy(
                    lfoMinMaxMode = true,
                    randomizeDcOffset = enabled,
                    randomizeDepth = enabled,
                    dcOffsetMin = if (enabled && rMin == rMax) (curMin - 0.1f).coerceAtLeast(-1f) else if (!enabled) curMin else rMin,
                    dcOffsetMax = if (enabled && rMin == rMax) (curMin + 0.1f).coerceAtMost(1f) else if (!enabled) curMin else rMax,
                    depthMin = if (enabled && dMin == dMax) (curMax - 0.1f).coerceAtLeast(-1f) else if (!enabled) curMax else dMin,
                    depthMax = if (enabled && dMin == dMax) (curMax + 0.1f).coerceAtMost(1f) else if (!enabled) curMax else dMax
                ))
            },
            onRandomizeNow = {
                onReplace(existing.randomizeDcOffset().randomizeDepth())
            },
            onMinMaxChanged = { min, max ->
                onReplace(existing.withLfoRange(min, max).copy(lfoMinMaxMode = true))
            },
            onMinRangeChanged = { rMin, rMax ->
                onReplace(existing.copy(lfoMinMaxMode = true, dcOffsetMin = rMin, dcOffsetMax = rMax))
            },
            onMaxRangeChanged = { rMin, rMax ->
                onReplace(existing.copy(lfoMinMaxMode = true, depthMin = rMin, depthMax = rMax))
            },
            modulatorIndex = modulatorIndex,
            modulatorId = existing.id,
            paramKey = paramKey
        )
        ImGui.spacing()
    }
}
