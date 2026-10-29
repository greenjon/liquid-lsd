package llm.slop.liquidlsd.presets

import kotlinx.serialization.decodeFromString
import llm.slop.liquidlsd.models.DeckPresetDto
import llm.slop.liquidlsd.rendering.Deck
import llm.slop.liquidlsd.rendering.FlameSource
import llm.slop.liquidlsd.rendering.PoolMember
import mu.KotlinLogging
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The Garden: kept offspring of an evolving source (plan: .planning/flame-source-plan.md, "Lineage"). An offspring is
 * a normal `.lsd` preset in `library/presets/garden/`, so the browser, playlists and queues already handle it.
 * Lineage lives in the tags: `garden`, `gen:N` and one `parent:<name>` per parent.
 */
object Garden {
    private val logger = KotlinLogging.logger {}
    const val FOLDER = "library/presets/garden"
    const val TAG = "garden"
    /** Breeding stock only: structure worth inheriting, not worth playing. Pool skips these; Breed draws them. */
    const val SEED_TAG = "seed"
    private const val GEN_PREFIX = "gen:"
    private const val PARENT_PREFIX = "parent:"

    fun lineageTags(generation: Int, parents: List<String>): List<String> =
        listOf(TAG, "$GEN_PREFIX$generation") + parents.map { "$PARENT_PREFIX$it" }

    fun generationOf(tags: List<String>): Int =
        tags.firstNotNullOfOrNull { t -> t.takeIf { it.startsWith(GEN_PREFIX) }?.removePrefix(GEN_PREFIX)?.toIntOrNull() } ?: 0

    fun parentsOf(tags: List<String>): List<String> =
        tags.filter { it.startsWith(PARENT_PREFIX) }.map { it.removePrefix(PARENT_PREFIX) }

    /** A child is one generation after its youngest parent: max(parent generations) + 1 (1 with no parents). */
    fun childGeneration(parentTags: List<List<String>>): Int = (parentTags.maxOfOrNull { generationOf(it) } ?: 0) + 1

    /** File-name stem for a source: lower-case letters and digits, dash-separated. */
    fun slug(sourceName: String): String =
        sourceName.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').ifEmpty { "offspring" }

    /** Next free `<slug>-NNNN` in [folder] (numbering continues after the highest existing one). */
    fun nextName(folder: File, sourceName: String): String {
        val stem = slug(sourceName)
        val used = folder.listFiles()?.mapNotNull { f ->
            Regex("^${Regex.escape(stem)}-(\\d+)\\.lsd$").find(f.name)?.groupValues?.get(1)?.toIntOrNull()
        } ?: emptyList()
        return "%s-%04d".format(stem, (used.maxOrNull() ?: 0) + 1)
    }

    /**
     * Keeps the deck's live state (what is playing) as a new offspring.
     * @param parents names of the presets it was bred from (empty for a random or drifted genome)
     * @return the file being written (saving is asynchronous)
     */
    fun keep(deck: Deck, deckIndex: Int = -1, parents: List<String> = emptyList(), parentTags: List<List<String>> = emptyList(), folder: File = File(FOLDER)): File {
        val name = nextName(folder, deck.source.displayName)
        val file = File(folder, "$name.lsd")
        // mid-morph the live values are a blend: save the nearer end instead. The DTO is built synchronously inside
        // saveDeckPresetAsync, so the live values only need to be swapped for that call.
        val end = (deck.source as? FlameSource)?.nearerEndValues()
        val live = end?.mapValues { it.key.baseValue }
        try {
            end?.forEach { (p, v) -> p.baseValue = v }
            PresetRepository.saveDeckPresetAsync(file, deck, name, lineageTags(childGeneration(parentTags), parents), deckIndex)
        } finally {
            live?.forEach { (p, v) -> p.baseValue = v }
        }
        // the thumbnail is what the deck shows right now (mid-morph that is the blend, not the saved end); skipped silently where there is no GL context
        runCatching { GardenThumbnails.capture(deck.getOutputTexture(), deck.cleanFBO.width, deck.cleanFBO.height, file) }
        // queued behind the save, so the next Pool / Breed draw can pick the new offspring
        refreshPool()
        return file
    }

