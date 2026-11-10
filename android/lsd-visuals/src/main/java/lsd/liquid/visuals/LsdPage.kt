package lsd.liquid.visuals

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.AssetManager
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.annotation.RequiresApi
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream

private const val TAG = "LsdPage"

/**
 * The Liquid LSD web TV in its `?embed` mode (picture only), bundled in this module's assets/visuals and served
 * to a WebView from a made-up https origin, so it works offline. Data files downloaded by [ContentUpdates]
 * overlay the bundled ones. The page is driven through `window.LSD` (liquid-lsd `web/embed.js`) and calls back
 * through `window.LSDHost` (ready, failed).
 *
 * This is the plain-View half: [LsdVisuals] wraps it for Compose, and anything that needs a WebView outside
 * Compose (a `Presentation` on an external display) uses it directly.
 */
object LsdPage {
    const val HOST = "appassets.androidplatform.net"
    const val PAGE = "https://$HOST/visuals/index.html?embed"

    /**
     * A WebView loading the page. Callbacks arrive on the main thread. [onRenderGone] fires when the WebView's
     * renderer crashed or was killed; the WebView is dead then and must be replaced. Throws if the system has
     * no usable WebView (none installed, or being updated). With [onState] the page reports what is on air and how
     * its controls are set (`LSDHost.state`, parsed by [LsdState.parse]) whenever that changes.
     */
    @SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
    fun create(
        ctx: Context,
        debug: Boolean,
        onReady: () -> Unit,
        onFailed: (String) -> Unit,
        onRenderGone: () -> Unit,
        onState: ((String) -> Unit)? = null,
    ): WebView = WebView(ctx).apply {
        if (debug) WebView.setWebContentsDebuggingEnabled(true) // chrome://inspect over adb
        setBackgroundColor(Color.BLACK)
        isFocusable = false
        isFocusableInTouchMode = false
        isClickable = false
        isLongClickable = false
        isHapticFeedbackEnabled = false
        isVerticalScrollBarEnabled = false
        isHorizontalScrollBarEnabled = false
        overScrollMode = View.OVER_SCROLL_NEVER
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            mediaPlaybackRequiresUserGesture = false // the page's AudioContext starts by itself
            setSupportZoom(false)
        }
        val main = Handler(Looper.getMainLooper())
        // Without a state listener the page sees no LSDHost.state, so it doesn't build the reports at all.
        val bridge = if (onState == null) HostBridge(main, onReady, onFailed) else StateBridge(main, onReady, onFailed, onState)
        addJavascriptInterface(bridge, "LSDHost")
        webViewClient = AssetClient(ctx.assets, ContentUpdates.store(ctx).activeDir(), onRenderGone)
        webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(m: ConsoleMessage): Boolean {
                Log.d(TAG, "js ${m.messageLevel()}: ${m.message()} (${m.sourceId().substringAfterLast('/')}:${m.lineNumber()})")
                return true
            }
        }
        loadUrl(if (debug) "$PAGE&debug" else PAGE)
    }

    /** Sends [settings] to the page; Auto quality drops to Low while [powerSave] (the system's battery saver) is on. */
    fun applySettings(web: WebView, settings: VisualsSettings, powerSave: Boolean) {
        val quality = if (settings.quality == VisualQuality.AUTO && powerSave) VisualQuality.LOW else settings.quality
        web.evaluateJavascript(
            "(function(){var L=window.LSD;if(!L||!L.setOption)return;" +
                "L.setOption('quality','${quality.js}');" +
                "L.setOption('reactivity',${settings.reactivity});" +
                "L.setOption('reducedMotion','${settings.reducedMotion.js}');" +
                "L.setOption('live',${settings.live});" +
                (settings.maxPixels?.let { "L.setOption('maxPixels',$it);" } ?: "") + "})()",
            null,
        )
    }

    fun resume(web: WebView) = web.evaluateJavascript("window.LSD&&LSD.resume()", null)

    fun pause(web: WebView) = web.evaluateJavascript("window.LSD&&LSD.pause()", null)

    /** One chunk of mono 16-bit little-endian PCM, base64 (as [PcmTap] makes them), at [rate] Hz. */
    fun feed(web: WebView, base64: String, rate: Int) = web.evaluateJavascript("window.LSD&&LSD.feed('$base64',$rate)", null)
}

/** The page's callbacks (`window.LSDHost`); they arrive on a WebView thread, so they hop to the main thread. */
internal open class HostBridge(
    protected val handler: Handler,
    private val onReady: () -> Unit,
    private val onFailed: (String) -> Unit,
) {
    @JavascriptInterface
    fun ready() { handler.post(onReady) }

    @JavascriptInterface
    fun failed(reason: String?) { handler.post { onFailed(reason.orEmpty()) } }
}

/** [HostBridge] plus the state reports (`LSDHost.state(json)`, web/embed.js). */
internal class StateBridge(
    handler: Handler,
    onReady: () -> Unit,
    onFailed: (String) -> Unit,
    private val onState: (String) -> Unit,
) : HostBridge(handler, onReady, onFailed) {
    @JavascriptInterface
    fun state(json: String?) { if (json != null) handler.post { onState(json) } }
}

/**
 * Serves assets/visuals/ at https://appassets.androidplatform.net/visuals/ and refuses everything else. Data files
 * downloaded by [ContentUpdates] ([content], fixed when the page loads) win over the bundled copy.
 */
internal class AssetClient(
    private val assets: AssetManager,
    private val content: File?,
    private val onRenderGone: () -> Unit,
) : WebViewClient() {
    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
        val url = request.url
        val path = url.path.orEmpty().removePrefix("/")
        if (url.scheme != "https" || url.host != LsdPage.HOST) return reply(403, "Forbidden")
        if (!path.startsWith("visuals/") || path.contains("..")) return reply(404, "Not Found")
        val downloaded = content?.let { ContentStore.resolve(it, path.removePrefix("visuals/")) }
        val stream = if (downloaded != null) FileInputStream(downloaded)
        else try { assets.open(path) } catch (_: Exception) { return reply(404, "Not Found") }
        val mime = mimeFor(path)
        val text = mime.startsWith("text/") || mime == "application/json"
        return WebResourceResponse(mime, if (text) "utf-8" else null, 200, "OK", mapOf("Cache-Control" to "no-store"), stream)
    }

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest) = true

    // The framework only calls this on API 26+, where RenderProcessGoneDetail exists.
    @RequiresApi(26)
    override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
        Log.w(TAG, "WebView renderer gone (crashed=${detail.didCrash()})")
        onRenderGone()
        return true
    }

    private fun reply(status: Int, reason: String) =
        WebResourceResponse("text/plain", "utf-8", status, reason, emptyMap(), ByteArrayInputStream(ByteArray(0)))

    private fun mimeFor(path: String) = when (path.substringAfterLast('.', "").lowercase()) {
        "html" -> "text/html"
        "js", "mjs" -> "text/javascript"
        "css" -> "text/css"
        "json" -> "application/json"
        "frag", "vert", "fs", "lsd", "lsdplay", "lsdfxchain", "lsdtrans", "txt" -> "text/plain"
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        else -> "application/octet-stream"
    }
}
