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
        val rounded = (v * 10000.0f).roundToInt() / 10000.0f
        return if (kotlin.math.abs(rounded) < 1e-6f) 0.0f else rounded
    }

    /** Wire protocol version. The web client refuses to mix versions (web/autopilot.js). */
    const val PROTOCOL_VERSION = 2

    /** One occupied FX slot as it goes on the wire: filter id, effective dry/wet, ISF input NAME -> value. */
    data class FxSlotSnapshot(val id: String, val dryWet: Float, val params: Map<String, Float>)

    private fun paramsJson(values: Map<String, Float>): JsonObject = buildJsonObject {
        for ((name, v) in values) put(name, JsonPrimitive(round4(v)))
    }

    /**
     * Three FX slots keyed "0".."2" (an object, not an array, so [computeDeltaPatch] can diff slot
     * parameters individually); an empty slot is JSON null. Mirrors the browser's `graph.js`.
     */
    fun fxSlotsJson(slots: List<FxSlotSnapshot?>): JsonObject = buildJsonObject {
        for (i in 0 until FxChain.SLOT_COUNT) {
            val slot = slots.getOrNull(i)
            if (slot == null) {
                put(i.toString(), JsonNull)
            } else {
                put(i.toString(), buildJsonObject {
                    put("id", JsonPrimitive(slot.id))
                    put("dryWet", JsonPrimitive(round4(slot.dryWet)))
                    put("params", paramsJson(slot.params))
                })
            }
        }
    }

    /** Effective (post-modulation, post-dip) FX chain state. */
    fun snapshotChain(chain: FxChain): List<FxSlotSnapshot?> =
        List(FxChain.SLOT_COUNT) { i ->
            val slot = chain.slots[i] ?: return@List null
            FxSlotSnapshot(
                id = slot.id,
                dryWet = chain.effectiveSlotWet(i),
                params = slot.parameters.mapValues { it.value.value }
            )
        }

    fun serializeDeck(deck: Deck): JsonObject {
        if (deck.isEmpty) return buildJsonObject { put("empty", JsonPrimitive(true)) }
        val src = deck.source

        return buildJsonObject {
            val sourceId = if (src is DynamicVisualSource) src.id else "unknown_source"
            put("source", JsonPrimitive(sourceId))
            // Keyed by the exact parameter name (ISF input NAME; Mandala's own names), the same
            // key the browser uses for the uniform.
            put("params", paramsJson(src.parameters.mapValues { it.value.value }))
            if (src is Mandala) {
                // Shader-ready values: the browser has no recipe table.
                put("mandala", paramsJson(src.uniformSnapshot()))
            }
            put("viewZoom", JsonPrimitive(round4(deck.viewZoom.value)))
            put("viewRotateZ", JsonPrimitive(round4(deck.viewRotateZ.value)))
            put("globalAlpha", JsonPrimitive(round4(src.globalAlpha.value)))
            put("fx", fxSlotsJson(snapshotChain(deck.fxChain)))
            put("fxDryWet", JsonPrimitive(round4(deck.fxChain.effectiveChainWet())))
        }
    }

    fun serializeMixer(mixer: Mixer): JsonObject = buildJsonObject {
        val balance01 = ((mixer.crossfade.value + 1.0f) * 0.5f).coerceIn(0.0f, 1.0f)
        put("balance", JsonPrimitive(round4(balance01)))
        put("alpha", JsonPrimitive(round4(mixer.masterLevel.value)))
        put("levelA", JsonPrimitive(round4(mixer.levelA.value)))
        put("levelB", JsonPrimitive(round4(mixer.levelB.value)))
        put("levelBG", JsonPrimitive(round4(mixer.levelBG.value)))
        val transition = mixer.transitionFilter
        put("transition", JsonPrimitive(transition?.id ?: "linear_crossfade"))
        put("transitionParams", paramsJson(
            transition?.parameters
                ?.filterKeys { !it.equals("progress", ignoreCase = true) }
                ?.mapValues { it.value.value }
                ?: emptyMap()
        ))
        put("fx", fxSlotsJson(snapshotChain(mixer.masterFxChain)))
        put("fxDryWet", JsonPrimitive(round4(mixer.masterFxChain.effectiveChainWet())))
    }

    fun serializeFullPreset(mixer: Mixer): JsonObject = buildJsonObject {
        put("deckA", serializeDeck(mixer.deckA))
        put("deckB", serializeDeck(mixer.deckB))
        put("deckBG", serializeDeck(mixer.deckBG))
        put("mixer", serializeMixer(mixer))
    }

    /**
     * The beat clock at the moment of sending: total beats since the beat anchor's origin and the
     * tempo. The browser extrapolates from the moment it receives this, so it needs no beat
     * detection of its own while a broadcast is live.
     */
    fun clockJson(beats: Double, bpm: Float): JsonObject = buildJsonObject {
        put("beats", JsonPrimitive(beats))
        put("bpm", JsonPrimitive(round4(bpm)))
    }

    fun currentClock(): JsonObject = clockJson(
        llm.slop.liquidlsd.cv.CVRegistry.getSynchronizedTotalBeats(),
        llm.slop.liquidlsd.cv.CVRegistry.get("bpm")
    )

    fun buildStateFullMessage(mixer: Mixer, clock: JsonObject = currentClock()): String =
        buildStateFullMessage(serializeFullPreset(mixer), clock)

    fun buildStateFullMessage(preset: JsonObject, clock: JsonObject): String {
        val root = buildJsonObject {
            put("type", JsonPrimitive("state_full"))
            put("v", JsonPrimitive(PROTOCOL_VERSION))
            put("preset", preset)
            put("clock", clock)
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

    /** [patch] may be empty: a heartbeat that only carries the clock. */
    fun buildStateDeltaMessage(patch: JsonObject, clock: JsonObject = currentClock()): String {
        val root = buildJsonObject {
            put("type", JsonPrimitive("state_delta"))
            put("patch", patch)
            put("clock", clock)
        }
        return json.encodeToString(root)
    }
}
