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
}
