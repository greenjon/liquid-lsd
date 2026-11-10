package lsd.liquid.visuals

import android.webkit.WebView
import com.google.gson.Gson
import com.google.gson.JsonParser

/**
 * Drives the visuals' controls (web/embed.js `window.LSD`) from the app. Hand one to [LsdVisuals] (or attach it to
 * a WebView made by [LsdPage.create]); calls made before the page is up are dropped. Results come back as state
 * reports ([LsdState], the `onState` callback), not as return values: several commands finish asynchronously.
 * Main thread only. Every command does nothing while a desktop live broadcast has the picture ([LsdState.live]).
 */
class LsdControl {
    private var web: WebView? = null

    /** The page this control talks to, or null to detach. */
    fun attach(view: WebView?) { web = view }

    val attached: Boolean get() = web != null

    fun next() = call("next")
    fun prev() = call("prev")
    fun random() = call("random")
    /** Tunes to a scene by its channel number ([LsdCatalog.Scene.channel]). */
    fun channel(n: Int) = call("channel", n)
    /** Rerolls the random parameters of the scene on air. */
    fun roll() = call("roll")
    /** HOLD: the scene stays until released (no automatic scene changes). */
    fun hold(on: Boolean) = call("hold", on)
    /** How long a scene holds, in bars; the background holds twice that. */
    fun time(bars: Int) = call("time", bars)
    /** Rerolls the scene every this many bars; 0 = off. */
    fun autoRoll(bars: Int) = call("autoRoll", bars)
    fun effects(on: Boolean) = call("effects", on)
    fun nextChain() = call("nextChain")
    /** An FX chain ([LsdCatalog.Choice.id]) on the scene on air now; null = none. */
    fun chain(id: String?) = call("chain", id)
    /** Every scene takes this chain until unlocked (null). */
    fun lockChain(id: String?) = call("lockChain", id)
    /** Every scene change uses this transition until unlocked (null). */
    fun lockTransition(id: String?) = call("lockTransition", id)
    /** Calm scenes in breakdowns, a cut on the drop, tempo-weighted picks. */
    fun follow(on: Boolean) = call("follow", on)
    /** "scenes" (changes with the scenes), "hold" (stays) or "off". */
    fun backgroundMode(mode: String) = call("backgroundMode", mode)
    fun nextBackground() = call("nextBackground")
    fun prevBackground() = call("prevBackground")
    fun background(index: Int) = call("background", index)
    /** Skips the scene on air (it no longer comes up by itself), or restores it if skipped. */
    fun skip() = call("skip")
    fun restore(name: String) = call("restore", name)
    fun setSkipped(names: List<String>) = call("setSkipped", names)

    /** Everything that can be chosen; [onResult] gets null if the page isn't up. */
    fun catalog(onResult: (LsdCatalog?) -> Unit) {
        val w = web ?: return onResult(null)
        w.evaluateJavascript("(function(){var L=window.LSD;return L&&L.catalog?JSON.stringify(L.catalog()):null})()") { r ->
            onResult(unquote(r)?.let { LsdCatalog.parse(it) })
        }
    }

    /** The current state, once; [onResult] gets null if the page isn't up. */
    fun state(onResult: (LsdState?) -> Unit) {
        val w = web ?: return onResult(null)
        w.evaluateJavascript("(function(){var L=window.LSD;return L&&L.state?JSON.stringify(L.state()):null})()") { r ->
            onResult(unquote(r)?.let { LsdState.parse(it) })
        }
    }

    private fun call(method: String, vararg args: Any?) {
        web?.evaluateJavascript(script(method, *args), null)
    }

    companion object {
        private val gson = Gson()

        /** The JavaScript for one command: arguments JSON-encoded, so names with quotes are safe. */
        fun script(method: String, vararg args: Any?): String {
            require(method.all { it.isLetter() }) { "bad method $method" }
            val a = args.joinToString(",") { gson.toJson(it) }
            return "(function(){var L=window.LSD;if(L&&L.$method)L.$method($a);})()"
        }

        /** evaluateJavascript hands back a JSON value; a string result arrives quoted. */
        internal fun unquote(r: String?): String? {
            if (r == null || r == "null") return null
            return try { JsonParser.parseString(r).asString } catch (_: Exception) { null }
        }
    }
}
