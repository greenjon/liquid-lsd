package llm.slop.liquidlsd.broadcast

import kotlinx.serialization.json.JsonObject
import llm.slop.liquidlsd.rendering.Mixer
import mu.KotlinLogging
import java.net.URI
import java.net.http.HttpClient
import java.net.http.WebSocket
import java.time.Duration
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Manages the live broadcast WebSocket connection to the Liquid LSD relay server.
 * Handles state synchronization, throttled delta streaming, and automatic reconnection.
 */
object BroadcastEngine {
    private val logger = KotlinLogging.logger {}

    enum class ConnectionState {
        DISCONNECTED,
        CONNECTING,
        CONNECTED,
        ERROR
    }

    @Volatile
    var connectionState: ConnectionState = ConnectionState.DISCONNECTED
        private set

    @Volatile
    var isLive: Boolean = false
        private set

    @Volatile
    var lastError: String? = null
        private set

    @Volatile
    var lastConnectedUrl: String? = null
        private set

    private val ioExecutor: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "BroadcastEngine-IO").apply { isDaemon = true }
    }

    private val httpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .build()

    @Volatile
    private var activeWebSocket: WebSocket? = null
    private val reconnectAttempt = java.util.concurrent.atomic.AtomicInteger(0)
    private val needsFullSync = AtomicBoolean(false)
    private var lastSentFull: JsonObject? = null
    private var lastTickTimeNanos: Long = 0L
    @Volatile
    private var sendFuture: java.util.concurrent.CompletableFuture<WebSocket>? = null

    /**
     * Toggles live broadcast on or off.
     */
    fun toggleBroadcast(mixer: Mixer) {
        if (isLive) {
            stopBroadcast()
        } else {
            startBroadcast(mixer)
        }
    }

    /**
     * Starts live broadcast and initiates connection.
     */
    fun startBroadcast(mixer: Mixer) {
        if (isLive) return
        logger.info { "Starting live broadcast to ${BroadcastSettings.serverUrl}..." }
        isLive = true
        reconnectAttempt.set(0)
        lastError = null
        needsFullSync.set(true)
        connectAsync(mixer)
    }

    /**
     * Stops live broadcast and cleanly disconnects.
     */
    fun stopBroadcast() {
        logger.info { "Stopping live broadcast..." }
        isLive = false
        reconnectAttempt.set(0)
        connectionState = ConnectionState.DISCONNECTED
        closeActiveWebSocket("Broadcaster stopped")
    }

    /**
     * Requests an immediate full state sync on the next tick.
     */
    fun forceSync() {
        needsFullSync.set(true)
    }

    /**
     * Notifies the engine that a preset or setlist has changed so full state is pushed immediately.
     */
    fun notifyStateChanged() {
        needsFullSync.set(true)
    }

    private fun sanitizeWebSocketUri(rawUrl: String, token: String): URI {
        var base = rawUrl.trim()
        if (base.startsWith("http://", ignoreCase = true)) {
            base = "ws://" + base.substring(7)
        } else if (base.startsWith("https://", ignoreCase = true)) {
            base = "wss://" + base.substring(8)
        } else if (!base.startsWith("ws://", ignoreCase = true) && !base.startsWith("wss://", ignoreCase = true)) {
            base = "ws://$base"
        }
        val encodedToken = java.net.URLEncoder.encode(token, "UTF-8")
        val uri = URI.create(base)
        val query = if (uri.query == null) "role=broadcast&key=$encodedToken" else "${uri.query}&role=broadcast&key=$encodedToken"
        return URI(uri.scheme, uri.userInfo, uri.host, uri.port, uri.path, query, uri.fragment)
    }

    private fun connectAsync(mixer: Mixer) {
        if (!isLive) return
        connectionState = ConnectionState.CONNECTING

        ioExecutor.execute {
            try {
                val uri = sanitizeWebSocketUri(BroadcastSettings.serverUrl, BroadcastSettings.token)
                lastConnectedUrl = uri.toString()
                logger.info { "Connecting WebSocket to $uri..." }

                httpClient.newWebSocketBuilder()
                    .header("User-Agent", "Liquid-LSD-Desktop-Broadcaster/1.0")
                    .connectTimeout(Duration.ofSeconds(6))
                    .buildAsync(uri, object : WebSocket.Listener {
                        override fun onOpen(webSocket: WebSocket) {
                            logger.info { "WebSocket broadcast connection established!" }
                            activeWebSocket = webSocket
                            connectionState = ConnectionState.CONNECTED
                            lastError = null
                            reconnectAttempt.set(0)
                            needsFullSync.set(true)
                            webSocket.request(1)
                        }

                        override fun onText(webSocket: WebSocket, data: CharSequence, last: Boolean): CompletionStage<*>? {
                            logger.debug { "Received from relay: $data" }
                            webSocket.request(1)
                            return null
                        }

                        override fun onClose(webSocket: WebSocket, statusCode: Int, reason: String): CompletionStage<*>? {
                            logger.warn { "WebSocket closed by relay: code=$statusCode, reason='$reason'" }
                            activeWebSocket = null
                            if (statusCode == 4003) {
                                lastError = "Unauthorized: Invalid Broadcast Token"
                                isLive = false
                                connectionState = ConnectionState.ERROR
                            } else if (isLive) {
                                connectionState = ConnectionState.CONNECTING
                                scheduleReconnect(mixer)
                            } else {
                                connectionState = ConnectionState.DISCONNECTED
                            }
                            return null
                        }

                        override fun onError(webSocket: WebSocket, error: Throwable) {
                            val friendlyErr = formatConnectionError(error, uri)
                            logger.warn { "WebSocket broadcast error for $uri: $friendlyErr" }
                            activeWebSocket = null
                            lastError = friendlyErr
                            if (isLive) {
                                connectionState = ConnectionState.CONNECTING
                                scheduleReconnect(mixer)
                            } else {
                                connectionState = ConnectionState.ERROR
                            }
                        }
                    }).exceptionally { ex ->
                        val cause = ex.cause ?: ex
                        val friendlyErr = formatConnectionError(cause, uri)
                        logger.warn { "Failed to connect WebSocket to $uri: $friendlyErr" }
                        activeWebSocket = null
                        lastError = friendlyErr
                        if (isLive) {
                            connectionState = ConnectionState.CONNECTING
                            scheduleReconnect(mixer)
                        } else {
                            connectionState = ConnectionState.ERROR
                        }
                        null
                    }
            } catch (e: Exception) {
                val msg = e.message ?: e.javaClass.simpleName
                logger.error(e) { "Invalid connection URI or parameters: $msg" }
                lastError = msg
                connectionState = ConnectionState.ERROR
                if (isLive) scheduleReconnect(mixer)
            }
        }
    }

    private fun formatConnectionError(cause: Throwable, uri: URI): String {
        val root = generateSequence(cause) { it.cause }.lastOrNull() ?: cause
        val hostPort = if (uri.port != -1) "${uri.host}:${uri.port}" else (uri.host ?: uri.toString())
        return when {
            root is java.net.ConnectException || root is java.nio.channels.ClosedChannelException -> {
                "Connection refused: No relay server running at $hostPort"
            }
            root is java.net.http.HttpConnectTimeoutException || root is java.util.concurrent.TimeoutException -> {
                "Connection timed out connecting to $hostPort"
            }
            root is java.net.UnknownHostException -> {
                "Host not found: ${uri.host}"
            }
            root is javax.net.ssl.SSLException -> {
                "TLS/SSL handshake failed for $hostPort"
            }
            !root.message.isNullOrBlank() -> {
                root.message!!
            }
            else -> {
                root.javaClass.simpleName
            }
        }
    }

    private fun scheduleReconnect(mixer: Mixer) {
        if (!isLive) return
        val currentAttempt = reconnectAttempt.incrementAndGet()
        val delaySec = (1L shl (currentAttempt.coerceAtMost(4))).coerceIn(2L, 16L)
        logger.info { "Scheduling broadcast reconnect attempt #$currentAttempt in ${delaySec}s..." }
        ioExecutor.schedule({
            if (isLive && connectionState != ConnectionState.CONNECTED) {
                connectAsync(mixer)
            }
        }, delaySec, TimeUnit.SECONDS)
    }

    private fun closeActiveWebSocket(reason: String) {
        val ws = activeWebSocket
        activeWebSocket = null
        if (ws != null) {
            ioExecutor.execute {
                try {
                    ws.sendClose(WebSocket.NORMAL_CLOSURE, reason)
                } catch (e: Exception) {
                    logger.debug(e) { "Error closing WebSocket" }
                }
            }
        }
    }

    fun tick(mixer: Mixer) {
        if (!isLive || connectionState != ConnectionState.CONNECTED) return

        val now = System.nanoTime()
        val targetIntervalNanos = 1_000_000_000L / BroadcastSettings.targetFps.coerceIn(5, 60)
        if (now - lastTickTimeNanos < targetIntervalNanos) return
        lastTickTimeNanos = now

        val ws = activeWebSocket ?: return

        try {
            val currentFull = WebPresetSerializer.serializeFullPreset(mixer)

            if (sendFuture != null && !sendFuture!!.isDone) {
                return
            }

            if (needsFullSync.getAndSet(false) || lastSentFull == null) {
                val msg = WebPresetSerializer.buildStateFullMessage(mixer)
                sendTextAsync(ws, msg)
                lastSentFull = currentFull
            } else {
                val patch = WebPresetSerializer.computeDeltaPatch(lastSentFull!!, currentFull)
                if (patch != null) {
                    val msg = WebPresetSerializer.buildStateDeltaMessage(patch)
                    sendTextAsync(ws, msg)
                    lastSentFull = currentFull
                }
            }
        } catch (e: Exception) {
            logger.warn(e) { "Error during broadcast tick state extraction" }
        }
    }

    private fun sendTextAsync(ws: WebSocket, text: String) {
        try {
            sendFuture = ws.sendText(text, true).exceptionally { e ->
                logger.warn(e) { "Failed to send WebSocket payload" }
                ws
            }
        } catch (e: Exception) {
            logger.warn(e) { "Failed to initiate WebSocket payload send" }
        }
    }

    fun shutdown() {
        stopBroadcast()
        ioExecutor.shutdownNow()
    }
}
