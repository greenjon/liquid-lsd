package llm.slop.liquidlsd.broadcast

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import llm.slop.liquidlsd.rendering.*
import kotlin.math.roundToInt

/**
 * Serializes Desktop Liquid LSD Mixer and Deck states into the JSON schema
 * expected by the WebGL2 TV client (web/renderer.js, web/autopilot.js).
 */
object WebPresetSerializer {

    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    private fun round4(v: Float): Float {
        return (v * 10000.0f).roundToInt() / 10000.0f
    }

    fun serializeFeedback(deck: Deck): JsonObject = buildJsonObject {
        put("decay", JsonPrimitive(round4(deck.fbDecay.value)))
        put("gain", JsonPrimitive(round4(deck.fbGain.value)))
        put("zoom", JsonPrimitive(round4(deck.fbZoom.value)))
        put("rotate", JsonPrimitive(round4(deck.fbRotate.value)))
        put("hueShift", JsonPrimitive(round4(deck.fbHueShift.value)))
        put("blur", JsonPrimitive(round4(deck.fbBlur.value)))
        put("chroma", JsonPrimitive(round4(deck.fbChroma.value)))
        put("mode", JsonPrimitive(round4(deck.fbMode.value)))
        put("kaleido", JsonPrimitive(round4(deck.fbKaleido.value)))
    }

    fun serializeDeck(deck: Deck): JsonObject {
        val src = deck.source
        val fb = serializeFeedback(deck)

        return buildJsonObject {
            val sourceId = if (src is DynamicVisualSource) src.id else "unknown_source"
            put("source", JsonPrimitive(sourceId))
            for ((key, param) in src.parameters) {
                val cleanKey = key.replace(" ", "")
                val camelKey = cleanKey.replaceFirstChar { it.lowercase() }
                put(camelKey, JsonPrimitive(round4(param.value)))
            }
            if (src is Mandala) {
                put("a", JsonPrimitive(src.recipe.a))
                put("b", JsonPrimitive(src.recipe.b))
                put("c", JsonPrimitive(src.recipe.c))
                put("d", JsonPrimitive(src.recipe.d))
            } else if (src is DynamicSpiral) {
                put("integratedTime", JsonPrimitive(round4(src.integratedTime)))
                put("integratedShear", JsonPrimitive(round4(src.integratedShear)))
            }
            put("feedback", fb)
        }
    }

    fun serializeMixer(mixer: Mixer): JsonObject = buildJsonObject {
        put("mode", JsonPrimitive(mixer.mode.value.roundToInt()))
        val balance01 = ((mixer.crossfade.value + 1.0f) * 0.5f).coerceIn(0.0f, 1.0f)
        put("balance", JsonPrimitive(round4(balance01)))
        put("alpha", JsonPrimitive(round4(mixer.masterAlpha.value)))
        put("bloom", JsonPrimitive(round4(mixer.bloom.value)))
    }

    fun serializeFullPreset(mixer: Mixer): JsonObject = buildJsonObject {
        put("deckA", serializeDeck(mixer.deckA))
        put("deckB", serializeDeck(mixer.deckB))
        put("deckBG", serializeDeck(mixer.deckBG))
        put("mixer", serializeMixer(mixer))
    }

    fun buildStateFullMessage(mixer: Mixer): String {
        val root = buildJsonObject {
            put("type", JsonPrimitive("state_full"))
            put("preset", serializeFullPreset(mixer))
        }
        return json.encodeToString(root)
    }

    fun computeDeltaPatch(lastFull: JsonObject, currentFull: JsonObject): JsonObject? {
        val patch = mutableMapOf<String, JsonElement>()
        for ((key, curVal) in currentFull) {
            val prevVal = lastFull[key]
            if (prevVal != curVal) {
                if (curVal is JsonObject && prevVal is JsonObject) {
                    val subPatch = computeSubDelta(prevVal, curVal)
                    if (subPatch.isNotEmpty()) {
                        patch[key] = JsonObject(subPatch)
                    }
                } else {
                    patch[key] = curVal
                }
            }
        }
        for ((key, _) in lastFull) {
            if (!currentFull.containsKey(key)) {
                patch[key] = JsonNull
            }
        }
        if (patch.isEmpty()) return null
        return JsonObject(patch)
    }

    private fun computeSubDelta(prev: JsonObject, curr: JsonObject): Map<String, JsonElement> {
        val sub = mutableMapOf<String, JsonElement>()
        for ((k, curV) in curr) {
            val prevV = prev[k]
            if (prevV != curV) {
                if (curV is JsonObject && prevV is JsonObject) {
                    val deeper = computeSubDelta(prevV, curV)
                    if (deeper.isNotEmpty()) sub[k] = JsonObject(deeper)
                } else {
                    sub[k] = curV
                }
            }
        }
        for ((k, _) in prev) {
            if (!curr.containsKey(k)) {
                sub[k] = JsonNull
            }
        }
        return sub
    }

    fun buildStateDeltaMessage(patch: JsonObject): String {
        val root = buildJsonObject {
            put("type", JsonPrimitive("state_delta"))
            put("patch", patch)
        }
        return json.encodeToString(root)
    }
}
