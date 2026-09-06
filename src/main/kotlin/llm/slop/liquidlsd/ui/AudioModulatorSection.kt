package llm.slop.liquidlsd.ui

import imgui.ImGui
import imgui.type.ImInt
import llm.slop.liquidlsd.SessionContext
import llm.slop.liquidlsd.parameters.AudioFollowerMode
import llm.slop.liquidlsd.parameters.CvModulator
import llm.slop.liquidlsd.parameters.ModulatableParameter

/**
 * Dedicated UI section for Audio CV modulators (audio_amp, audio_bass, audio_mid, audio_high).
 * Renders the dynamics Envelope Follower preset dropdown, contextual Custom Attack/Decay sliders,
 * DC Offset, and Depth.
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

    fun draw(
        session: SessionContext,
        param: ModulatableParameter,
        existing: CvModulator,
        themeColor: Int,
        onReplace: (CvModulator) -> Unit
    ) {
        val bypassed = existing.bypassed
        val fontScale = 0.95f

        val isTransient = existing.sourceId.startsWith("audio_flux_")
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

        // Continuous Button
        val isContActive = !isTransient
        if (isContActive) {
            ImGui.pushStyleColor(imgui.flag.ImGuiCol.Button, themeColor)
            ImGui.pushStyleColor(imgui.flag.ImGuiCol.ButtonHovered, themeColor)
            ImGui.pushStyleColor(imgui.flag.ImGuiCol.ButtonActive, themeColor)
        } else {
            ImGui.pushStyleColor(imgui.flag.ImGuiCol.Button, ImGui.colorConvertFloat4ToU32(0.15f, 0.15f, 0.15f, 1f))
            ImGui.pushStyleColor(imgui.flag.ImGuiCol.ButtonHovered, ImGui.colorConvertFloat4ToU32(0.25f, 0.25f, 0.25f, 1f))
            ImGui.pushStyleColor(imgui.flag.ImGuiCol.ButtonActive, ImGui.colorConvertFloat4ToU32(0.35f, 0.35f, 0.35f, 1f))
        }
        if (ImGui.button("Continuous (RMS)##mode_cont_${existing.id}", btnW, btnH)) {
            if (isTransient) {
                val newSource = RMS_SOURCES[currentBandIdx]
                onReplace(existing.copy(sourceId = newSource))
            }
        }
        if (ImGui.isItemHovered() && session.uiTheme.tooltipsEnabled) {
            ImGui.setTooltip("Continuous Envelope: Tracks continuous volume and sustained body of audio frequencies.")
        }
        ImGui.popStyleColor(3)

        ImGui.sameLine(0f, 4f * fontScale)

        // Transient Button
        val isFluxActive = isTransient
        if (isFluxActive) {
            ImGui.pushStyleColor(imgui.flag.ImGuiCol.Button, themeColor)
            ImGui.pushStyleColor(imgui.flag.ImGuiCol.ButtonHovered, themeColor)
            ImGui.pushStyleColor(imgui.flag.ImGuiCol.ButtonActive, themeColor)
        } else {
            ImGui.pushStyleColor(imgui.flag.ImGuiCol.Button, ImGui.colorConvertFloat4ToU32(0.15f, 0.15f, 0.15f, 1f))
            ImGui.pushStyleColor(imgui.flag.ImGuiCol.ButtonHovered, ImGui.colorConvertFloat4ToU32(0.25f, 0.25f, 0.25f, 1f))
            ImGui.pushStyleColor(imgui.flag.ImGuiCol.ButtonActive, ImGui.colorConvertFloat4ToU32(0.35f, 0.35f, 0.35f, 1f))
        }
        if (ImGui.button("Transient (Flux)##mode_flux_${existing.id}", btnW, btnH)) {
            if (!isTransient) {
                val newSource = FLUX_SOURCES[currentBandIdx]
                onReplace(existing.copy(sourceId = newSource))
            }
        }
        if (ImGui.isItemHovered() && session.uiTheme.tooltipsEnabled) {
            ImGui.setTooltip("Transient Trigger: Tracks sudden onsets, drum strikes, and energy growth (Spectral Flux).")
        }
        ImGui.popStyleColor(3)

        ImGui.spacing()

        // 2. Frequency Band Selector
        session.uiTheme.body("Frequency Band:")
        ImGui.sameLine(0f, 8f * fontScale)
        ImGui.pushItemWidth(180f * fontScale)
        val bandIdxWrapper = ImInt(currentBandIdx)
        if (ImGui.combo("##band_${existing.id}", bandIdxWrapper, BAND_LABELS)) {
            val selectedIdx = bandIdxWrapper.get().coerceIn(0, 3)
            val newSource = if (isTransient) FLUX_SOURCES[selectedIdx] else RMS_SOURCES[selectedIdx]
            onReplace(existing.copy(sourceId = newSource))
        }
        if (ImGui.isItemHovered() && session.uiTheme.tooltipsEnabled) {
            ImGui.setTooltip("Frequency Band:\nFull Mix: Entire frequency spectrum\nLow / Bass: Sub and kick frequencies (< 150Hz)\nMid: Vocals, synths, and snares (150Hz - 2.5kHz)\nHigh: Cymbals, hi-hats, and air (> 2.5kHz)")
        }
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
        if (ImGui.combo("##follower_mode_${existing.id}", modeIdxWrapper, modeLabels)) {
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
        if (ImGui.isItemHovered() && session.uiTheme.tooltipsEnabled) {
            ImGui.setTooltip("Dynamics / Smoothing:\nInstant: 1-frame strobe or raw amplitude flutter\nStrobe / Snap: 0ms attack, 35ms decay snap\nPunchy (Accent): 5ms attack, 150ms decay\nSmooth Swell: 40ms attack, 400ms decay\nSlow Bloom: 100ms attack, 900ms decay\nAmbient Drift: 250ms attack, 1800ms decay\nCustom…: Freely adjust Attack and Decay sliders")
        }
        ImGui.popItemWidth()
        if (bypassed) ImGui.pushStyleVar(imgui.flag.ImGuiStyleVar.Alpha, 0.5f)
        ImGui.spacing()

        // 2. Custom Attack and Decay Sliders
        if (existing.followerMode == AudioFollowerMode.CUSTOM) {
            // Attack (ms) Slider
            CustomRangeSlider.drawCustomRangeSlider(
                session = session,
                idPrefix = "${existing.id}_att",
                label = "Attack",
                themeColor = themeColor,
                currentValue = existing.attackMs,
                currentMin = existing.attackMsMin,
                currentMax = existing.attackMsMax,
                minLimit = 0f,
                maxLimit = 500f,
                defaultValue = 0f,
                isRandomizable = existing.randomizeAttackMs,
                isRandomizeDisabled = param.isRandomizeDisabled,
                randomizeDisabledTooltip = llm.slop.liquidlsd.rendering.Mixer.FORBIDDEN_RANDOMIZE_TOOLTIP,
                formatValue = { "${it.toInt()}ms" },
                onRandomizableChanged = { checked ->
                    if (checked) {
                        val rMin = existing.attackMsMin
                        val rMax = existing.attackMsMax
                        val (nextMin, nextMax) = if (rMin == rMax) {
                            Pair((existing.attackMs - 20f).coerceAtLeast(0f), (existing.attackMs + 50f).coerceAtMost(500f))
                        } else {
                            Pair(rMin, rMax)
                        }
                        onReplace(existing.copy(
                            randomizeAttackMs = true,
                            attackMsMin = nextMin,
                            attackMsMax = nextMax
                        ))
                    } else {
                        onReplace(existing.copy(
                            randomizeAttackMs = false,
                            attackMsMin = existing.attackMs,
                            attackMsMax = existing.attackMs
                        ))
                    }
                },
                onRandomizeNow = {
                    onReplace(existing.randomizeAttackMs())
                },
                onRangeChanged = { nextMin, nextMax ->
                    val safeMin = minOf(nextMin, nextMax)
                    val safeMax = maxOf(nextMin, nextMax)
                    val nextActive = existing.attackMs.coerceIn(safeMin, safeMax)
                    onReplace(existing.copy(
                        attackMsMin = safeMin,
                        attackMsMax = safeMax,
                        attackMs = nextActive
                    ))
                },
                onValueChanged = { newVal ->
                    onReplace(existing.copy(
                        attackMs = newVal,
                        attackMsMin = newVal,
                        attackMsMax = newVal
                    ))
                }
            )
            ImGui.spacing()

            // Decay (ms) Slider
            CustomRangeSlider.drawCustomRangeSlider(
                session = session,
                idPrefix = "${existing.id}_dec",
                label = "Decay",
                themeColor = themeColor,
                currentValue = existing.decayMs,
                currentMin = existing.decayMsMin,
                currentMax = existing.decayMsMax,
                minLimit = 10f,
                maxLimit = 3000f,
                defaultValue = 100f,
                isRandomizable = existing.randomizeDecayMs,
                isRandomizeDisabled = param.isRandomizeDisabled,
                randomizeDisabledTooltip = llm.slop.liquidlsd.rendering.Mixer.FORBIDDEN_RANDOMIZE_TOOLTIP,
                formatValue = { "${it.toInt()}ms" },
                onRandomizableChanged = { checked ->
                    if (checked) {
                        val rMin = existing.decayMsMin
                        val rMax = existing.decayMsMax
                        val (nextMin, nextMax) = if (rMin == rMax) {
                            Pair((existing.decayMs - 100f).coerceAtLeast(10f), (existing.decayMs + 200f).coerceAtMost(3000f))
                        } else {
                            Pair(rMin, rMax)
                        }
                        onReplace(existing.copy(
                            randomizeDecayMs = true,
                            decayMsMin = nextMin,
                            decayMsMax = nextMax
                        ))
                    } else {
                        onReplace(existing.copy(
                            randomizeDecayMs = false,
                            decayMsMin = existing.decayMs,
                            decayMsMax = existing.decayMs
                        ))
                    }
                },
                onRandomizeNow = {
                    onReplace(existing.randomizeDecayMs())
                },
                onRangeChanged = { nextMin, nextMax ->
                    val safeMin = minOf(nextMin, nextMax)
                    val safeMax = maxOf(nextMin, nextMax)
                    val nextActive = existing.decayMs.coerceIn(safeMin, safeMax)
                    onReplace(existing.copy(
                        decayMsMin = safeMin,
                        decayMsMax = safeMax,
                        decayMs = nextActive
                    ))
                },
                onValueChanged = { newVal ->
                    onReplace(existing.copy(
                        decayMs = newVal,
                        decayMsMin = newVal,
                        decayMsMax = newVal
                    ))
                }
            )
            ImGui.spacing()
        }

        // 3. DC Offset Slider
        CustomRangeSlider.drawCustomRangeSlider(
            session = session,
            idPrefix = existing.id,
            label = "DC Offset",
            themeColor = themeColor,
            currentValue = existing.dcOffset,
            currentMin = existing.dcOffsetMin,
            currentMax = existing.dcOffsetMax,
            minLimit = -1f,
            maxLimit = 1f,
            defaultValue = 0f,
            isRandomizable = existing.randomizeDcOffset,
            isRandomizeDisabled = param.isRandomizeDisabled,
            randomizeDisabledTooltip = llm.slop.liquidlsd.rendering.Mixer.FORBIDDEN_RANDOMIZE_TOOLTIP,
            formatValue = { "%.3f".format(it) },
            onRandomizableChanged = { checked ->
                if (checked) {
                    val rMin = existing.dcOffsetMin
                    val rMax = existing.dcOffsetMax
                    val (nextMin, nextMax) = if (rMin == rMax) {
                        Pair((existing.dcOffset - 0.1f).coerceAtLeast(-1f), (existing.dcOffset + 0.1f).coerceAtMost(1f))
                    } else {
                        Pair(rMin, rMax)
                    }
                    onReplace(existing.copy(
                        randomizeDcOffset = true,
                        dcOffsetMin = nextMin,
                        dcOffsetMax = nextMax
                    ))
                } else {
                    onReplace(existing.copy(
                        randomizeDcOffset = false,
                        dcOffsetMin = existing.dcOffset,
                        dcOffsetMax = existing.dcOffset
                    ))
                }
            },
            onRandomizeNow = {
                onReplace(existing.randomizeDcOffset())
            },
            onRangeChanged = { nextMin, nextMax ->
                val safeMin = minOf(nextMin, nextMax)
                val safeMax = maxOf(nextMin, nextMax)
                val nextActive = existing.dcOffset.coerceIn(safeMin, safeMax)
                onReplace(existing.copy(
                    dcOffsetMin = safeMin,
                    dcOffsetMax = safeMax,
                    dcOffset = nextActive
                ))
            },
            onValueChanged = { newVal ->
                onReplace(existing.copy(
                    dcOffset = newVal,
                    dcOffsetMin = newVal,
                    dcOffsetMax = newVal
                ))
            }
        )
        ImGui.spacing()

        // 4. Depth Slider
        CustomRangeSlider.drawCustomRangeSlider(
            session = session,
            idPrefix = existing.id,
            label = "Depth",
            themeColor = themeColor,
            currentValue = existing.depth,
            currentMin = existing.depthMin,
            currentMax = existing.depthMax,
            minLimit = 0f,
            maxLimit = 1f,
            defaultValue = 0.5f,
            isRandomizable = existing.randomizeDepth,
            isRandomizeDisabled = param.isRandomizeDisabled,
            randomizeDisabledTooltip = llm.slop.liquidlsd.rendering.Mixer.FORBIDDEN_RANDOMIZE_TOOLTIP,
            formatValue = { "%.3f".format(it) },
            onRandomizableChanged = { checked ->
                if (checked) {
                    val rMin = existing.depthMin
                    val rMax = existing.depthMax
                    val (nextMin, nextMax) = if (rMin == rMax) {
                        Pair((existing.depth - 0.1f).coerceAtLeast(0f), (existing.depth + 0.1f).coerceAtMost(1f))
                    } else {
                        Pair(rMin, rMax)
                    }
                    onReplace(existing.copy(
                        randomizeDepth = true,
                        depthMin = nextMin,
                        depthMax = nextMax
                    ))
                } else {
                    onReplace(existing.copy(
                        randomizeDepth = false,
                        depthMin = existing.depth,
                        depthMax = existing.depth
                    ))
                }
            },
            onRandomizeNow = {
                onReplace(existing.randomizeDepth())
            },
            onRangeChanged = { nextMin, nextMax ->
                val safeMin = minOf(nextMin, nextMax)
                val safeMax = maxOf(nextMin, nextMax)
                val nextActive = existing.depth.coerceIn(safeMin, safeMax)
                onReplace(existing.copy(
                    depthMin = safeMin,
                    depthMax = safeMax,
                    depth = nextActive
                ))
            },
            onValueChanged = { newVal ->
                onReplace(existing.copy(
                    depth = newVal,
                    depthMin = newVal,
                    depthMax = newVal
                ))
            }
        )
        ImGui.spacing()
    }
}
