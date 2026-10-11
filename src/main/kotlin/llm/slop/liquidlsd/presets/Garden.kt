package llm.slop.liquidlsd.presets

import kotlinx.serialization.decodeFromString
import llm.slop.liquidlsd.models.DeckPresetDto
import llm.slop.liquidlsd.rendering.Deck
import llm.slop.liquidlsd.rendering.FlameSource
import llm.slop.liquidlsd.rendering.PoolMember
import mu.KotlinLogging
import java.io.File

/**
 * The Garden: kept offspring of an evolving source (plan: .planning/flame-source-plan.md, "Lineage"). An offspring is
 * a normal `.lsd` preset in `library/presets/garden/`, so the browser, playlists and queues already handle it.
 * Lineage lives in the tags: `garden`, `gen:N` and one `parent:<name>` per parent.
 */
object Garden {
    private val logger = KotlinLogging.logger {}
    const val FOLDER = "library/presets/garden"
    const val TAG = "garden"
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
        return file
    }

    /** Absolute paths of the presets starred in the Sources browser, for weighting the Pool draws. */
    fun favoritePaths(): Set<String> =
        llm.slop.liquidlsd.ui.browser.BrowseFavorites.keys(llm.slop.liquidlsd.ui.browser.BrowseKind.SRC)
            .mapTo(HashSet()) { File(it).absoluteFile.normalize().path }

    private class Cached(val modified: Long, val member: PoolMember?)
    private val cache = HashMap<String, Cached>()

    /**
     * Every readable preset in [folder] (recursively) made from source [sourceId], for the Pool and Breed strategies.
     * Parsed files are cached by modification time, so repeated calls only touch new or changed files; unreadable or
     * half-written files are skipped.
     */
    @Synchronized
    fun pool(sourceId: String, folder: File = File(FOLDER)): List<PoolMember> {
        val files = folder.walkTopDown().filter { it.isFile && it.extension == "lsd" }.toList()
        val live = files.map { it.absolutePath }.toSet()
        cache.keys.retainAll { key -> key.substringBefore('|').let { it in live || !it.startsWith(folder.absolutePath) } }
        return files.mapNotNull { f ->
            val key = f.absolutePath + "|" + sourceId
            val modified = f.lastModified()
            val hit = cache[key]
            if (hit != null && hit.modified == modified) return@mapNotNull hit.member
            val member = try {
                val dto = PresetManager.json.decodeFromString<DeckPresetDto>(f.readText())
                if (dto.visualSourceType != sourceId) null
                else PoolMember(f.nameWithoutExtension, dto.tags, dto.parameters.mapValues { it.value.baseValue }, f.absolutePath, modified)
            } catch (e: Exception) {
                logger.debug(e) { "Skipping unreadable garden file ${f.name}" }
                null
            }
            cache[key] = Cached(modified, member)
            member
        }
    }
}
