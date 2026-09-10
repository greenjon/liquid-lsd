package llm.slop.liquidlsd.rendering.isf

import mu.KotlinLogging
import java.io.File
import java.nio.file.*
import java.nio.file.StandardWatchEventKinds.*
import java.util.concurrent.*

private val logger = KotlinLogging.logger {}

/**
 * Monitors active ISF directories for file creation, modification, and deletion
 * using Java NIO WatchService with a debounced update mechanism.
 */
class ISFFileWatcher(
    private val debounceMillis: Long = 250L,
    private val onReloadNeeded: () -> Unit
) {
    private var watchService: WatchService = FileSystems.getDefault().newWatchService()
    private val keys = ConcurrentHashMap<WatchKey, Path>()
    
    private var scheduler = createScheduler()
    private var watcherExecutor = createWatcherExecutor()

    private fun createScheduler() = Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "ISF-FileWatcher-Scheduler").apply { isDaemon = true }
    }

    private fun createWatcherExecutor() = Executors.newSingleThreadExecutor { r ->
        Thread(r, "ISF-FileWatcher-Thread").apply { isDaemon = true }
    }

    private val debounceFutures = ConcurrentHashMap<Path, ScheduledFuture<*>>()

    @Volatile
    private var isRunning = false

    private val monitoredPaths = mutableSetOf<Path>()

    @Synchronized
    fun watchDirectories(directories: List<File>) {
        stop()
        try {
            watchService = FileSystems.getDefault().newWatchService()
        } catch (e: Exception) {
            logger.error(e) { "Failed to create WatchService" }
        }
        scheduler = createScheduler()
        watcherExecutor = createWatcherExecutor()
        monitoredPaths.clear()

        for (dir in directories) {
            if (dir.exists() && dir.isDirectory) {
                registerAll(dir.toPath())
            }
        }

        start()
    }

    private fun registerAll(start: Path) {
        try {
            if (!Files.exists(start)) return
            val walker = Files.walk(start)
            walker.use { stream ->
                stream.filter { Files.isDirectory(it) }.forEach { path ->
                    registerPath(path)
                }
            }
        } catch (e: Exception) {
            logger.error(e) { "Failed to recursively register watch service for $start" }
        }
    }

    private fun registerPath(path: Path) {
        try {
            val key = path.register(
                watchService,
                ENTRY_CREATE,
                ENTRY_MODIFY,
                ENTRY_DELETE
            )
            keys[key] = path
            monitoredPaths.add(path)
            logger.debug { "Watching ISF directory: $path" }
        } catch (e: Exception) {
            logger.error(e) { "Failed to register path for watching: $path" }
        }
    }

    @Synchronized
    fun start() {
        if (isRunning) return
        isRunning = true

        watcherExecutor.submit {
            while (isRunning) {
                val key: WatchKey = try {
                    watchService.take()
                } catch (e: InterruptedException) {
                    break
                } catch (e: ClosedWatchServiceException) {
                    break
                } catch (e: Exception) {
                    break
                }

                val dir = keys[key]
                if (dir == null) {
                    key.reset()
                    continue
                }

                for (event in key.pollEvents()) {
                    val kind = event.kind()
                    if (kind == OVERFLOW) continue

                    @Suppress("UNCHECKED_CAST")
                    val ev = event as WatchEvent<Path>
                    val name = ev.context()
                    val child = dir.resolve(name)

                    // If a new directory is created, register it for watching
                    if (kind == ENTRY_CREATE && Files.isDirectory(child, LinkOption.NOFOLLOW_LINKS)) {
                        registerAll(child)
                    }

                    // Debounce reload trigger
                    triggerDebouncedReload(child)
                }

                val valid = key.reset()
                if (!valid) {
                    keys.remove(key)
                    if (keys.isEmpty()) {
                        break
                    }
                }
            }
        }
    }

    private fun triggerDebouncedReload(path: Path) {
        val extension = path.extension.lowercase()
        if (extension !in setOf("fs", "isf", "frag", "vs", "vert")) {
            return
        }

        // Cancel existing scheduled future for this path if present
        debounceFutures[path]?.cancel(false)

        try {
            val future = scheduler.schedule({
                debounceFutures.remove(path)
                logger.info { "ISF file change detected: $path. Triggering library live reload..." }
                try {
                    onReloadNeeded()
                } catch (e: Exception) {
                    logger.error(e) { "Error executing ISF library reload callback" }
                }
            }, debounceMillis, TimeUnit.MILLISECONDS)

            debounceFutures[path] = future
        } catch (e: RejectedExecutionException) {
            // Scheduler shut down; ignore
        }
    }

    @Synchronized
    fun stop() {
        isRunning = false
        try {
            watcherExecutor.shutdownNow()
            scheduler.shutdownNow()
            keys.keys().asIterator().forEach { it.cancel() }
            keys.clear()
            debounceFutures.values.forEach { it.cancel(true) }
            debounceFutures.clear()
            watchService.close()
        } catch (e: Exception) {
            logger.error(e) { "Error closing ISF file watcher service" }
        }
    }
}

private val Path.extension: String
    get() {
        val name = this.fileName.toString()
        val idx = name.lastIndexOf('.')
        return if (idx > 0 && idx < name.length - 1) name.substring(idx + 1) else ""
    }
