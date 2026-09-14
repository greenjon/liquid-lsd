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
}
