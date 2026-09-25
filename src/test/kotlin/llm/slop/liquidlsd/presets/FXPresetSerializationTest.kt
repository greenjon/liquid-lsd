package llm.slop.liquidlsd.presets

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import llm.slop.liquidlsd.models.FXChainDto
import llm.slop.liquidlsd.models.FXPresetDto
import llm.slop.liquidlsd.models.FXSlotDto
import llm.slop.liquidlsd.models.ParameterDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FXPresetSerializationTest {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    private fun createDummyParameter(value: Float = 0.5f): ParameterDto {
        return ParameterDto(
            baseValue = value,
            baseMin = 0.0f,
            baseMax = 1.0f,
            randomizeBase = false,
            modulators = emptyList()
        )
    }

    private fun createDummySlot(filterId: String = "isf_invert"): FXSlotDto {
        return FXSlotDto(
            filterId = filterId,
            enabled = true,
            dryWet = createDummyParameter(1.0f),
            parameters = mapOf(
                "intensity" to createDummyParameter(0.8f)
            )
        )
    }

    @Test
    fun testFXPresetDtoSerialization() {
        val slot = createDummySlot("isf_glitch")
        val preset = FXPresetDto(
            version = 1,
            name = "my_glitch_preset",
            tags = listOf("glitch", "psy", "strobe"),
            slot = slot
        )

        val jsonStr = json.encodeToString(preset)
        val decoded = json.decodeFromString<FXPresetDto>(jsonStr)

        assertEquals(1, decoded.version)
        assertEquals("my_glitch_preset", decoded.name)
        assertEquals(listOf("glitch", "psy", "strobe"), decoded.tags)
        assertEquals("isf_glitch", decoded.slot.filterId)
        assertEquals(true, decoded.slot.enabled)
        assertEquals(1.0f, decoded.slot.dryWet.baseValue)
        assertNotNull(decoded.slot.parameters["intensity"])
        assertEquals(0.8f, decoded.slot.parameters["intensity"]?.baseValue)
    }

    @Test
    fun testFXChainDtoSerialization() {
        val slot1 = createDummySlot("isf_blur")
        val slot3 = createDummySlot("isf_kaleidoscope")

        val chain = FXChainDto(
            version = 1,
            name = "dreamy_kaleido_chain",
            tags = listOf("blur", "kaleido"),
            slots = listOf(slot1, null, slot3, null)
        )

        val jsonStr = json.encodeToString(chain)
        val decoded = json.decodeFromString<FXChainDto>(jsonStr)

        assertEquals(1, decoded.version)
        assertEquals("dreamy_kaleido_chain", decoded.name)
        assertEquals(listOf("blur", "kaleido"), decoded.tags)
        assertEquals(4, decoded.slots.size)
        assertNotNull(decoded.slots[0])
        assertEquals("isf_blur", decoded.slots[0]?.filterId)
        assertNull(decoded.slots[1])
        assertNotNull(decoded.slots[2])
        assertEquals("isf_kaleidoscope", decoded.slots[2]?.filterId)
        assertNull(decoded.slots[3])
    }

    @Test
    fun testFXChainEmptySlots() {
        val chain = FXChainDto(
            version = 1,
            name = "empty_chain",
            tags = emptyList(),
            slots = listOf(null, null, null, null)
        )

        val jsonStr = json.encodeToString(chain)
        val decoded = json.decodeFromString<FXChainDto>(jsonStr)

        assertEquals(4, decoded.slots.size)
        assertNull(decoded.slots[0])
        assertNull(decoded.slots[1])
        assertNull(decoded.slots[2])
        assertNull(decoded.slots[3])
    }

    private fun p(value: Float, min: Float = 0.0f, max: Float = 1.0f): ParameterDto =
        ParameterDto(baseValue = value, baseMin = min, baseMax = max, randomizeBase = false, modulators = emptyList())

    @Test
    fun generateAndValidateDefaultBundledFxChains() {
        val chainsDir = java.io.File("library/fx_chains")
        chainsDir.mkdirs()

        val chains = listOf(
            FXChainDto(
                name = "Hyperspace Trip",
                tags = listOf("psychedelic", "fractal", "feedback", "ambient"),
                dryWet = p(1.0f),
                superKnob = p(0.5f),
                slotSuperKnobLink = listOf(true, true, true),
                slots = listOf(
                    FXSlotDto(
                        filterId = "kaleidoscope",
                        dryWet = p(1.0f),
                        parameters = mapOf(
                            "segments" to p(8.0f, 2.0f, 24.0f),
                            "rotation" to p(0.0f, -3.14159f, 3.14159f),
                            "zoom" to p(1.0f, 0.1f, 5.0f),
                            "centerX" to p(0.5f, 0.0f, 1.0f),
                            "centerY" to p(0.5f, 0.0f, 1.0f),
                            "originOffset" to p(0.0f, -1.0f, 1.0f)
                        )
                    ),
                    FXSlotDto(
                        filterId = "feedback",
                        dryWet = p(1.0f),
                        parameters = mapOf(
                            "fbDecay" to p(0.72f, 0.0f, 1.0f),
                            "fbGain" to p(0.95f, 0.0f, 2.0f),
                            "fbZoom" to p(0.05f, -1.0f, 1.0f),
                            "fbRotate" to p(0.02f, -3.14159f, 3.14159f),
                            "fbBlur" to p(0.2f, 0.0f, 1.0f),
                            "fbChroma" to p(0.15f, 0.0f, 1.0f)
                        )
                    ),
                    FXSlotDto(
                        filterId = "gradient_map",
                        dryWet = p(0.85f),
                        parameters = mapOf(
                            "mixAmount" to p(0.85f, 0.0f, 1.0f),
                            "palette" to p(0.0f, 0.0f, 6.0f),
                            "cycleSpeed" to p(0.08f, -2.0f, 2.0f),
                            "cycleOffset" to p(0.0f, 0.0f, 1.0f),
                            "hueShift" to p(0.0f, 0.0f, 1.0f)
                        )
                    )
                )
            ),
            FXChainDto(
                name = "The Drop Weapon",
                tags = listOf("drop", "strobe", "bass", "glitch", "high-energy"),
                dryWet = p(1.0f),
                superKnob = p(0.0f),
                slotSuperKnobLink = listOf(true, true, true),
                slots = listOf(
                    FXSlotDto(
                        filterId = "radial_blur",
                        dryWet = p(1.0f),
                        parameters = mapOf(
                            "blurAmount" to p(0.4f, 0.0f, 1.0f),
                            "decay" to p(0.92f, 0.5f, 1.0f),
                            "exposure" to p(1.2f, 0.1f, 2.5f),
                            "centerX" to p(0.5f, 0.0f, 1.0f),
                            "centerY" to p(0.5f, 0.0f, 1.0f)
                        )
                    ),
                    FXSlotDto(
                        filterId = "video_strobe",
                        dryWet = p(1.0f),
                        parameters = mapOf(
                            "rate" to p(8.0f, 0.0f, 30.0f),
                            "freezeHold" to p(0.0f, 0.0f, 1.0f),
                            "strobeMode" to p(1.0f, 0.0f, 3.0f),
                            "dutyCycle" to p(0.25f, 0.05f, 0.95f)
                        )
                    ),
                    FXSlotDto(
                        filterId = "rgb_split",
                        dryWet = p(1.0f),
                        parameters = mapOf(
                            "amount" to p(0.04f, 0.0f, 0.1f),
                            "radialMode" to p(1.0f, 0.0f, 1.0f),
                            "dispersionMode" to p(0.0f, 0.0f, 1.0f)
                        )
                    )
                )
            ),
            FXChainDto(
                name = "Cyberpunk 1984",
                tags = listOf("retro", "crt", "arcade", "synthwave", "lofi"),
                dryWet = p(1.0f),
                superKnob = p(0.5f),
                slotSuperKnobLink = listOf(true, true, true),
                slots = listOf(
                    FXSlotDto(
                        filterId = "pixelate",
                        dryWet = p(1.0f),
                        parameters = mapOf(
                            "pixelSize" to p(4.0f, 1.0f, 128.0f),
                            "latticeMode" to p(0.0f, 0.0f, 2.0f),
                            "colorDepth" to p(16.0f, 0.0f, 32.0f)
                        )
                    ),
                    FXSlotDto(
                        filterId = "retro_crt",
                        dryWet = p(1.0f),
                        parameters = mapOf(
                            "scanlineIntensity" to p(0.45f, 0.0f, 1.0f),
                            "scanlineCount" to p(240.0f, 50.0f, 800.0f),
                            "curvature" to p(0.12f, 0.0f, 0.6f),
                            "phosphorMask" to p(0.3f, 0.0f, 1.0f),
                            "vignette" to p(0.35f, 0.0f, 1.0f),
                            "brightnessBoost" to p(1.25f, 0.8f, 2.0f)
                        )
                    ),
                    FXSlotDto(
                        filterId = "rgb_split",
                        dryWet = p(1.0f),
                        parameters = mapOf(
                            "amount" to p(0.015f, 0.0f, 0.1f),
                            "angle" to p(0.0f, -3.14159f, 3.14159f),
                            "radialMode" to p(0.0f, 0.0f, 1.0f),
                            "dispersionMode" to p(1.0f, 0.0f, 1.0f)
                        )
                    )
                )
            ),
            FXChainDto(
                name = "Liquid Mercury",
                tags = listOf("liquid", "fluid", "organic", "marbling", "ambient"),
                dryWet = p(1.0f),
                superKnob = p(0.5f),
                slotSuperKnobLink = listOf(true, true, true),
                slots = listOf(
                    FXSlotDto(
                        filterId = "luma_displace",
                        dryWet = p(1.0f),
                        parameters = mapOf(
                            "refractAmount" to p(0.08f, 0.0f, 0.25f),
                            "smoothness" to p(3.0f, 0.5f, 8.0f),
                            "chromaDispersion" to p(0.03f, 0.0f, 0.1f),
                            "flowSpeed" to p(0.5f, -3.0f, 3.0f)
                        )
                    ),
                    FXSlotDto(
                        filterId = "fluid_smear",
                        dryWet = p(1.0f),
                        parameters = mapOf(
                            "smearAmount" to p(0.6f, 0.0f, 1.0f),
                            "curlScale" to p(3.5f, 0.5f, 20.0f),
                            "flowSpeed" to p(0.4f, -2.0f, 2.0f),
                            "decay" to p(0.95f, 0.8f, 1.0f),
                            "gravity" to p(0.05f, -1.0f, 1.0f)
                        )
                    ),
                    FXSlotDto(
                        filterId = "bloom",
                        dryWet = p(0.85f),
                        parameters = mapOf(
                            "bloomIntensity" to p(0.9f, 0.0f, 3.0f),
                            "threshold" to p(0.45f, 0.0f, 1.0f),
                            "blurAmount" to p(1.2f, 0.1f, 3.0f)
                        )
                    )
                )
            ),
            FXChainDto(
                name = "Neon Wireframe",
                tags = listOf("neon", "laser", "techno", "minimal", "edges"),
                dryWet = p(1.0f),
                superKnob = p(0.5f),
                slotSuperKnobLink = listOf(true, true, true),
                slots = listOf(
                    FXSlotDto(
                        filterId = "neon_edge",
                        dryWet = p(1.0f),
                        parameters = mapOf(
                            "edgeStrength" to p(3.5f, 0.0f, 10.0f),
                            "threshold" to p(0.04f, 0.0f, 0.5f),
                            "glowSpread" to p(1.5f, 0.5f, 5.0f),
                            "palette" to p(0.0f, 0.0f, 3.0f),
                            "backgroundBlend" to p(0.0f, 0.0f, 1.0f)
                        )
                    ),
                    FXSlotDto(
                        filterId = "directional_blur",
                        dryWet = p(0.8f),
                        parameters = mapOf(
                            "blurAmount" to p(0.3f, 0.0f, 1.0f),
                            "angle" to p(0.0f, -3.14159f, 3.14159f),
                            "decay" to p(0.9f, 0.5f, 1.0f),
                            "bidirectional" to p(1.0f, 0.0f, 1.0f),
                            "exposure" to p(1.2f, 0.2f, 2.0f)
                        )
                    ),
                    FXSlotDto(
                        filterId = "feedback",
                        dryWet = p(0.7f),
                        parameters = mapOf(
                            "fbDecay" to p(0.55f, 0.0f, 1.0f),
                            "fbGain" to p(0.85f, 0.0f, 2.0f),
                            "fbZoom" to p(0.0f, -1.0f, 1.0f),
                            "fbRotate" to p(0.0f, -3.14159f, 3.14159f),
                            "fbBlur" to p(0.1f, 0.0f, 1.0f)
                        )
                    )
                )
            ),
            FXChainDto(
                name = "Wormhole Flight",
                tags = listOf("tunnel", "space", "warp", "cosmic"),
                dryWet = p(1.0f),
                superKnob = p(0.5f),
                slotSuperKnobLink = listOf(true, true, true),
                slots = listOf(
                    FXSlotDto(
                        filterId = "polar_tunnel",
                        dryWet = p(1.0f),
                        parameters = mapOf(
                            "depth" to p(2.0f, 0.1f, 5.0f),
                            "twist" to p(0.4f, -3.14159f, 3.14159f),
                            "zoom" to p(1.0f, 0.1f, 5.0f),
                            "symmetry" to p(2.0f, 1.0f, 8.0f),
                            "depthFog" to p(0.6f, 0.0f, 1.0f)
                        )
                    ),
                    FXSlotDto(
                        filterId = "pinch_bulge",
                        dryWet = p(1.0f),
                        parameters = mapOf(
                            "amount" to p(0.4f, -1.0f, 1.0f),
                            "radius" to p(0.6f, 0.05f, 1.5f),
                            "centerX" to p(0.5f, 0.0f, 1.0f),
                            "centerY" to p(0.5f, 0.0f, 1.0f)
                        )
                    ),
                    FXSlotDto(
                        filterId = "bloom",
                        dryWet = p(1.0f),
                        parameters = mapOf(
                            "bloomIntensity" to p(1.2f, 0.0f, 3.0f),
                            "threshold" to p(0.35f, 0.0f, 1.0f),
                            "blurAmount" to p(1.0f, 0.1f, 3.0f)
                        )
                    )
                )
            ),
            FXChainDto(
                name = "FLIR Predator Vision",
                tags = listOf("thermal", "flir", "military", "tactical", "industrial"),
                dryWet = p(1.0f),
                superKnob = p(0.5f),
                slotSuperKnobLink = listOf(true, true, true),
                slots = listOf(
                    FXSlotDto(
                        filterId = "thermal_scanner",
                        dryWet = p(1.0f),
                        parameters = mapOf(
                            "intensity" to p(1.0f, 0.0f, 1.0f),
                            "mode" to p(0.0f, 0.0f, 2.0f),
                            "thermalBloom" to p(0.4f, 0.0f, 1.0f),
                            "sensorGrain" to p(0.25f, 0.0f, 1.0f),
                            "vignette" to p(0.3f, 0.0f, 1.0f)
                        )
                    ),
                    FXSlotDto(
                        filterId = "retro_crt",
                        dryWet = p(0.85f),
                        parameters = mapOf(
                            "scanlineIntensity" to p(0.25f, 0.0f, 1.0f),
                            "scanlineCount" to p(320.0f, 50.0f, 800.0f),
                            "curvature" to p(0.06f, 0.0f, 0.6f),
                            "phosphorMask" to p(0.0f, 0.0f, 1.0f),
                            "vignette" to p(0.2f, 0.0f, 1.0f),
                            "brightnessBoost" to p(1.1f, 0.8f, 2.0f)
                        )
                    ),
                    FXSlotDto(
                        filterId = "video_strobe",
                        dryWet = p(0.6f),
                        parameters = mapOf(
                            "rate" to p(3.0f, 0.0f, 30.0f),
                            "freezeHold" to p(0.0f, 0.0f, 1.0f),
                            "strobeMode" to p(0.0f, 0.0f, 3.0f),
                            "dutyCycle" to p(0.12f, 0.05f, 0.95f)
                        )
                    )
                )
            ),
            FXChainDto(
                name = "Liquid Chrome Dimension",
                tags = listOf("chrome", "surreal", "3d", "sphere", "psychedelic"),
                dryWet = p(1.0f),
                superKnob = p(0.5f),
                slotSuperKnobLink = listOf(true, true, true),
                slots = listOf(
                    FXSlotDto(
                        filterId = "wave_displace",
                        dryWet = p(0.7f),
                        parameters = mapOf(
                            "amplitude" to p(0.04f, 0.0f, 0.2f),
                            "frequency" to p(8.0f, 0.5f, 50.0f),
                            "speed" to p(1.0f, -5.0f, 5.0f),
                            "rippleMode" to p(1.0f, 0.0f, 1.0f)
                        )
                    ),
                    FXSlotDto(
                        filterId = "mirror_sphere",
                        dryWet = p(1.0f),
                        parameters = mapOf(
                            "sphereRadius" to p(0.45f, 0.0f, 1.0f),
                            "fresnelGlow" to p(0.6f, 0.0f, 1.0f),
                            "chromaFringe" to p(0.03f, 0.0f, 0.1f),
                            "backgroundMix" to p(0.2f, 0.0f, 1.0f)
                        )
                    ),
                    FXSlotDto(
                        filterId = "color_levels",
                        dryWet = p(1.0f),
                        parameters = mapOf(
                            "contrast" to p(1.3f, 0.0f, 3.0f),
                            "saturation" to p(1.2f, 0.0f, 3.0f),
                            "filmicTone" to p(0.4f, 0.0f, 1.0f)
                        )
                    )
                )
            ),
            FXChainDto(
                name = "2D to 3D Elevation with Feedback",
                tags = listOf("3d", "geometry", "feedback", "favorite"),
                dryWet = p(1.0f),
                superKnob = p(0.5f),
                slotSuperKnobLink = listOf(true, true, true),
                slots = listOf(
                    FXSlotDto(
                        filterId = "3d_elevation",
                        dryWet = p(1.0f),
                        parameters = mapOf(
                            "mode3D" to p(0.0f, 0.0f, 3.0f),
                            "zoom" to p(1.0f, 0.1f, 5.0f),
                            "perspective" to p(0.5f, 0.0f, 1.0f),
                            "separation" to p(0.2f, 0.0f, 2.0f),
                            "roundness" to p(1.0f, 0.0f, 1.0f)
                        )
                    ),
                    FXSlotDto(
                        filterId = "feedback",
                        dryWet = p(0.85f),
                        parameters = mapOf(
                            "fbDecay" to p(0.67f, 0.0f, 1.0f),
                            "fbGain" to p(0.77f, 0.0f, 2.0f),
                            "fbZoom" to p(0.13f, -1.0f, 1.0f),
                            "fbBlur" to p(0.5f, 0.0f, 1.0f),
                            "fbChroma" to p(0.7f, 0.0f, 1.0f)
                        )
                    ),
                    FXSlotDto(
                        filterId = "bloom",
                        dryWet = p(0.6f),
                        parameters = mapOf(
                            "bloomIntensity" to p(0.8f, 0.0f, 3.0f),
                            "threshold" to p(0.5f, 0.0f, 1.0f),
                            "blurAmount" to p(1.0f, 0.1f, 3.0f)
                        )
                    )
                )
            ),
            FXChainDto(
                name = "Laser Concert Anamorphic",
                tags = listOf("cinematic", "anamorphic", "optics", "concert"),
                dryWet = p(1.0f),
                superKnob = p(0.5f),
                slotSuperKnobLink = listOf(true, true, true),
                slots = listOf(
                    FXSlotDto(
                        filterId = "anamorphic_streak",
                        dryWet = p(1.0f),
                        parameters = mapOf(
                            "streakIntensity" to p(1.2f, 0.0f, 3.0f),
                            "streakLength" to p(0.65f, 0.05f, 1.0f),
                            "threshold" to p(0.6f, 0.0f, 1.0f),
                            "knee" to p(0.2f, 0.01f, 0.5f),
                            "colorMode" to p(0.0f, 0.0f, 2.0f),
                            "crossFlare" to p(0.0f, 0.0f, 1.0f)
                        )
                    ),
                    FXSlotDto(
                        filterId = "bloom",
                        dryWet = p(0.75f),
                        parameters = mapOf(
                            "bloomIntensity" to p(0.75f, 0.0f, 3.0f),
                            "threshold" to p(0.45f, 0.0f, 1.0f),
                            "blurAmount" to p(1.2f, 0.1f, 3.0f)
                        )
                    ),
                    FXSlotDto(
                        filterId = "color_levels",
                        dryWet = p(1.0f),
                        parameters = mapOf(
                            "contrast" to p(1.3f, 0.0f, 3.0f),
                            "exposure" to p(0.1f, -2.0f, 2.0f),
                            "saturation" to p(1.15f, 0.0f, 3.0f)
                        )
                    )
                )
            ),
            FXChainDto(
                name = "Pop-Art Comic Print",
                tags = listOf("stylize", "retro", "print", "halftone"),
                dryWet = p(1.0f),
                superKnob = p(0.5f),
                slotSuperKnobLink = listOf(true, true, true),
                slots = listOf(
                    FXSlotDto(
                        filterId = "neon_edge",
                        dryWet = p(0.9f),
                        parameters = mapOf(
                            "edgeStrength" to p(2.5f, 0.0f, 5.0f),
                            "threshold" to p(0.15f, 0.0f, 1.0f),
                            "mode" to p(2.0f, 0.0f, 3.0f),
                            "invertBg" to p(1.0f, 0.0f, 1.0f)
                        )
                    ),
                    FXSlotDto(
                        filterId = "halftone",
                        dryWet = p(1.0f),
                        parameters = mapOf(
                            "dotScale" to p(50.0f, 5.0f, 150.0f),
                            "screenAngle" to p(0.0f, 0.0f, 90.0f),
                            "mode" to p(0.0f, 0.0f, 2.0f),
                            "smoothness" to p(0.15f, 0.02f, 0.8f),
                            "paperTint" to p(0.15f, 0.0f, 1.0f),
                            "mixRatio" to p(1.0f, 0.0f, 1.0f)
                        )
                    ),
                    FXSlotDto(
                        filterId = "gradient_map",
                        dryWet = p(0.7f),
                        parameters = mapOf(
                            "mixAmount" to p(0.7f, 0.0f, 1.0f),
                            "palette" to p(0.0f, 0.0f, 6.0f),
                            "cycleSpeed" to p(0.0f, -2.0f, 2.0f),
                            "cycleOffset" to p(0.0f, 0.0f, 1.0f)
                        )
                    )
                )
            ),
            FXChainDto(
                name = "Grindhouse VHS Bootleg",
                tags = listOf("retro", "vhs", "glitch", "analog"),
                dryWet = p(1.0f),
                superKnob = p(0.5f),
                slotSuperKnobLink = listOf(true, true, true),
                slots = listOf(
                    FXSlotDto(
                        filterId = "vhs_glitch",
                        dryWet = p(1.0f),
                        parameters = mapOf(
                            "trackingJitter" to p(0.5f, 0.0f, 1.0f),
                            "headSwitching" to p(0.7f, 0.0f, 1.0f),
                            "ycDelay" to p(0.4f, 0.0f, 1.0f),
                            "rfDropouts" to p(0.35f, 0.0f, 1.0f),
                            "tapeNoise" to p(0.4f, 0.0f, 1.0f),
                            "mixRatio" to p(1.0f, 0.0f, 1.0f)
                        )
                    ),
                    FXSlotDto(
                        filterId = "retro_crt",
                        dryWet = p(0.85f),
                        parameters = mapOf(
                            "scanlineIntensity" to p(0.6f, 0.0f, 1.0f),
                            "curvature" to p(0.2f, 0.0f, 1.0f),
                            "phosphorIntensity" to p(0.4f, 0.0f, 1.0f),
                            "vignette" to p(0.35f, 0.0f, 1.0f)
                        )
                    ),
                    FXSlotDto(
                        filterId = "color_levels",
                        dryWet = p(1.0f),
                        parameters = mapOf(
                            "contrast" to p(1.25f, 0.0f, 3.0f),
                            "exposure" to p(-0.05f, -2.0f, 2.0f),
                            "saturation" to p(0.9f, 0.0f, 3.0f)
                        )
                    )
                )
            ),
            FXChainDto(
                name = "Prismatic Crystal Kaleidoscope",
                tags = listOf("geometry", "prism", "gem", "kaleidoscope"),
                dryWet = p(1.0f),
                superKnob = p(0.5f),
                slotSuperKnobLink = listOf(true, true, true),
                slots = listOf(
                    FXSlotDto(
                        filterId = "faceted_glass",
                        dryWet = p(1.0f),
                        parameters = mapOf(
                            "facetScale" to p(16.0f, 3.0f, 60.0f),
                            "refraction" to p(0.5f, 0.0f, 1.0f),
                            "dispersion" to p(0.4f, 0.0f, 1.0f),
                            "bevelStrength" to p(0.65f, 0.0f, 1.0f),
                            "facetTilt" to p(0.55f, 0.0f, 1.0f)
                        )
                    ),
                    FXSlotDto(
                        filterId = "kaleidoscope",
                        dryWet = p(1.0f),
                        parameters = mapOf(
                            "segments" to p(6.0f, 2.0f, 24.0f),
                            "rotation" to p(0.0f, -3.14159f, 3.14159f),
                            "zoom" to p(1.0f, 0.1f, 5.0f)
                        )
                    ),
                    FXSlotDto(
                        filterId = "rgb_split",
                        dryWet = p(0.8f),
                        parameters = mapOf(
                            "amount" to p(0.03f, 0.0f, 0.1f),
                            "mode" to p(1.0f, 0.0f, 1.0f),
                            "angle" to p(0.0f, 0.0f, 6.28318f)
                        )
                    )
                )
            ),
            FXChainDto(
                name = "Cosmic Black Hole Vortex",
                tags = listOf("space", "vortex", "distortion", "wormhole"),
                dryWet = p(1.0f),
                superKnob = p(0.5f),
                slotSuperKnobLink = listOf(true, true, true),
                slots = listOf(
                    FXSlotDto(
                        filterId = "vortex_swirl",
                        dryWet = p(1.0f),
                        parameters = mapOf(
                            "twist" to p(1.2f, -3.0f, 3.0f),
                            "radius" to p(0.9f, 0.1f, 2.5f),
                            "dispersion" to p(0.45f, 0.0f, 1.0f),
                            "spiralArms" to p(0.0f, 0.0f, 12.0f)
                        )
                    ),
                    FXSlotDto(
                        filterId = "luma_displace",
                        dryWet = p(0.85f),
                        parameters = mapOf(
                            "refractAmount" to p(0.12f, 0.0f, 0.25f),
                            "smoothness" to p(0.4f, 0.05f, 1.0f),
                            "chromaDispersion" to p(0.35f, 0.0f, 1.0f)
                        )
                    ),
                    FXSlotDto(
                        filterId = "polar_tunnel",
                        dryWet = p(0.9f),
                        parameters = mapOf(
                            "depth" to p(1.5f, 0.1f, 5.0f),
                            "rotation" to p(0.0f, -3.14159f, 3.14159f),
                            "twist" to p(0.5f, -2.0f, 2.0f)
                        )
                    )
                )
            ),
            FXChainDto(
                name = "Subtle Optical Warmth",
                tags = listOf("warmth", "filmic", "master"),
                dryWet = p(0.75f),
                superKnob = p(0.5f),
                slotSuperKnobLink = listOf(true, true, true),
                slots = listOf(
                    FXSlotDto(filterId = "color_levels", dryWet = p(1.0f), parameters = mapOf("contrast" to p(1.15f, 0f, 3f), "saturation" to p(1.05f, 0f, 3f))),
                    FXSlotDto(filterId = "retro_crt", dryWet = p(0.35f), parameters = mapOf("scanlineIntensity" to p(0.25f, 0f, 1f), "curvature" to p(0.05f, 0f, 1f))),
                    FXSlotDto(filterId = "bloom", dryWet = p(0.5f), parameters = mapOf("bloomIntensity" to p(0.6f, 0f, 3f), "threshold" to p(0.6f, 0f, 1f)))
                )
            ),
            FXChainDto(
                name = "Digital Bitcrush Mosaic",
                tags = listOf("bitcrush", "mosaic", "digital"),
                dryWet = p(1.0f),
                superKnob = p(0.5f),
                slotSuperKnobLink = listOf(true, true, true),
                slots = listOf(
                    FXSlotDto(filterId = "pixelate", dryWet = p(1.0f), parameters = mapOf("pixelSize" to p(16f, 1f, 64f), "lattice" to p(1f, 0f, 2f))),
                    FXSlotDto(filterId = "rgb_split", dryWet = p(0.9f), parameters = mapOf("amount" to p(0.04f, 0f, 0.1f), "mode" to p(0f, 0f, 1f))),
                    FXSlotDto(filterId = "video_strobe", dryWet = p(0.8f), parameters = mapOf("rate" to p(8f, 0f, 20f), "strobeMode" to p(2f, 0f, 3f)))
                )
            )
        )

        for (chain in chains) {
            val fileName = chain.name.lowercase().replace(" ", "_").replace("-", "_") + ".lsdfxchain"
            val file = java.io.File(chainsDir, fileName)
            val jsonText = json.encodeToString(chain)
            file.writeText(jsonText)

            // Validate that it reads back cleanly
            val readBack = json.decodeFromString<FXChainDto>(file.readText())
            assertEquals(chain.name, readBack.name)
            assertEquals(3, readBack.slots.size)
        }
    }
}
