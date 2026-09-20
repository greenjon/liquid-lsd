package llm.slop.liquidlsd.rendering

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import llm.slop.liquidlsd.models.DeckPresetDto
import llm.slop.liquidlsd.models.FXChainDto
import llm.slop.liquidlsd.models.FXSlotDto
import llm.slop.liquidlsd.models.MixerDto
import llm.slop.liquidlsd.models.ParameterDto
import llm.slop.liquidlsd.models.SessionStateDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class MasterFxTest {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    private fun dummyParam(value: Float = 0.5f) = ParameterDto(
        baseValue = value,
        baseMin = 0f,
        baseMax = 1f,
        randomizeBase = false,
        modulators = emptyList()
    )

    @Test
    fun testMixerDtoSerializationAndDeserialization() {
        val dummy = dummyParam(0.5f)
        val fxSlot = FXSlotDto(
            filterId = "invert",
            enabled = true,
            dryWet = dummyParam(0.8f),
            parameters = mapOf("amount" to dummyParam(0.4f))
        )

        val mixerDto = MixerDto(
            crossfade = dummy,
            masterAlpha = dummyParam(1.0f),
            blendMode = 4.0f,
            bloom = dummyParam(0.2f),
            xfadeSpeed = dummyParam(5.0f),
            queueNext = dummy,
            queuePrev = dummy,
            bgQueueNext = dummy,
            bgQueuePrev = dummy,
            tapTempo = dummy,
            levelA = dummyParam(1.0f),
            levelB = dummyParam(0.8f),
            levelBG = dummyParam(0.6f),
            levelPV = dummyParam(0.4f),
            masterLevel = dummyParam(0.9f),
            transitionSlot = FXSlotDto("linear_crossfade", true, dummy),
            masterFxSlots = listOf(fxSlot, null, null, null)
        )

        val jsonStr = json.encodeToString(mixerDto)
        val decoded = json.decodeFromString<MixerDto>(jsonStr)

        assertEquals(1.0f, decoded.levelA?.baseValue)
        assertEquals(0.8f, decoded.levelB?.baseValue)
        assertEquals(0.6f, decoded.levelBG?.baseValue)
        assertEquals(0.4f, decoded.levelPV?.baseValue)
        assertEquals(0.9f, decoded.masterLevel?.baseValue)
        assertEquals(4, decoded.masterFxSlots.size)
        assertNotNull(decoded.masterFxSlots[0])
        assertEquals("invert", decoded.masterFxSlots[0]?.filterId)
        assertTrue(decoded.masterFxSlots[0]!!.enabled)
        assertEquals(0.8f, decoded.masterFxSlots[0]!!.dryWet.baseValue)
    }

    @Test
    fun testSessionStateVersion6WithMixerDto() {
        val dummy = dummyParam(0.5f)
        val dummyDeck = DeckPresetDto(
            name = "Deck A",
            visualSourceType = "mandala",
            parameters = emptyMap(),
            feedbackParameters = emptyMap(),
            globalAlpha = dummy,
            isEmpty = false
        )

        val mixerDto = MixerDto(
            crossfade = dummy,
            masterAlpha = dummy,
            blendMode = 4.0f,
            bloom = dummy,
            xfadeSpeed = dummy,
            queueNext = dummy,
            queuePrev = dummy,
            bgQueueNext = dummy,
            bgQueuePrev = dummy,
            tapTempo = dummy,
            levelA = dummyParam(1.0f),
            levelB = dummyParam(0.9f),
            levelBG = dummyParam(0.8f),
            levelPV = dummyParam(0.7f),
            masterLevel = dummyParam(1.0f),
            masterFxSlots = listOf(FXSlotDto("crt_glitch", true, dummy))
        )

        val session = SessionStateDto(
            version = 6,
            deckA = dummyDeck,
            deckB = dummyDeck.copy(name = "Deck B"),
            deckBG = dummyDeck.copy(name = "Deck BG", isEmpty = true),
            deckPV = dummyDeck.copy(name = "Deck PV", isEmpty = true),
            mixer = mixerDto,
            queue = emptyList(),
            activeIndex = -1,
            isAutoVJEnabled = false
        )

        val jsonStr = json.encodeToString(session)
        val decoded = json.decodeFromString<SessionStateDto>(jsonStr)

        assertEquals(6, decoded.version)
        assertEquals(0.9f, decoded.mixer.levelB?.baseValue)
        assertEquals(1, decoded.mixer.masterFxSlots.size)
        assertEquals("crt_glitch", decoded.mixer.masterFxSlots[0]?.filterId)
    }

    @Test
    fun testMasterFxChainDtoSerialization() {
        val slot0 = FXSlotDto(filterId = "invert", enabled = true, dryWet = dummyParam(1.0f))
        val slot1 = FXSlotDto(filterId = "hue_shift", enabled = true, dryWet = dummyParam(0.5f))

        val chainDto = FXChainDto(
            name = "TestMasterChain",
            tags = listOf("master", "glitch"),
            slots = listOf(slot0, slot1, null, null)
        )

        val jsonStr = json.encodeToString(chainDto)
        val decoded = json.decodeFromString<FXChainDto>(jsonStr)

        assertEquals("TestMasterChain", decoded.name)
        assertEquals(listOf("master", "glitch"), decoded.tags)
        assertEquals(4, decoded.slots.size)
        assertNotNull(decoded.slots[0])
        assertEquals("invert", decoded.slots[0]?.filterId)
        assertNotNull(decoded.slots[1])
        assertEquals("hue_shift", decoded.slots[1]?.filterId)
    }
}
