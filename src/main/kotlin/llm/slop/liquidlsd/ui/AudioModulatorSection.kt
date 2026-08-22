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

    fun draw(
        session: SessionContext,
        param: ModulatableParameter,
        existing: CvModulator,
        themeColor: Int,
        onReplace: (CvModulator) -> Unit
    ) {
        val bypassed = existing.bypassed
        val fontScale = (session.uiTheme.baseSize / 15f).coerceIn(0.8f, 2.5f)

        // 1. Envelope Follower Preset Dropdown
        session.uiTheme.body("Envelope Follower:")
        ImGui.sameLine(0f, 10f * fontScale)

        val modes = AudioFollowerMode.values()
        val modeLabels = modes.map { it.label }.toTypedArray()
        val currentModeIdx = modes.indexOf(existing.followerMode).coerceAtLeast(0)
        val modeIdxWrapper = ImInt(currentModeIdx)

        if (bypassed) ImGui.popStyleVar()
        ImGui.pushItemWidth(180f * fontScale)
        if (ImGui.combo("##follower_mode_${existing.id}", modeIdxWrapper, modeLabels)) {
            val selectedMode = modes[modeIdxWrapper.get()]
            if (selectedMode == AudioFollowerMode.CUSTOM) {
                // Retain current values or populate from previous preset defaults
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
            ImGui.setTooltip("Audio Dynamics / Smoothing:\nRaw: Instant amplitude jitter (bypassed follower)\nPresets: Musically tuned attack and decay envelopes\nCustom: Freely adjust Attack and Decay sliders")
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
                isRandomizable = existing.randomizeAttackMs,
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
                isRandomizable = existing.randomizeDecayMs,
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
            isRandomizable = existing.randomizeDcOffset,
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
            isRandomizable = existing.randomizeDepth,
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
