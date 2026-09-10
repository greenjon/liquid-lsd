package llm.slop.liquidlsd.rendering.isf

import mu.KotlinLogging
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.Future

private val logger = KotlinLogging.logger {}

/**
 * Registry that discovers, parses, indexes, and resolves collisions across all enabled ISF directory sources.
 */
object ISFLibraryRegistry {
    private val assetsMap = ConcurrentHashMap<String, ISFAsset>()
    private val executor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "ISF-Library-Scanner").apply { isDaemon = true }
    }

    private var currentScanFuture: Future<*>? = null

    private val fileWatcher = ISFFileWatcher(debounceMillis = 250L) {
        logger.info { "ISF file change detected via watcher. Re-scanning library..." }
        scanLibrary()
    }

    private var autoReloadEnabled = true

    fun setAutoReloadEnabled(enabled: Boolean) {
        autoReloadEnabled = enabled
        if (!enabled) {
            fileWatcher.stop()
        }
    }

    // Sorted snapshot of all assets — rebuilt after each scan, never re-sorted per-frame.
    @Volatile private var cachedAssets: List<ISFAsset> = emptyList()

    val allAssets: List<ISFAsset> get() = cachedAssets

    fun getAsset(id: String): ISFAsset? = assetsMap[id]

    /**
     * Synchronously scans all enabled and accessible ISF directories and resolves collisions.
     */
    @Synchronized
    fun scanLibrary(
        onProgress: (Int, Int, String) -> Unit = { _, _, _ -> }
    ): List<ISFAsset> {
        val resolvedDirs = ISFDirectoryManager.getResolvedDirectories()
        val enabledDirs = resolvedDirs.filter { it.config.isEnabled && it.status == DirectoryStatus.ACTIVE }

        if (autoReloadEnabled) {
            val dirsToWatch = enabledDirs.map { File(it.expandedPath) }
            fileWatcher.watchDirectories(dirsToWatch)
        }

        val rawDiscovered = mutableListOf<ISFAsset>()
        val totalDirs = enabledDirs.size
        var scannedCount = 0

        for (resolved in enabledDirs) {
            scannedCount++
            onProgress(scannedCount, totalDirs, resolved.expandedPath)
            val dir = File(resolved.expandedPath)
            val scannedAssets = ISFScanner.scanDirectory(dir, resolved.config.type, resolved.expandedPath)
            rawDiscovered.addAll(scannedAssets)
        }

        // Collision Resolution: Honor source priority (Custom > UserStandard > SystemStandard > BuiltIn)
        // Group by unique shader ID
        val winningAssets = mutableMapOf<String, ISFAsset>()
        for (asset in rawDiscovered) {
            val existing = winningAssets[asset.id]
            if (existing == null) {
                winningAssets[asset.id] = asset
            } else {
                // Compare priorities
                if (asset.sourceType.priority > existing.sourceType.priority) {
                    winningAssets[asset.id] = asset
                } else if (asset.sourceType.priority == existing.sourceType.priority) {
                    // Tie-breaker: latest discovered / custom override
                    winningAssets[asset.id] = asset
                }
            }
        }

        assetsMap.clear()
        assetsMap.putAll(winningAssets)
        // Rebuild the cached sorted snapshot once per scan — zero allocation on the render thread.
        cachedAssets = assetsMap.values.sortedBy { it.displayName }

        logger.info { "ISF Library scan completed. Indexed ${assetsMap.size} unique shaders from ${enabledDirs.size} directories." }
        return cachedAssets
    }

    /**
     * Asynchronously scans the ISF library on a background worker thread.
     */
    fun scanLibraryAsync(
        onProgress: (Int, Int, String) -> Unit = { _, _, _ -> },
        onComplete: (List<ISFAsset>) -> Unit = {}
    ) {
        currentScanFuture?.cancel(true)
        currentScanFuture = executor.submit {
            try {
                val results = scanLibrary(onProgress)
                onComplete(results)
            } catch (e: Exception) {
                if (e !is InterruptedException) {
                    logger.error(e) { "Error during asynchronous ISF library scan" }
                }
            }
        }
    }

    /**
     * Clears all indexed assets.
     */
    fun clear() {
        assetsMap.clear()
        cachedAssets = emptyList()
    }
}
