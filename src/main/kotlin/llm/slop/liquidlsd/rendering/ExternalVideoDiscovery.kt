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
            else -> emptyList()
        }
    }

    private fun fetchSpoutServers(): List<String> {
        val results = mutableListOf<String>()
        try {
            val lib = com.sun.jna.Native.load("SpoutLibrary", SpoutLibrary::class.java)
            val ptr = lib.CreateSpout()
            if (ptr != null) {
                val count = lib.GetSenderCount(ptr)
                val nameBuffer = ByteArray(256)
                for (i in 0 until count) {
                    if (lib.GetSender(ptr, i, nameBuffer, 256)) {
                        val name = String(nameBuffer).trimEnd('\u0000')
                        if (name.isNotBlank()) {
                            results.add(name)
                        }
                    }
                }
                lib.ReleaseSpout(ptr)
            }
        } catch (e: Throwable) {
            // Silently ignore if SpoutLibrary is missing
        }
        return results
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
