package lsd.liquid.visuals

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.util.Base64
import android.util.Log
import android.view.View
import android.webkit.WebView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.delay
import java.util.concurrent.atomic.AtomicInteger

private const val TAG = "LsdVisuals"
private const val STARTUP_TIMEOUT_MS = 20_000L
private const val MAX_RESTARTS = 2
private const val FIRST_LOOK_GRACE_MS = 6_000L // the first scene needs a moment to load before a freeze can show it

/**
 * The Liquid LSD visuals as a Compose element (see [LsdPage]). The app's audio reaches the page through
 * [PcmTap] -> `LSD.feed()`, held back by [VisualsSettings.syncMs]. The view takes no touches. Drawing and analysis
 * run only while [active] and the app is on screen. [onFailed] fires if the picture can't start (no WebView, no
 * WebGL2, repeated renderer crashes, or no sign of life within 20 s), so the caller can show something else.
 * [control] (optional) drives the scenes, effects and backgrounds; [onState] (optional) receives what is on air
 * and how the controls are set whenever that changes.
 */
@Composable
fun LsdVisuals(
    settings: VisualsSettings,
    active: Boolean,
    onFailed: () -> Unit,
    modifier: Modifier = Modifier,
    control: LsdControl? = null,
    onState: ((LsdState) -> Unit)? = null,
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val handler = remember { Handler(Looper.getMainLooper()) }
    val feedHandler = remember { Handler(Looper.getMainLooper()) } // the audio feed's own queue, cleared on its own
    var restarts by remember { mutableIntStateOf(0) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    var ready by remember { mutableStateOf(false) }
    var visible by remember { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) }
    val powerSave = rememberPowerSave(context)
    val currentSettings by rememberUpdatedState(settings)
    val currentOnFailed by rememberUpdatedState(onFailed)
    val currentOnState by rememberUpdatedState(onState)
    val wantsState = onState != null

    // The control talks to the page only once it is up, and lets go of it when it goes.
    DisposableEffect(control, webView, ready) {
        control?.attach(if (ready) webView else null)
        onDispose { control?.attach(null) }
    }

    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) visible = true
            if (event == Lifecycle.Event.ON_STOP) visible = false
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    // A page that never says it is up (module failed to load, shader compile hung) counts as failed.
    LaunchedEffect(restarts, visible) {
        if (!visible) return@LaunchedEffect // a hidden page draws no frames, so it can't say it is up
        delay(STARTUP_TIMEOUT_MS)
        if (!ready) currentOnFailed()
    }

    LaunchedEffect(webView, ready, settings, powerSave) {
        val web = webView
        if (web == null || !ready) return@LaunchedEffect
        LsdPage.applySettings(web, settings, powerSave)
    }

    var warmedUp by remember { mutableStateOf(false) }
    LaunchedEffect(webView, ready, active, visible) {
        val web = webView
        if (web == null || !ready) return@LaunchedEffect
        Log.d(TAG, "picture active=$active visible=$visible ready=$ready")
        if (active && visible) {
            warmedUp = true
            web.onResume()
            LsdPage.resume(web)
        } else {
            // Just started while inactive: keep drawing until the first scene is on screen, then freeze.
            if (!warmedUp && visible) {
                delay(FIRST_LOOK_GRACE_MS)
                warmedUp = true
            }
            LsdPage.pause(web)
            web.onPause()
        }
    }

    // The audio feed: while the picture runs, audio goes to the page, held back by the sync setting.
    DisposableEffect(webView, ready, active, visible) {
        val web = webView
        if (web != null && ready && active && visible) {
            val inFlight = AtomicInteger(0)
            PcmTap.listener = { chunk, rate ->
                val delayMs = currentSettings.syncMs.toLong()
                // About 25 chunks a second; more than the sync delay plus a second's worth waiting means the UI thread is stuck.
                if (inFlight.get() < 25 + delayMs / 40) {
                    inFlight.incrementAndGet()
                    val b64 = Base64.encodeToString(chunk, Base64.NO_WRAP)
                    feedHandler.postDelayed({
                        inFlight.decrementAndGet()
                        LsdPage.feed(web, b64, rate)
                    }, delayMs)
                }
            }
        }
        onDispose {
            PcmTap.listener = null
            feedHandler.removeCallbacksAndMessages(null)
        }
    }

    key(restarts) {
        AndroidView(
            modifier = modifier,
            factory = { ctx ->
                try {
                    val debug = (ctx.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
                    LsdPage.create(
                        ctx, debug,
                        onReady = { Log.d(TAG, "page ready"); ready = true },
                        onFailed = { reason -> Log.w(TAG, "visuals failed: $reason"); currentOnFailed() },
                        onRenderGone = {
                            handler.post {
                                ready = false
                                if (restarts < MAX_RESTARTS) restarts++ else currentOnFailed()
                            }
                        },
                        onState = if (wantsState) { json -> LsdState.parse(json)?.let { currentOnState?.invoke(it) } } else null,
                    ).also { w -> handler.post { webView = w } } // state is not written during composition
                } catch (e: Exception) { // no WebView installed or it is being updated
                    Log.w(TAG, "WebView unavailable", e)
                    handler.post { currentOnFailed() }
                    View(ctx)
                }
            },
            onRelease = { view ->
                (view as? WebView)?.let { it.stopLoading(); it.destroy() }
                handler.post { if (webView === view) webView = null; ready = false }
            },
        )
    }
}

@Composable
private fun rememberPowerSave(context: Context): Boolean {
    val pm = remember { context.getSystemService(Context.POWER_SERVICE) as PowerManager }
    var saving by remember { mutableStateOf(pm.isPowerSaveMode) }
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, i: Intent?) { saving = pm.isPowerSaveMode }
        }
        context.registerReceiver(receiver, IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED))
        onDispose { context.unregisterReceiver(receiver) }
    }
    return saving
}
