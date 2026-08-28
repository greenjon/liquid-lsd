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
        put("decay", JsonPrimitive(round4(deck.fbDecay.baseValue)))
        put("gain", JsonPrimitive(round4(deck.fbGain.baseValue)))
        put("zoom", JsonPrimitive(round4(deck.fbZoom.baseValue)))
        put("rotate", JsonPrimitive(round4(deck.fbRotate.baseValue)))
        put("hueShift", JsonPrimitive(round4(deck.fbHueShift.baseValue)))
        put("blur", JsonPrimitive(round4(deck.fbBlur.baseValue)))
        put("chroma", JsonPrimitive(round4(deck.fbChroma.baseValue)))
        put("mode", JsonPrimitive(round4(deck.fbMode.baseValue)))
        put("kaleido", JsonPrimitive(round4(deck.fbKaleido.baseValue)))
    }

    fun serializeDeck(deck: Deck): JsonObject {
        val src = deck.source
        val fb = serializeFeedback(deck)

        return buildJsonObject {
            if (src is Mandala) {
                put("source", JsonPrimitive("mandala"))
                put("L1", JsonPrimitive(round4(src.parameters["L1"]?.baseValue ?: 0.4f)))
                put("L2", JsonPrimitive(round4(src.parameters["L2"]?.baseValue ?: 0.3f)))
                put("L3", JsonPrimitive(round4(src.parameters["L3"]?.baseValue ?: 0.2f)))
                put("L4", JsonPrimitive(round4(src.parameters["L4"]?.baseValue ?: 0.1f)))
                put("A", JsonPrimitive(src.recipe.a))
                put("B", JsonPrimitive(src.recipe.b))
                put("C", JsonPrimitive(src.recipe.c))
                put("D", JsonPrimitive(src.recipe.d))
                put("thickness", JsonPrimitive(round4(src.parameters["Thickness"]?.baseValue ?: 0.012f)))
                put("zoom", JsonPrimitive(round4(src.parameters["Zoom"]?.baseValue ?: 0.8f)))
                put("rotateZ", JsonPrimitive(round4(src.parameters["Rotate Z"]?.baseValue ?: 0.0f)))
                put("hueOffset", JsonPrimitive(round4(src.parameters["Hue Offset"]?.baseValue ?: 0.0f)))
                put("hueSweep", JsonPrimitive(round4(src.parameters["Hue Sweep"]?.baseValue ?: 0.3f)))
                put("depth", JsonPrimitive(round4(src.parameters["Depth"]?.baseValue ?: 0.35f)))
                put("maxR", JsonPrimitive(round4(src.parameters["Max R"]?.baseValue ?: 0.85f)))
                put("feedback", fb)
            } else if (src is DynamicSpiral || (src is DynamicVisualSource && src.id == "dynamic_spiral")) {
                put("source", JsonPrimitive("dynamic_spiral"))
                put("maxPoints", JsonPrimitive(round4(src.parameters["Max Points"]?.baseValue ?: 500f)))
                put("scale", JsonPrimitive(round4(src.parameters["Scale"]?.baseValue ?: 0.5f)))
                put("damping", JsonPrimitive(round4(src.parameters["Damping"]?.baseValue ?: 100.0f)))
                put("waveFreq", JsonPrimitive(round4(src.parameters["Wave Freq"]?.baseValue ?: 0.2f)))
                put("waveAmp", JsonPrimitive(round4(src.parameters["Wave Amp"]?.baseValue ?: 0.0f)))
                put("shear", JsonPrimitive(round4(src.parameters["Shear"]?.baseValue ?: 0.1f)))
                put("speed", JsonPrimitive(round4(src.parameters["Speed"]?.baseValue ?: 0.5f)))
                put("dotSize", JsonPrimitive(round4(src.parameters["Dot Size"]?.baseValue ?: 0.01f)))
                put("glow", JsonPrimitive(round4(src.parameters["Glow"]?.baseValue ?: 1.5f)))
                put("hueOffset", JsonPrimitive(round4(src.parameters["Hue Offset"]?.baseValue ?: 0.33f)))
                put("hueSweep", JsonPrimitive(round4(src.parameters["Hue Sweep"]?.baseValue ?: 0.01f)))
                put("trailDecay", JsonPrimitive(round4(src.parameters["Trail Decay"]?.baseValue ?: 0.85f)))
                put("feedback", fb)
            } else {
                val sourceId = if (src is DynamicVisualSource) src.id else "mandala"
                put("source", JsonPrimitive(sourceId))
                for ((key, param) in src.parameters) {
                    val cleanKey = key.replace(" ", "")
                    val camelKey = cleanKey.replaceFirstChar { it.lowercase() }
                    put(camelKey, JsonPrimitive(round4(param.baseValue)))
                }
                put("feedback", fb)
            }
        }
    }

    fun serializeMixer(mixer: Mixer): JsonObject = buildJsonObject {
        put("mode", JsonPrimitive(mixer.mode.baseValue.roundToInt()))
        val balance01 = ((mixer.crossfade.baseValue + 1.0f) * 0.5f).coerceIn(0.0f, 1.0f)
        put("balance", JsonPrimitive(round4(balance01)))
        put("alpha", JsonPrimitive(round4(mixer.masterAlpha.baseValue)))
        put("bloom", JsonPrimitive(round4(mixer.bloom.baseValue)))
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
