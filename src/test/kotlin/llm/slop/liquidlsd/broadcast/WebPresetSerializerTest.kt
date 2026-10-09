package llm.slop.liquidlsd.broadcast

import kotlinx.serialization.json.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class WebPresetSerializerTest {

    @Test
    fun testComputeDeltaPatchDetectsChanges() {
        val initialFull = buildJsonObject {
            put("deckA", buildJsonObject {
                put("zoom", JsonPrimitive(0.8f))
                put("rotateZ", JsonPrimitive(0.0f))
                put("feedback", buildJsonObject {
                    put("decay", JsonPrimitive(0.04f))
                    put("gain", JsonPrimitive(0.96f))
                })
            })
            put("mixer", buildJsonObject {
                put("balance", JsonPrimitive(0.5f))
                put("mode", JsonPrimitive(4))
            })
        }

        val unchangedFull = buildJsonObject {
            put("deckA", buildJsonObject {
                put("zoom", JsonPrimitive(0.8f))
                put("rotateZ", JsonPrimitive(0.0f))
                put("feedback", buildJsonObject {
                    put("decay", JsonPrimitive(0.04f))
                    put("gain", JsonPrimitive(0.96f))
                })
            })
            put("mixer", buildJsonObject {
                put("balance", JsonPrimitive(0.5f))
                put("mode", JsonPrimitive(4))
            })
        }

        // When nothing changes, delta patch should be null
        assertNull(WebPresetSerializer.computeDeltaPatch(initialFull, unchangedFull))

        // Modify mixer balance and feedback decay
        val modifiedFull = buildJsonObject {
            put("deckA", buildJsonObject {
                put("zoom", JsonPrimitive(0.8f))
                put("rotateZ", JsonPrimitive(0.0f))
                put("feedback", buildJsonObject {
                    put("decay", JsonPrimitive(0.08f)) // changed
                    put("gain", JsonPrimitive(0.96f))
                })
            })
            put("mixer", buildJsonObject {
                put("balance", JsonPrimitive(0.75f)) // changed
                put("mode", JsonPrimitive(4))
            })
        }

        val patch = WebPresetSerializer.computeDeltaPatch(initialFull, modifiedFull)
        assertNotNull(patch)

        // Verify patch contains only the modified keys
        val mixerPatch = patch["mixer"]?.jsonObject
        assertNotNull(mixerPatch)
        assertEquals(0.75f, mixerPatch["balance"]?.jsonPrimitive?.float)
        assertNull(mixerPatch["mode"])

        val deckAPatch = patch["deckA"]?.jsonObject
        assertNotNull(deckAPatch)
        assertNull(deckAPatch["zoom"])
        val feedbackPatch = deckAPatch["feedback"]?.jsonObject
        assertNotNull(feedbackPatch)
        assertEquals(0.08f, feedbackPatch["decay"]?.jsonPrimitive?.float)
        assertNull(feedbackPatch["gain"])
    }

    @Test
    fun testBuildStateDeltaMessageFormat() {
        val patch = buildJsonObject {
            put("mixer", buildJsonObject {
                put("balance", JsonPrimitive(0.85f))
            })
        }

        val msg = WebPresetSerializer.buildStateDeltaMessage(patch)
        val parsed = Json.parseToJsonElement(msg).jsonObject

        assertEquals("state_delta", parsed["type"]?.jsonPrimitive?.content)
        assertEquals(0.85f, parsed["patch"]?.jsonObject?.get("mixer")?.jsonObject?.get("balance")?.jsonPrimitive?.float)
    }

    @Test
    fun testFxSlotsKeyedByIndexWithNullForEmptySlots() {
        val json = WebPresetSerializer.fxSlotsJson(
            listOf(
                WebPresetSerializer.FxSlotSnapshot("bloom", 0.5f, mapOf("Intensity" to 0.25f)),
                null,
                null
            )
        )
        assertEquals(setOf("0", "1", "2"), json.keys)
        val slot0 = json["0"]!!.jsonObject
        assertEquals("bloom", slot0["id"]?.jsonPrimitive?.content)
        assertEquals(0.5f, slot0["dryWet"]?.jsonPrimitive?.float)
        assertEquals(0.25f, slot0["params"]?.jsonObject?.get("Intensity")?.jsonPrimitive?.float)
        assertEquals(JsonNull, json["1"])
    }

    @Test
    fun testFxParamChangeDeltaIsJustThatParam() {
        fun full(intensity: Float) = buildJsonObject {
            put("deckA", buildJsonObject {
                put("fx", WebPresetSerializer.fxSlotsJson(
                    listOf(
                        WebPresetSerializer.FxSlotSnapshot("bloom", 1f, mapOf("Intensity" to intensity, "Radius" to 0.5f)),
                        null, null
                    )
                ))
            })
        }
        val patch = WebPresetSerializer.computeDeltaPatch(full(0.25f), full(0.5f))
        assertNotNull(patch)
        val params = patch["deckA"]!!.jsonObject["fx"]!!.jsonObject["0"]!!.jsonObject["params"]!!.jsonObject
        assertEquals(setOf("Intensity"), params.keys)
    }

    @Test
    fun testRemovingFxSlotPatchesNull() {
        fun full(withSlot: Boolean) = buildJsonObject {
            put("mixer", buildJsonObject {
                put("fx", WebPresetSerializer.fxSlotsJson(
                    listOf(if (withSlot) WebPresetSerializer.FxSlotSnapshot("invert", 1f, emptyMap()) else null, null, null)
                ))
            })
        }
        val patch = WebPresetSerializer.computeDeltaPatch(full(true), full(false))
        assertNotNull(patch)
        assertEquals(JsonNull, patch["mixer"]!!.jsonObject["fx"]!!.jsonObject["0"])
    }

    @Test
    fun testMessagesCarryVersionAndClock() {
        val clock = WebPresetSerializer.clockJson(12.5, 128f)
        val full = Json.parseToJsonElement(
            WebPresetSerializer.buildStateFullMessage(buildJsonObject { put("mixer", buildJsonObject {}) }, clock)
        ).jsonObject
        assertEquals("state_full", full["type"]?.jsonPrimitive?.content)
        assertEquals(WebPresetSerializer.PROTOCOL_VERSION, full["v"]?.jsonPrimitive?.int)
        assertEquals(12.5, full["clock"]?.jsonObject?.get("beats")?.jsonPrimitive?.double)

        val heartbeat = Json.parseToJsonElement(
            WebPresetSerializer.buildStateDeltaMessage(JsonObject(emptyMap()), clock)
        ).jsonObject
        assertEquals("state_delta", heartbeat["type"]?.jsonPrimitive?.content)
        assertEquals(128f, heartbeat["clock"]?.jsonObject?.get("bpm")?.jsonPrimitive?.float)
        assertEquals(0, heartbeat["patch"]?.jsonObject?.size)
    }
}
