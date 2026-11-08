package llm.slop.liquidlsd.rendering

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import mu.KotlinLogging
import java.util.concurrent.atomic.AtomicBoolean

private val logger = KotlinLogging.logger {}

/**
 * Global singleton that polls for available Spout/Syphon servers periodically.
 */
object ExternalVideoDiscovery {

    private val _availableServers = MutableStateFlow<List<String>>(emptyList())
    val availableServers: StateFlow<List<String>> = _availableServers.asStateFlow()

    private val isRunning = AtomicBoolean(false)
    private var job: Job? = null
    
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun startPolling() {
        if (isRunning.getAndSet(true)) return
        
        job = scope.launch {
            while (isActive) {
                try {
                    val servers = fetchServers()
                    if (servers != _availableServers.value) {
                        _availableServers.value = servers
                    }
                } catch (e: Exception) {
                    logger.debug(e) { "Error fetching external video servers" }
                }
                delay(2000) // Poll every 2 seconds
            }
        }
    }

    fun stopPolling() {
        if (!isRunning.getAndSet(false)) return
        job?.cancel()
        job = null
    }

    private fun fetchServers(): List<String> {
        val osName = System.getProperty("os.name").lowercase()
        return when {
            osName.contains("win") -> fetchSpoutServers()
            osName.contains("mac") -> fetchSyphonServers()
            osName.contains("linux") -> fetchPipeWireStreams()
            else -> emptyList()
        }
    }

    /** Label shown in the browser -> PipeWire `node.name`, which is what `target.object` matches. */
    private val pipeWireTargets = java.util.concurrent.ConcurrentHashMap<String, String>()

    /** The `node.name` to connect to for a label from [fetchPipeWireStreams] (the label itself if unknown). */
    fun pipeWireTarget(label: String): String = pipeWireTargets[label] ?: label

    /**
     * Video sources other apps publish on PipeWire (`Video/Source` devices and `Stream/Output/Video` streams),
     * labelled by description, else node name. Our own receiver (a consumer) is left out.
     */
    fun fetchPipeWireStreams(): List<String> {
        val results = linkedSetOf<String>()
        try {
            val process = ProcessBuilder("pw-dump").redirectError(ProcessBuilder.Redirect.DISCARD).start()
            val output = process.inputStream.bufferedReader().use { it.readText() }
            process.waitFor()
            val root = kotlinx.serialization.json.Json.parseToJsonElement(output)
            for (obj in root as? kotlinx.serialization.json.JsonArray ?: return emptyList()) {
                val o = obj as? kotlinx.serialization.json.JsonObject ?: continue
                if ((o["type"] as? kotlinx.serialization.json.JsonPrimitive)?.content?.endsWith("Node") != true) continue
                val props = ((o["info"] as? kotlinx.serialization.json.JsonObject)?.get("props") as? kotlinx.serialization.json.JsonObject) ?: continue
                fun prop(k: String) = (props[k] as? kotlinx.serialization.json.JsonPrimitive)?.content
                val mediaClass = prop("media.class") ?: continue
                if (mediaClass != "Video/Source" && mediaClass != "Stream/Output/Video") continue
                val name = prop("node.name") ?: continue
                val base = prop("node.description")?.takeIf { it.isNotBlank() } ?: name
                // Two nodes can share a description (a webcam and its IR sensor both say "HP HD Camera"): number the rest.
                var label = base; var n = 2
                while (label in results) label = "$base ($n)".also { n++ }
                pipeWireTargets[label] = name
                results.add(label)
            }
        } catch (e: Throwable) {
            logger.debug { "PipeWire stream discovery failed: ${e.message}" }
        }
        return results.toList()
    }

    /** One SpoutLibrary object kept for the sender list (no GL needed for it). */
    private var spoutLister: SpoutApi? = null
    private val spoutNameBuffer = ByteArray(256)

    private fun fetchSpoutServers(): List<String> {
        val api = spoutLister ?: SpoutApi.create()?.also { spoutLister = it } ?: return emptyList()
        return try {
            val results = mutableListOf<String>()
            for (i in 0 until api.senderCount()) {
                spoutNameBuffer.fill(0)
                if (api.sender(i, spoutNameBuffer)) {
                    val end = spoutNameBuffer.indexOf(0).let { if (it < 0) spoutNameBuffer.size else it }
                    val name = String(spoutNameBuffer, 0, end, Charsets.UTF_8)
                    if (name.isNotBlank()) results.add(name)
                }
            }
            results
        } catch (e: Throwable) {
            logger.debug { "Spout sender list failed: ${e.message}" }
            emptyList()
        }
    }

    private fun fetchSyphonServers(): List<String> {
        return try {
            val bridge = SyphonBridge()
            bridge.getAvailableServers()
        } catch (e: Throwable) {
            emptyList()
        }
    }
}