    /** Absolute paths of the presets starred in the Sources browser, for weighting the Pool draws. */
    fun favoritePaths(): Set<String> =
        llm.slop.liquidlsd.ui.browser.BrowseFavorites.keys(llm.slop.liquidlsd.ui.browser.BrowseKind.SRC)
            .mapTo(HashSet()) { File(it).absoluteFile.normalize().path }

    private class Cached(val modified: Long, val sourceType: String?, val member: PoolMember?)
    /** Parsed files by absolute path. Touched only by [scan] (the preset IO thread, or a test). */
    private val cache = HashMap<String, Cached>()

    /** Kept offspring per source id as of the last [scan]. Immutable and replaced whole, so thread 0 reads it lock-free. */
    @Volatile private var poolSnapshot: Map<String, List<PoolMember>> = emptyMap()
    private val rescanQueued = AtomicBoolean(false)

    /**
     * The Pool and Breed candidates for source [sourceId]: the last scan of the Garden, never the disk (this is called from
     * `deck.update` on thread 0 when a morph target is drawn). Each call also queues a coalesced rescan, so files added,
     * changed or deleted outside the app (or by the browser) are in the next draw.
     */
    fun pool(sourceId: String): List<PoolMember> {
        if (rescanQueued.compareAndSet(false, true)) {
            PresetManager.presetIoExecutor.execute { rescanQueued.set(false); rescan(File(FOLDER)) }
        }
        return poolSnapshot[sourceId] ?: emptyList()
    }

    /**
     * Rescans [folder] on the preset IO thread and replaces the pool snapshot. Called at startup and after a Keep, where it
     * queues behind the Keep's own save on the same single-thread executor, so the new offspring is in the next draw.
     */
    fun refreshPool(folder: File = File(FOLDER)) {
        PresetManager.presetIoExecutor.execute { rescan(folder) }
    }

    private fun rescan(folder: File) {
        try { poolSnapshot = scan(folder) } catch (e: Exception) { logger.warn(e) { "Could not scan the Garden in ${folder.path}" } }
    }

    /**
     * Every readable preset in [folder] (recursively), grouped by source id, for the Pool and Breed strategies. Parsed
     * files are cached by modification time, so repeated scans only read new or changed files; unreadable or half-written
     * files are skipped. Blocking file I/O: preset IO thread only (see [refreshPool]), or a test.
     */
    @Synchronized
    internal fun scan(folder: File): Map<String, List<PoolMember>> {
        val files = folder.walkTopDown().filter { it.isFile && it.extension == "lsd" }.toList()
        val live = files.mapTo(HashSet()) { it.absolutePath }
        val root = folder.absolutePath
        cache.keys.retainAll { it in live || !it.startsWith(root) }
        val out = LinkedHashMap<String, MutableList<PoolMember>>()
        for (f in files) {
            val key = f.absolutePath
            val modified = f.lastModified()
            val entry = cache[key]?.takeIf { it.modified == modified } ?: try {
                val dto = PresetManager.json.decodeFromString<DeckPresetDto>(f.readText())
                Cached(modified, dto.visualSourceType, PoolMember(f.nameWithoutExtension, dto.tags, dto.parameters.mapValues { it.value.baseValue }, f.absolutePath, modified))
            } catch (e: Exception) {
                logger.debug(e) { "Skipping unreadable garden file ${f.name}" }
                Cached(modified, null, null)
            }.also { cache[key] = it }
            val type = entry.sourceType ?: continue
            val member = entry.member ?: continue
            out.getOrPut(type) { ArrayList() } += member
        }
        return out
    }
}

