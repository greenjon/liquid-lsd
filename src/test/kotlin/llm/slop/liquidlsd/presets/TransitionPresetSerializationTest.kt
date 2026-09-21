package llm.slop.liquidlsd.presets

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import llm.slop.liquidlsd.models.FXSlotDto
import llm.slop.liquidlsd.models.ParameterDto
import llm.slop.liquidlsd.models.TransitionPresetDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TransitionPresetSerializationTest {
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

    private fun createDummySlot(filterId: String): FXSlotDto {
        return FXSlotDto(
            filterId = filterId,
            enabled = true,
            dryWet = createDummyParameter(1.0f),
            parameters = mapOf(
                "smoothness" to createDummyParameter(0.2f),
                "angle" to createDummyParameter(45.0f)
            )
        )
    }

    @Test
    fun testTransitionPresetDtoSerialization() {
        val slot = createDummySlot("vortex_swirl")
        val preset = TransitionPresetDto(
            version = 1,
            name = "custom_radial",
            tags = listOf("radial", "wipe", "fast"),
            slot = slot
        )

        val jsonStr = json.encodeToString(preset)
        val decoded = json.decodeFromString<TransitionPresetDto>(jsonStr)

        assertEquals(1, decoded.version)
        assertEquals("custom_radial", decoded.name)
        assertEquals(listOf("radial", "wipe", "fast"), decoded.tags)
        assertEquals("vortex_swirl", decoded.slot.filterId)
        assertTrue(decoded.slot.enabled)
        assertEquals(1.0f, decoded.slot.dryWet.baseValue)
        assertNotNull(decoded.slot.parameters["smoothness"])
        assertEquals(0.2f, decoded.slot.parameters["smoothness"]?.baseValue)
        assertEquals(45.0f, decoded.slot.parameters["angle"]?.baseValue)
    }

    @Test
    fun testTransitionPresetDefaults() {
        val preset = TransitionPresetDto(
            name = "minimal_trans",
            slot = FXSlotDto(
                filterId = "linear_crossfade",
                enabled = true,
                dryWet = createDummyParameter(1.0f)
            )
        )

        val jsonStr = json.encodeToString(preset)
        val decoded = json.decodeFromString<TransitionPresetDto>(jsonStr)
        assertEquals(1, decoded.version)
        assertEquals("minimal_trans", decoded.name)
        assertTrue(decoded.tags.isEmpty())
        assertEquals("linear_crossfade", decoded.slot.filterId)
    }
}
