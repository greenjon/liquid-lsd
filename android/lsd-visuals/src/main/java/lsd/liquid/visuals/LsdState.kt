package lsd.liquid.visuals

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser

/**
 * What the visuals have on air and how their controls are set: the page's `LSD.state()` (web/embed.js), reported
 * to the host through `LSDHost.state` whenever it changes.
 */
data class LsdState(
    /** The scene on air (its preset name) and its channel number, 1-based. */
    val scene: String? = null,
    val channel: Int = 0,
    val channels: Int = 0,
    /** The FX chain on the scene (its display name), or null. */
    val chain: String? = null,
    /** The background on air and the one a step is heading to. */
    val background: String? = null,
    val backgroundPicked: String? = null,
    val skipped: Boolean = false,
    val effects: Boolean = true,
    /** "scenes", "hold" or "off". */
    val bgMode: String = "hold",
    val hold: Boolean = false,
    val holdBars: Int = 16,
    val autoRollBars: Int = 0,
    val follow: Boolean = true,
    val lockedChain: String? = null,
    val lockedTransition: String? = null,
    /** A desktop live broadcast has the picture: every control does nothing until it ends. */
    val live: Boolean = false,
    val broadcaster: String? = null,
    val bpm: Double = 0.0,
    val beatLocked: Boolean = false,
    val paused: Boolean = false,
) {
    companion object {
        /** Parses a state report; null if it isn't one. */
        fun parse(json: String): LsdState? = try {
            val o = JsonParser.parseString(json).asJsonObject
            LsdState(
                scene = o.str("name"), channel = o.int("channel"), channels = o.int("channels"), chain = o.str("chain"),
                background = o.str("background"), backgroundPicked = o.str("backgroundPicked"), skipped = o.bool("skipped"),
                effects = o.bool("effects", true), bgMode = o.str("bgMode") ?: "hold", hold = o.bool("hold"),
                holdBars = o.int("holdBars", 16), autoRollBars = o.int("autoRollBars"), follow = o.bool("follow", true),
                lockedChain = o.str("lockedChain"), lockedTransition = o.str("lockedTransition"), live = o.bool("live"),
                broadcaster = o.str("broadcaster"), bpm = o.num("bpm"), beatLocked = o.bool("beatLocked"), paused = o.bool("paused"),
            )
        } catch (_: Exception) {
            null
        }
    }
}

/** What the visuals can show: `LSD.catalog()` (web/embed.js). */
data class LsdCatalog(
    val scenes: List<Scene>,
    val backgrounds: List<Background>,
    val fxChains: List<Choice>,
    val transitions: List<Choice>,
) {
    /** A playlist scene: [channel] tunes to it; [runnable] false = too heavy for this device's GPU. */
    data class Scene(val channel: Int, val name: String, val tags: List<String>, val source: String?, val skipped: Boolean, val runnable: Boolean)
    data class Background(val index: Int, val name: String, val source: String?, val tags: List<String>)
    /** An FX chain or transition preset; [inRotation] = the autopilot picks it by itself. */
    data class Choice(val id: String, val name: String, val inRotation: Boolean)

    companion object {
        fun parse(json: String): LsdCatalog? = try {
            val o = JsonParser.parseString(json).asJsonObject
            fun objs(key: String) = o.getAsJsonArray(key)?.map { it.asJsonObject }.orEmpty()
            fun choices(key: String) = objs(key).map { Choice(it.str("id") ?: "", it.str("name") ?: "", it.bool("inRotation")) }
            LsdCatalog(
                scenes = objs("scenes").map {
                    Scene(it.int("channel"), it.str("name") ?: "", it.strings("tags"), it.str("source"), it.bool("skipped"), it.bool("runnable", true))
                },
                backgrounds = objs("backgrounds").map { Background(it.int("index"), it.str("name") ?: "", it.str("source"), it.strings("tags")) },
                fxChains = choices("fxChains"),
                transitions = choices("transitions"),
            )
        } catch (_: Exception) {
            null
        }
    }
}

private fun JsonObject.value(key: String): JsonElement? = get(key)?.takeUnless { it.isJsonNull }
private fun JsonObject.str(key: String): String? = value(key)?.asString
private fun JsonObject.int(key: String, default: Int = 0): Int = value(key)?.asInt ?: default
private fun JsonObject.num(key: String): Double = value(key)?.asDouble ?: 0.0
private fun JsonObject.bool(key: String, default: Boolean = false): Boolean = value(key)?.asBoolean ?: default
private fun JsonObject.strings(key: String): List<String> = value(key)?.asJsonArray?.map { it.asString }.orEmpty()
