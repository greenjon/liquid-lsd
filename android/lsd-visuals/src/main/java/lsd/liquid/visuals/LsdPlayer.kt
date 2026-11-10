package lsd.liquid.visuals

import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.util.Log
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.FrameLayout
import java.util.concurrent.atomic.AtomicInteger

private const val TAG = "LsdPlayer"
private const val STARTUP_TIMEOUT_MS = 20_000L
private const val MAX_RESTARTS = 2
private const val FIRST_LOOK_GRACE_MS = 6_000L // the first scene needs a moment to load before a freeze can show it

/**
 * One running picture, outside Compose: the page ([LsdPage]) in [view], its settings, pausing, the delayed audio
 * feed from [PcmTap], the startup timeout and renderer-crash restarts. [LsdVisuals] wraps one for Compose; an
 * external display shows one in a [LsdPresentation]. Main thread only; [context] decides the display (density,
 * size) the page renders for. Only one player should be [running] at a time: [PcmTap] feeds one listener.
 *
 * Callbacks: [onReady] each time a page (first, or after a restart) is up; [onFailed] when the picture can't
 * start (no WebView, no WebGL2, repeated crashes, no sign of life within 20 s); [onState] state reports.
 */
class LsdPlayer(
    private val context: Context,
    private val control: LsdControl? = null,
    private val onReady: () -> Unit = {},
    private val onFailed: () -> Unit = {},
    private val onState: ((LsdState) -> Unit)? = null,
) {
    private val handler = Handler(Looper.getMainLooper())
    private val feedHandler = Handler(Looper.getMainLooper()) // the audio feed's own queue, cleared on its own
    private val debug = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

    /** Holds the WebView; a restart swaps the WebView inside, so hosts keep this one view. */
    val view: FrameLayout = FrameLayout(context).apply { setBackgroundColor(android.graphics.Color.BLACK) }

    private var web: WebView? = null
    private var ready = false
    private var restarts = 0
    private var destroyed = false
    private var running = false
    private var warmedUp = false
    private var feeding = false

    var settings: VisualsSettings = VisualsSettings()
        set(v) { if (v != field) { field = v; applySettings() } }

    /** The system's battery saver: Auto quality drops to Low while it is on. */
    var powerSave: Boolean = false
        set(v) { if (v != field) { field = v; applySettings() } }

    /** True while the picture should draw and listen (on screen and wanted); false freezes it. */
    var active: Boolean = false
        set(v) { if (v != field) { field = v; applyActive() } }

    /** The WebView, once its page is up (for snapshots and the like). */
    val page: WebView? get() = web?.takeIf { ready }

    init { load() }

    private fun load() {
        ready = false
        handler.removeCallbacks(timeout)
        try {
            val w = LsdPage.create(
                context, debug,
                onReady = {
                    if (destroyed) return@create
                    Log.d(TAG, "page ready")
                    ready = true
                    handler.removeCallbacks(timeout)
                    control?.attach(web)
                    applySettings()
                    applyActive()
                    onReady()
                },
                onFailed = { reason -> Log.w(TAG, "visuals failed: $reason"); onFailed() },
                onRenderGone = { handler.post { restart() } },
                onState = onState?.let { cb -> { json: String -> LsdState.parse(json)?.let(cb) } },
            )
            web = w
            view.addView(w, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            handler.postDelayed(timeout, STARTUP_TIMEOUT_MS)
        } catch (e: Exception) { // no WebView installed or it is being updated
            Log.w(TAG, "WebView unavailable", e)
            handler.post { onFailed() }
        }
    }

    // A page that never says it is up (module failed to load, shader compile hung) counts as failed.
    private val timeout = Runnable { if (!ready && !destroyed) onFailed() }

    private fun restart() {
        if (destroyed) return
        dropWebView()
        if (restarts < MAX_RESTARTS) { restarts++; load() } else onFailed()
    }

    private fun applySettings() {
        val w = web ?: return
        if (ready) LsdPage.applySettings(w, settings, powerSave)
    }

    private fun applyActive() {
        val w = web ?: return
        if (!ready) return
        handler.removeCallbacks(freeze)
        Log.d(TAG, "picture active=$active")
        if (active) {
            warmedUp = true
            w.onResume()
            LsdPage.resume(w)
            running = true
            startFeed(w)
        } else {
            stopFeed()
            running = false
            // Just started while inactive: keep drawing until the first scene is on screen, then freeze.
            if (!warmedUp) { warmedUp = true; handler.postDelayed(freeze, FIRST_LOOK_GRACE_MS) } else freeze.run()
        }
    }

    private val freeze = Runnable {
        val w = web ?: return@Runnable
        if (active) return@Runnable
        LsdPage.pause(w)
        w.onPause()
    }

    /** True while this player draws and takes the audio. */
    val isRunning: Boolean get() = running

    // The audio feed: while the picture runs, audio goes to the page, held back by the sync setting.
    private fun startFeed(w: WebView) {
        if (feeding) return
        feeding = true
        val inFlight = AtomicInteger(0)
        PcmTap.listener = { chunk, rate ->
            val delayMs = settings.syncMs.toLong()
            // About 25 chunks a second; more than the sync delay plus a second's worth waiting means the UI thread is stuck.
            if (inFlight.get() < 25 + delayMs / 40) {
                inFlight.incrementAndGet()
                val b64 = Base64.encodeToString(chunk, Base64.NO_WRAP)
                feedHandler.postDelayed({
                    inFlight.decrementAndGet()
                    if (web === w) LsdPage.feed(w, b64, rate)
                }, delayMs)
            }
        }
    }

    private fun stopFeed() {
        if (!feeding) return
        feeding = false
        PcmTap.listener = null
        feedHandler.removeCallbacksAndMessages(null)
    }

    private fun dropWebView() {
        stopFeed()
        running = false
        ready = false
        handler.removeCallbacks(freeze)
        web?.let { w ->
            control?.detach(w)
            view.removeView(w)
            w.stopLoading()
            w.destroy()
        }
        web = null
    }

    /** Stops the picture for good and frees the WebView. */
    fun destroy() {
        if (destroyed) return
        destroyed = true
        handler.removeCallbacksAndMessages(null)
        dropWebView()
    }

}
