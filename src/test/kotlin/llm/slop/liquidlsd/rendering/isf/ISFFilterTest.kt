package llm.slop.liquidlsd.rendering.isf

import io.mockk.mockk
import llm.slop.liquidlsd.models.FXSlotDto
import llm.slop.liquidlsd.models.toDto
import llm.slop.liquidlsd.rendering.Shader
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ISFFilterTest {

    @Test
    fun `test ISF header parsing and parameter creation`() {
        val source = """
            /*{
                "DESCRIPTION": "Test Filter",
                "INPUTS": [
                    { "NAME": "inputImage", "TYPE": "image" },
                    { "NAME": "intensity", "TYPE": "float", "MIN": 0.0, "MAX": 1.0, "DEFAULT": 0.5 }
                ]
            }*/
            void main() { gl_FragColor = IMG_NORM_PIXEL(inputImage, isf_FragNormCoord) * intensity; }
        """.trimIndent()

        val header = ISFParser.parseHeader(source)
        assertNotNull(header)
        assertEquals("Test Filter", header?.DESCRIPTION)

        val shader = mockk<Shader>(relaxed = true)
        val filter = ISFFilter("test", "Test", header!!, shader)

        assertEquals(1, filter.parameters.size)
        val intensityParam = filter.parameters["intensity"]
        assertNotNull(intensityParam)
        assertEquals(0.5f, intensityParam?.baseValue)
        assertEquals(0.0f, intensityParam?.minClamp)
        assertEquals(1.0f, intensityParam?.maxClamp)
    }

    @Test
    fun `test ISFFilter parameter registration`() {
        val header = ISFHeader(INPUTS = listOf(
            ISFInput(NAME = "speed", TYPE = "float", DEFAULT = kotlinx.serialization.json.JsonPrimitive(0.5f))
        ))
        val shader = mockk<Shader>(relaxed = true)
        val filter = ISFFilter("fx1", "FX 1", header, shader)
        
        val paths = filter.getParameterPaths("A/FX1")
        assertTrue(paths.any { it.first == "A/FX1/DryWet" })
        assertTrue(paths.any { it.first == "A/FX1/speed" })
    }

    @Test
    fun `test FXSlotDto creation`() {
        val header = ISFHeader(INPUTS = listOf(
            ISFInput(NAME = "intensity", TYPE = "float", DEFAULT = kotlinx.serialization.json.JsonPrimitive(1.0f))
        ))
        val shader = mockk<Shader>(relaxed = true)
        val filter = ISFFilter("invert", "Invert", header, shader)
        
        val fxDto = FXSlotDto(
            filterId = filter.id,
            enabled = filter.enabled,
            dryWet = filter.dryWet.toDto(),
            parameters = filter.parameters.mapValues { it.value.toDto() }
        )
        
        assertEquals("invert", fxDto.filterId)
        assertEquals(1.0f, fxDto.dryWet.baseValue)
        assertTrue(fxDto.parameters.containsKey("intensity"))
    }

    @Test
    fun `test ISFFilter clone and reset`() {
        val header = ISFHeader(INPUTS = listOf(
            ISFInput(NAME = "speed", TYPE = "float", DEFAULT = kotlinx.serialization.json.JsonPrimitive(0.5f))
        ))
        val shader = mockk<Shader>(relaxed = true)
        val filter = ISFFilter("fx1", "FX 1", header, shader)
        filter.dryWet.baseValue = 0.8f
        filter.parameters["speed"]?.baseValue = 0.9f
        filter.enabled = false

        val clone = filter.clone()
        assertEquals(filter.id, clone.id)
        assertEquals(false, clone.enabled)
        assertEquals(0.8f, clone.dryWet.baseValue)
        assertEquals(0.9f, clone.parameters["speed"]?.baseValue)

        clone.reset()
        assertEquals(true, clone.enabled)
        assertEquals(1.0f, clone.dryWet.baseValue)
        assertEquals(0.5f, clone.parameters["speed"]?.baseValue)
    }

    @Test
    fun `test 3D elevation ISF filter header parsing`() {
        val stream = javaClass.classLoader.getResourceAsStream("default_filters/3d_elevation.fs")
        assertNotNull(stream, "3d_elevation.fs bundled resource must exist")
        val source = stream!!.bufferedReader().use { it.readText() }

        val header = ISFParser.parseHeader(source)
        assertNotNull(header, "3d_elevation.fs header should parse successfully")
        assertEquals(11, header?.INPUTS?.size, "3d_elevation should have 11 inputs")

        val shader = mockk<Shader>(relaxed = true)
        val filter = ISFFilter("3d_elevation", "3D Elevation", header!!, shader)
        val modeParam = filter.parameters["mode3D"]
        assertNotNull(modeParam)
        assertEquals(0.0f, modeParam?.minClamp)
        assertEquals(3.0f, modeParam?.maxClamp)

        assertTrue(filter.parameters.containsKey("pitch"))
        assertTrue(filter.parameters.containsKey("yaw"))
        assertTrue(filter.parameters.containsKey("roll"))
        assertTrue(filter.parameters.containsKey("zoom"))
        assertTrue(filter.parameters.containsKey("separation"))
        assertTrue(filter.parameters.containsKey("perspective"))
        assertTrue(filter.parameters.containsKey("depthDim"))
        assertTrue(filter.parameters.containsKey("blendMode"))
        assertTrue(filter.parameters.containsKey("roundness"))
    }

    @Test
    fun `test parameter clamp derivation from VALUES when MIN and MAX are omitted`() {
        val header = ISFHeader(INPUTS = listOf(
            ISFInput(
                NAME = "mode",
                TYPE = "long",
                DEFAULT = kotlinx.serialization.json.JsonPrimitive(0),
                VALUES = listOf(
                    kotlinx.serialization.json.JsonPrimitive(0),
                    kotlinx.serialization.json.JsonPrimitive(1),
                    kotlinx.serialization.json.JsonPrimitive(2),
                    kotlinx.serialization.json.JsonPrimitive(3)
                )
            )
        ))
        val shader = mockk<Shader>(relaxed = true)
        val filter = ISFFilter("test_mode", "Test Mode", header, shader)
        val param = filter.parameters["mode"]
        assertNotNull(param)
        assertEquals(0.0f, param?.minClamp)
        assertEquals(3.0f, param?.maxClamp)
    }

    @Test
    fun `test Phase 1 bundled filter headers parse and instantiate parameters`() {
        val filtersToTest = listOf(
            "kaleidoscope" to listOf("segments", "rotation", "zoom", "centerX", "centerY", "originOffset"),
            "radial_blur" to listOf("blurAmount", "centerX", "centerY", "decay", "exposure"),
            "rgb_split" to listOf("amount", "angle", "radialMode", "dispersionMode"),
            "polar_tunnel" to listOf("depth", "twist", "centerX", "centerY", "zoom", "symmetry", "depthFog"),
            "color_levels" to listOf("brightness", "contrast", "saturation", "gamma", "warmth", "tint", "filmicTone"),
            "gradient_map" to listOf("mixAmount", "palette", "cycleSpeed", "cycleOffset", "hueShift")
        )

        val shader = mockk<Shader>(relaxed = true)

        for ((filterName, expectedParams) in filtersToTest) {
            val resourcePath = "default_filters/$filterName.fs"
            val stream = javaClass.classLoader.getResourceAsStream(resourcePath)
            assertNotNull(stream, "Bundled filter resource must exist: $resourcePath")

            val source = stream!!.bufferedReader().use { it.readText() }
            val header = ISFParser.parseHeader(source)
            assertNotNull(header, "$filterName header should parse successfully")

            val filter = ISFFilter(filterName, filterName, header!!, shader)
            for (paramName in expectedParams) {
                assertTrue(
                    filter.parameters.containsKey(paramName),
                    "Filter $filterName should register parameter: $paramName"
                )
            }
        }
    }

    @Test
    fun `test Phase 2 bundled filter headers parse and instantiate parameters`() {
        val filtersToTest = listOf(
            "directional_blur" to listOf("blurAmount", "angle", "decay", "bidirectional", "exposure"),
            "wave_displace" to listOf("amplitude", "frequency", "speed", "rippleMode", "centerX", "centerY", "phaseOffset"),
            "pixelate" to listOf("pixelSize", "latticeMode", "colorDepth"),
            "retro_crt" to listOf("scanlineIntensity", "scanlineCount", "curvature", "phosphorMask", "vignette", "brightnessBoost"),
            "pinch_bulge" to listOf("amount", "radius", "centerX", "centerY"),
            "neon_edge" to listOf("edgeStrength", "threshold", "glowSpread", "palette", "backgroundBlend")
        )

        val shader = mockk<Shader>(relaxed = true)

        for ((filterName, expectedParams) in filtersToTest) {
            val resourcePath = "default_filters/$filterName.fs"
            val stream = javaClass.classLoader.getResourceAsStream(resourcePath)
            assertNotNull(stream, "Bundled filter resource must exist: $resourcePath")

            val source = stream!!.bufferedReader().use { it.readText() }
            val header = ISFParser.parseHeader(source)
            assertNotNull(header, "$filterName header should parse successfully")

            val filter = ISFFilter(filterName, filterName, header!!, shader)
            for (paramName in expectedParams) {
                assertTrue(
                    filter.parameters.containsKey(paramName),
                    "Filter $filterName should register parameter: $paramName"
                )
            }
        }
    }

    @Test
    fun `test Phase 3 bundled filter headers parse and instantiate parameters`() {
        val filtersToTest = listOf(
            "luma_displace" to listOf("refractAmount", "smoothness", "chromaDispersion", "flowSpeed"),
            "video_strobe" to listOf("rate", "freezeHold", "strobeMode", "dutyCycle"),
            "fluid_smear" to listOf("smearAmount", "curlScale", "flowSpeed", "decay", "gravity"),
            "thermal_scanner" to listOf("intensity", "mode", "thermalBloom", "sensorGrain", "vignette"),
            "mirror_sphere" to listOf("sphereRadius", "centerX", "centerY", "fresnelGlow", "chromaFringe", "backgroundMix")
        )

        val shader = mockk<Shader>(relaxed = true)

        for ((filterName, expectedParams) in filtersToTest) {
            val resourcePath = "default_filters/$filterName.fs"
            val stream = javaClass.classLoader.getResourceAsStream(resourcePath)
            assertNotNull(stream, "Bundled filter resource must exist: $resourcePath")

            val source = stream!!.bufferedReader().use { it.readText() }
            val header = ISFParser.parseHeader(source)
            assertNotNull(header, "$filterName header should parse successfully")

            val filter = ISFFilter(filterName, filterName, header!!, shader)
            for (paramName in expectedParams) {
                assertTrue(
                    filter.parameters.containsKey(paramName),
                    "Filter $filterName should register parameter: $paramName"
                )
            }
        }
    }

    @Test
    fun `test Phase 4 bundled filter headers parse and instantiate parameters`() {
        val filtersToTest = listOf(
            "halftone" to listOf("dotScale", "screenAngle", "mode", "smoothness", "paperTint", "mixRatio"),
            "anamorphic_streak" to listOf("streakIntensity", "streakLength", "threshold", "knee", "colorMode", "crossFlare"),
            "vhs_glitch" to listOf("trackingJitter", "headSwitching", "ycDelay", "rfDropouts", "tapeNoise", "mixRatio"),
            "vortex_swirl" to listOf("twist", "radius", "dispersion", "spiralArms", "centerX", "centerY"),
            "faceted_glass" to listOf("facetScale", "refraction", "dispersion", "bevelStrength", "facetTilt")
        )

        val shader = mockk<Shader>(relaxed = true)

        for ((filterName, expectedParams) in filtersToTest) {
            val resourcePath = "default_filters/$filterName.fs"
            val stream = javaClass.classLoader.getResourceAsStream(resourcePath)
            assertNotNull(stream, "Bundled filter resource must exist: $resourcePath")

            val source = stream!!.bufferedReader().use { it.readText() }
            val header = ISFParser.parseHeader(source)
            assertNotNull(header, "$filterName header should parse successfully")

            val filter = ISFFilter(filterName, filterName, header!!, shader)
            for (paramName in expectedParams) {
                assertTrue(
                    filter.parameters.containsKey(paramName),
                    "Filter $filterName should register parameter: $paramName"
                )
            }
        }
    }
}

