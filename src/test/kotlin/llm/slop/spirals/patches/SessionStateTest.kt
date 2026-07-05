package llm.slop.spirals.patches

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import llm.slop.spirals.models.DeckPatchDto
import llm.slop.spirals.models.ParameterDto
import llm.slop.spirals.models.SessionStateDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class SessionStateTest {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    @Test
    fun testSessionStateDtoSerializationAndBackwardCompatibility() {
        // 1. Verify deserializing an older version (version 2) of SessionStateDto lacking the new optional fields
        val oldJsonStr = """
            {
                "version": 2,
                "deckA": {
                    "name": "Deck A",
                    "visualSourceType": "Mandala",
                    "parameters": {},
                    "feedbackParameters": {},
                    "globalAlpha": {
                        "baseValue": 1.0,
                        "baseMin": 1.0,
                        "baseMax": 1.0,
                        "randomizeBase": false,
                        "modulators": []
                    },
                    "isEmpty": false
                },
                "deckB": {
                    "name": "Deck B",
                    "visualSourceType": "Mandala",
                    "parameters": {},
                    "feedbackParameters": {},
                    "globalAlpha": {
                        "baseValue": 1.0,
                        "baseMin": 1.0,
                        "baseMax": 1.0,
                        "randomizeBase": false,
                        "modulators": []
                    },
                    "isEmpty": false
                },
                "deckC": {
                    "name": "Deck C",
                    "visualSourceType": "Mandala",
                    "parameters": {},
                    "feedbackParameters": {},
                    "globalAlpha": {
                        "baseValue": 1.0,
                        "baseMin": 1.0,
                        "baseMax": 1.0,
                        "randomizeBase": false,
                        "modulators": []
                    },
                    "isEmpty": true
                },
                "crossfade": {
                    "baseValue": 0.0,
                    "baseMin": 0.0,
                    "baseMax": 0.0,
                    "randomizeBase": false,
                    "modulators": []
                },
                "masterAlpha": {
                    "baseValue": 1.0,
                    "baseMin": 1.0,
                    "baseMax": 1.0,
                    "randomizeBase": false,
                    "modulators": []
                },
                "blendMode": 4.0,
                "queue": [],
                "activeIndex": -1,
                "isAutoVJEnabled": false
            }
        """.trimIndent()

        val decodedOld = json.decodeFromString<SessionStateDto>(oldJsonStr)
        assertEquals(2, decodedOld.version)
        assertNull(decodedOld.bloom)
        assertNull(decodedOld.xfadeSpeed)
        assertNull(decodedOld.queueNext)
        assertNull(decodedOld.queuePrev)
        assertFalse(decodedOld.isRepeatEnabled)
        assertFalse(decodedOld.isShuffleEnabled)

        // 2. Verify serializing and deserializing a new version (version 4) of SessionStateDto including the new fields
        val dummyParam = ParameterDto(
            baseValue = 0.5f,
            baseMin = 0.0f,
            baseMax = 1.0f,
            randomizeBase = false,
            modulators = emptyList()
        )
        val newSession = SessionStateDto(
            version = 4,
            deckA = decodedOld.deckA,
            deckB = decodedOld.deckB,
            deckC = decodedOld.deckC,
            crossfade = decodedOld.crossfade,
            masterAlpha = decodedOld.masterAlpha,
            blendMode = decodedOld.blendMode,
            queue = emptyList(),
            activeIndex = -1,
            isAutoVJEnabled = true,
            bloom = dummyParam,
            xfadeSpeed = dummyParam,
            queueNext = dummyParam,
            queuePrev = dummyParam,
            isRepeatEnabled = true,
            isShuffleEnabled = true
        )

        val newJsonStr = json.encodeToString(newSession)
        val decodedNew = json.decodeFromString<SessionStateDto>(newJsonStr)
        assertEquals(4, decodedNew.version)
        assertNotNull(decodedNew.bloom)
        assertEquals(0.5f, decodedNew.bloom?.baseValue)
        assertNotNull(decodedNew.xfadeSpeed)
        assertEquals(0.5f, decodedNew.xfadeSpeed?.baseValue)
        assertNotNull(decodedNew.queueNext)
        assertEquals(0.5f, decodedNew.queueNext?.baseValue)
        assertNotNull(decodedNew.queuePrev)
        assertEquals(0.5f, decodedNew.queuePrev?.baseValue)
        assertTrue(decodedNew.isRepeatEnabled)
        assertTrue(decodedNew.isShuffleEnabled)
    }
}
