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

    fun fetchPipeWireStreams(): List<String> {
        val results = mutableSetOf<String>()
        try {
            val process = ProcessBuilder("pw-dump", "Node")
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start()
            val output = process.inputStream.bufferedReader().use { it.readText() }
            process.waitFor()

            if (output.isNotBlank()) {
                val nodeBlocks = output.split("{\n    \"id\":", "{\n  \"id\":")
                for (block in nodeBlocks) {
                    val isVideo = block.contains("\"media.class\": \"Video") ||
                            block.contains("\"media.class\": \"Stream/Output/Video") ||
                            block.contains("\"mediaType\": \"video\"") ||
                            block.contains("\"media.type\": \"Video\"")
                    if (isVideo) {
                        val descMatch = Regex("\"node.description\": \"([^\"]+)\"").find(block)
                        val nameMatch = Regex("\"node.name\": \"([^\"]+)\"").find(block)
                        val name = descMatch?.groupValues?.get(1) ?: nameMatch?.groupValues?.get(1)
                        if (!name.isNullOrBlank()) {
                            results.add(name)
                        }
                    }
                }
            }
        } catch (e: Throwable) {
            // Silently fall back
        }

        if (results.isEmpty()) {
            try {
                val process = ProcessBuilder("pw-cli", "list-objects", "Node")
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start()
                val output = process.inputStream.bufferedReader().use { it.readText() }
                process.waitFor()

                var currentName = ""
                var isVideoNode = false

                for (line in output.lines()) {
                    val trimmed = line.trim()
                    if (trimmed.startsWith("id ")) {
                        if (isVideoNode && currentName.isNotBlank()) {
                            results.add(currentName)
                        }
                        currentName = ""
                        isVideoNode = false
                    }
                    if (trimmed.contains("media.class = \"Video") || trimmed.contains("media.type = \"Video")) {
                        isVideoNode = true
                    }
                    if (trimmed.startsWith("node.description =")) {
                        val desc = trimmed.substringAfter("node.description =").trim('"', ' ', '\t')
                        if (desc.isNotBlank()) currentName = desc
                    } else if (trimmed.startsWith("node.name =") && currentName.isBlank()) {
                        val n = trimmed.substringAfter("node.name =").trim('"', ' ', '\t')
                        if (n.isNotBlank()) currentName = n
                    }
                }
                if (isVideoNode && currentName.isNotBlank()) {
                    results.add(currentName)
                }
            } catch (e: Throwable) {
                // Ignore
            }
        }

        return results.toList()
    }

    private var cachedSpoutLib: SpoutLibrary? = null
    private var spoutLoadFailed = false
    private val spoutNameBuffer = ByteArray(256)
    private var lastSpoutSenderCount = -1
    private var cachedSpoutList: List<String> = emptyList()

    private fun fetchSpoutServers(): List<String> {
        if (spoutLoadFailed) return emptyList()
        val lib = cachedSpoutLib ?: try {
            com.sun.jna.Native.load("SpoutLibrary", SpoutLibrary::class.java).also { cachedSpoutLib = it }
        } catch (e: Throwable) {
            spoutLoadFailed = true
            return emptyList()
        }

        return try {
            val ptr = lib.CreateSpout()
            if (com.sun.jna.Pointer.nativeValue(ptr) == 0L) return emptyList()
            try {
                val count = lib.GetSenderCount(ptr)
                if (count == 0) {
                    lastSpoutSenderCount = 0
                    cachedSpoutList = emptyList()
                    return emptyList()
                }
                val results = mutableListOf<String>()
                for (i in 0 until count) {
                    spoutNameBuffer.fill(0)
                    if (lib.GetSender(ptr, i, spoutNameBuffer, 256)) {
                        val name = String(spoutNameBuffer).trimEnd('\u0000')
                        if (name.isNotBlank()) {
                            results.add(name)
                        }
                    }
                }
                lastSpoutSenderCount = count
                cachedSpoutList = results
                results
            } finally {
                lib.ReleaseSpout(ptr)
            }
        } catch (e: Throwable) {
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
